# Phase 1 Data Model: Geolocation Module

## Entity: `LocationEntity` (`rg-logic`, `vg.rg.entity`)

Implements `UniqueIdEntity`; audited via `AuditingEntityListener` (`@EnableJpaAuditing` already active).

| Field | Type (Java) | Column | Constraints / Notes |
|-------|-------------|--------|---------------------|
| `uniqueId` | `Long` | `unique_id BIGINT` PK | Assigned via `saveWithNewUniqueId(...)`; `pk_location_entity` |
| `version` | `int` | `version INT NOT NULL` | `@Version` — optimistic concurrency (FR-019) |
| `latitude` | `BigDecimal` | `latitude DECIMAL(9,6)` (nullable) | Optional; −90..90 when present; match coordinate (NOT unique) |
| `longitude` | `BigDecimal` | `longitude DECIMAL(9,6)` (nullable) | Optional; −180..180 when present; match coordinate (NOT unique) |
| `name` | `String` | `name VARCHAR(255) NOT NULL` | Non-blank (FR-011); bounded length |
| `description` | `String` | `description VARCHAR(1024)` | Optional; free text (FR-018 guidance in UI) |
| `googlePlaceId` | `String` | `google_place_id VARCHAR(512)` | Optional (FR-009); opaque; no URL stored |
| `author` | `UniqueId` | `author BIGINT` | `@CreatedBy` — abstract `userUniqueId`; audit only (via `UniqueId`↔BIGINT `@Convert`) |
| `lastEditor` | `UniqueId` | `last_editor BIGINT` | `@LastModifiedBy` — abstract `userUniqueId`; audit only (via `UniqueId`↔BIGINT `@Convert`) |
| `createdAt` | `Instant` | `created_at DATETIME(8) NOT NULL` | `@CreatedDate`, not updatable |
| `updatedAt` | `Instant` | `updated_at DATETIME(8) NOT NULL` | `@LastModifiedDate` |

**Table**: `rg_location` (PK `pk_rg_location`).

**Indexes**: B-tree on `latitude` for the bounding-box prefilter (longitude filtered in the same
query); index on `name` for name search. No spatial types (portable to H2).

**Validation rules** (enforced in `LocationServiceImpl` before persistence):
- `name` required, non-blank, ≤ 255 chars.
- Coordinates optional: `latitude`/`longitude` may both be null (e.g. Google Maps unavailable); when
  supplied they must be a complete pair with `latitude` in [−90, 90] and `longitude` in [−180, 180].
- `description` ≤ 1024 chars when present; `googlePlaceId` ≤ 512 chars when present.
- Invalid input → domain validation error surfaced to UI as localized feedback (no partial write).

**Lifecycle / state transitions**:
- `create` → new `uniqueId`, `version=0`, author=lastEditor=current `userUniqueId`.
- `update` → load by id, apply changes, `save`; `@Version` bump; stale version →
  `ObjectOptimisticLockingFailureException` (localized reload/retry).
- `delete` → hard delete of the row (protected by a UI confirmation step, FR-008).
- `googlePlaceId` may become unresolvable upstream → entity stays valid; UI flags preview unavailable.

**Privacy**: no natural-person mapping stored; `author`/`lastEditor` are abstract IDs used only for
audit (Constitution I; FR-010, FR-014-privacy).

## Domain models (`rg-logic`, `vg.rg.model`)

### `LocationModel` (implements `Identifiable`)
`UniqueId uniqueId`, `BigDecimal latitude`, `BigDecimal longitude`, `String name`,
`String description`, `String googlePlaceId`, `UniqueId author`, `UniqueId lastEditor`,
`Instant createdAt`, `Instant updatedAt`, `int version`. Mapped to/from entity by `LocationMapper`
(MapStruct, `uses = UniqueIdMapper.class`).

### `ProximityQuery`
`BigDecimal latitude`, `BigDecimal longitude`, `Integer radiusMeters` (optional; defaults to configured
radius). Represents coordinates acquired from the Google Maps picker in the add flow, to run the
proximity suggestion against. (Only runs when coordinates were acquired; a location added without
coordinates skips the suggestion.)

### `ProximityMatch`
`LocationModel location`, `double distanceMeters`. Result element; the service returns matches within
radius ordered by ascending `distanceMeters` (FR-003).

## Repository (`rg-logic`, `vg.rg.repository`)

`LocationRepository extends UniqueIdJpaRepository<LocationEntity>`:
- Bounding-box prefilter: `@Query` selecting rows where `latitude BETWEEN :minLat AND :maxLat AND
  longitude BETWEEN :minLng AND :maxLng`.
- Name search: derived query `findByNameContainingIgnoreCase(String, Pageable)` (bounded/paged).
- `findAll(Pageable)` for browsing large shared collections.

## Permissions (`rg-logic`, `vg.rg.security.model.Permissions`)

New nested class `Location`: `VIEW="location:view"`, `ADD="location:add"`, `EDIT="location:edit"`,
`DELETE="location:delete"`; added to `Permissions.ALL`. See [contracts/permissions.md](contracts/permissions.md).

## Configuration (`rg-logic`)

`GeoProperties` (`@ConfigurationProperties("rg.geo")`): `int matchRadiusMeters` (default 500),
optional `int maxNameSearchResults`. `CurrentUserAuditorAware implements AuditorAware<UniqueId>` →
`AuthorityChecker.currentUserUniqueId()`. A `UniqueId`↔`Long` `AttributeConverter` (BIGINT) persists the
`author`/`lastEditor` columns.
