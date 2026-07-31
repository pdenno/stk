(ns situations.bottleneck
  "FOL translation of sched6 data/onto/sched6/challenges/bottleneck.ttl (v2.3).
   First entry in the STK canonical-situation (competency) library.

   A canonical Situation asserts STK competency in a circumstance: it carries the
   science that warrants its causal claims (here Li & Meerkov 2009, queuing theory,
   Theory of Constraints), the computable predicates a recognizer needs, and glosses
   for mentoring human participants.

   CONVENTIONS (inherited from sched6 data/onto/experiment/sched6/task-relations.clj)
   ---------------------------------------------------------------------------------
   * Logic is data, written as NESTED VECTORS - never lists.
   * Ontology terms are NAMESPACED SYMBOLS mirroring IRIs: `bneck/Foo`, `dul/Bar`.
     Variables are `?x`-style symbols. Operators and KIF builtins are bare symbols:
     forall exists => <=> and or not member setof card >= > = max.
   * Three axiom sets:
       - `axioms-faithful` : mechanical DL->FOL, auditable 1:1 against the TTL.
       - `axioms-enriched` : n-ary relations that collapse OWL reification chains,
                             plus COMPUTABLE definitions the recognizer executes.
                             Each enrichment is reviewed judgment.
   * Two ground-fact sets (NEW, the recognizer contract):
       - `ground-claims` : low-level observables as feeds would supply them
                           (topology, cycle times, buffer states). At ingestion each
                           acquires the claim envelope (valid-time, modality,
                           provenance) per etk docs/etk-diachronic-claims.md.
       - `oracle`        : what the hand-built TTL ABox asserts about the situation.
                           The recognizer must RECONSTRUCT these from ground-claims
                           alone. Recognizer = classification + instance binding
                           within a located neighborhood; no type discovery.
   * `glosses` keeps rdfs:label/comment - the NL bridge for interviews & mentoring."
  )

;;; ===========================================================================
;;; Ontology metadata
;;; ===========================================================================

(def metadata
  {:ontology-iri "http://sched6.org/ont/bottleneck.owl"
   :source-file  "sched6:data/onto/sched6/challenges/bottleneck.ttl"
   :source-version "2.3"
   :label        "Bottleneck Situation"
   :imports      ["EMF causality/composition/correlation/interpretation/participation"
                  "sched6 event-patterns, performance-goals"
                  "DUL Roles, SystemsLite"]
   :purpose      (str "Canonical situation: throughput constrained by the station with maximum cycle time.\n"
                      "Two viewpoints:\n"
                      "  1) generic form (infinite buffers) from cycle-time imbalance, and\n"
                      "  2) buffered extension adds blocking/starvation coupling through finite buffers (Li & Meerkov 2009).\n"
                      "Note, however, that Li & Meerkov would likely interpret the variation in cycle to be owing to breakdowns.\n"
                      "There is no notion of breakdown in these axioms. You might use this as a template to be modified for that purpose.\n"
                      "Likewise, it could serve as a template for cyclical scheduling and line balancing problems.")
   :source-divergence (str "Enriched/ground vocabulary says occupancy-time where the TTL says cycle time: "
                           "'cycle time' is overloaded, and occupancy is neutral about cause "
                           "(processing, setup, breakdown-inflated). The faithful layer keeps TTL names for 1:1 audit.")
   :relevant-SQs ["What is limiting our throughput this week?"
                  "Which station should improvement effort target?"]})

