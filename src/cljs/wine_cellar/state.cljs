(ns wine-cellar.state)

(def initial-app-state
  "Initial app state structure - shared between initialization and reset"
  {:wines []
   :classifications []
   :new-wine {}
   :error nil
   :loading? false
   :selected-wine-id nil
   :show-out-of-stock? false
   :show-wine-form? false
   :show-stats? false
   :show-filters? false
   :show-debug-controls? false
   :tasting-notes []
   :new-tasting-note {}
   :sort {:field nil :direction :asc}
   :filters {:search "" :country nil :region nil :style nil}
   :view nil
   :chat {:open? false :messages []}})