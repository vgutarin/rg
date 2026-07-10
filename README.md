# RG Telegram application

## rg-logic

- Defines the application contracts and implements the secure authorization boundary and application services.

## rg-frontend-vaadin

- Provides the mobile-first Vaadin application and Telegram authentication entry point.

## Authorization facade selection

- Authorization services are always enabled. Setting `rg.secure-service.enabled=true` explicitly
  selects the permissive `DevSecureAuthorizationFacade`; this setting belongs only in ignored
  local development configuration.
- When `rg.secure-service.enabled` is false or absent, `IdentitySecureAuthorizationFacade`
  delegates Telegram authentication to `IdentityApplicationApi` through `identity-rest-client`.
- Configure the REST client with `VG_IDENTITY_REST_CLIENT_BASE_URL` and the required secret
  `VG_IDENTITY_REST_CLIENT_API_KEY`; no Spring profile is required.
- The adapter sends no personal-data consent because the current RG request has no explicit user
  consent signal. Existing identity users can authenticate; a provisional principal without `sub`
  is denied until a consent flow is implemented.

## TODO

- remove "logout" button for telegram mini app
- consider to recover integration-tests? or configure func tests to use mysql
- Clean up "\* Template \*"
- add and use bom project with
  - versions of apis/implementations
  - test util version
- add ACL
- add Audit
- add common errors (like, "Version conflict", "Access denied", "Validation error" etc)
