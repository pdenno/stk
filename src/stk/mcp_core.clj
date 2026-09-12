(ns stk.mcp-core
  "Core MCP server implementation for stk using Java MCP SDK.
   Based on clojure-mcp approach for compatibility.
   Allows selection of t/r/p from clojure-mcp, as well as ours."
  (:require
   [clojure.edn :as edn]
   [mount.core :as mount :refer [defstate]]
   [promesa.core :as p]
   [stk.descriptions.schema :as sschema] ; For mount
   [stk.util :as util :refer [log!]])
  (:import [io.modelcontextprotocol.server.transport
            StdioServerTransportProvider
            WebFluxSseServerTransportProvider
            WebFluxStreamableServerTransportProvider]
           [io.modelcontextprotocol.server McpServer]
           [io.modelcontextprotocol.spec McpSchema$ServerCapabilities]
           [io.modelcontextprotocol.server McpServer]
           [io.modelcontextprotocol.json McpJsonMapper]
           [org.springframework.http.server.reactive ReactorHttpHandlerAdapter]
           [org.springframework.web.reactive.function.server RouterFunctions]
           [reactor.netty DisposableServer]
           [reactor.netty.http.server HttpServer]))

;;;"The server object. Satisfies mutil/mcp-server?
(defonce mcp-server-atm
  (atom nil))

(def ^:diag http-server-atm
  "Holds the Reactor Netty HTTP server for SSE or Streamable HTTP transport."
  (atom nil))

;;; -------------------------------- Transport Configuration --------------------------------
(def mcp-port
  "Port for the MCP server. Override via config.edn :mcp-port"
  (atom 8090))

;;; See https://medium.com/@ia_taras/server-sent-events-in-spring-webflux-efficient-data-updates-70c97917b449
(defn create-sse-transport-provider
  "Creates a WebFlux SSE transport provider for HTTP-based MCP communication.
   Note: basePath only affects the URL sent to client, NOT the actual routes.
   So we use full paths in sseEndpoint and messageEndpoint."
  []
  (-> (WebFluxSseServerTransportProvider/builder)
      (.jsonMapper (McpJsonMapper/getDefault))
      (.basePath "")
      (.messageEndpoint "/mcp/message")
      (.sseEndpoint "/mcp/sse")
      (.build)))

(defn mcp-sse-server
  "Creates an MCP server using SSE transport (deprecated).
   Returns a map with :mcp-server and :transport-provider."
  []
  (log! :info (str "Starting MCP SSE server on port " @mcp-port))
  (try
    (let [transport-provider (create-sse-transport-provider)
          server (-> (McpServer/async transport-provider)
                     (.serverInfo "stk" "0.1.0")
                     (.capabilities (-> (McpSchema$ServerCapabilities/builder)
                                        (.tools true)
                                        (.prompts true)
                                        (.resources true true)
                                        (.build)))
                     (.build))]
      (log! :info "MCP SSE server initialized successfully")
      {:mcp-server server
       :transport-provider transport-provider})
    (catch Exception e
      (log! :error (str "Failed to initialize MCP SSE server: " e))
      (throw e))))

(defn start-http-server!
  "Starts a Reactor Netty HTTP server with the given transport provider.
   Works with both SSE and Streamable HTTP transport providers.
   Returns the DisposableServer instance."
  [transport-provider port]
  (let [router-fn (.getRouterFunction transport-provider)
        http-handler (RouterFunctions/toHttpHandler router-fn)
        adapter (ReactorHttpHandlerAdapter. http-handler)
        server (-> (HttpServer/create)
                   (.port port)
                   (.handle adapter)
                   (.bindNow))]
    (log! :info (str "HTTP server started on port " port))
    server))

;;; -------------------------------- Streamable HTTP Transport --------------------------------
(defn create-streamable-transport-provider
  "Creates a WebFlux Streamable HTTP transport provider.
   This is the recommended transport as of MCP spec 2025-03-26, replacing SSE."
  []
  (-> (WebFluxStreamableServerTransportProvider/builder)
      (.jsonMapper (McpJsonMapper/getDefault))
      (.messageEndpoint "/mcp")
      (.build)))

(defn mcp-streamable-server
  "Creates an MCP server using Streamable HTTP transport.
   Returns a map with :mcp-server and :transport-provider."
  []
  (log! :info (str "Starting MCP Streamable HTTP server on port " @mcp-port))
  (try
    (let [transport-provider (create-streamable-transport-provider)
          server (-> (McpServer/async transport-provider)
                     (.serverInfo "stk" "0.1.0")
                     (.capabilities (-> (McpSchema$ServerCapabilities/builder)
                                        (.tools true)
                                        (.prompts true)
                                        (.resources true true)
                                        (.build)))
                     (.build))]
      (log! :info "MCP Streamable HTTP server initialized successfully")
      {:mcp-server server
       :transport-provider transport-provider})
    (catch Exception e
      (log! :error (str "Failed to initialize MCP Streamable HTTP server: " e))
      (throw e))))

