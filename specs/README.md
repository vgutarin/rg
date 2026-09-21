# Specifications

[Current documentation](current/README.md) contains UI, logic, engineering, and supporting records.

## Documentation authoring and maintenance

- Keep documentation-authoring instructions in this `specs/README.md`. Root `AGENTS.md`
  supplies the mandatory usage reference. Do not duplicate this guide in the constitution,
  Spec Kit templates, scoped `AGENTS.md` files, or document scaffolds.
  The documentation organization does not require changes under `./specify/` or `.specify/`.
- Keep the structure under `specs/current/`: `ui/` for interaction contracts, `logic/` for business
  contracts, `engineering/` for technical mechanisms and operational guidance, `templates/` for
  scaffolds, `decisions/` for rationale/history, and `open-decisions.md` for pending work/questions.
  Indexes provide navigation and ownership; templates link here for instructions.
- **Current state only in `ui/` and `logic/`, including indexes and shared documents.** Describe what
  the implementation does in present-tense, verifiable terms. Exclude history, migration narratives,
  retired behaviour, rejected alternatives, historical comparisons, TODOs, roadmaps, and proposals.
  Remove obsolete statements instead of appending a chronology. Preserve useful explanations in
  `decisions/` and unresolved work in `open-decisions.md`, outside both behavioural trees.
- Ground claims in actual source and relevant tests. Confirm names, fields, routes, validation,
  authorization, and persistence constraints. Do not turn intended behaviour or an unverified
  platform outcome into a guarantee. Describe limitations neutrally; record discrepancies and
  missing verification in `open-decisions.md` without silently changing application behaviour.
- Give each rule one authoritative home and cross-link consumers. UI owns how an interaction
  presents a domain result; logic owns the business rule. Generic configuration and encryption
  mechanisms belong in engineering; business disclosure/access rules belong in logic.
- Use capability-level UI documents, shared foundations, and a compact shared-interactions document.
  Split component documents only when size or independent maintenance warrants it. Describe purpose,
  actors, routes/entry conditions, journeys, controls/outcomes, navigation, implemented loading/empty/
  validation/error/access states, and accessibility, responsiveness, localization and platform
  constraints. Link governing logic and code/tests; do not duplicate CSS or pixel inventories.
- Use Domain-Driven Design to describe existing logic: responsibility/context, ubiquitous language,
  entities/value objects/identity, commands/queries/results, lifecycle, invariants, authorization,
  actual transaction/consistency boundaries, interactions, and business-relevant persistence
  constraints. Do not invent aggregates, bounded contexts, or domain events to fill a template.
  Workspace ownership is not proof of a single aggregate; scheduled events are not DDD domain events.
- Omit template sections that genuinely do not apply and replace all placeholders. Requirements
  and invariants must be independently checkable against code, tests, or observable behavior.
  Include source/test references and links to related contracts rather than copying their rules.
- Use Diátaxis to distinguish reference (UI/logic contracts), how-to guidance (engineering tasks),
  explanation (decisions), and tutorials (learning). Do not add parallel directory trees or empty
  documents just to represent all four categories.
- Identify affected current documents when planning feature work. Update them alongside each
  implementation change, not only in a final documentation task. Before completion, verify section
  coverage, links/anchors/source references, current-only compliance, and consistency between UI
  visibility/validation/outcomes and domain authorization/service rules. Review meaning, not just
  a forbidden-word list. Documentation-only work does not require application tests; logic changes
  still require the unit and integration coverage in [AGENTS.md](../AGENTS.md#definition-of-done-mandatory).
- Preserve historical feature paths and meaning. Repair their moved-document links when needed,
  without rewriting feature records as current requirements. Retire duplicate authoritative copies
  after their content is accounted for; do not leave compatibility symlinks or competing spec trees.
- Keep open decisions actionable and remove entries when they close, preserving useful rationale
  in decisions. Add operational surprises to engineering notes. When referring to history, prefer
  a change description over a short commit hash because branch history can be rewritten.

### Architecture decision records (ADRs)

Use an ADR for a significant, long-lived decision whose alternatives and consequences
matter beyond one implementation task. Examples include authorization boundaries,
contact-data storage, deletion strategy, external integrations, and documentation
organization. Routine bug fixes, verification tasks, and small implementation details
do not need an ADR unless they introduce such a tradeoff.

#### Location and format

- Keep one decision per file in `specs/current/decisions/`, using
  `NNNN-short-kebab-case-title.md` (for example, `0001-contact-storage.md`). Start at
  `0001` and allocate the next unused number after the highest existing ADR number.
  Never reuse or renumber published identifiers; feature-directory numbers are separate.
- Start from the [ADR template](current/templates/adr.md). Record status, creation date,
  decision date when resolved, decision makers, context, alternatives, the decision,
  consequences, and related contracts/evidence. Use ISO dates (`YYYY-MM-DD`).
- Explain the constraints and why the chosen option wins, including costs, risks,
  and disadvantages. Alternatives belong in the same ADR, not separate files for
  every option. Keep the record concise rather than copying implementation details.
- Existing unnumbered decision/history documents remain supporting records; this
  policy does not automatically convert them into accepted ADRs. An extracted ADR
  links to its source and does not invent an original approval or decision date.

#### Lifecycle

| Status | Meaning and transition |
| --- | --- |
| Proposed | A choice under discussion; editable until explicitly accepted or rejected by the user/project decision maker |
| Accepted | An agreed decision, with its decision date and decision makers recorded |
| Rejected | A proposal not adopted; retained with the rejection date and reason |
| Superseded | An accepted decision replaced by another accepted ADR, with replacement date and reciprocal links |

Creating a record or finding matching code is not approval. An accepted decision is
not proof that it has been implemented. A proposed replacement does not supersede
the existing accepted ADR until the replacement is accepted.

Preserve accepted and rejected records as historical decisions. Minor factual/link
corrections and status metadata updates are allowed; do not silently rewrite the
decision or its original rationale. A changed choice requires a new ADR. On replacement,
set the old record to Superseded, link both records, and append a dated explanation
without deleting the original context or decision.

#### Relationship to other documentation

- ADRs explain **why an approach was chosen**. They may contain alternatives, historical
  context, and proposed choices; they remain outside the current-only UI/logic trees.
- `specs/current/open-decisions.md` tracks **unresolved questions, bugs, implementation
  work, and verification gaps**. When an item needs an architectural choice, link it
  to a proposed ADR. Keep remaining implementation/verification work open even after
  the decision is accepted; close the item only when that work is resolved.
- UI and logic specifications describe **what the implementation does now**. An ADR
  does not replace those contracts or authorize presenting a proposal as current behavior.
  Update affected contracts alongside implementation, linking relevant ADRs where useful
  without embedding their history in current specs.
- Engineering guidance describes **how to configure, operate, or troubleshoot** the
  chosen mechanism and links to ADRs for rationale rather than duplicating it.

Before completing ADR work, verify its identifier, status, decision ownership/dates,
alternatives and consequences, related-spec links, and any reciprocal supersession links.
