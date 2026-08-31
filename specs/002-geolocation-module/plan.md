# Implementation Plan: Geolocation Module

**Branch**: `002-geolocation-module` | **Date**: 2026-08-26 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/002-geolocation-module/spec.md`

## Summary

Add a shared collection of saved **locations** (optional coordinates + name + optional description +
optional Google Place ID) that authenticated, permission-holding users can add, edit, remove, display,
and search. The **"Add location" flow** is: acquire coordinates from a browser **Google Maps picker**
(Place ID + coordinates via search or POI tap, or coordinates alone via a map point); if Maps is
unavailable, add the location **without coordinates** (coordinates are optional — no second acquisition
source); then run the **proximity suggestion** (already-registered locations within a
configurable radius, default ±500 m, nearest-first) and let the user pick an existing one or proceed
to create. The suggestion is advisory — adding a new location within the radius is always allowed (no
dedup/uniqueness gate). Business logic,
persistence, and proximity computation live in `rg-logic`; UI, Google Maps browser integration, and
localization live in `rg-frontend-vaadin`. Authorization uses the existing permissions model with new
`location:*` permissions; optimistic concurrency (JPA `@Version`) prevents silent update loss; author
and last-editor are recorded as abstract user IDs for audit only. No personal data about natural
persons is persisted.

## Technical Context

**Language/Version**: Java 21

**Primary Dependencies**: Spring Boot 4.1.1, Spring Data JPA, Spring Security (method security),
MapStruct 1.6.3, Lombok, Liquibase (rg-logic); Vaadin 25.2.2 (rg-frontend-vaadin); `vg.unique-id`
(abstract IDs), `vg.identity` (secure-service auth). Google Maps JavaScript API + **Places API (New)**
(browser-side, modern `PlaceAutocompleteElement`/`Place`) for the map picker; browser geolocation and
Telegram WebApp `LocationManager` are used only to **centre** the map (never to acquire the saved
coordinate). Served over **https** (secure origin required for geolocation / Telegram location);
`server.forward-headers-strategy=framework` honours `X-Forwarded-Proto` behind the TLS-terminating proxy.

**Storage**: Relational via JPA. MySQL in `rg-logic` func tests / production path; H2 at
`rg-frontend-vaadin` dev runtime. Schema via Liquibase changelogs under
`rg-logic/src/main/resources/db/liquibase/` (aggregated by `includeAll`).

**Testing**: JUnit 5 + Spring Boot Test, AssertJ, Mockito (`MockitoExtension`), `vg.lib:test`;
DB-backed functional tests (`BaseFuncTest`); contract/architecture tests under `rg-logic/.../security`
and `.../contract`. Vaadin view unit tests in `rg-frontend-vaadin`.

**Target Platform**: Telegram webview and mobile browsers (front end, `server.port=9000`); Spring Boot
server-side for `rg-logic`.

**Project Type**: Web application — multi-module Gradle (`rg-logic` domain library + `rg-frontend-vaadin`
Vaadin UI depending on it).

**Performance Goals**: Proximity match and name search are application-owned and return correct results
effectively instantly from the user's perspective (excluding any external Google Maps wait), validated
functionally (per spec SC-003). No fixed end-to-end latency target is set for flows that wait on Google
Maps (per constitution); no numeric app-owned latency bar and no large-scale load test are imposed.

**Constraints**: Mobile-first narrow-screen layout; no personal data about natural persons persisted;
finite Google Maps timeouts with graceful degradation (server-side proximity match never depends on
Google Maps); optimistic concurrency on edits; all user-facing text internationalized.

**Scale/Scope**: One shared collection, no per-user cap. Feature spans both
modules: ~1 entity + repository + service + mapper + Liquibase in `rg-logic`; list/detail/form views +
Google Maps bridge + messages in `rg-frontend-vaadin`.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

Constitution v3.1.0.

| Principle / Section | Assessment | Status |
|---------------------|-----------|--------|
| I. Personal-Data Prohibition | Locations are business data. Coordinates/name/description carry no stored natural-person mapping; author/editor are abstract user IDs (`sub`). Free-text fields show anti-PII guidance, no scanning (FR-018). No display name persisted. | PASS |
| II. Secure-Service Trust Boundaries | Auth/identity remain with the secure service; module consumes authenticated `AuthenticatedUserPrincipal`. Google Maps browser key is referrer-restricted, injected via runtime config, never in source/logs/errors. Coordinates from the browser are validated server-side before use. | PASS |
| III. Delegated Telegram Authorization | No new identity flow; actions authorized via `@authorityChecker.hasAuthority('location:*')` against secure-service-supplied permissions and `sub`. | PASS |
| IV. Resilient Middleware Boundaries | Google Maps is a browser-side enhancement with finite timeouts and clear retry/fallback; server-side proximity/CRUD never depend on it. Optimistic locking yields a clear recoverable state (existing localized `ObjectOptimisticLockingFailureException`). | PASS |
| V. Mobile-First, Accessible, i18n UX | List/detail/form designed for narrow Telegram webview first; all strings via `LocalizationService`/`messages*.properties`; accessible controls, empty/error/retry states. | PASS |
| VI. Module Ownership | Business rules, proximity, persistence, permissions in `rg-logic`; presentation + Google Maps browser integration in `rg-frontend-vaadin`; UI calls `rg-logic` via `LocationService`. | PASS |
| Architecture & Data Constraints | JPA + Liquibase migration; version-pinned deps; no new async infra; simplicity preserved (bounding-box + great-circle distance, no spatial-index dependency). | PASS |
| Dev Workflow & Test Coverage | Unit + integration/func tests for service, proximity, concurrency, permissions; Vaadin view tests; no end-to-end latency target on Maps flows. | PASS |
| Spec Artifacts & Organization | Final tasks phase will actualize `specs/current/` per constitution. | PASS (enforced in tasks) |

**Result**: PASS — no violations; Complexity Tracking not required.

## Project Structure

### Documentation (this feature)

```text
specs/002-geolocation-module/
├── plan.md              # This file
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/           # Phase 1 output
│   ├── location-service.md
│   ├── permissions.md
│   └── maps-integration.md
├── checklists/
│   └── requirements.md  # from /speckit-specify + /speckit-clarify
└── tasks.md             # /speckit-tasks output (NOT created here)
```

### Source Code (repository root)

```text
rg-logic/src/main/java/vg/rg/
├── entity/LocationEntity.java              # @Entity, @Version, @Created/LastModifiedBy/Date, lat/lng, placeId
├── repository/LocationRepository.java      # UniqueIdJpaRepository + bounding-box proximity query
├── model/
│   ├── LocationModel.java                  # domain model (UniqueId, coords, name, description, placeId, audit, version)
│   ├── ProximityQuery.java                 # input coords + radius
│   └── ProximityMatch.java                 # LocationModel + distanceMeters
├── mapper/LocationMapper.java              # MapStruct
├── service/
│   ├── LocationService.java                # interface (contract)
│   └── LocationServiceImpl.java            # @PreAuthorize per action, proximity + great-circle distance refine
├── security/
│   ├── model/Permissions.java              # add Location.{VIEW,ADD,EDIT,DELETE}
│   └── CurrentUserAuditorAware.java        # AuditorAware<UniqueId> → current userUniqueId (author/editor)
├── config/GeoProperties.java               # default match radius (configurable)
└── resources/db/liquibase/002-location-init.yaml

