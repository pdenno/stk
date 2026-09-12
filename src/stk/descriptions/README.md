# The canonical Description library

Per DOLCE/DnS: Descriptions are the type-like things; Situations are the instance-like
settings that `satisfy` them. In DUL, `Theory`, `Plan`, and `Diagnosis` are all
subclasses of `Description` — hence the subdirectories:

- `theories/`  — Descriptions whose core is explanatory science (e.g., bottleneck:
  Li & Meerkov, queuing theory, Theory of Constraints).
- `plans/`     — Plan-like Descriptions, including capability validity conditions
  (a capability's Description specializes canonical Descriptions; its executions
  are Situations checked against it, PlanExecution-style).
- `diagnoses/` — Diagnosis Descriptions: explanations of deviation, the RCA-shaped
  entries (adjacent to the vacant MAESTRO Critic niche).

There is no "Situation Type" category anywhere in this library: kinds of situations
are given by the Descriptions they satisfy. Entry contract: `stk.descriptions.schema`
(`validate-entry` must return nil). Subdirectories are provisional scaffolding to keep
the framing straight; consolidate later if they don't earn their keep.
