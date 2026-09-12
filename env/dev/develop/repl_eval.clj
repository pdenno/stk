(ns develop.repl-eval
  "DEV-ONLY MCP tool: evaluate Clojure in the running stk JVM, giving the LLM
   the equivalent of clj-nrepl-eval. State persists across calls in the
   dedicated ns `stk-eval`.

   Hot-load and register from the REPL:
     (load-file \"env/dev/develop/repl_eval.clj\")
     (develop.repl-eval/register!)"
  (:require
   [stk.mcp-core   :as mcore]
   [stk.mcp-util   :as mutil]
   [stk.tool-system :as ts]
   [stk.util       :as util :refer [log!]]))

(swap! ts/tool-types conj {:tool-type :repl-eval})

(defonce eval-ns
  (let [n (create-ns 'stk-eval)]
    (binding [*ns* n] (refer-clojure))
    n))

(defn eval-code
  "Evaluate (possibly several) top-level forms in the stk-eval ns.
   Returns {:value <pr-str of last value> :out <captured output>} or
   {:err <message> :out <captured output>}."
  [code-str]
  (let [out (java.io.StringWriter.)]
    (binding [*ns* eval-ns *out* out *err* out]
      (try
        {:value (pr-str (load-string code-str))
         :out   (str out)}
        (catch Throwable e
          {:err (str (-> e class .getSimpleName) ": " (ex-message e)
                     (when-let [d (ex-data e)] (str " " (pr-str d))))
           :out (str out)})))))

(defmethod ts/tool-description :repl-eval [_]
  (str "Evaluate Clojure code in the running stk JVM (dev only). "
       "Multiple top-level forms allowed; the value of the last form is returned along with any printed output. "
       "Evaluation happens in the ns `stk-eval`, and state (defs, requires) persists there across calls. "
       "Use (require '[some.ns :as alias] :reload) to pick up file edits. "
       "Long-running forms risk client timeout; keep calls short."))

(defmethod ts/tool-schema :repl-eval [_]
  {:type "object"
   :properties {:code {:type "string"
                       :description "Clojure code to evaluate."}}
   :required [:code]})

(defmethod ts/validate-inputs :repl-eval [_ {:keys [code] :as inputs}]
  (if (string? code)
    {:valid? true}
    {:valid? false :error "Parameter 'code' (string) is required."}))

(defmethod ts/execute-tool :repl-eval [_ {:keys [code]}]
  (log! :debug (str "repl-eval: " code))
  (eval-code code))

(defmethod ts/format-results :repl-eval [_ {:keys [value out err]}]
  {:result [(str (when (seq out) (str out "\n"))
                 (or err value))]
   :error (boolean err)})

(defn register!
  "Add the repl_eval tool to the running MCP server."
  []
  (mutil/add-tools @mcore/mcp-server-atm [:repl-eval]))
