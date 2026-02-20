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

(defn safe-url?
  "Returns true if the URL is a valid http/https URL pointing to a public host.
   Blocks private/loopback IPs and non-http schemes (SSRF protection)."
  [url]
  (try (let [uri (java.net.URI. url)
             scheme (some-> uri
                            .getScheme
                            str/lower-case)
             host (some-> uri
                          .getHost
                          str/lower-case)]
         (and (contains? #{"http" "https"} scheme)
              (some? host)
              (not (str/blank? host))
              ;; Block loopback
              (not (= host "localhost"))
              (not (str/starts-with? host "127."))
              (not (= host "::1"))
              ;; Block private RFC-1918 ranges
              (not (str/starts-with? host "10."))
              (not (re-matches #"172\.(1[6-9]|2\d|3[01])\..+" host))
              (not (str/starts-with? host "192.168."))
              ;; Block link-local
              (not (str/starts-with? host "169.254."))
              ;; Block metadata services
              (not (= host "metadata.google.internal"))
              (not (str/starts-with? host "fd"))))
       (catch Exception _ false)))

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

(defn fetch-shopify-products
  "Attempts to fetch structured product data from a Shopify store's public API.
   Returns a formatted string on success, or nil on failure.
   Pass limit=nil to fetch all products (Shopify default: 50)."
  [origin & [limit]]
  (try (let [url
             (str origin "/products.json" (when limit (str "?limit=" limit)))
             {:keys [status body error]} @(http/get url
                                                    {:timeout fetch-timeout-ms
                                                     :headers {"User-Agent"
                                                               user-agent}
                                                     :follow-redirects true})]
         (when (and (nil? error) (= 200 status) body)
           (let [data (json/read-value body json/keyword-keys-object-mapper)
                 products (:products data)]
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
                    (str/join "\n"))))))
       (catch Exception _ nil)))

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
    (try (let [uri (java.net.URI. url)
               origin (str (.getScheme uri)
                           "://"
                           (.getHost uri)
                           (let [port (.getPort uri)]
                             (if (pos? port) (str ":" port) "")))
               ;; No limit when URL explicitly targets products.json (fetch
               ;; all)
               shopify-limit
               (when-not (str/includes? (.getPath uri) "products.json") 1)]
           ;; 1. Try Shopify API
           (if-let [shopify-text (fetch-shopify-products origin shopify-limit)]
             {:ok shopify-text}
             ;; 2. Fall back to raw HTML
             (let [{:keys [status body error]}
                   @(http/get url
                              {:timeout fetch-timeout-ms
                               :headers {"User-Agent" user-agent}
                               :follow-redirects true})]
               (cond error {:error (str "Fetch error: " error)}
                     (not= 200 status) {:error (str "HTTP " status)}
                     :else (let [text (strip-html (str body))
                                 truncated (if (> (count text) max-html-chars)
                                             (str (subs text 0 max-html-chars)
                                                  "...")
                                             text)]
                             {:ok truncated})))))
         (catch Exception e {:error (.getMessage e)}))))
