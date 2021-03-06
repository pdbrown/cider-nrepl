(ns cider.nrepl.middleware.complete
  "Code completion middleware.
  Delegates to the compliment library for the heavy lifting.
  Uses clj-suitable for ClojureScript completion."
  (:require
   [cider.nrepl.middleware.util.cljs :as cljs]
   [cider.nrepl.middleware.util.error-handling :refer [with-safe-transport]]
   [compliment.core :as complete]
   [compliment.utils :as complete-utils]
   [orchard.misc :as misc]
   [suitable.compliment.sources.cljs :as suitable-sources]))

;; TODO: Replace this with a presence check for shadow-cljs
;; See https://github.com/rksm/clj-suitable/issues/15 for details
(def suitable-enabled? (System/getProperty "cider.internal.test.cljs-suitable-enabled"))

(when suitable-enabled?
  (require 'suitable.complete-for-nrepl))

(def suitable-complete-for-nrepl
  (when suitable-enabled?
    @(resolve 'suitable.complete-for-nrepl/complete-for-nrepl)))

(def clj-sources
  "Source keywords for Clojure completions."
  [:compliment.sources.special-forms/literals
   :compliment.sources.class-members/static-members
   :compliment.sources.ns-mappings/ns-mappings
   :compliment.sources.resources/resources
   :compliment.sources.keywords/keywords
   :compliment.sources.local-bindings/local-bindings
   :compliment.sources.class-members/members
   :compliment.sources.namespaces-and-classes/namespaces-and-classes
   :compliment.sources.special-forms/special-forms])

(def cljs-sources
  "Source keywords for ClojureScript completions."
  [::suitable-sources/cljs-source])

(defn complete
  [{:keys [ns prefix symbol context extra-metadata enhanced-cljs-completion?] :as msg}]
  ;; TODO: Drop legacy symbol param in version 1.0
  (let [prefix (str (or prefix symbol))
        completion-opts {:ns             (misc/as-sym ns)
                         :context        context
                         :extra-metadata (set (map keyword extra-metadata))}]
    (if-let [cljs-env (cljs/grab-cljs-env msg)]
      (binding [suitable-sources/*compiler-env* cljs-env]
        (cond-> (complete/completions prefix (merge completion-opts {:sources cljs-sources}))
          (and suitable-enabled? enhanced-cljs-completion?)
          (concat (suitable-complete-for-nrepl (assoc msg :symbol prefix)))))
      (complete/completions prefix (merge completion-opts {:sources clj-sources})))))

(defn completion-doc
  [{:keys [ns sym symbol] :as msg}]
  ;; TODO: Drop legacy symbol param in version 1.0
  (let [sym (str (or sym symbol))
        ns (misc/as-sym ns)]
    (if-let [cljs-env (cljs/grab-cljs-env msg)]
      (binding [suitable-sources/*compiler-env* cljs-env]
        (complete/documentation sym ns {:sources cljs-sources}))
      (complete/documentation sym ns {:sources clj-sources}))))

(defn complete-reply [msg]
  {:completions (complete msg)})

(defn doc-reply
  [msg]
  {:completion-doc (completion-doc msg)})

(defn flush-caches-reply
  [_msg]
  (complete-utils/flush-caches)
  {})

(defn handle-complete [handler msg]
  (with-safe-transport handler msg
    "complete" complete-reply
    "complete-doc" doc-reply
    "complete-flush-caches" flush-caches-reply))
