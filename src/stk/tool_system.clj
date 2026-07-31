(ns stk.tool-system
  "Core system for defining and registering MCP tools for scheduling domain.
   Adapted from clojure-mcp.tool-system but independent implementation."
  (:require
   [clojure.string :as str]
   [clojure.tools.logging :as log]
   [clojure.walk :as walk]))

(def ^:dynamic *mcp-exchange*
  "The MCP exchange object for the current tool invocation.
   Used by long-running tools to send progress notifications.
   Bound during tool execution in registration-map."
  nil)

;;;   "A set of the {:tool-type <tool-name-keyword>} forms"
(defonce tool-types (atom #{}))

(defonce pre-execute-hook  (atom nil))  ;; fn of zero args, called before execute-tool
(defonce post-execute-hook (atom nil))  ;; fn of zero args, called after callback

(defn tool-id->tool-type
  "Convert MCP tool name (string with underscores) to tool-type keyword (with dashes).
   Example: \"mzn_run_model\" -> :mzn-run-model"
  [tool-id]
  (-> tool-id (str/replace "_" "-") keyword))

(defmulti tool-name
  "Returns the name of the tool as a string. Dispatches on :tool-type."
  :tool-type)

(defmethod tool-name :default [tool-config]
  (-> tool-config
      :tool-type
      name
      (str/replace "-" "_")))

(defmulti tool-id :tool-type)

(defmethod tool-id :default [tool-config]
  (keyword (tool-name tool-config)))

(defmulti tool-description
  "Returns the description of the tool as a string. Dispatches on :tool-type."
  :tool-type)

(defmulti tool-schema
  "Returns the parameter validation schema for the tool. Dispatches on :tool-type."
  :tool-type)

(defmulti validate-inputs
  "Validates inputs against the schema and returns validated/coerced inputs.
   Throws exceptions for invalid inputs.
   Dispatches on :tool-type in the tool-config."
  (fn [tool-config _inputs] (:tool-type tool-config)))

(defmulti execute-tool
  "Executes the tool with the validated inputs and returns the result.
   Dispatches on :tool-type in the tool-config."
  (fn [tool-config _inputs] (:tool-type tool-config)))

(defmulti format-results
  "Formats the results from tool execution into the expected MCP response format.
   Must return a map with :result (a vector or sequence of strings) and :error (boolean).
   The MCP protocol requires that results are always provided as a sequence of strings,
   never as a single string.

   This standardized format is then used by the tool-fn to call the callback with:
   (callback (:result formatted) (:error formatted))

   Dispatches on :tool-type in the tool-config."
  (fn [tool-config _result] (:tool-type tool-config)))

;; Multimethod to assemble the registration map

(def diag (atom nil))

#_(defn dispatch-registration-map
    "Dispatch function for post-Q&A - actions to perform after interview completion"
    [tag & args]
    (reset! diag (list tag args))
    tag)

(defmulti registration-map
  "Creates the MCP registration map for a tool.
   Registrations maps have keys :name :id :description :schema and :tool-fn.
   Dispatches on :tool-type."
  :tool-type
  #_#'dispatch-registration-map)

;; Function to handle java.util.Map and other collection types before keywordizing
(defn convert-java-collections
  "Converts Java collection types to their Clojure equivalents recursively."
  [x]
  (clojure.walk/prewalk
   (fn [node]
     (cond
       (instance? java.util.Map node) (into {} node)
       (instance? java.util.List node) (into [] node)
       (instance? java.util.Set node) (into #{} node)
       :else node))
   x))

;; Helper function to keywordize map keys while preserving underscores
(defn keywordize-keys-preserve-underscores
  "Recursively transforms string map keys into keywords.
   Unlike clojure.walk/keywordize-keys, this preserves underscores.
   Works with Java collection types by converting them first."
  [m]
  (walk/keywordize-keys (convert-java-collections m))) ; ToDo: use malli/jasonista

;; Default implementation for registration-map
(defmethod registration-map :default [tool-config]
  {:name (tool-name tool-config)
   :id (tool-id tool-config)
   :description (tool-description tool-config)
   :schema (tool-schema tool-config)
   :tool-fn (fn [exchange params callback]
              (try
                ;; Bind exchange so tools can access it for progress notifications
                (binding [*mcp-exchange* exchange]
                  (when-let [hook @pre-execute-hook] (hook))
                  (let [keywordized-params (keywordize-keys-preserve-underscores params) ; ToDo: use malli/jasonista
                        validation-result (validate-inputs tool-config keywordized-params)
                        _ (when-not (:valid? validation-result)
                            (throw (ex-info "Validation failed" {:error-details (:error validation-result)})))
                        result (execute-tool tool-config keywordized-params)
                        formatted (format-results tool-config result)]
                    (callback (:result formatted) (:error formatted))
                    (when-let [hook @post-execute-hook] (hook))))
                (catch Exception e
                  (if (:error-details (ex-data e))
                    (log/warn (str (ex-message e) ": " (:error-details (ex-data e))))
                    (log/error e))
                  ;; On error, create a sequence of error messages
                  (let [error-msg (or (ex-message e) "Unknown error")
                        data (ex-data e)
                        ;; Construct error messages sequence
                        error-msgs (cond-> [error-msg]
                                     ;; Add any error-details from ex-data if available
                                     (and data (:error-details data))
                                     (concat (if (sequential? (:error-details data))
                                               (:error-details data)
                                               [(:error-details data)])))]
                    (callback error-msgs true)
                    (when-let [hook @post-execute-hook] (hook))))))})

;;; Common schemas
(def project-id-schema
  {:type "string"
   :description "The project ID returned from start_interview"})

(def conversation-id-schema
  {:type "string"
   :description "The conversation ID for this interview session"})

(def ds-id-schema
  {:type "string"
   :description "Discovery Schema ID (e.g., 'process/warm-up')"})

;;; Stuff particularly for sched6
(defn validate-required-params
  "Check that all required parameters are present"
  [inputs required-params]
  (doseq [param required-params]
    (when-not (get inputs param)
      (throw (ex-info (str "Missing required parameter: " (name param))
                      {:missing-param param
                       :inputs inputs}))))
  inputs)
