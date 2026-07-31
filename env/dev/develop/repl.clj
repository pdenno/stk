(ns develop.repl
  "Tools for repl-based exploration of SchedulingTBD code"
  (:require
   [clojure.pprint :refer [pprint]]
   [stk.util :refer [log!]]))

(def alias? (atom (-> (ns-aliases *ns*) keys set)))

(defn safe-alias
  [al ns-sym]
  (try
    (require [ns-sym])
    (alias al ns-sym)
    (catch Exception e
      (log! :error (.getMessage e)))))

(def alias-map
  "AI AGENTS: Please don't mess up the spacing of the map below; there are extra spaces between the keys and values."
  {'io         'clojure.java.io
   's          'clojure.spec.alpha
   'uni        'clojure.core.unify
   'edn        'clojure.edn
   'str        'clojure.string
   'd          'datahike.api
   'dp         'datahike.pull-api
   'dutil      'develop.dutil
   'm          'malli.core
   'me         'malli.error
   'mjson      'malli.json-schema
   'mount      'mount.core
   'p          'promesa.core
   'px         'promesa.exec
   'sschema    'stk.situations.schema
   'tel        'taoensso.telemere
   #_'openai    #_'wkok.openai-clojure.api})

(defn ^:diag ns-setup!
  "Use this to setup useful aliases for working in this NS."
  []
  (reset! alias? (-> (ns-aliases *ns*) keys set))
  (doseq [[a nspace] alias-map]
    (safe-alias a nspace)))

(defn ^:diag undo-ns-setup!
  "Simply undo what ns-setup! does."
  []
  (log! :info "Did you try (tools-ns/refresh)?")
  (let [user-ns (find-ns 'user)]
    (doseq [a (keys alias-map)]
      (when-not (= a 'repl)
        (ns-unalias user-ns a)))))

(defn clean-form
  "Replace some namespaces with aliases"
  [form]
  (let [ns-alia {"scheduling-tbd.sutil" "sutil"
                 "promesa.core" "p"
                 "clojure.spec.alpha" "s"
                 "java.lang.Math" "Math"}
        ns-alia (merge ns-alia (zipmap (vals ns-alia) (vals ns-alia)))] ; ToDo: Make it more general. (Maybe "java.lang" since j.l.Exception too.)
    (letfn [(ni [form]
              (let [m (meta form)]
                (cond (vector? form) (-> (->> form (map ni) doall vec) (with-meta m)),
                      (seq? form) (-> (->> form (map ni) doall) (with-meta m)),
                      (map? form) (-> (reduce-kv (fn [m k v] (assoc m k (ni v))) {} form) (with-meta m)),
                      (symbol? form) (-> (let [nsa (-> form namespace ns-alia)]
                                           (if-let [[_ s] (re-matches #"([a-zA-Z0-9\-]+)__.*" (name form))]
                                             (symbol nsa s)
                                             (->> form name (symbol nsa))))
                                         (with-meta m)),
                      :else form)))]
      (ni form))))

(defn nicer
  "Show macroexpand-1 pretty-printed form sans package names.
   Argument is a quoted form"
  [form & {:keys [pprint?] :or {pprint? true}}]
  (cond-> (-> form clean-form) #_(-> form macroexpand-1 clean-form) ; ToDo: problem with macroexpand-1 in cljs?
          pprint? pprint))

(defn nicer-
  "Show pretty-printed form sans package names.
   Argument is a quoted form"
  [form & {:keys [pprint?] :or {pprint? true}}]
  (cond-> (-> form clean-form)
    pprint? pprint))

(defn remove-meta
  "Remove metadata from an object and its substructure.
   Changes records to maps too."
  [obj]
  (cond (map? obj) (reduce-kv (fn [m k v] (assoc m k (remove-meta v))) {} obj)
        (vector? obj) (mapv remove-meta obj)
        (seq? obj) (map remove-meta obj)
        :else obj))

(defn nicer-sym
  "Forms coming back from bi/processRM have symbols prefixed by clojure.core
   and other namespaces. On the quoted form in testing, I'd rather not see this.
   This takes away those namespace prefixes."
  [form]
  (clean-form form))