(def prefixes
  {'bneck  "http://sched6.org/ont/bottleneck#"
   'dul    "http://www.ontologydesignpatterns.org/ont/dul/DUL.owl#"
   'sysl   "http://www.ontologydesignpatterns.org/ont/dul/SystemsLite.owl#"
   'epats  "http://sched6.org/ont/event-patterns.owl#"
   'causal "http://events.semantic-multimedia.org/ontology/2010/12/05/causality.owl#"
   'ecomp  "http://events.semantic-multimedia.org/ontology/2010/12/05/composition.owl#"
   'ecorr  "http://events.semantic-multimedia.org/ontology/2010/12/05/correlation.owl#"
   'eintp  "http://events.semantic-multimedia.org/ontology/2010/12/05/interpretation.owl#"
   'epart  "http://events.semantic-multimedia.org/ontology/2010/12/05/participation.owl#"
   'pgoals "http://sched6.org/ont/performance-goals#"})

;;; ===========================================================================
;;; Signature (declared vocabulary)
;;; ===========================================================================

(def signature
  '{:classes
    [;; physical
     ;; ToDo: Workstation, Buffer, WIP, SystemThroughput and many more are more general and therefore probably belong elsewhere.
     bneck/Workstation bneck/Buffer bneck/WorkInProcess
     ;; qualities & regions
     bneck/CycleTimeBalance bneck/CycleTimeVariabilityRegion
     bneck/BalancedCVRegion bneck/ImbalancedCVRegion
     bneck/BufferCapacity bneck/SystemThroughput
     bneck/BottleneckConstraintParameter
     ;; event/state types
     bneck/BottleneckStateType bneck/BufferedBottleneckStateType
     bneck/ImbalancedOperationType bneck/WIPAccumulationType bneck/StarvationType
     bneck/BlockingType bneck/BufferStateType
     bneck/BufferEmptyState bneck/BufferPartialState bneck/BufferFullState
     ;; roles
     bneck/BottleneckStationRole bneck/StarvedStationRole bneck/BlockedStationRole
     bneck/QueuedJobRole bneck/BufferRole
     ;; theories (the science; MDC-style mechanism warrant)
     bneck/GenericBottleneckTheory bneck/BufferedLineTheory
     ;; system framing
     bneck/ProductionLineSystem bneck/ProductionLineDesign]
    :object-properties
    [bneck/upstreamOf bneck/downstreamOf]
    :external
    [dul/PhysicalObject dul/Quality dul/Region dul/Parameter dul/Theory dul/Event
     dul/Situation dul/hasLocation dul/isQualityOf dul/parametrizes
     sysl/SystemAsSituation sysl/DynamicSystemDesign sysl/WorkingItemRole
     epats/SystemState causal/Cause causal/Effect epart/Participant]
    ;; Predicates INTRODUCED by axioms-enriched (not in the OWL source).
    :introduced
    [plays in-setting causes-in correlated-in composed-in
     occupancy-time buffer-state buffer-capacity station-of
     cv-of-line max-occupancy-station imbalanced-line bottleneck-station
     blocked starved throughput-shortfall]})

;;; ===========================================================================
;;; (M) Faithful DL -> FOL.  Mechanically checkable 1:1 against the TTL.
;;; ===========================================================================

(def axioms-faithful
  '[;; ---- physical objects ----
    [forall [?x] [=> [bneck/Workstation ?x]     [dul/PhysicalObject ?x]]]
    [forall [?x] [=> [bneck/Buffer ?x]          [dul/PhysicalObject ?x]]]
    [forall [?x] [=> [bneck/WorkInProcess ?x]   [dul/PhysicalObject ?x]]]

    ;; ---- qualities, regions, parameters ----
    [forall [?x] [=> [bneck/CycleTimeBalance ?x]           [dul/Quality ?x]]]
    [forall [?x] [=> [bneck/BufferCapacity ?x]             [dul/Quality ?x]]]
    [forall [?x] [=> [bneck/SystemThroughput ?x]           [dul/Quality ?x]]]
    [forall [?x] [=> [bneck/CycleTimeVariabilityRegion ?x] [dul/Region ?x]]]
    [forall [?x] [=> [bneck/BalancedCVRegion ?x]           [bneck/CycleTimeVariabilityRegion ?x]]]
    [forall [?x] [=> [bneck/ImbalancedCVRegion ?x]         [bneck/CycleTimeVariabilityRegion ?x]]]
    [forall [?x] [not [and
                       [bneck/BalancedCVRegion ?x]
                       [bneck/ImbalancedCVRegion ?x]]]]
    ;; CV space is partitioned (owl:unionOf equivalence)
    [forall [?x] [<=> [bneck/CycleTimeVariabilityRegion ?x]
                  [or
                   [bneck/BalancedCVRegion ?x]
                   [bneck/ImbalancedCVRegion ?x]]]]
    [forall [?x] [=> [bneck/BottleneckConstraintParameter ?x] [dul/Parameter ?x]]]
    ;; restriction: the constraint parameter parametrizes only imbalanced-CV regions
    [forall [?x ?r] [=> [and
                         [bneck/BottleneckConstraintParameter ?x]
                         [dul/parametrizes ?x ?r]]
                     [bneck/ImbalancedCVRegion ?r]]]
    ;; restriction: a BufferCapacity is a quality only of Buffers
    [forall [?q ?b] [=> [and [bneck/BufferCapacity ?q] [dul/isQualityOf ?q ?b]]
                     [bneck/Buffer ?b]]]

    ;; ---- event/state types ----
    [forall [?x] [=> [bneck/BottleneckStateType ?x]     [and [causal/Effect ?x] [epats/SystemState ?x]]]]
    [forall [?x] [=> [bneck/BufferedBottleneckStateType ?x] [bneck/BottleneckStateType ?x]]]
    [forall [?x] [=> [bneck/ImbalancedOperationType ?x] [and [causal/Cause ?x] [epats/SystemState ?x]]]]
    [forall [?x] [=> [bneck/BlockingType ?x]            [and [causal/Effect ?x] [epats/SystemState ?x]]]]
    [forall [?x] [=> [bneck/WIPAccumulationType ?x]     [and [ecomp/Component ?x] [epats/SystemState ?x]]]]
    [forall [?x] [=> [bneck/StarvationType ?x]          [and [ecomp/Component ?x] [epats/SystemState ?x]]]]
    [forall [?x] [=> [bneck/BufferStateType ?x]         [epats/SystemState ?x]]]
    ;; buffer-state partition (disjoint union)
    [forall [?x] [<=> [bneck/BufferStateType ?x]
                  [or [bneck/BufferEmptyState ?x] [bneck/BufferPartialState ?x] [bneck/BufferFullState ?x]]]]
    [forall [?x] [not [and [bneck/BufferEmptyState ?x]   [bneck/BufferFullState ?x]]]]
    [forall [?x] [not [and [bneck/BufferEmptyState ?x]   [bneck/BufferPartialState ?x]]]]
    [forall [?x] [not [and [bneck/BufferPartialState ?x] [bneck/BufferFullState ?x]]]]
    [forall [?x] [=> [bneck/BufferFullState ?x] [causal/Cause ?x]]]

    ;; ---- roles ----
    [forall [?x] [=> [bneck/BottleneckStationRole ?x] [epart/Participant ?x]]]
    [forall [?x] [=> [bneck/StarvedStationRole ?x]    [epart/Participant ?x]]]
    [forall [?x] [=> [bneck/BlockedStationRole ?x]    [epart/Participant ?x]]]
    [forall [?x] [=> [bneck/BufferRole ?x]            [epart/Participant ?x]]]
    [forall [?x] [=> [bneck/QueuedJobRole ?x]         [and [epart/Participant ?x] [sysl/WorkingItemRole ?x]]]]

    ;; ---- theories ----
    [forall [?x] [=> [bneck/GenericBottleneckTheory ?x] [dul/Theory ?x]]]
    [forall [?x] [=> [bneck/BufferedLineTheory ?x]      [dul/Theory ?x]]]

    ;; ---- system framing ----
    [forall [?x] [=> [bneck/ProductionLineSystem ?x] [sysl/SystemAsSituation ?x]]]
    [forall [?x] [=> [bneck/ProductionLineDesign ?x] [sysl/DynamicSystemDesign ?x]]]

    ;; ---- flow topology properties ----
    [forall [?x ?y] [=> [bneck/upstreamOf ?x ?y]   [dul/hasLocation ?x ?y]]]
    [forall [?x ?y] [=> [bneck/downstreamOf ?x ?y] [dul/hasLocation ?x ?y]]]
    [forall [?x ?y] [<=> [bneck/upstreamOf ?x ?y]  [bneck/downstreamOf ?y ?x]]]])

