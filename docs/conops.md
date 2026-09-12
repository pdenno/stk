# STK Concept of Operations — draft

Status: working draft for discussion (PD + Claude, 2026-08).
This document exists to force clarity, per the cii26 method: write the ConOps as a story and let it surface the distinctions.
Revisions expected.

## Terminology

- **Standing Question (SQ)**: an entry point to collaborative work, phrased as a question the partner wants answerable against their own operation.
  SQs have roots in ontology competency questions, but they are not "retrieval with light inference."
  Adopting an SQ initiates teaming that *implements a capability*; the SQ then names what that capability keeps answerable.
- **Canonical Description**: an entry in the canonical Description library (`src/stk/descriptions/`).
  Per DOLCE/DnS, Descriptions are the type-like things — Theories, Plans, and Diagnoses are all Descriptions — and Situations are the instance-like settings that `satisfy` them.
  A canonical Description carries roles, axioms (faithful + enriched), computable predicates, the science that warrants its causal claims (its Theories), and a `:purpose` text the orchestrator reads to judge fit.
  Having a canonical Description for a circumstance is STK's claim of competency in that circumstance.
  There is no separate "Situation Type" category: kinds of situations are given by the Descriptions they satisfy.
- **Claim**: a timestamped, provenance-bearing assertion about the partner's operation, gathered by interview, by the Surveyor's probing of IT/OT, or by feeds.
  Claims carry valid-time and modality {observed, projected, counterfactual}; belief-time comes from the database (see etk docs/etk-diachronic-claims.md).
  Claims are superseded, not contradicted.
- **Capability Description**: the validity conditions of a capability — itself a Description.
  It *specializes* one or more canonical Descriptions during teaming: roles are bound to the partner's vocabulary and particulars from the interview are added.
  Its assumption set is a selected subset of claims, generalized into conditions.
  (Specialization is also the relation behind `closest-template` adaptation; the two differ only in degree.)
- **Situation**: the plant-as-it-stands, bound as a setting: the instance that satisfies (or fails to satisfy) a Description.
  Interviews *characterize* Situations (populate their settings with claims) and *specialize* Descriptions; the recognizer checks `satisfies` between them.
  Capability executions are Situations too, checked against the capability's plan-like Description (cf. DUL PlanExecution).
- **Assumption**: a claim-citing validity condition inside a Description.
  Example: "material-on-hand is queryable from Ignition at daily granularity."
  Assumptions are recorded during capability construction and checked at every reuse.
- **Capability**: a runnable artifact (e.g., a MiniZinc scheduling model with its data pipeline) built through teaming, valid in any Situation that satisfies its Description.
- **DPO / DPAO**: Design Plan Objects and Design Plan Advice Objects; the agile development discipline carried over from sched6.

## Positioning

The AI Accelerator projects (MAESTRO, GUILD, PRISM, ORBIT) address process- and automation-scoped problems: changeover reduction, robotic assembly, inspection models, and the simulation/synthetic-data substrate for de-risking them.
STK sits one level up: operations and logistics decisions — what to produce, when, with what material and resources — a mix of ISA-95 Level 4 and Level 3 scope.
Whether STK is an integrated collaboration with MAESTRO or an adjunct to it is undecided and may not matter: both sides are developing agentic, MCP-based code, so integration seams exist wherever they become useful.
We want to explore methods for situation awareness in SMM production environments; the MAESTRO folks want the same, though they would describe it in terms of ORBIT/GUILD/PRISM capabilities.
Note: MITRE has indicated they will not have time to implement Critic (their monitoring / root-cause-analysis component).
That niche — monitoring production reality against plans and models, and explaining deviations — is adjacent to STK's diagnosis machinery (warranted causation within Situations), and may become a natural contribution point.

## The story

An AI Accelerator Industrial Partner engages with our software for the first time.
They understand it to be what Industrial Partners are experimenting with to gain situation awareness for choosing what to produce when, and related questions.

