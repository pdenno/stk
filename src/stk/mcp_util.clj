(ns stk.mcp-util
  (:require
   [clojure.set]
   [clojure.string :as str]
   [mount.core :as mount]
   [stk.file-content :as file-content]
   [stk.json :as json]
   [stk.tool-system :as ts]
   [stk.util :as util :refer [log!]])
  (:import [io.modelcontextprotocol.server
            McpServerFeatures$AsyncToolSpecification
            McpServerFeatures$AsyncResourceSpecification]
           [io.modelcontextprotocol.spec
            McpSchema$Tool
            McpSchema$JsonSchema
            McpSchema$CallToolResult
            McpSchema$TextContent
            McpSchema$Prompt
            McpSchema$PromptArgument
            McpSchema$GetPromptRequest
            McpSchema$GetPromptResult
            McpSchema$PromptMessage
            McpSchema$Role
            McpSchema$Resource
            McpSchema$ReadResourceResult
            McpSchema$TextResourceContents
            McpSchema$ProgressNotification]
           [io.modelcontextprotocol.server
            McpServerFeatures$AsyncToolSpecification
            McpServerFeatures$AsyncPromptSpecification]
           [io.modelcontextprotocol.json McpJsonMapper]
           [reactor.core.publisher Mono]))

(def mcp-components
  "Vars for all the tools, resources and prompts, mount/defstate. The keys correspond to namespaces, not tools."
  ;; You do not want these in the :require of the ns declaration
  {#_:agent     #_{:var 'stk.tools.agent.agent-core/agent-tools :type :tool}})

;;;  "A map with keys :tools :resources :prompts with values being their names (not clojure idiomatic keys with hyphens).
;;;   If you want the whole thing, you can use ts/registration-map, though it creates one."
;;; (->> @mutil/mcp-components-atm :tools)
(defonce ^:diag mcp-components-atm
  (atom {:tools #{}
         :resources #{}
         :prompts #{}
         :tool-maps {}
         :resource-maps {}
         :prompt-maps {}}))

(defn create-mono-from-callback
  "Creates a function that takes the exchange and the arguments map and
  returns a Mono promise. The callback function should take three arguments:
   - exchange: The MCP exchange object (can be used for progress notifications)
   - arguments: The arguments map sent in the request
   - continuation: A function that will be called with the result and will fulfill the promise

  Note: Long-running tools should send progress notifications via exchange.progressNotification()
  to keep the SSE connection alive. The connection may timeout after ~60 seconds without activity."
  [callback-fn]
  (fn [exchange arguments]
    (Mono/create
     (reify java.util.function.Consumer
       (accept [_this sink]
         (try
           (callback-fn
            exchange
            arguments
            (fn [result]
              (.success sink result)))
           (catch Exception e
             (log! :error (str "Error in MCP tool callback: " (.getMessage e)))
             (.error sink e))))))))

(defn send-progress!
  "Send a progress notification to keep the SSE connection alive during long operations.
   Parameters:
   - exchange: The MCP exchange object from tool invocation
   - progress: Current progress value (0.0 to 1.0)
   - total: Total expected value (typically 1.0)
   - message: Human-readable progress message

   Note: This is a no-op if exchange is nil. The progress notification helps prevent
   SSE connection timeouts during long-running tools like delegate_DS_interview.

   Progress token is set to a placeholder since Claude Code may not send one.
   The notification is sent via exchange.progressNotification()."
  [exchange progress total message]
  (when exchange
    (try
      ;; Constructor: (Object progressToken, double progress, Double total, String message)
      ;; Using a placeholder token since Claude Code may not provide one
      (let [notification (McpSchema$ProgressNotification.
                          "progress-token"  ; progressToken (placeholder)
                          (double progress) ; progress
                          (Double. total)   ; total (boxed Double)
                          message)]         ; message
        (.progressNotification exchange notification))
      (catch Exception e
        (log! :debug (str "Progress notification failed (client may not support it): " (.getMessage e)))))))

(defn adapt-result [result]
  (cond
    (string? result) (McpSchema$TextContent. result)
    (file-content/file-response? result)
    (file-content/file-response->file-content result)
    :else (McpSchema$TextContent. " ")))

(defn ^McpSchema$CallToolResult adapt-results [list-str error?]
  (McpSchema$CallToolResult. (vec (keep adapt-result list-str)) error?))

(defn create-async-tool
  "Creates an AsyncToolSpecification with the given parameters.

   Takes a map with the following keys:
    :name         - The name of the tool
    :description  - A description of what the tool does
    :schema       - JSON schema for the tool's input parameters
    :tool-fn      - Function that implements the tool's logic.
                    Signature: (fn [exchange args-map clj-result-k] ... )
                      * exchange     - ignored (or used for advanced features)
                      * arg-map      - map with string keys representing the mcp tool call args
                      * clj-result-k - continuation fn taking vector of strings and boolean error flag."
  [{:keys [name description schema tool-fn]}]
  (let [tool-name (clojure.core/name name)
        tool-desc (str description)
        schema-json (json/write-str schema)
        ;; Parse JSON string into JsonSchema object for MCP SDK 0.15.0
        json-mapper (McpJsonMapper/getDefault)
        json-schema (.readValue json-mapper schema-json McpSchema$JsonSchema)
        ;; Use builder pattern for MCP SDK 0.15.0
        mcp-tool (-> (McpSchema$Tool/builder)
                     (.name tool-name)
                     (.description tool-desc)
                     (.inputSchema json-schema)
                     (.build))
        mono-fn (create-mono-from-callback
                 (fn [exchange arg-map mono-fill-k]
                   (let [clj-result-k
                         (fn [res-list error?]
                           (mono-fill-k (adapt-results res-list error?)))]
                     (tool-fn exchange arg-map clj-result-k))))]
    (McpServerFeatures$AsyncToolSpecification.
     mcp-tool
     (reify java.util.function.BiFunction
       (apply [_this exchange arguments]
         (log! :debug (str "Args from MCP: " (pr-str arguments)))
         (mono-fn exchange arguments))))))

(defn ^McpSchema$GetPromptResult adapt-prompt-result
  "Adapts a Clojure prompt result map into an McpSchema$GetPromptResult.
   Expects a map like {:description \"...\" :messages [{:role :user :content \"...\"}]}"
  [{:keys [description messages]}]
  (let [mcp-messages (mapv (fn [{:keys [role content]}]
                             (McpSchema$PromptMessage.
                              (case role ;; Convert keyword role to McpSchema$Role enum
                                ;; :system McpSchema$Role/SYSTEM
                                :user McpSchema$Role/USER
                                :assistant McpSchema$Role/ASSISTANT
                                ;; Add other roles if needed
                                )
                              (McpSchema$TextContent. content))) ;; Assuming TextContent for now
                           messages)]
    (McpSchema$GetPromptResult. description mcp-messages)))

(defn create-async-prompt
  "Creates an AsyncPromptSpecification with the given parameters.

   Takes a map with the following keys:
    :name        - The name (ID) of the prompt
    :description - A description of the prompt
    :arguments   - A vector of maps, each defining an argument:
                   {:name \"arg-name\" :description \"...\" :required? true/false}
    :prompt-fn   - Function that implements the prompt logic.
                   Signature: (fn [exchange request-args clj-result-k] ... )
                     * exchange - The MCP exchange object
                     * request-args - Map of arguments provided in the client request
                     * clj-result-k - Continuation fn taking one map argument:
                                      {:description \"...\" :messages [{:role :user :content \"...\"}]} "
  [{:keys [name description arguments prompt-fn]}]
  (let [mcp-args (mapv (fn [{:keys [name description required?]}]
                         (McpSchema$PromptArgument. name description required?))
                       arguments)
        mcp-prompt (McpSchema$Prompt. name description mcp-args)
        mono-fn (create-mono-from-callback ;; Reuse the existing helper
                 (fn [_ request mono-fill-k]
                   ;; The request object has an .arguments() method
                   (let [request-args (.arguments ^McpSchema$GetPromptRequest request)] ;; <-- Corrected method call
                     (prompt-fn _ request-args
                                (fn [clj-result-map]
                                  (mono-fill-k (adapt-prompt-result clj-result-map)))))))]
    (McpServerFeatures$AsyncPromptSpecification.
     mcp-prompt
     (reify java.util.function.BiFunction
       (apply [_this exchange request]
         (mono-fn exchange request))))))

(defn create-async-resource
  "Creates an AsyncResourceSpecification with the given parameters.

   Takes a map with the following keys:
    :url          - The URL of the resource
    :name         - The name of the resource
    :description  - A description of what the resource is
    :mime-type    - The MIME type of the resource
    :resource-fn  - Function that implements the resource retrieval logic.
                    Signature: (fn [exchange request clj-result-k] ... )
                      * exchange     - The MCP exchange object
                      * request      - The request object
                      * clj-result-k - continuation fn taking a vector of strings"
  [{:keys [url name description mime-type resource-fn]}]
  (let [resource (McpSchema$Resource. url name description mime-type nil)
        mono-fn (create-mono-from-callback
                 (fn [exchange request mono-fill-k]
                   (resource-fn
                    exchange
                    request
                    (fn [result-strings]
                      ;; Create TextResourceContents objects with the URL and MIME type
                      (let [resource-contents (mapv #(McpSchema$TextResourceContents. url mime-type %)
                                                    result-strings)]
                        ;; Create ReadResourceResult with the list of TextResourceContents
                        (mono-fill-k (McpSchema$ReadResourceResult. resource-contents)))))))]
    (McpServerFeatures$AsyncResourceSpecification.
     resource
     (reify java.util.function.BiFunction
       (apply [_this exchange request]
         (mono-fn exchange request))))))

(defn add-resource
  "Helper function to create an async resource from a map and add it to the server.

   Takes an MCP server and a resource map with:
    :url          - The URL of the resource
    :name         - The name of the resource
    :description  - A description of what the resource is
    :mime-type    - The MIME type of the resource
    :resource-fn  - Function that implements the resource retrieval logic."
  [mcp-server resource-map]
  (swap! mcp-components-atm #(assoc-in % [:resource-maps (:name resource-map)] resource-map))
  (.removeResource mcp-server (:url resource-map))
  (-> (.addResource mcp-server (create-async-resource resource-map))
      (.subscribe)))

(defn add-prompt
  "Helper function to create an async prompt from a map and add it to the server.

   Takes an MCP server and a prompt map with:
    :name        - The name (ID) of the prompt
    :description - A description of the prompt
    :arguments   - A vector of maps, each defining an argument
    :prompt-fn   - Function that implements the prompt logic."
  [mcp-server prompt-map]
  (swap! mcp-components-atm #(assoc-in % [:prompt-maps (:name prompt-map)] prompt-map))
  (.removePrompt mcp-server (:name prompt-map))
  (-> (.addPrompt mcp-server (create-async-prompt prompt-map))
      (.subscribe)))

;;; Claude Code: When you have 26 tools + 8 prompts + 5 resources, that's 39 notifications trying to be sent during initialization, overwhelming the STDIO transport before it's fully ready.
;;; The fix is to remove the .subscribe() calls - the MCP SDK handles subscriptions internally. Here's the fix:
;;; That was a hypothesis that was never verified, IMO.
(defn add-tool
  "Helper function to create an async tool from a map and add it to the server."
  [mcp-server tool-map]
  (log! :info (str "Adding tool " (:name tool-map)))
  (swap! mcp-components-atm #(assoc-in % [:tool-maps (:name tool-map)] tool-map))
  (.removeTool mcp-server (:name tool-map))
  ;; Pass the service-atom along when creating the tool
  (-> (.addTool mcp-server (create-async-tool tool-map))
      (.subscribe))) ; Claude Code suggests overwhelmed by this. (See above.)

(defn mcp-server?
  [s]
  (= io.modelcontextprotocol.server.McpAsyncServer (type s)))

(defn add-tools
  [server tools]
  (if (mcp-server? server)
    (try
      (doseq [t tools]
        (->> {:tool-type t} ts/registration-map (add-tool server))
        (swap! mcp-components-atm #(update % :tools conj (-> t name (str/replace "-" "_")))))
      (log! :info (str "Registered MCP tools: " tools))
      tools
      (catch Exception e
        (log! :error (str "Error registering MCP tools: " (.getMessage e)))))
    (log! :error (str "Could not register MCP tool(s) " tools " ; server not started."))))

(defn remove-tools
  "There should be much more to this...once I figure it out!"
  [server tools]
  (let [tool-name-set (->> tools (map name) (map #(str/replace % "-" "_")) set)]
    (swap! mcp-components-atm (fn [atm-val] (update atm-val :tools #(clojure.set/difference % tool-name-set))))
    (doseq [t tool-name-set]
      (.removeTool server t))))

(defn add-resources
  [server resources]
  (if (mcp-server? server)
    (try
      (doseq [r-map resources]
        (add-resource server r-map)
        (swap! mcp-components-atm #(update % :resources conj (:name r-map)))
        (log! :info (str "Registered MCP resource: " (:name r-map))))
      (mapv :name resources)
      (catch Exception e
        (log! :error (str "Error registering MCP resource: " (.getMessage e)))))
    (log! :error (str "Could not register MCP resources(s) " (mapv :name resources) " ; server not started."))))

(defn remove-resources
  "There should be much more to this...once I figure it out!"
  [server resources]
  (let [remove? (->> resources (map :name) set)]
    (swap! mcp-components-atm (fn [v] (update v :resources (fn [tools] (->> tools (remove #(remove? (:name %))) set)))))
    (doseq [r resources]
      (.removeResource server (:url r)))))

(defn add-prompts
  [server prompts]
  (if (mcp-server? server)
    (try
      (doseq [p-map prompts]
        (add-prompt server p-map)
        (swap! mcp-components-atm #(update % :prompts conj (:name p-map)))
        (log! :info (str "Registered MCP prompt: " (:name p-map))))
      (mapv :name prompts)
      (catch Exception e
        (log! :error (str "Error registering MCP prompt: " (.getMessage e)))))
    (log! :error (str "Could not register MCP prompts(s) " (mapv :name prompts) " ; server not started."))))

(defn remove-prompts
  "There should be much more to this...once I figure it out!"
  [server prompts]
  (let [remove? (->> prompts (map :name) set)]
    (swap! mcp-components-atm (fn [v] (update v :prompts (fn [tools] (->> tools (remove #(remove? (:name %))) set)))))
    (doseq [r prompts]
      (.removePrompt server (:url r)))))

(def comp-name? (-> mcp-components keys set))

(def ^:diag diag (atom nil))

(defn reload-mcp-components!
  "Using their mount/defstate stop and start the tools.
   Argument can be
      - a single keyword (e.g. :sur, :dpo :stepper, etc.) naming an mcp componet (see (-> mutil/mcp-components keys))
      - a vector of such keywords
      - :all to load all components
      - :tools to load all tools
      - :resources to load all resources
      - :prompts to load all prompts."
  [comps]
  (letfn [(load-one [c]
            (reset! diag {:c c})
            (if (comp-name? c)
              (let [v (-> (get mcp-components c) :var find-var)]
                (mount/stop v) (Thread/sleep 500) (mount/start v))
              (log! :warn (str "Not a component keyword: " c))))]
    (cond (comp-name? comps)      (load-one comps)
          (vector? comps)         (doseq [c comps] (load-one c))
          (= :all comps)          (doseq [c (keys mcp-components)] (load-one c))
          (= :tools comps)        (doseq [c (reduce-kv (fn [r k v] (if (= :tool     (:type v)) (conj r k) r)) [] mcp-components)] (load-one c))
          (= :resources comps)    (doseq [c (reduce-kv (fn [r k v] (if (= :resource (:type v)) (conj r k) r)) [] mcp-components)] (load-one c))
          (= :prompts comps)      (doseq [c (reduce-kv (fn [r k v] (if (= :prompt   (:type v)) (conj r k) r)) [] mcp-components)] (load-one c))
          :else                   (log! :warn (str "Invalid argument: " comps)))))

;;; (mutil/clj->mcp-args {:ds_id :process/flow-shop})
(defn ^:diag clj->mcp-args
  "Convert a Clojure map with keyword keys/values to MCP-style string keys/values."
  [m]
  (reduce-kv (fn [acc k v]
               (assoc acc
                      (if (keyword? k) (name k) (str k))
                      (if (keyword? v) (subs (str v) 1) v)))
             {} m))

;;; (mutil/run-tool "advise_next_step")
;;; (mutil/run-tool "delegate_DS_interview" {:ds_id :process/warm-up})
;;; (mutil/run-tool "db_query" {:db_type :system, :query_string "[:find ?text . :where [?eid :DS/id :process/scheduling-action-types] [?eid :DS/interview-objective ?text]]"})
(defn ^:diag run-tool
  "Run the MCP tool function named the the first argument using the remaining args as function args.
   The arg map should use Clojure keyword keys/values; but they should use '_' rather than '-'."
  ([tool-str] (run-tool tool-str {}))
  ([tool-str arg]
   (let [f   (-> @mcp-components-atm :tool-maps (get tool-str) :tool-fn)
         arg (if (some keyword? (concat (keys arg) (vals arg)))
               (clj->mcp-args arg)
               arg)
         res (atom nil)
         err (atom nil)
         callback (fn [result error?] (reset! res result) (reset! err error?))
         all-args [nil arg callback]]
     (if (fn? f)
       (do (apply f all-args)
           (if @err
             (do (log! :warn (str "run-fun error: " (first @res))) {:error (first @res)})
             (-> @res first json/read-str)))
       (log! :warn (str "No such function: " tool-str))))))

;;; (mutil/get-resource-text "DB_SCHEMA.md")
;;; (mutil/get-resource-text "STK_MCP_GUIDE.md")
(defn get-resource-text
  [res-str]
  (-> ((-> @mcp-components-atm :resource-maps (get res-str) :resource-fn) nil nil (fn [x] x)) first))
