(ns wine-cellar.db.thumbnails
  "Rebuilds label thumbnails from the full-size label photo, for wines whose
   thumbnail was made back when uploads capped it at 100 pixels."
  (:require [honey.sql :as sql]
            [next.jdbc :as jdbc]
            [wine-cellar.common :as common]
            [wine-cellar.db.connection :refer [db-opts ds]])
  (:import [java.awt RenderingHints]
           [java.awt.image BufferedImage]
           [java.io ByteArrayInputStream ByteArrayOutputStream]
           [javax.imageio IIOImage ImageIO ImageWriteParam]))

(defn- read-image
  ^BufferedImage [^bytes bytes]
  (when bytes (ImageIO/read (ByteArrayInputStream. bytes))))

(defn- longest-side [^BufferedImage img] (max (.getWidth img) (.getHeight img)))

(defn- jpeg-bytes
  [^BufferedImage img quality]
  (let [writer (.next (ImageIO/getImageWritersByFormatName "jpeg"))
        params (doto (.getDefaultWriteParam writer)
                 (.setCompressionMode ImageWriteParam/MODE_EXPLICIT)
                 (.setCompressionQuality (float quality)))
        out (ByteArrayOutputStream.)]
    (with-open [ios (ImageIO/createImageOutputStream out)]
      (.setOutput writer ios)
      (.write writer nil (IIOImage. img nil nil) params))
    (.dispose writer)
    (.toByteArray out)))

(defn thumbnail-bytes
  "A JPEG of the photo scaled so its longest side is `label-thumbnail-size`,
   matching what the browser makes on upload."
  [^bytes image-bytes]
  (when-let [img (read-image image-bytes)]
    (let [scale (min 1.0 (/ common/label-thumbnail-size (longest-side img)))
          w (max 1 (int (* scale (.getWidth img))))
          h (max 1 (int (* scale (.getHeight img))))
          ;; JPEG has no alpha, and an ARGB source written as JPEG comes
          ;; out with shifted colours, so draw onto a plain RGB canvas.
          out (BufferedImage. w h BufferedImage/TYPE_INT_RGB)
          g (.createGraphics out)]
      (.setRenderingHint g
                         RenderingHints/KEY_INTERPOLATION
                         RenderingHints/VALUE_INTERPOLATION_BICUBIC)
      (.setRenderingHint g
                         RenderingHints/KEY_RENDERING
                         RenderingHints/VALUE_RENDER_QUALITY)
      (.drawImage g img 0 0 w h nil)
      (.dispose g)
      (jpeg-bytes out 0.8))))

(def ^:private old-thumbnail-size
  "Uploads used to cap thumbnails here. Matching that rather than anything under
   the current size means a wine whose photo is itself small isn't redone on
   every startup."
  100)

(defn- undersized?
  [^bytes thumb]
  (let [img (read-image thumb)]
    (or (nil? img) (<= (longest-side img) old-thumbnail-size))))

(defn- stale-thumbnail-ids
  []
  (->> (jdbc/execute! ds
                      (sql/format {:select [:id :label_thumbnail]
                                   :from :wines
                                   :where [:is-not :label_image nil]})
                      db-opts)
       (filter #(undersized? (:label_thumbnail %)))
       (map :id)))

(defn- rebuild!
  [id]
  (let [{:keys [label_image]}
        (jdbc/execute-one!
         ds
         (sql/format {:select [:label_image] :from :wines :where [:= :id id]})
         db-opts)]
    (when-let [thumb (thumbnail-bytes label_image)]
      (jdbc/execute-one! ds
                         (sql/format {:update :wines
                                      :set {:label_thumbnail thumb}
                                      :where [:= :id id]})
                         db-opts)
      true)))

(defn rebuild-undersized-thumbnails!
  "Remake every thumbnail from the old 100-pixel days, one wine at a time so a
   cellar of full-size photos never sits in memory at once. A photo that
   can't be decoded keeps its old thumbnail."
  []
  (let [ids (stale-thumbnail-ids)
        rebuilt (count (filter #(try (rebuild! %)
                                     (catch Exception e
                                       (tap> ["thumbnail rebuild failed" %
                                              (ex-message e)])
                                       false))
                               ids))]
    (tap> ["Rebuilt label thumbnails" rebuilt "of" (count ids)])
    rebuilt))