;;; ===========================================================================
;;; (E) Enriched: n-ary relations collapsing OWL reification, plus COMPUTABLE
;;; definitions. This is the layer the recognizer executes against.
;;; ===========================================================================

(def axioms-enriched
  '[;; --- E1. Role-playing in a situation, one 3-ary relation.
    ;; Collapses: role-individual classifies entity + isDefinedIn pattern +
    ;; pattern isSatisfiedBy situation + entity includedIn situation.
    [forall [?ent ?role ?sit]
     [=> [plays ?ent ?role ?sit]
      [and
       [dul/Situation ?sit]
       [in-setting ?ent ?sit]]]]

    ;; --- E2. Setting membership, collapsing includesEvent/includesObject.
    [forall [?x ?sit]
     [=> [in-setting ?x ?sit] [dul/Situation ?sit]]]

    ;; --- E3. WARRANTED CAUSATION, 4-ary: within situation ?sit, ?c causes ?e,
    ;; warranted by theory ?th. Collapses the EMF EventCausalityPattern /
    ;; EventCausalitySituation / Justification-role chain (~8 individuals in the TTL) into one relation.
    ;; The theory argument is the MDC-style mechanism warrant: no causal claim without its science.
    [forall [?c ?e ?sit ?th]
     [=> [causes-in ?c ?e ?sit ?th]
      [and
       [in-setting ?c ?sit]
       [in-setting ?e ?sit]
       [dul/Theory ?th]
       [in-setting ?th ?sit]]]]

    ;; --- E4. Warranted correlation (blocking <-> starvation coupling).
    [forall [?e1 ?e2 ?sit ?th]
     [=> [correlated-in ?e1 ?e2 ?sit ?th]
      [and [in-setting ?e1 ?sit]
       [in-setting ?e2 ?sit]
       [dul/Theory ?th]
       [in-setting ?th ?sit]]]]
    [forall [?e1 ?e2 ?sit ?th]
     [=> [correlated-in ?e1 ?e2 ?sit ?th] [correlated-in ?e2 ?e1 ?sit ?th]]]

    ;; --- E5. Composition within a situation (bottleneck = WIP accumulation +
    ;; downstream starvation).
    [forall [?whole ?part ?sit]
     [=> [composed-in ?whole ?part ?sit]
      [and
       [in-setting ?whole ?sit]
       [in-setting ?part ?sit]]]]

    ;; --- E6. COMPUTABLE DEFINITIONS - the recognizer's teeth.
    ;; cv-of-line is a measurement predicate: its value is COMPUTED from
    ;; occupancy-time ground claims (sigma/mu), not matched. Tool seam.
    [forall [?line ?t ?v]
     [=> [cv-of-line ?line ?t ?v] [>= ?v 0]]]

    ;; A line is imbalanced when its CV falls in the imbalanced region
    ;; (threshold supplied by the region; 0.1 in the TTL glosses).
    [forall [?line ?t]
     [<=> [imbalanced-line ?line ?t]
      [exists [?v]
       [and
        [cv-of-line ?line ?t ?v]
        [>= ?v 0.1]]]]]

    ;; The bottleneck station is the max-occupancy-time station of an imbalanced line.
    [forall [?ws ?line ?t]
     [<=> [max-occupancy-station ?ws ?line ?t]
      [and
       [station-of ?ws ?line]
       [forall [?w2] [=> [station-of ?w2 ?line]
                      [exists [?c1 ?c2]
                       [and
                        [occupancy-time ?ws ?c1 ?t]
                        [occupancy-time ?w2 ?c2 ?t]
                        [>= ?c1 ?c2]]]]]]]]

    [forall [?ws ?line ?t]
     [<=> [bottleneck-station ?ws ?line ?t]
      [and
       [max-occupancy-station ?ws ?line ?t]
       [imbalanced-line ?line ?t]]]]

    ;; Blocking: a station is blocked when some buffer immediately downstream
    ;; of it is full. Only possible in finite-buffer lines.
    [forall [?ws ?t]
     [<=> [blocked ?ws ?t]
      [exists [?b] [and
                    [bneck/Buffer ?b]
                    [bneck/upstreamOf ?ws ?b]
                    [buffer-state ?b full ?t]]]]]

    ;; Starvation: a station is starved when some buffer immediately upstream
    ;; of it is empty.
    [forall [?ws ?t]
     [<=> [starved ?ws ?t]
      [exists [?b] [and
                    [bneck/Buffer ?b]
                    [bneck/upstreamOf ?b ?ws]
                    [buffer-state ?b empty ?t]]]]]

    ;; --- E7. RECOGNITION BRIDGE: computable layer entails the DnS layer.
    ;; When a bottleneck station exists, there is a situation in which it plays
    ;; the BottleneckStationRole, warranted by generic bottleneck theory.
    ;; (The recognizer CONSTRUCTS this situation; the axiom licenses it.)
    [forall [?ws ?line ?t]
     [=> [bottleneck-station ?ws ?line ?t]
      [exists [?sit ?imb ?bstate ?th]
       [and
        [dul/Situation ?sit]
        [plays ?ws bneck/BottleneckStationRole ?sit]
        [causes-in ?imb ?bstate ?sit ?th]
        [bneck/GenericBottleneckTheory ?th]]]]]

    ;; Blocking and starvation on the same imbalanced line are correlated,
    ;; warranted by buffered-line theory (Li & Meerkov).
    [forall [?w1 ?w2 ?line ?t]
     [=> [and
          [blocked ?w1 ?t]
          [starved ?w2 ?t]
          [station-of ?w1 ?line]
          [station-of ?w2 ?line]
          [imbalanced-line ?line ?t]]
      [exists [?sit ?th]
       [and
        [dul/Situation ?sit]
        [plays ?w1 bneck/BlockedStationRole ?sit]
        [plays ?w2 bneck/StarvedStationRole ?sit]
        [correlated-in ?w1 ?w2 ?sit ?th]
        [bneck/BufferedLineTheory ?th]]]]]

    ;; Throughput shortfall (performance-goals bridge): a bottleneck situation
    ;; causes a shortfall against the assumed throughput goal.
    [forall [?line ?t]
     [=> [exists [?ws]
          [bottleneck-station ?ws ?line ?t]]
      [throughput-shortfall ?line ?t]]]])

