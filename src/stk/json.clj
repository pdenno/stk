(ns stk.json
  "JSON compatibility functions and MCP transformer for Malli decode/coerce.
   Jsonista and Malli are complementary libraries from Metosin."
  (:require
   ;[camel-snake-kebab.core :as csk]
   [clojure.string :as str]
   [jsonista.core :as j]
   [malli.transform :as mt]))

(def pretty-mapper
  (j/object-mapper {:pretty true}))

(defn write-str
  ([x] (write-str x j/default-object-mapper))
  ([x mapper] (j/write-value-as-string x mapper))
  ([x mapper _] (j/write-value-as-string x mapper)))

(def keyword-mapper
  (j/object-mapper {:decode-key-fn keyword}))

(defn read-str
  ([s] (j/read-value s))
  ([s & _opts] (j/read-value s keyword-mapper)))

(defn- string->keyword
  "Convert string to keyword, stripping leading ':' if present."
  [x]
  (if (string? x)
    (keyword (cond-> x (str/starts-with? x ":") (subs 1)))
    x))

;;; Top-level only: MCP/JSON snake_case <-> Clojure kebab-case (via camel-snake-kebab)
#_(defn top-keys-json2clj
  "Convert top-level map keys from snake_case to kebab-case (underscore -> hyphen).
   Nested maps are left unchanged. Handles both string and keyword keys."
  [m]
  (when (map? m)
    (reduce-kv
     (fn [acc k v]
       (assoc acc
              (cond (keyword? k) (csk/->kebab-case-keyword k)
                    (string? k)  (csk/->kebab-case-keyword k)
                    :else k)
              v))
     {} m)))

#_(defn top-keys-clj2json
  "Convert top-level map keys from kebab-case to snake_case (hyphen -> underscore).
   Nested maps are left unchanged. Returns string keys for JSON."
  [m]
  (when (map? m)
    (reduce-kv
     (fn [acc k v]
       (assoc acc
              (cond (keyword? k) (csk/->snake_case_string k)
                    (string? k)  (csk/->snake_case_string k)
                    :else (str k))
              v))
     {} m)))

(def mcp-json2clj
  "Malli transformer for MCP tool inputs (JSON → Clojure).
   Converts string keys to keyword keys, handles keyword coercion,
   stripping the leading ':' that LLMs often include.
   Enum values are converted to keywords (schemas should use keyword enums).
   Use with (top-keys-json2clj params) before decode for top-level snake->kebab conversion."
  (mt/transformer
   (mt/key-transformer {:decode keyword})
   {:name :mcp-json2clj
    :decoders
    {:keyword {:enter string->keyword}
     :enum {:enter string->keyword}}}))

#_(def mcp-transformer-clj2json
  "Malli transformer for MCP tool outputs (Clojure → JSON).
   Converts keyword keys to strings, keyword values to strings.
   Use with (top-keys-clj2json result) after encode for top-level kebab->snake conversion."
  (mt/transformer
   (mt/key-transformer {:encode name})
   {:name :mcp-clj2json
    :encoders
    {:keyword {:leave str}
     :enum {:leave str}}}))

#_(defn json-schema-props-to-snake
  "Convert top-level 'properties' keys in a JSON schema from kebab-case to snake_case.
   Use when generating MCP tool schemas so clients see ds_id not ds-id."
  [schema]
  (if (and (map? schema) (contains? schema "properties"))
    (update schema "properties"
            (fn [props]
              (when (map? props)
                (reduce-kv (fn [acc k v]
                             (assoc acc (csk/->snake_case_string k) v))
                           {} props))))
    schema))