(defn stop-http-server!
  "Stops the HTTP server gracefully."
  []
  (when-let [^DisposableServer server @http-server-atm]
    (log! :info "Stopping HTTP server...")
    (.disposeNow server)
    (reset! http-server-atm nil)
    (log! :info "HTTP server stopped")))

;; helper tool to demonstrate how all this gets hooked together
(defn ^:diag mcp-server--diag
  "Creates a basic stdio mcp server"
  []
  (log! :info "Starting MCP server")
  (try
    (let [transport-provider (StdioServerTransportProvider. (McpJsonMapper/getDefault))
          server (-> (McpServer/async transport-provider)
                     (.serverInfo "stk" "0.1.0")
                     (.capabilities (-> (McpSchema$ServerCapabilities/builder)
                                        (.tools true)
                                        (.prompts true)
                                        (.resources true true)
                                        (.build)))
                     (.build))]

      (log! :info "MCP server initialized successfully")
      server)
    (catch Exception e
      (log! :error (str "Failed to initialize MCP server: " e))
      (throw e))))

(defn close-mcp-server!
  "Convenience higher-level API function to gracefully shut down the MCP server.

    This function handles the complete shutdown process including:
   - Gracefully closing the MCP server
   - Proper error handling and logging"
  []
  (log! :info "Shutting down servers")
  (try
    (when-let [mcp-server @mcp-server-atm]
      (log! :info "Closing MCP server gracefully")
      (.closeGracefully mcp-server)
      (log! :info "Servers shut down successfully")
      (reset! mcp-server-atm nil))
    (catch Exception e
      (log! :error (str "Error during server shutdown: " e))
      (throw (ex-info "error during shutdown" {:msg (.getMessae e)})))))

;;; ------------------------------ Starting and stopping -------------------------
(def server-promise
  "This is used in main to keep the server from exiting immediately."
  (p/deferred))

(defn start-mcp-server-sse
  "Start MCP server with SSE transport on HTTP (deprecated, use streamable)."
  []
  (try
    (let [config (or (not-empty (-> "config.edn" slurp edn/read-string)) {})
          port (or (:mcp-port config) @mcp-port)
          _ (reset! mcp-port port)
          {:keys [mcp-server transport-provider]} (mcp-sse-server)]
      (reset! http-server-atm (start-http-server! transport-provider port))
      (log! :info (str "MCP SSE server ready at http://localhost:" port "/mcp/sse"))
      (reset! mcp-server-atm mcp-server))
    (catch Exception e
      (log! :error (str "SSE Server exception: " e))
      (throw e))))

(defn start-mcp-server-streamable
  "Start MCP server with Streamable HTTP transport.
   This is the recommended transport as of MCP spec 2025-03-26."
  []
  (try
    (let [config (or (not-empty (-> "config.edn" slurp edn/read-string)) {})
          port (or (:mcp-port config) @mcp-port)
          _ (reset! mcp-port port)
          {:keys [mcp-server transport-provider]} (mcp-streamable-server)]
      (reset! http-server-atm (start-http-server! transport-provider port))
      (log! :info (str "MCP Streamable HTTP server ready at http://localhost:" port "/mcp"))
      (reset! mcp-server-atm mcp-server))
    (catch Exception e
      (log! :error (str "Streamable HTTP Server exception: " e))
      (throw e))))

(defn start-mcp-server-stdio
  "Start MCP server with stdio transport (legacy, for wrapper script)."
  []
  (try
    (log! :error "Don't run this; it has been gutted!")
    (catch Exception e
      (log! :error (str "Server exception: " e))
      (p/resolve! server-promise :failed-to-start))))

(defn start-mcp-server
  "Start MCP server. Uses Streamable HTTP transport by default.
   Set :mcp-transport in config.edn to :sse for legacy SSE transport."
  []
  (let [config (or (not-empty (-> "config.edn" slurp edn/read-string)) {})
        transport (or (:mcp-transport config) :streamable)]
    (case transport
      :stdio      (start-mcp-server-stdio)
      :sse        (start-mcp-server-sse)
      :streamable (start-mcp-server-streamable))
    (Thread/sleep 2000)))

(defn stop-mcp-server
  "Stop the MCP server and HTTP server if running."
  []
  (log! :info "Stopping stk server...")
  (stop-http-server!)
  (close-mcp-server!)
  (p/resolve! server-promise :exited))

;;; Starting and stopping
;;; (mount/stop #'stk.mcp-core/mcp-core-server)
(defstate mcp-server
  :start (start-mcp-server)
  :stop (stop-mcp-server))