rg-frontend-vaadin/src/main/java/vg/rg/frontend/vaadin/
├── view/
│   ├── LocationsView.java                  # @Route("locations"), list + name search + proximity entry
│   └── LocationFormDialog.java             # add/edit form; optimistic-lock + validation feedback
├── service/MapsResolutionBridge.java       # receives browser-acquired {placeId?, lat, lng}; validates ranges
rg-frontend-vaadin/src/main/frontend/
└── google-maps-connector.ts                # Google Maps JS picker (Place ID+coords, or coords only);
                                            #   best-effort map centering (browser geolocation → Telegram
                                            #   LocationManager → Kyiv); Maps unavailable → add w/o coords
rg-frontend-vaadin/src/main/resources/
├── messages.properties / messages_en.properties  # add location.* + permission.location:* keys
└── application.properties                  # add google maps key + geo radius properties (placeholders)
```

**Structure Decision**: Reuse the established two-module split. `rg-logic` owns the `LocationEntity`,
repository (with the proximity query), `LocationService` (business rules, permission checks, great-circle distance
refinement, optimistic concurrency), MapStruct mapper, the new `location:*` permissions, an
`AuditorAware` for author/editor, and the Liquibase migration. `rg-frontend-vaadin` owns the
mobile-first views, the Google Maps browser connector (TypeScript), the server bridge that accepts
browser-resolved coordinates, and localization. This mirrors the existing Template/ProtectedAction
patterns exactly.

## Complexity Tracking

No constitution violations — section intentionally empty.
