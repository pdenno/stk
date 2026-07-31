(ns user
  "Development namespace for stk"
  (:require
   [clojure.pprint]
   [clojure.spec.alpha :as s]
   [clojure.tools.namespace.repl :as tools-ns :refer [refresh set-refresh-dirs]]
   [develop.repl :refer [ns-setup! undo-ns-setup!]] ; for use at REPL.
   [expound.alpha :as expound]
   [lambdaisland.classpath.watch-deps :as watch-deps] ; hot loading for deps.
   [mount.core :as mount]
   [stk.mcp-core] ; for mount
   [stk.mcp-util :as mutil]
   [taoensso.telemere :as tel]))

[ns-setup! undo-ns-setup!] ; for mount

;;; uncomment to enable hot loading for deps
(binding [*out* *err*] (watch-deps/start! {:aliases [:nrepl :dev :test]}))

(alter-var-root #'s/*explain-out* (constantly expound/printer))
(add-tap (bound-fn* clojure.pprint/pprint))
(set-refresh-dirs "src/stk" #_"test/stk") ; Put here as many as you need. test messes with ns-setup!
(s/check-asserts true) ; Error on s/assert, run s/valid? rather than just returning the argument.
(tel/call-on-shutdown! tel/stop-handlers!)

(defn ^:diag start
  "Start the sched6 system for development"
  []
  (mount/start)
  (let [var-syms (->> mutil/mcp-components vals (mapv :var))]
    (doseq [vs var-syms]
      (require (-> vs namespace symbol) :reload)))
  (mutil/reload-mcp-components! :all)
  ;(doseq [ns (vals schema/system-ds-map)] (require ns :reload))
  :started)

(defn stop
  "Stop the system"
  []
  (mount/stop)
  :stopped)

(defn ^:diag restart
  "Stop, refresh code, and start again"
  []
  (stop)
  (refresh :after 'user/start))
