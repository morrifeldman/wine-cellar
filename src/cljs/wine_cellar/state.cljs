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
   :sort {:field :created_at :direction :desc}
   :filters {:search "" :country nil :region nil :styles [] :style nil :varieties []}
   :view nil
   :chat {:open? false
          :provider :anthropic
          :messages []
          :conversations []
          :active-conversation nil
          :active-conversation-id nil
          :conversation-loading? false
          :conversations-loaded? false
          :messages-loading? false
          :creating-conversation? false
          :pinning-conversation-id nil
          :renaming-conversation-id nil
          :deleting-conversation-id nil
          :sidebar-open? false
          :error nil}})
