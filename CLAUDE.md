# CLAUDE.md - AI Coding Copilot Instructions

Operating instructions for LLM agents working on **stk** (Situation Teaming Kernel).

## What stk is
STK is a human/AI teaming system for building and maintaining *situation awareness* of
production operations at small and medium-sized manufacturers, from which decision support
(production scheduling first) is derived. It is a sibling of sched6 (same author, shared
idioms, some ported code) but a separate project with a different DB schema and different
discovery-schema content. Design background lives in `~/Documents/git/papers/etk/`
(esp. `docs/etk-diachronic-claims.md` and `etk-conversation.org`).

## Core concepts (read before touching src/stk/descriptions/)
- **Canonical Description library** (`src/stk/descriptions/{theories,plans,diagnoses}/*.clj`):
  each entry is FOL-as-data (nested VECTORS, never lists) translating a
  DnS/Event-Model-F-style ontology. Per DOLCE/DnS: Descriptions are type-like
  (Theory, Plan, Diagnosis ⊑ Description); Situations are the instance-like settings
  that `satisfy` them — there is NO "Situation Type" category; kinds of situations are
  given by the Descriptions they satisfy. An entry = STK's claim of competency in a
  circumstance. Membership is BY CONTRACT:
  `stk.descriptions.schema/validate-entry` (Malli) must return nil.
- **Entry anatomy**: `metadata` (`:purpose` is read by the orchestrator to judge fit),
  `signature`, `axioms-faithful` (1:1 vs source TTL), `axioms-enriched` (n-ary collapses +
  COMPUTABLE definitions), `computable` (tool seam), `ground-claims` (recognizer input),
  `oracle` (what must be reconstructed), `provenance`, `glosses`.
- **Recognizer** (to be built): classification + instance binding within a located
  neighborhood — NOT open-ended discovery. Three fit verdicts:
  `:satisfied`, `:closest-template` (adapt), `:none` (commissioning, a management choice).
- **Claims** are diachronic: valid-time + modality {observed, projected, counterfactual}
  as claim attributes; belief-time comes from Datahike `:keep-history? true` (`as-of`/`history`;
  avoid `since`). Provenance: belief-time on the tx entity, world-time on the claim.
  Supersede, don't contradict; mark derived claims `:stale`, don't auto-retract.

## Code layout
- `src/stk/` - production code (MCP core/util, tool-system, descriptions/schema, utils)
- `src/stk/descriptions/` - the canonical Description library (theories/, plans/,
  diagnoses/; entries loaded via `load-file`)
- `env/dev/develop/` - dev-only code incl. `repl_eval.clj` (MCP eval tool; hot-load + `register!`)
- Style: follow `LLM_CODE_STYLE.md`. Booleans end `?`, mutators end `!`, `^:diag` for
  REPL-only defns, comment string before args, preserve alignment whitespace.

## REPL
nREPL is usually running (CIDER, `C-c M-j`; see `.dir-locals.el`). Use the
`clojure-eval` skill / `clj-nrepl-eval` with `--discover-ports`. Always `:reload` when
requiring. The MCP server (`stk.mcp-core`, mount defstate) serves streamable HTTP at
`http://localhost:8091/mcp` (port via `config.edn` `:mcp-port`).

Quick health checks:
- `(require '[stk.descriptions.schema :as sschema] :reload)` then `(sschema/check-bottleneck)` => nil
- `(mutil/run-tool "repl_eval" {:code "(+ 1 2)"})` (after hot-loading env/dev/develop/repl_eval.clj)

## Rules
- Datahike pinned 0.6.1610 (do NOT upgrade to 0.7.x). New project DBs: `:keep-history? true`.
- Isolate Datahike queries/pulls to a few designated files (pattern from sched6).
- Prefer atoms over dynamic vars; prefer DB over atoms for durable state.
- Maps/vectors/sets only for data — no lists (recursive walks key on map?/vector?).
- Malli schemas for important objects; see `src/stk/descriptions/schema.clj`.
- Surgical edits only; ask questions when uncertain.

## Current focus
Recognizer v0 - see `docs/recognizer-v0.md`.

*Keep this file under 120 lines.*
