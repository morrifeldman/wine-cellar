(ns wine-cellar.utils.web-fetch
  "URL fetching utilities for AI chat context enrichment.
   Supports Shopify /products.json API and plain HTML fallback."
  (:require [clojure.string :as str]
            [jsonista.core :as json]
            [org.httpkit.client :as http]))

(def ^:private fetch-timeout-ms 15000)

(def ^:private user-agent
  "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")

(def ^:private max-html-chars 8000)

(defn- public-address?
  [^java.net.InetAddress addr]
  (let [b (.getAddress addr)]
    (not (or (.isLoopbackAddress addr)
             (.isAnyLocalAddress addr)
             (.isLinkLocalAddress addr) ; includes 169.254.169.254 metadata
             (.isSiteLocalAddress addr) ; 10/8, 172.16/12, 192.168/16
             (.isMulticastAddress addr)
             ;; IPv6 unique local, fc00::/7 (Fly's private network is
             ;; fdaa::)
             (and (= 16 (alength b)) (= 0xfc (bit-and (aget b 0) 0xfe)))
             ;; IPv4 carrier-grade NAT, 100.64.0.0/10
             (and (= 4 (alength b))
                  (= 100 (bit-and (aget b 0) 0xff))
                  (= 64 (bit-and (aget b 1) 0xc0)))))))

(defn safe-url?
  "True for an http(s) URL whose host resolves only to public addresses.
   Blocks loopback, private, link-local and unique-local targets (SSRF)."
  [url]
  (try (let [uri (java.net.URI. url)
             scheme (some-> uri
                            .getScheme
                            str/lower-case)
             host (.getHost uri)]
         (and (contains? #{"http" "https"} scheme)
              (not (str/blank? host))
              (every? public-address?
                      (java.net.InetAddress/getAllByName host))))
       (catch Exception _ false)))

(def ^:private max-redirects 5)

(defn- http-get
  "GETs url, following redirects only to URLs that pass `allowed?`, which
   every hop must. Returns the http-kit response, or {:error msg}."
  ([url] (http-get url safe-url?))
  ([url allowed?]
   (loop [url url
          hops 0]
     (if-not (allowed? url)
       {:error (str "URL not allowed: " url)}
       (let [{:keys [status headers] :as response}
             @(http/get url
                        {:timeout fetch-timeout-ms
                         :headers {"User-Agent" user-agent}
                         :follow-redirects false})
             location (:location headers)]
         (cond (not (and (<= 300 (or status 0) 399) location)) response
               (>= hops max-redirects) {:error "Too many redirects"}
               :else (recur (str (.resolve (java.net.URI. url)
                                           ^String location))
                            (inc hops))))))))

(defn- format-shopify-product
  [{:keys [title vendor price body_html tags]}]
  (let [notes (when body_html
                (let [stripped (-> body_html
                                   (str/replace #"<[^>]+>" " ")
                                   (str/replace #"\s+" " ")
                                   str/trim)]
                  (subs stripped 0 (min 300 (count stripped)))))]
    (str/join ", "
              (remove str/blank?
                      [(when title (str "Wine: " title))
                       (when vendor (str "Producer: " vendor))
                       (when price (str "Price: $" price))
                       (when (seq tags) (str "Tags: " (str/join " " tags)))
                       (when notes (str "Notes: " notes))]))))

(defn- fetch-shopify-json
  [url]
  (try (let [{:keys [status body error]} (http-get url)]
         (when (and (nil? error) (= 200 status) body)
           (json/read-value body json/keyword-keys-object-mapper)))
       (catch Exception _ nil)))

(defn- format-products
  [products]
  (when (seq products)
    (->> products
         (map (fn [p]
                (let [variant (first (:variants p))
                      price (some-> variant
                                    :price)]
                  (format-shopify-product {:title (:title p)
                                           :vendor (:vendor p)
                                           :price price
                                           :body_html (:body_html p)
                                           :tags (:tags p)}))))
         (str/join "\n"))))

(defn fetch-shopify-products
  "Attempts to fetch structured product data from a Shopify store's public API.
   products-url is the full URL to products.json (with any query params).
   Returns a formatted string on success, or nil on failure."
  [products-url]
  (some-> (fetch-shopify-json products-url)
          :products
          format-products))

(defn fetch-shopify-product
  "Attempts to fetch a single product from a Shopify store's public API.
   product-url is the full URL to products/<handle>.json.
   Returns a formatted string on success, or nil on failure."
  [product-url]
  (some-> (fetch-shopify-json product-url)
          :product
          vector
          format-products))

(defn strip-html
  "Removes HTML tags and decodes common entities, returning plain text."
  [html]
  (-> html
      ;; Remove script and style blocks entirely
      (str/replace #"(?si)<script[^>]*>.*?</script>" " ")
      (str/replace #"(?si)<style[^>]*>.*?</style>" " ")
      ;; Strip remaining tags
      (str/replace #"<[^>]+>" " ")
      ;; Decode common entities
      (str/replace "&amp;" "&")
      (str/replace "&lt;" "<")
      (str/replace "&gt;" ">")
      (str/replace "&quot;" "\"")
      (str/replace "&#39;" "'")
      (str/replace "&nbsp;" " ")
      ;; Collapse whitespace
      (str/replace #"\s+" " ")
      str/trim))

(defn fetch-url-content
  "Fetches content from a URL and returns {:ok text} or {:error msg}.
   Strategy:
   1. Validate URL (SSRF protection)
   2. Try Shopify /products.json API
   3. Fall back to HTML fetch + strip, truncated to max-html-chars"
  [url]
  (if-not (safe-url? url)
    {:error (str "URL not allowed: " url)}
    (try
      (let [uri (java.net.URI. url)
            origin (str (.getScheme uri)
                        "://"
                        (.getHost uri)
                        (let [port (.getPort uri)]
                          (if (pos? port) (str ":" port) "")))
            path (.getPath uri)
            shopify-handle (second (re-matches #"/products/([^/.]+)" path))]
        ;; 1. Try Shopify API
        (if-let [shopify-text
                 (cond
                   ;; Specific product page — fetch /products/<handle>.json
                   shopify-handle
                   (fetch-shopify-product
                    (str origin "/products/" shopify-handle ".json"))
                   ;; Already targeting products.json — use as-is
                   (str/includes? path "products.json") (fetch-shopify-products
                                                         url)
                   ;; Unknown URL — probe store's current offers
                   :else (fetch-shopify-products
                          (str origin "/products.json?limit=1")))]
          {:ok shopify-text}
          ;; 2. Fall back to raw HTML
          (let [{:keys [status body error]} (http-get url)]
            (cond error {:error (str "Fetch error: " error)}
                  (not= 200 status) {:error (str "HTTP " status)}
                  :else (let [text (strip-html (str body))
                              truncated (if (> (count text) max-html-chars)
                                          (str (subs text 0 max-html-chars)
                                               "...")
                                          text)]
                          {:ok truncated})))))
      (catch Exception e {:error (.getMessage e)}))))