(def computable
  "Predicates the recognizer COMPUTES (tool seam) rather than matches.
   Everything else in axioms-enriched is derived by match/unify over claims."
  '{cv-of-line       "sigma/mu over the line's occupancy-time claims at ?t"
    max-occupancy-station "argmax of occupancy-time claims over station-of ?line"
    imbalanced-line  "threshold test on cv-of-line (0.1 per TTL gloss)"
    blocked          "join upstream-of x buffer-state=full"
    starved          "join upstream-of x buffer-state=empty"})

;;; ===========================================================================
;;; GROUND CLAIMS - what feeds would supply. The recognizer's ONLY input.
;;; At ingestion each acquires the claim envelope (valid-time, modality
;;; :observed, provenance) per etk docs/etk-diachronic-claims.md; shown bare
;;; here. Values from the TTL ABox: 5 stations [30 30 45 30 30] min, 4 buffers
;;; capacity 10, buffer-2 full, buffer-3 empty at observation time t1.
;;; ===========================================================================

(def ground-claims
  '[;; topology
    [bneck/Workstation ws-1] [bneck/Workstation ws-2] [bneck/Workstation ws-3]
    [bneck/Workstation ws-4] [bneck/Workstation ws-5]
    [bneck/Buffer b-1] [bneck/Buffer b-2] [bneck/Buffer b-3] [bneck/Buffer b-4]
    [station-of ws-1 line-1] [station-of ws-2 line-1] [station-of ws-3 line-1]
    [station-of ws-4 line-1] [station-of ws-5 line-1]
    [bneck/upstreamOf ws-1 b-1] [bneck/upstreamOf b-1 ws-2]
    [bneck/upstreamOf ws-2 b-2] [bneck/upstreamOf b-2 ws-3]
    [bneck/upstreamOf ws-3 b-3] [bneck/upstreamOf b-3 ws-4]
    [bneck/upstreamOf ws-4 b-4] [bneck/upstreamOf b-4 ws-5]
    [buffer-capacity b-1 10] [buffer-capacity b-2 10]
    [buffer-capacity b-3 10] [buffer-capacity b-4 10]
    ;; machine occupancy times (minutes; the TTL calls these cycle times) -
    ;; the observable that carries the imbalance
    [occupancy-time ws-1 30 t1] [occupancy-time ws-2 30 t1] [occupancy-time ws-3 45 t1]
    [occupancy-time ws-4 30 t1] [occupancy-time ws-5 30 t1]
    ;; buffer states observed at t1
    [buffer-state b-1 partial t1]
    [buffer-state b-2 full t1]
    [buffer-state b-3 empty t1]
    [buffer-state b-4 partial t1]])

