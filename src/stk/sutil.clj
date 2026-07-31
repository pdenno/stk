(ns stk.sutil
  "Server utilities."
  (:require
   [clojure.java.basis]
   [clojure.java.io :as io]
   [clojure.string :as str]
   [datahike.api :as d]
   [datahike.pull-api :as dp]
   [stk.json :as json]
   [stk.util :refer [log!]])
  (:import ; ToDo: Why does clj-kondo complain?
   java.net.URI
   java.nio.file.StandardCopyOption
   java.nio.file.Paths))

(def ^:diag diag (atom nil))

(defn init-project-run!
  "Reset session-level state when starting a new project."
  [])

(defmacro with-connect-atom [[conn-sym db-id] & body]
  `(let [~conn-sym (connect-atm ~db-id)]
     (try ~@body
          (finally (d/release ~conn-sym)))))

;(def llm-provider "Default provider to use. Choices are #{:openai :azure}." :openai) ; Values are azure and :openai
(def default-llm-provider "Default provider to use. Choices are #{:openai :azure}." (atom :openai)) ; Values are azure and :openai

(defonce databases-atm (atom {}))

(defn db-ids [] (-> @databases-atm keys sort vec))

(defn register-db
  "Add a DB configuration."
  [k config]
  (log! :debug (str "Registering DB " k "config = " config))
  (swap! databases-atm #(assoc % k config)))

(defn deregister-db
  "Remove a DB configuration."
  [k]
  (log! :info (str "Deregistering DB " k))
  (swap! databases-atm #(dissoc % k)))

(def db-template
  "Hitchhiker file-based DBs follow this form."
  {:store {:backend :file :path "Provide a value!"} ; This is path to the database's root directory
   :keep-history? false
   :base-dir "Provide a value!" ; For convenience, this is just above the database's root directory.
   :recreate-dbs? false ; If true, it will recreate the system DB and project directories too.
   :schema-flexibility :write})

(defn run-mode?
  "Return a set of aliases that were present at startup."
  []
  (->> (clojure.java.basis/initial-basis) :basis-config :aliases set))

(defn db-base-path
  "Get the path for system and project DBs.
   This is from the environment variable STK_DB unless running in nREPL mode,
   in which case it is ./test/dbs. This function creates parents in necessary."
  []
  (let [bpath
        (if ((run-mode?) :nrepl)
          (str (System/getenv "PWD") "/test/dbs")
          (or (System/getenv "STK_DB")
              (throw (ex-info "STK_DB environment var not set and not running in nrepl mode." {}))))]
    (io/make-parents (str bpath "/system/dummy"))
    (io/make-parents (str bpath "/projects/dummy"))
    bpath))

;;; https://cljdoc.org/d/io.replikativ/datahike/0.6.1545/doc/datahike-database-configuration
;;; https://github.com/replikativ/datahike/issues/769 - [Bug]: database-exists? blocks forever if store config is invalid #769
;;; I used to use :allow-unsafe-config = true. I don't even see that in the options now.
;;; ToDo: Consider (again?) using :attribute-refs? = true. https://cljdoc.org/d/io.replikativ/datahike/0.6.1545/doc/datahike-database-configuration
(defn db-cfg-map
  "Return a datahike configuration map for argument database (or its base).
     id   - a keyword uniquely identifying the DB in the scope of DBs.
     type - the type of DB configuration being make: (:project, :system, :study, or :him, so far)"
  [{:keys [type id in-mem?]}]
  (when (and (= :project type) (not id)) (throw (ex-info "projects need an ID." {})))
  (let [base-dir (db-base-path)
        db-dir (->> (case type
                      :system "/system"
                      :project (str "/projects/" (name id) "/db/")
                      :study "/study"
                      :planning-domains "/planning-domains"
                      :him "/him")
                    (str base-dir))
        dir-file (io/file db-dir)]
    (when-not (.exists dir-file) (-> db-dir java.io.File. .mkdirs))
    (cond-> db-template
      true (assoc :base-dir base-dir)
      (not in-mem?) (assoc :store {:backend :file :path db-dir})
      in-mem? (assoc :store {:backend :mem :id (name id)}))))

(defn get-db-cfg
  "Return the cfg map for the given DB."
  [pid]
  (get @databases-atm pid))

(defn files-directory
  "Return the files directory of the project."
  [cfg]
  (let [dir (-> cfg :store :path)]
    (str (subs dir 0 (- (count dir) 3)) "files/"))) ; knock off the "db/ and add

(defn connect-atm
  "Return a connection atom for the DB.
   Throw an error if the DB does not exist and :error? is true (default)."
  [k & {:keys [error?] :or {error? true}}]
  (if-let [db-cfg (get-db-cfg k)]
    (if (d/database-exists? db-cfg)
      (d/connect db-cfg)
      (when error?
        (throw (ex-info (str "Could not connect to DB: " k) {:key k}))))
    (when error?
      (throw (ex-info (str "No such DB: " k) {:key k})))))

(defn datahike-schema
  "Create a Datahike-compatible schema from map-type schema with notes such as :mm/info and :ext/* extension properties."
  [schema]
  (reduce-kv (fn [r k v]
               (conj r (-> v
                           (dissoc :pre/fn :post/fn)
                           ;; Remove all :ext/* extension properties (e.g., :ext/edn-readable?)
                           (as-> m (apply dissoc m (filter #(and (keyword? %)
                                                                 (= "ext" (namespace %)))
                                                           (keys m))))
                           (assoc :db/ident k))))
             []
             schema))
;;; ToDo:
;;;  - cljs complains about not finding x/element-nss, which I don't see in the  0.2.0-alpha8 source at all.
;;;    (Yet it does work in clj!) I suppose reading xml isn't something I need in cljs, but it would be
;;;    nice to know what is going on here.
;;; ToDo: Get some more types in here, and in implementation generally.
(defn db-type-of
  "Return a Datahike schema :db/valueType object for the argument"
  [obj]
  (cond (string? obj) :db.type/string
        (number? obj) :db.type/number
        (keyword? obj) :db.type/keyword
        (map? obj) :db.type/ref
        (boolean? obj) :db.type/boolean))

;;; This seems to cause problems in recursive resolution. (See resolve-db-id)"
(defn db-ref?
  "It looks to me that a datahike ref is a map with exactly one key: :db/id."
  [obj]
  (and (map? obj) (= [:db/id] (keys obj))))

;;; {:db/id 3779}
(defn resolve-db-id
  "Return the form resolved, removing properties in filter-set,
   a set of db attribute keys, for example, #{:db/id}."
  [form conn & {:keys [keep-set drop-set]
                :or {drop-set #{:db/id}
                     keep-set #{}}}]
  (let [cyclical? (atom false)
        visited? (atom #{})]
    (letfn [(rem-nil [obj]
              (cond (map? obj) (reduce-kv (fn [m k v] (if (nil? v) m (assoc m k (rem-nil v)))) {} obj)
                    (vector? obj) (reduce (fn [res v] (if (nil? v) res (conj res (rem-nil v)))) [] obj)
                    :else obj))
            (resolve-aux [obj]
              (cond
                (db-ref? obj) (if (@visited? (:db/id obj))
                                (do (log! :warn (str "id " (:db/id obj) " has been visted already."))
                                    (reset! cyclical? true))
                                (let [res (dp/pull conn '[*] (:db/id obj))]
                                  (swap! visited? conj (:db/id obj))
                                  (if (= res obj) nil (resolve-aux res))))
                (map? obj) (reduce-kv (fn [m k v]
                                        (cond (drop-set k) m
                                              (and (not-empty keep-set) (not (keep-set k))) m
                                              :else (assoc m k (resolve-aux v))))
                                      {}
                                      obj)
                (vector? obj) (mapv resolve-aux obj)
                (set? obj) (set (mapv resolve-aux obj))
                (coll? obj) (map resolve-aux obj)
                :else obj))]
      (let [res (resolve-aux form)]
        (if @cyclical? (rem-nil res) res)))))

(defn root-entities
  "Return a sorted vector of root entities (natural numbers) for all root entities of the DB."
  [conn]
  (-> (d/q '[:find [?e ...] :where
             [?e]
             (not [_ _ ?e])]
           conn)
      sort
      vec))

(defn nspaces
  "Return a string of n spaces."
  [n]
  (reduce (fn [s _] (str s " ")) "" (range n)))

(defn elide
  "Return a string no longer than n where the last 3 is ellipsis '...' if the string is > n long."
  [s n]
  (let [cnt (count s)]
    (cond (> n cnt) s
          (< n 3) ""
          :else (str (subs s 0 (- n 3)) "..."))))

(defn not-nothing
  "Returns true if it is a collection and not empty or something else non-nil"
  [x]
  (if (seq? x)
    (not-empty x)
    x))

;;; https://gist.github.com/lnostdal/cc956e2a80dc49d8097b7c950f7213bd
(defn ^:admin move-file
  "Move file/directory from source to target. Both are full pathnames.
   This will also replace the target file if it exists since REPLACE_EXISTING is included in the options at the end."
  [source target]
  (let [source-file (java.nio.file.Paths/get (java.net.URI/create (str "file://" source)))
        target-file (java.nio.file.Paths/get (java.net.URI/create (str "file://" target)))]
    (java.nio.file.Files/move source-file target-file
                              (into-array java.nio.file.CopyOption
                                          [(java.nio.file.StandardCopyOption/ATOMIC_MOVE)
                                           (java.nio.file.StandardCopyOption/REPLACE_EXISTING)]))))

(defn markdown2html
  "Do heuristic light modification to the argument text to make it more like HTML.
   Specifically:
     - Change: **bold** to <b>bold</b>.
   This is mostly for use with chatbots that return markup."
  [s]
  (let [lines (for [line (str/split-lines s)]
                (let [[success pre bold post] (re-matches #"(.*)\*\*(.+)\*\*(.*)" line)] ; ToDo: I can't put \- in the bold stuff.
                  (if success
                    (str pre "<b>" bold "</b>" post "\n")
                    (str line "\n")))) ; ToDo: interpose!
        last (last lines)
        others (butlast lines)]
    (str (apply str others)
         (subs last 0 (dec (count last))))))

(defn remove-preamble
  "The LLM might put text and markup around the answer, return the answer without this crap."
  [response]
  (let [response (str/replace response #"\s" " ")]
    (cond (re-matches #".*```clojure.*" response)
          (let [pos (str/index-of response "```clojure")
                response (subs response (+ pos 10))
                pos (str/index-of response "```")]
            (subs response 0 pos))

          (re-matches #".*```json.*" response)
          (let [pos (str/index-of response "```json")
                response (subs response (+ pos 7))
                pos (str/index-of response "```")]
            (subs response 0 pos))
          :else response)))

