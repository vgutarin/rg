# Resource publication

## Responsibility and language

A resource publication controls when a resource is disclosed outside its owner or
container. Publication state is reusable and is used by events.
A publication is a draft while `visible_since` is absent and is published from the UTC
instant recorded there. Scheduling future visibility is not implemented.

## Model and identity

`rg_resource_publication` is identified by the published resource's unique ID; it has
no foreign key to a particular resource table because resource types are polymorphic.
It stores a version, publication instant, `PUBLIC`/`RESTRICTED` mode, and full audit
metadata. Resource lifecycle code owns cleanup of its publication.

`rg_resource_visibility_rule` has its own identity and belongs to one publication.
Rules target a user, workspace, or reserved workspace group without a polymorphic
target foreign key. A resource can name a subject once per kind; rules have `ALLOW` or
`DENY` effect. Persisted resource enum ordinals are `PUBLIC=0`/`RESTRICTED=1`,
`ALLOW=0`/`DENY=1`, and `USER=0`/`WORKSPACE=1`/`WORKSPACE_GROUP=2`; values are only
appended.

## Commands and queries

Resource owners create one publication row with their resource. Publishing records the
injected UTC clock instant; unpublishing clears it. Publication-state and policy writes
use the publication version. A changed mode or changed rules advance the publication
version once, including changes that keep the mode `RESTRICTED`. An identical mode
and rule set are a no-op; rule ordering has no policy meaning. Replacing a changed
policy replaces its rules in the same transaction. Removing a resource removes rules
before its publication.

An event read repairs a missing publication row as a Public draft. This permits event
rows created before publication support to remain owner-visible and editable; the repair
does not disclose the event to an audience.

`ResourcePublicationService.isResourceVisibleForCurrentUser` decides audience visibility
for one current principal through `ResourcePublicationRepository.isResourceVisibleForUser`:
a missing or draft publication is hidden, a published public publication is visible, and a
published restricted publication requires a matching allow and no matching deny. Workspace
rules match a bound participant. This query does not grant resource-owner access; owner
management remains resource-specific.

`ResourcePublicationService.browseWorkspaceEvents` returns audience-safe event projections
for the current principal. Its event-specific name is deliberate: resource publication owns
the reusable audience-policy decision, while the method supplies the first resource type's
projection. `ResourcePublicationRepository` evaluates visibility and selects the event,
location, and membership state before paging; other resource types must provide their own
audience query.

## Functional requirements

- **FR-001**: Creating a published resource or transitioning a draft to published MUST record the injected clock's current instant; unpublishing MUST clear that instant.
- **FR-002**: Publication-state and policy updates MUST reject a stale publication version.
- **FR-003**: Repairing a missing publication MUST create a Public draft with no visibility rules.
- **FR-004**: Audience visibility MUST require a current application principal and the repository's publication-policy predicate; workspace ownership MUST NOT bypass that predicate.
- **FR-005**: Audience event browsing MUST apply visibility before paging and return only the audience projection.
- **FR-006**: Publication deletion MUST remove its visibility rules before removing the publication row.
- **FR-007**: A changed mode or rule set MUST advance the publication version and return that new version; an identical policy MUST preserve the version and existing rule rows.
- **FR-008**: Replacing rules MUST allow retaining a subject while changing its effect without violating the unique-target constraint.

## Invariants

- **INV-001**: Every visibility rule references an existing publication (checked by schema FK `fk_rg_resource_visibility_rule_publication`).
- **INV-002**: A publication has at most one rule per subject kind and identifier (checked by schema constraint `uq_rg_resource_visibility_rule_target`).
- **INV-003**: `ResourceVisibilityPolicy` values contain no rules in Public mode and no duplicate targets in restricted mode (checked by `ResourceVisibilityPolicyTest`).

## Consistency

Policy replacement compares and advances the publication version in a conditional
database update before changing rules. Only one concurrent writer with the same
version can succeed; a losing writer changes no rules. The update records the current
editor and clock instant and refreshes the managed publication before returning it.
Rule deletions are flushed before replacements are inserted so retained targets do
not collide with their previous rows. All changes commit or roll back together.
A publication
does not reference its resource, so generic persistence cannot remove orphan rows
automatically. Policy authorization and content authorization remain resource-specific.

## Success criteria

- **SC-001**: Repairing an event's absent publication returns an unpublished Public state (`ResourcePublicationServiceImplTest`).
- **SC-002**: Public, allowed restricted, and denied restricted events yield the correct audience page and total after database filtering (`WorkspaceMembershipRequestPersistenceFuncTest`).
- **SC-003**: A stale publication update performs no save (`ResourcePublicationServiceImplTest`).
- **SC-004**: A rule-only change advances the version, rejects a stale writer, and permits a retained target's effect to change (`ResourcePublicationServiceFuncTest`).
- **SC-005**: An identical policy, including reordered rules, preserves its version and rule identities; concurrent changed policies admit exactly one writer (`ResourcePublicationServiceFuncTest`).

## Related contracts

- [Workspace events](workspace/events.md)
- [Event visibility and membership ADR](../decisions/0001-event-visibility-and-membership.md)
- [Resource publication ADR](../decisions/0002-resource-publication.md)

## Evidence

- [ResourcePublicationServiceImpl.java](../../../rg-logic/src/main/java/vg/rg/service/resource/ResourcePublicationServiceImpl.java)
- [007-event-visibility-membership.yaml](../../../rg-logic/src/main/resources/db/liquibase/007-event-visibility-membership.yaml)
- [ResourcePublicationServiceImplTest.java](../../../rg-logic/src/test/java/vg/rg/service/resource/ResourcePublicationServiceImplTest.java)
- [ResourcePublicationServiceFuncTest.java](../../../rg-logic/src/test/java/vg/rg/service/resource/ResourcePublicationServiceFuncTest.java)
- [ResourcePublicationSchemaTest.java](../../../rg-logic/src/test/java/vg/rg/entity/resource/ResourcePublicationSchemaTest.java)
- [ResourceVisibilityPolicyTest.java](../../../rg-logic/src/test/java/vg/rg/model/resource/ResourceVisibilityPolicyTest.java)
- [WorkspaceMembershipRequestPersistenceFuncTest.java](../../../rg-logic/src/test/java/vg/rg/entity/workspace/WorkspaceMembershipRequestPersistenceFuncTest.java)
