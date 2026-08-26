# Contract: `LocationService` (rg-logic → consumed by rg-frontend-vaadin)

Interface `vg.rg.service.LocationService`. The UI depends only on this interface (Constitution VI).
Every method is authorized against the caller's permissions; denial fails safely without leaking
internals. Author/last-editor audit fields are set by JPA auditing, not by callers.

## Methods

### `LocationModel create(LocationModel model)`
- **Permission**: `location:add` (`@PreAuthorize`).
- **Preconditions**: `name` non-blank; `latitude`/`longitude` in valid ranges; optional `description`,
  `googlePlaceId` within length bounds. `uniqueId`/`version`/audit fields ignored on input.
- **Behavior**: assigns a new `uniqueId`, persists, sets author=lastEditor=current `userUniqueId`, `version=0`.
- **Returns**: persisted `LocationModel` (with id, timestamps, version).
- **Errors**: validation error (invalid/blank) → domain `ValidationException`; missing permission →
  `AccessDeniedException`.

### `LocationModel update(LocationModel model)`
- **Permission**: `location:edit`.
- **Preconditions**: existing `uniqueId`; `version` **must** equal the persisted version (optimistic
  concurrency); same field validation as create.
- **Behavior**: applies changes, bumps `version`, sets lastEditor=current `userUniqueId`.
- **Returns**: updated `LocationModel`.
- **Errors**: not found → `EntityNotFoundException`; stale version →
  `ObjectOptimisticLockingFailureException` (UI shows localized reload/retry — FR-019); validation →
  `ValidationException`; missing permission → `AccessDeniedException`.

### `void delete(UniqueId id)`
- **Permission**: `location:delete`.
- **Behavior**: hard-deletes the location. UI must confirm before calling (FR-008).
- **Errors**: not found → `EntityNotFoundException`; missing permission → `AccessDeniedException`.

### `List<ProximityMatch> findNearby(ProximityQuery query)`
- **Permission**: `location:view`.
- **Behavior**: bounding-box prefilter by `query` coordinates and radius (default from `GeoProperties`,
  500 m), then great-circle distance refine to `distanceMeters ≤ radius`, ordered nearest-first (FR-003). Result is
  **advisory suggestions only** — it does not block creation and `create` never consults it (FR-003a).
- **Returns**: possibly empty list; empty means "no nearby place to suggest" (US1 scenario 2).
- **Errors**: invalid coordinates → `ValidationException`; missing permission → `AccessDeniedException`.

### `List<LocationModel> searchByName(String query, int limit)`
- **Permission**: `location:view`.
- **Behavior**: case-insensitive name contains-match, bounded by `limit` (FR-005). Blank query → full
  collection (paged by caller).
- **Returns**: matching locations (possibly empty).

### `Page<LocationModel> browse(Pageable pageable)`
- **Permission**: `location:view`.
- **Behavior**: paged listing of the shared collection for display (FR-006), scalable to ≥10k rows.

## Cross-cutting guarantees
- Server-side proximity/CRUD never depend on Google Maps (Constitution IV; FR-013).
- All outward-facing failures map to stable message keys the UI localizes; internals are not exposed.
- No natural-person data is persisted; audit identities are abstract `userUniqueId` values only.
