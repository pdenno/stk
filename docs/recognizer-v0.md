# Recognizer v0 — working spec (handoff)

Status: not started. This document is the distilled state of the design conversation
(Cowork sessions, 2026-07/08) sufficient to implement without re-reading transcripts.

## Goal
Given ONLY `ground-claims` from `src/stk/descriptions/theories/bottleneck.clj`,
reconstruct the `oracle` (modulo gensym'd situation/event identifiers). That
reconstruction is the proof-of-concept success criterion for the
canonical-Description approach.
(Terminology note: corrected per docs/conops.md — library entries are Descriptions,
type-like; Situations are the instance-like settings that satisfy them.)

## What the recognizer is (and is not)
Two layers, deliberately separated:

1. **Fit judgment** (orchestrator/LLM, NOT v0 scope): reads a canonical Description's
   `metadata :purpose` against an SQ/context and renders a verdict:
   `:satisfied` | `:closest-template` (adapt a derived description; provenance points
   to the canonical parent) | `:none` (commissioning — a strategic/management process,
   never runtime improvisation). v0 assumes the verdict was `:satisfied` for bottleneck.
2. **Verify + bind** (mechanical, THE v0 scope): compute the computable predicates,
   construct the situation instance, bind roles, attach warrants and justifications.

No open-ended abduction. The neighborhood is already located (by SQ ↔ Description index,
`metadata :relevant-SQs`); v0's job is classification + instance binding only.

## Implementation plan
Namespace: `stk.recognizer` (src/stk/recognizer.clj) — the recognizer is not itself a Description.

1. **Claim store**: for v0, an in-memory indexed set of the ground-claim vectors
   (claims are values). Datahike + claim envelope (valid-time/modality per
   etk docs/etk-diachronic-claims.md) comes later; keep the interface narrow
   (a `claims` protocol or just fns over a set) so the swap is cheap.
2. **Computables** (see the `computable` map in bottleneck.clj — the tool seam):
   - `cv-of-line`: sigma/mu over `[occupancy-time ?ws ?min ?t]` joined via `station-of`.
   - `max-occupancy-station`: argmax of the same.
   - `imbalanced-line`: cv >= 0.1 (threshold from TTL gloss; later a revisable Parameter).
   - `blocked`: join `[bneck/upstreamOf ?ws ?b]` x `[buffer-state ?b full ?t]`.
   - `starved`: join `[bneck/upstreamOf ?b ?ws]` x `[buffer-state ?b empty ?t]`.
   NOTE the oracle also marks ws-5 starved (buffer drain propagation in the TTL prose).
   v0's local-adjacency `starved` will find only ws-4. Options: (a) accept the delta and
   note it, or (b) add a propagation rule (starved station with empty output buffer
   starves its successor). Decide with Peter; (a) is honest for v0.
3. **Construction per the E7 recognition bridge** (axioms license it):
   when `bottleneck-station` holds, mint a situation id, assert
   `[dul/Situation sit-N]`, `[plays ws bneck/BottleneckStationRole sit-N]`, the
   `causes-in` 4-ary with a `bneck/GenericBottleneckTheory` individual, and (when
   blocked+starved on an imbalanced line) `correlated-in` warranted by
   `bneck/BufferedLineTheory`. Composition (`composed-in`) mirrors oracle entries.
4. **Justifications**: every derived claim carries the provenance shape used in
   bottleneck.clj's `provenance` def: `{:computed-by <pred> | :constructed-by :recognizer,
   :fit-verdict :satisfied, :justified-by [<claim forms>]}`. In-file/in-memory the
   justifications cite claim FORMS (claims are values); under Datahike they become eids.
5. **Match/unify**: `org.clojure/core.unify` is in deps for matching axiom patterns
   against claim vectors (?-prefixed symbols are variables; `stk.descriptions.schema/variable?`).
   v0 can hand-code the five computables + bridge rather than build a general FOL engine —
   generality can grow with the second library entry.
6. **Test**: compare recognizer output to `oracle` with an alpha-renaming equivalence on
   the minted ids (sit-1, queuing-theory-1, etc.). A `deftest` in test/ mirroring
   `check-bottleneck` style; also validate output claims are `ground?`.

## Contracts to respect
- Entry contract: `stk.descriptions.schema` (Malli). No lists anywhere; no ?vars in ground data.
- The `:purpose` text carries scope caveats (e.g., breakdowns NOT modeled; occupancy-time
  deliberately cause-neutral). Do not "fix" the axioms to cover what :purpose excludes —
  that is adaptation/commissioning work, tracked separately.

## After v0 (do not start; recorded so intent survives)
- Expose recognizer as an MCP tool (tool-system multimethods, like repl_eval).
- Second library entry (raw-material-uncertainty genus -> material-blocked-order) +
  quadcopter SQ1 scenario ("What are the customer orders we can work on today?").
- Adaptation experiments: Bautista mixed-model sequencing (derived description);
  flowline DES tool interpreting occupancy variation as breakdowns (commissioning w/ template;
  DES doubles as the validation tool).
- Claims move to Datahike with `:keep-history? true`; staleness propagation through
  situation `includes` on supersession (see etk docs/etk-diachronic-claims.md).