;;; OpenAI thing. This became complicated once I couldn't use strict schema results.
#_(defn ai-response2clj
    "Translate content to a clj object. The content is a string that contains a JSON object, and may wrap the object in unhelpful language
   markup indicating the language in which the object should be interpreted. The function takes an optional second argument which defaults to true.
   If instead, false (not just nil, but false, the boolean)  is provided as second argument, the original string is returned, rather
   than throwing on an error."
    ([s-in] (ai-response2clj s-in true))
    ([s-in throw-error?]
     (try
       (let [s (remove-preamble s-in)
             m (ches/parse-string s)]
         (letfn [(upk [obj]
                   (cond (map? obj) (reduce-kv (fn [m k v] (assoc m (keyword k) (upk v))) {} obj)
                         (vector? obj) (mapv upk obj)
                         :else obj))]
           (upk m)))
       (catch Exception _e
         (if (false? throw-error?)
           s-in
           (throw (ex-info "Could not read object returned (should be a string containing JSON):" {:s-in s-in})))))))

;;; ToDo: Eliminate this
(defn clj2json-pretty
  "Return a pprinted string for given clojure object."
  [obj]
  (assert (not (nil? obj)))
  (json/write-str obj json/pretty-mapper))

#_(defn clj2json-pretty
    "Return a pprinted string for given clojure object."
    [obj]
    (assert (not (nil? obj)))
    (ches/generate-string obj {:pretty true}))