;;; ===========================================================================
;;; ORACLE - what the TTL ABox hand-asserts about this scenario. The recognizer
;;; must reconstruct these (module situation-identifier naming) from
;;; ground-claims alone. Reconstruction = PoC success criterion.
;;; ===========================================================================

(def oracle
  '[;; computable layer
    [cv-of-line line-1 t1 0.20]                      ; mean 33, sd 6.7 -> CV 0.20
    [imbalanced-line line-1 t1]
    [max-occupancy-station ws-3 line-1 t1]
    [bottleneck-station ws-3 line-1 t1]
    [blocked ws-2 t1]                                ; b-2 (downstream of ws-2) full
    [starved ws-4 t1]                                ; b-3 (upstream of ws-4) empty
    ;; DnS layer (TTL: production-line-situation-1 and its satellites)
    [dul/Situation sit-1]
    [plays ws-3 bneck/BottleneckStationRole sit-1]
    [plays ws-4 bneck/StarvedStationRole sit-1]
    [plays ws-5 bneck/StarvedStationRole sit-1]      ; TTL: starved via b-3/b-4 drain
    [plays ws-2 bneck/BlockedStationRole sit-1]
    [causes-in imbalanced-op-1 bottleneck-state-1 sit-1 queuing-theory-1]
    [bneck/GenericBottleneckTheory queuing-theory-1]
    [correlated-in blocking-1 starvation-1 sit-1 buffered-theory-1]
    [bneck/BufferedLineTheory buffered-theory-1]
    [composed-in bottleneck-state-1 wip-accumulation-1 sit-1]
    [composed-in bottleneck-state-1 starvation-1 sit-1]
    [throughput-shortfall line-1 t1]])

