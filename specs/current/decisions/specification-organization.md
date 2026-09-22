# Specification organization decision

## Decision and boundaries

The documentation refactor separates current UI contracts from domain logic while
retaining `specs/current/` as the root. Authoring/maintenance instructions live in
[specs/README.md](../../README.md#documentation-authoring-and-maintenance), with a mandatory
usage reference in root [AGENTS.md](../../../AGENTS.md), not in the constitution or Spec Kit templates.

The new structure comprises UI, logic, engineering, templates, and decisions, with
open questions kept at their existing path. Numbered feature directories retain
their paths and historical meaning. `./specify/`, `.specify/`, and the constitution
are excluded from modification. The original implementation plan
(`specs/specification-refactor-plan.md`) recorded the agreed migration scope.

## Rationale

Dedicated templates keep placeholders out of authoritative current contracts.
Capability-level UI documents and one shared-interactions document avoid premature
component-by-component fragmentation. DDD supplies vocabulary, identity, responsibilities,
invariants, and consistency boundaries without imposing a new application architecture.

Diátaxis distinguishes tutorials, how-to guides, reference, and explanation. Current
contracts are primarily reference, operational engineering notes are primarily how-to
guidance, and decision records provide explanation. Four additional directory trees
would not improve this repository's navigation.

Retaining the current root preserves the constitution's root-level references without
changing it. History and proposals remain supporting material, not implemented behavior.

## Migration accounting

Paths in the first column describe the superseded flat layout. Destination paths are
relative to `specs/current/`. Multiple sections are grouped only when they share the
same ownership split.

The original migration placed authoring instructions in root AGENTS as recorded below.
The subsequent user-requested move places that guide in `specs/README.md` instead.

| Source document and sections | Destination |
| --- | --- |
| `_TEMPLATE.md`: purpose, actors, domain entities, contract, requirements, invariants, success criteria, related | UI/logic template scaffolds; authoring requirements in root AGENTS |
| `home.md`: top-level destinations, Telegram native back navigation, Home | [UI navigation](../ui/navigation.md) |
| `dates-and-times.md`: values/formatting, components, examples | [Shared interactions](../ui/shared-interactions.md); event instant semantics linked from event logic |
| `workspace.md`: purpose, container, lifecycle capabilities | [Workspace vocabulary](../logic/workspace/README.md), [lifecycle](../logic/workspace/lifecycle.md), [UI workspaces](../ui/workspaces.md) |
| `workspace.md`: access control, resource-addressed check, scope resolution | [Workspace scope](../logic/workspace/README.md#authorization-scope), [identity/access](../logic/identity-access.md) |
| `workspace.md`: presentation | [UI navigation](../ui/navigation.md), [workspaces](../ui/workspaces.md), [foundations](../ui/foundations.md), [shared interactions](../ui/shared-interactions.md) |
| `workspace.md`: data | Owning workspace/location/participant/event/registration logic; retained-table history in [domain history](domain-history.md#global-location-scope) |
| `workspace.md`: retired global scope | [Domain history](domain-history.md#global-location-scope), open decision 2 |
| `workspace.md`: configuration, where it lives | Lifecycle/participant logic configuration; linked implementation and tests in each capability |
| `geolocation.md`: purpose, scope, data, access control | [Location logic](../logic/workspace/locations.md); obsolete scope history in domain history |
| `geolocation.md`: capabilities | [Location UI](../ui/locations.md), [location selection](../ui/shared-interactions.md#location-selection), location logic |
| `geolocation.md`: where it lives, constraints | Linked source/tests, [Maps configuration](../engineering/configuration.md#google-maps-browser-integration), location UI |
| `workspace-participants.md`: purpose, two identifiers, registering | [Participant logic](../logic/workspace/participants.md), [domain history](domain-history.md#participant-identity), open decision 6 |
| `workspace-participants.md`: why data lives here, descriptor rationale, no row binding | [Domain history](domain-history.md#contact-storage-and-encryption); current format/security rules in participant logic and encryption |
| `workspace-participants.md`: capabilities, ordering/filtering/paging, access | Participant logic and [participant UI](../ui/participants.md) |
| `workspace-participants.md`: presentation, shared presentation | Participant UI, shared interactions, foundations; rejected Grid design in domain history |
| `workspace-participants.md`: data, removal, configuration, where it lives | Participant logic, workspace deletion policy, source/schema/test links; cascade rationale in domain history |
| `workspace-events.md`: capabilities, access/lifecycle, data/presentation | [Event logic](../logic/workspace/events.md), [event UI](../ui/events.md) |
| `workspace-events.md`: participant registration | [Registration logic](../logic/workspace/registrations.md), event UI, shared scroll cues |
| `configuration.md`: convention, String parsing, absent/blank, relaxed binding | [Engineering configuration](../engineering/configuration.md), with implementation-confirmed development-holder exception |
| `encryption.md`: purpose, format, configuration, rotation, current usage | [Engineering encryption](../engineering/encryption.md); ownership/security rules in participant logic |
| `encryption.md`: copied implementation, independent keys, omitted blind index | [Domain history](domain-history.md#contact-storage-and-encryption) |
| `engineering-notes.md`: production bundle, CSS cache, select width, nested title, local production mode, tests, persistence, Java toolchain, Jackson | [Engineering notes](../engineering/engineering-notes.md) |
| `engineering-notes.md`: referring to history | Root AGENTS authoring instructions; historical reason in domain history |
| `open-decisions.md`: items 1–7 | Preserved at [open decisions](../open-decisions.md), with stale pointers corrected |
| Authentication documentation gap | [Authentication UI](../ui/authentication.md) and [identity/access logic](../logic/identity-access.md), grounded in source and tests |

## Evidence-driven corrections

The migration distinguishes runnable behavior from comments and earlier prose:

- Home renders a welcome heading, not a home launcher; the temporal example is a drawer destination.
- The `rg-bordered` stylesheet uses a raised shadow treatment and `border: none`,
  despite comments and earlier prose describing a current-color outline.
- Participant presentation renders Show phone but comments out the Call control. iOS
  calling remains open rather than being documented as working.
- Participant roster order is label/identifier, not insertion order despite its storage index.
  The model exposes phone presence, not the masked suffix mentioned in old mapper rationale.
- The sealed descriptor limit is enforced by the converter; the service has no second
  byte-size check. That discrepancy is recorded as open decision 9.
- Maps timeouts are code constants, not configurable properties.
- The development configuration holder is a mutable conditional component, not an
  immutable explicitly registered holder.
- Registration count authorization and query filtering have different scopes;
  open decision 10 records the isolation gap instead of claiming workspace filtering.
- Broad capability authorization is distinct from whether a declared permission has
  a service operation (for example, event read and workspace-local create).

No application behavior, schema, test expectation, or security policy is changed by
these documentation corrections. Evidence links name existing tests; they are not a
claim that the tests were executed as part of this documentation-only migration.