In this first engagement they quickly encounter Standing Questions.
SQs hint about system capabilities, latent or established.
An SQ is not answered by lookup; it is an entry point to teaming, in which the system uses a best-fit canonical Description and other templates bound to the SQ (e.g., discovery schemas) to implement a capability associated with the SQ.

Implementing the capability begins with a requirements interview, as in sched6.
Like sched6, development proceeds agilely through DPOs and DPAOs; that has worked well.
As we create the capability, we are careful to note the assumptions on which it is based.
Teaming, we create the capability with these means, and either use it for the first time or stage it for V&V.

When the partner revisits the capability, we check the assumptions established during its construction.
If they hold, we run it.
If they do not, the specific failed assumptions localize the remedial work, and we invoke a remedial process to update the capability.
The partner can also revisit a capability with the intention of enhancing it.

### Worked instance

The first SQ implemented leads to development of a scheduling capability using MiniZinc.
A few canonical Descriptions have been developed for scheduling; the best fit is determined to be one for classifiable job shops with workstations.
In the interview associated with the SQ, the partner notes that they use Ignition — an OPC UA / CESMII I3X-capable tool — for much of the information relevant to scheduling.
The partner allows us to probe the schema (the Surveyor's role), and we use what we discover there to ask better questions.
Because they identify tracking material-on-hand as a key challenge, incremental implementations focus attention there.

Early implementations of the scheduling capability take input directly from Ignition, without regard to whether inspection and assembly are automated.
However, the system has awareness, through Descriptions and Claims, of where and how various product types use automated assembly or inspection.
As MAESTRO services come online, the system can use them to develop further situation awareness.

## The reuse loop (the diachronic core)

Build time:
1. SQ adopted → canonical Description selected by fit judgment over `:purpose` texts (verdicts: satisfied / closest-template / none-commissioning).
2. Interview + Surveyor probing → Claims.
3. Canonical Description's roles bound to partner vocabulary + particulars → the capability's Description (a specialization), with its assumption set recorded as claim-citing conditions.
4. DPO/DPAO development → the capability; first use, or staged for V&V.

Reuse time:
1. Gather current claims (feeds, probes).
2. Check: does the current Situation satisfy the capability's Description?
   This is the recognizer's real job: assumption-checking, not free-floating classification.
3. Satisfied → run.
   Specific assumptions fail → remedial adaptation, localized by exactly the failed conditions (staleness propagation: a superseded claim marks stale the capabilities whose Descriptions cite it).
   Nothing near → recommission.

Sched6 precedent: DPO "Observations" are proto-claims, and DPAO critiques are proto-provenance.
STK makes first-class and checkable what sched6 does informally.

## First demonstration target

Material-on-hand, with CESMII profiles / Ignition probing — not bottleneck.
Reasons: it is the partner's stated pain in the story above; it is prerequisite to SQ1 ("What are the customer orders we can work on today?"); it exercises the Surveyor concretely (typed schema probe → ground claims, and a CESMII profile is itself a published Description, so profile-to-claims mapping is interop-as-reconciliation in miniature); it is squarely ISA-95 L3/4; and sched6's `raw-material-uncertainty.ttl` is a seed.
The bottleneck entry remains library exemplar #0: it validated the entry form (contract, faithful/enriched split, computables, oracle discipline).

## Open questions

- The exact recorded form of an assumption: which claims, generalized how, with what tolerances?
  (E.g., "daily granularity" — is a 26-hour-old reading a failed assumption?)
- "Stage it (somehow) for V&V": what is the staging mechanism, and what plays the role cii26's T&E scoring played?
- Who renders remedial verdicts — orchestrator proposes, human disposes?
- How exactly does an interview produce the capability's Description: which parts come from the canonical Description mechanically, and which require judgment?
- How MAESTRO services (and the vacant Critic niche) enter: consumers of our claims, producers of them, or both?