;;; ===========================================================================
;;; PROVENANCE - how sit-1 came to be believed. In-file, justifications cite
;;; claim FORMS (claims are values); in Datahike they become entity refs, and
;;; the belief-time side (who/when/what-tx) lands on the tx entity per
;;; etk docs/etk-diachronic-claims.md. Two-level chain: the situation is
;;; justified by computables, computables by ground claims.
;;; ===========================================================================

(def provenance
  '{sit-1
    {:satisfies      bneck/BottleneckStateType         ; the description satisfied
     :ttl-source     "bottleneck#production-line-situation-1"
     :constructed-by :recognizer                       ; vs :hand-asserted, :adapted
     :fit-verdict    :satisfied                        ; vs :closest-template, :none
     :justified-by   [[bottleneck-station ws-3 line-1 t1]
                      [blocked ws-2 t1]
                      [starved ws-4 t1]]}
    [bottleneck-station ws-3 line-1 t1]
    {:computed-by  max-occupancy-station
     :justified-by [[occupancy-time ws-1 30 t1] [occupancy-time ws-2 30 t1]
                    [occupancy-time ws-3 45 t1] [occupancy-time ws-4 30 t1]
                    [occupancy-time ws-5 30 t1]
                    [cv-of-line line-1 t1 0.20]]}
    [blocked ws-2 t1]
    {:computed-by  blocked
     :justified-by [[bneck/upstreamOf ws-2 b-2] [buffer-state b-2 full t1]]}})

;;; ===========================================================================
;;; Glosses - rdfs:label/comment; the NL bridge for interviews and mentoring.
;;; ===========================================================================