(defn update-resources-DS-json! [& _] (throw (ex-info "This should no longer be needed." {})))

;;;https://gist.github.com/olieidel/c551a911a4798312e4ef42a584677397
(defn ^:admin delete-directory-recursive
  "Recursively delete a directory."
  [path]
  (letfn [(ddr [file]
            (when (.isDirectory file)
              (run! ddr (.listFiles file)))
            (io/delete-file file))]
    (-> path java.io.File. ddr)))

;;;--------------------------------------- Shared stuff for mocking ----------------
(def mocking?
  "This is set to true when we start mocking a project execution."
  (atom false))

(defn shadow-pid
  "Return a shadow pid, if the argument is a shadow-pid, return the argument."
  [pid]
  (when pid
    (let [[success? _normal-pid] (re-matches #"^(.+)\-\-temp$" (name pid))]
      (if success?
        pid
        (-> pid name (str "--temp") keyword)))))

(defn normal-pid
  "When given a shadow-pid, return the normal pid."
  [pid]
  (let [[success? normal-pid] (re-matches #"^(.+)\-\-temp$" (name pid))]
    (if success?
      (keyword normal-pid)
      pid)))

(defn ns-str-from-path
  "Return the namespace (as a string) from argument string using heuristics peculiar to the stk codebase.
   Specifically, the string must begin with 'src', 'test' or 'env', the three places where code is expected."
  [s]
  (letfn [(ns-str [s] (-> s  (str/replace "/" ".") (str/replace "_" "-")))]
    (cond (re-matches #"^src/.*" s)     (let [[_ s-part] (re-matches #"^src/(.*)\.clj$" s)]
                                          (ns-str s-part))

          (re-matches #"^test/.*" s)    (let [[_ s-part] (re-matches #"^test/(.*)\.clj$" s)]
                                          (ns-str s-part))

          (re-matches #"^env/.+/.*" s)  (let [[_ _ s-part] (re-matches #"^env/(dev|test|prod)/(.*)\.clj$" s)]
                                          (ns-str s-part)))))
