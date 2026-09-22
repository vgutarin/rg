# Workspace locations

## Responsibility and model

A location is a saved point of interest belonging to exactly one workspace.
`LocationModel` contains identity, name, optional description, optional latitude/
longitude, optional Google Place ID, version, and audit information. Scope is assigned
from the create operation, not the caller's model.

`rg_workspace_location` stores required immutable workspace identity, coordinates
as nullable `DECIMAL(9,6)`, and plaintext name/description. Coordinates are not unique.
No Google Maps URL is persisted. Free text is stored as given; UI guidance discourages
personal data. Participant contact data belongs to [participants](participants.md).

## Commands and queries

- Create/update require a nonblank name. Coordinates may both be absent; when either
  is supplied, both are required. Latitude is bounded by ±90 and longitude by ±180.
- Update changes editable values only and compares the read version. Stale writes
  raise an optimistic-lock failure. Delete/read address the location's own identifier.
- Browse takes a workspace and pageable request; the repository applies that paging.
- Name search trims the query, uses case-insensitive containment, and orders by
  case-insensitive name then identifier. Blank input selects from the full workspace.
  A positive requested limit is capped by configuration; a nonpositive limit uses the cap.
- Nearby lookup requires valid coordinates and a positive radius. It obtains a
  workspace-scoped bounding-box subset, computes great-circle distance, retains
  points within the radius, and sorts nearest-first.

The location provider resolves read/update/delete via the location, while create/list
(including search/proximity) address the workspace. Author and last-editor never
determine access; [scope rules](README.md#authorization-scope) do.

## Functional requirements

- **FR-001**: Create MUST take workspace scope from the operation argument; update MUST preserve the existing scope.
- **FR-002**: Create/update MUST reject a blank name, an incomplete coordinate pair, or coordinates outside latitude ±90 and longitude ±180.
- **FR-003**: Update MUST reject a stale version before saving editable values.
- **FR-004**: Name search MUST stay within the addressed workspace, apply case-insensitive containment, and order by case-insensitive name then identifier.
- **FR-005**: Name-search limits MUST be capped by configuration; nonpositive limits MUST use that cap.
- **FR-006**: Nearby lookup MUST validate its origin and radius, return only workspace locations within the radius, and order nearest-first.

## Invariants

- **INV-001**: A location belongs to an existing workspace (checked by schema FK `fk_rg_workspace_location_workspace` and the required `workspace_unique_id` column).
- **INV-002**: Updating a location cannot move it to another workspace (checked by `WorkspaceLocationServiceImplTest`).
- **INV-003**: A coordinate-free location is a supported saved value (checked by `WorkspaceLocationServiceFuncTest`).

## Consistency

Writes are transactional and versioned. Workspace removal deletes locations after
events because an event's location foreign key is restrictive. Deleting a referenced
location is blocked by that constraint. Nearby suggestions are advisory, not a
deduplication constraint.

The business service is independent of Google Maps. Browser acquisition and selection
belong to [location UI](../../ui/locations.md).

## Configuration

| Property | Default |
| --- | --- |
| `rg.geo.match-radius-meters` | 500 |
| `rg.geo.max-name-search-results` | 50 |

The UI's 20-item request is distinct from this service cap.
[Configuration holders](../../engineering/configuration.md) own parsing and fallback.

## Success criteria

- **SC-001**: Name and nearby queries return zero locations from another workspace (`WorkspaceLocationServiceFuncTest`).
- **SC-002**: Nearby lookup excludes points beyond its radius and returns the retained points nearest-first (`WorkspaceLocationServiceImplTest`).
- **SC-003**: Another user's workspace denies create, browse, search, and deletion (`WorkspaceLocationServiceFuncTest`).

## Related contracts

- [Workspace authorization](README.md#authorization-scope).
- [Location UI](../../ui/locations.md) and [events](events.md).
- [Configuration](../../engineering/configuration.md).

## Evidence

- [WorkspaceLocationServiceImpl.java](../../../../rg-logic/src/main/java/vg/rg/service/workspace/WorkspaceLocationServiceImpl.java)
- [WorkspaceLocationRepository.java](../../../../rg-logic/src/main/java/vg/rg/repository/workspace/WorkspaceLocationRepository.java)
- [GeoDistance.java](../../../../rg-logic/src/main/java/vg/rg/model/geo/GeoDistance.java)
- [GeoProperties.java](../../../../rg-logic/src/main/java/vg/rg/config/GeoProperties.java)
- [002-workspace-init.yaml](../../../../rg-logic/src/main/resources/db/liquibase/002-workspace-init.yaml)
- [WorkspaceLocationServiceImplTest.java](../../../../rg-logic/src/test/java/vg/rg/service/workspace/WorkspaceLocationServiceImplTest.java)
- [WorkspaceLocationServiceFuncTest.java](../../../../rg-logic/src/test/java/vg/rg/service/workspace/WorkspaceLocationServiceFuncTest.java)
- [WorkspaceLocationServiceMethodSecurityTest.java](../../../../rg-logic/src/test/java/vg/rg/service/workspace/WorkspaceLocationServiceMethodSecurityTest.java)
- [GeoDistanceTest.java](../../../../rg-logic/src/test/java/vg/rg/model/geo/GeoDistanceTest.java)