(def glosses
  '{bneck/Workstation        "A workstation performing operations on work items."
    bneck/Buffer             "Finite-capacity storage between workstations (conveyor segments, racks, staging). Decouples neighbors but introduces blocking when full."
    bneck/WorkInProcess      "Material being processed, including items waiting in buffers."
    bneck/CycleTimeBalance   "Quality: how balanced cycle times are across stations, measured by CV = sigma/mu (dimensionless)."
    bneck/BalancedCVRegion   "CV region indicating balance; typically CV < 0.1. Perfectly balanced line has CV = 0."
    bneck/ImbalancedCVRegion "CV region indicating imbalance; the causal antecedent of bottleneck formation."
    bneck/BufferCapacity     "Max work items a buffer holds; at capacity the upstream station is blocked."
    bneck/SystemThroughput   "Output rate; constrained by the bottleneck: 1/max(cycle-times), not 1/mean."
    bneck/BottleneckStateType "State where throughput is constrained by the max-cycle-time station."
    bneck/BufferedBottleneckStateType "Bottleneck in a finite-buffer line: blocking propagates backward, starvation forward (Li & Meerkov 2009)."
    bneck/ImbalancedOperationType "State where cycle-time CV exceeds the balance threshold; causes bottleneck formation."
    bneck/BlockingType       "Upstream station cannot discharge work: downstream buffer full. Finite-buffer systems only."
    bneck/StarvationType     "Downstream stations wait for work from the bottleneck."
    bneck/WIPAccumulationType "WIP builds in the buffer before the constraining resource (Little's Law)."
    bneck/BottleneckStationRole "Role of the constraining station - longest cycle time."
    bneck/BlockedStationRole "Role of upstream stations blocked by full downstream buffers."
    bneck/StarvedStationRole "Role of downstream stations starved of input."
    bneck/QueuedJobRole      "Role of WIP accumulated before the bottleneck."
    bneck/GenericBottleneckTheory "Why imbalance causes bottlenecks: queuing theory, Little's Law, Theory of Constraints. Throughput = 1/max(cycle-times)."
    bneck/BufferedLineTheory "Why blocking and starvation couple in finite-buffer lines: both stem from the bottleneck, mediated by buffer capacity. Li & Meerkov, Production System Engineering, 2009, ch. 4-6."
    bneck/upstreamOf         "Flow topology: ?x is immediately upstream of ?y. WS1 upstreamOf B1 upstreamOf WS2."
    ;; introduced-by-enrichment
    plays          "(3-ary) entity ?ent plays role ?role in situation ?sit. Collapses classifies/isDefinedIn/isSatisfiedBy/includedIn."
    in-setting     "(binary) ?x is part of the setting of situation ?sit (event, object, or theory)."
    causes-in      "(4-ary) within ?sit, ?c causes ?e, warranted by theory ?th. No causal claim without its science."
    correlated-in  "(4-ary, symmetric) within ?sit, ?e1 and ?e2 are correlated, warranted by ?th."
    composed-in    "(3-ary) within ?sit, ?whole has component ?part."
    occupancy-time "(3-ary) station, minutes, time. Ground observable. The TTL's 'cycle time'; renamed because occupancy is neutral about cause (processing, setup, breakdown-inflated)."
    buffer-state   "(3-ary) buffer, {empty partial full}, time. Ground observable."
    station-of     "(binary) station belongs to line. Ground observable."
    cv-of-line     "(3-ary, COMPUTED) line, time, coefficient of variation of occupancy times."
    max-occupancy-station "(3-ary, COMPUTED) the argmax occupancy-time station of a line."
    imbalanced-line "(binary, COMPUTED) CV at or above threshold."
    bottleneck-station "(3-ary, COMPUTED) max-cycle station of an imbalanced line."
    blocked        "(binary, COMPUTED) station with a full buffer immediately downstream."
    starved        "(binary, COMPUTED) station with an empty buffer immediately upstream."
    throughput-shortfall "(binary) line's actual throughput falls short of the assumed throughput goal owing to a bottleneck."})
