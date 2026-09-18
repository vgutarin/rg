# <Domain>

<!-- Purpose: see AGENTS.md. Current-state specification of <domain>. Update this file in the same
change whenever <the governing code: classes/endpoints/config> changes. -->

<!--
HOW TO USE THIS TEMPLATE (delete this block in the real file)
- Copy to specs/current/<domain>.md. One file per LOGICAL DOMAIN, not per feature/ticket.
- Current-state ONLY. Describe what the system IS, as the authoritative behaviour.
  Banned words: was, previously, no longer, used to, migrate, backward-compat(history),
  enrichment, legacy, not yet, future, drift, "before X". Unfinished work → open-decisions.md.
- GROUND EVERY CLAIM IN CODE before writing. Never state a field/type/endpoint/enum/enforcement
  fact you have not confirmed in source. Prefer the `/spec-current` skill, which enforces this.
- Every FR/INV/SC must be answerable yes/no against the running system or a test.
- Machine-facing contracts are technical on purpose: naming fields/types/JSON keys IS the spec.
- Drop any section below that genuinely does not apply. Do not pad.
-->

## Purpose

<What this domain guarantees, and for whom. 1–3 sentences.>

## Actors

<Only if more than one consumer/author matters.>

- **<Actor>** — <what they consume or author here>.

## Domain entities

<The types/records/enums that make up the domain, with their meaningful fields and allowed values.>

- **<Entity>** — <fields, types, allowed values, key relationships>.

## Contract

<The observable surface: endpoint(s), request/response shape, emitted fields, config keys. State
exactly what is present and when a field is absent.>

| Field | Source | Notes |
|---|---|---|
| `<field>` | `<origin>` | <when present/absent, meaning> |

## Functional requirements

<Normative, each independently testable. MUST/SHOULD.>

- **FR-001**: The system MUST <observable rule>.
- **FR-002**: <...>

## Invariants

<Properties that always hold and map to a check (a test, a schema).>

- **INV-001**: <property> (checked by <test/schema>).

## Success criteria

<Measurable, outcome-focused.>

- **SC-001**: <measurable outcome>.

## Related

- [<other current-state file>](<file>.md) — <relationship>.
- Open questions for this domain: [open-decisions.md](open-decisions.md).
