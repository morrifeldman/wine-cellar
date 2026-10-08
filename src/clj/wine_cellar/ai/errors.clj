(ns wine-cellar.ai.errors)

(defn upstream-error
  "An ex-info for a failed call to an AI provider. Our client always gets a 502:
   the provider's own status (a 401 for a bad API key, say) is about our key,
   not the user's session. The data carries only the provider's status and
   message, never the raw http-kit response, whose request options hold the
   API key."
  ([provider message] (upstream-error provider message {}))
  ([provider message {:keys [status parsed cause]}]
   (let [detail (or (get-in parsed [:error :message])
                    (when (string? parsed) parsed))]
     (ex-info (str provider ": " (or detail message))
              (cond-> {:status 502} status (assoc :upstream-status status))
              cause))))
