(ns stk.situations.schema
  "Malli entry contract for the canonical-situation (competency) library.

   Library membership is BY CONTRACT, not by inheritance: a canonical Situation
   is whatever passes `validate-entry` - required metadata (:purpose above all,
   since the orchestrator reads it to judge fit), signature, faithful and
   enriched axiom sets, computables, ground-claims/oracle recognizer contract,
   and glosses. This replaces the unevenness of OWL's 'anything under
   dul:Situation' with a uniform entry shape.

   The FOL-as-data conventions are enforced structurally: forms are NESTED
   VECTORS with a symbol head; malli's :vector rejects lists, so the project's
   'no lists for data' rule is checked, not just stated. Ground claims and
   oracle entries additionally may not contain ?variables."
  (:require
   [clojure.string :as str]
   [malli.core     :as m]
   [malli.error    :as me]))

;;; ---------------------------------------------------------- FOL forms
(def Form
  "A FOL form: a vector whose head is a symbol and whose elements are
   symbols, numbers, strings, or nested forms."
  [:schema {:registry {::term [:or [:ref ::form] symbol? number? string?]
                       ::form [:and
                               [:vector {:min 1} [:ref ::term]]
                               [:fn {:error/message "form head must be a symbol"}
                                #(symbol? (first %))]]}}
   ::form])

(defn variable?
  "A ?-prefixed symbol is a variable."
  [x]
  (and (symbol? x) (str/starts-with? (name x) "?")))

(defn ground?
  "True when the form contains no variables at any depth."
  [form]
  (not-any? variable? (tree-seq vector? seq form)))

(defn quantifiers-ok?
  "Every forall/exists subform binds a vector of variables and has a body."
  [form]
  (if-not (vector? form)
    true
    (let [[head binding & body] form]
      (if ('#{forall exists} head)
        (and (vector? binding)
             (seq binding)
             (every? variable? binding)
             (seq body)
             (every? quantifiers-ok? body))
        (every? quantifiers-ok? form)))))

(def Axiom
  [:and Form [:fn {:error/message "malformed forall/exists binding"} quantifiers-ok?]])

(def GroundForm
  [:and Form [:fn {:error/message "ground claims may not contain ?variables"} ground?]])

;;; ---------------------------------------------------------- entry parts

(def Metadata
  [:map
   [:label   :string]
   [:purpose {:description "NL text the orchestrator reads to judge description/situation fit; includes scope caveats and template guidance."}
    :string]
   [:source-file       {:optional true} :string]
   [:source-version    {:optional true} :string]
   [:source-divergence {:optional true} :string]
   [:ontology-iri      {:optional true} :string]
   [:imports           {:optional true} [:vector :string]]
   [:relevant-SQs      {:description "Standing questions this situation helps answer; the SQ<->situation index locates the recognizer's neighborhood."
                        :optional true}
    [:vector :string]]])

(def Signature
  [:map
   [:classes           [:vector symbol?]]
   [:object-properties {:optional true} [:vector symbol?]]
   [:external          {:optional true} [:vector symbol?]]
   [:introduced        {:description "Predicates introduced by axioms-enriched."}
    [:vector symbol?]]])

(def Provenance
  [:map-of
   [:or symbol? Form]
   [:map
    [:satisfies      {:optional true} symbol?]
    [:ttl-source     {:optional true} :string]
    [:constructed-by {:optional true} [:enum :recognizer :hand-asserted :adapted]]
    [:fit-verdict    {:optional true} [:enum :satisfied :closest-template :none]]
    [:computed-by    {:optional true} symbol?]
    [:justified-by   {:optional true} [:vector Form]]]])

(def Entry
  "The library entry contract. Maps are open: entries may carry extra defs."
  [:map
   [:metadata        Metadata]
   [:prefixes        [:map-of symbol? :string]]
   [:signature       Signature]
   [:axioms-faithful [:vector Axiom]]
   [:axioms-enriched [:vector Axiom]]
   [:computable      {:description "Predicates the recognizer computes (tool seam) rather than matches."}
    [:map-of symbol? :string]]
   [:ground-claims   [:vector GroundForm]]
   [:oracle          [:vector GroundForm]]
   [:provenance      {:optional true} Provenance]
   [:glosses         [:map-of symbol? :string]]])

;;; ---------------------------------------------------------- validation

(defn load-entry
  "Load a library entry file; return its public defs as a map keyed by def name."
  [path ns-sym]
  (load-file path)
  (reduce-kv (fn [m k v] (assoc m (keyword k) @v)) {} (ns-publics ns-sym)))

(defn validate-entry
  "Return nil when the entry satisfies the contract, else humanized errors."
  [entry]
  (some-> (m/explain Entry entry) me/humanize))

(defn entry-ok?
  [entry]
  (nil? (validate-entry entry)))

(defn ^:diag check-bottleneck
  "REPL convenience: validate the first library entry."
  []
  (validate-entry (load-entry "data/situations/bottleneck.clj" 'situations.bottleneck)))
