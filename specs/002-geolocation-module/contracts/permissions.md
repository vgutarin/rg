# Contract: Location Permissions

New permissions registered in `vg.rg.security.model.Permissions` (added to `Permissions.ALL`). Format
MUST match `^[a-z][a-z0-9-]*:[a-z][a-z0-9-]*$` (colon separator).

| Constant                      | Value | Gates |
|-------------------------------|-------|-------|
| `Permissions.Location.READ`   | `location:read` | browse, detail, name search, proximity match |
| `Permissions.Location.CREATE` | `location:create` | create a location |
| `Permissions.Location.UPDATE` | `location:update` | update a location |
| `Permissions.Location.DELETE` | `location:delete` | remove a location |

## Enforcement
- **Service layer**: `@PreAuthorize("@authorityChecker.hasAuthority('" + Permissions.Location.X + "')")`
  on each `LocationService` method (pattern from `ProtectedActionServiceImpl`).
- **UI layer**: views gate on `authorityChecker.hasAuthority(...)` in `beforeEnter`, rerouting to
  `NoAccessView` (no effective permissions) or `AccessDeniedErrorView` (has some, lacks this one),
  mirroring `ReportsView`. Navigation entry for locations shows only when `location:read` is held.
- Permissions are supplied by the secure service inside `AuthenticatedUserPrincipal.permissions`; the
  module never grants them and never uses author/editor identity for access (FR-010).

## i18n keys (add to `messages*.properties`, escaped colon)
```
permission.location\:read=...
permission.location\:create=...
permission.location\:update=...
permission.location\:delete=...
```

## Tests
- Extend `PermissionsTest` to assert the four new values are recognized and well-formed and included in
  `ALL`.
- Service tests assert `AccessDeniedException` (or safe denial) when the required permission is absent.
