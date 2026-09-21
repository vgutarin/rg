# Configuration holders

How this application reads its own tunables. Domain-specific keys and their meanings live in the domain
files ([workspace lifecycle](../logic/workspace/lifecycle.md), [locations](../logic/workspace/locations.md),
[encryption.md](./encryption.md)); this file is about the shared mechanism.

## The convention

Most configuration holders are `@ConfigurationProperties` types with final fields, registered explicitly:
`RgLogicConfig` for the immutable `rg-logic` holders, `AppConfiguration` for `MapsClientProperties`.
`DevSecureServiceProperties` is an exception: it is a conditional `@Component` with mutable
setter binding, activated only when `rg.dev-secure-service.enabled=true`.
**`@ConfigurationProperties` alone does not enable component scanning** — a holder that is only annotated and never
registered simply does not exist as a bean, and `@Import` does not work either: it registers the type as
an ordinary bean whose constructor Spring then tries to autowire, failing with *"No qualifying bean of
type 'String'"*. Use `@EnableConfigurationProperties`, in tests too.

| Holder | Package | Prefix | Module |
|---|---|---|---|
| `GeoProperties` | `vg.rg.config` | `rg.geo` | rg-logic |
| `WorkspaceProperties` | `vg.rg.config` | `rg.workspace` | rg-logic |
| `EncryptionProperties` | `vg.rg.config` | `rg.encryption` | rg-logic |
| `SecureAuthorizationLimitsProperties` | `vg.rg.config.security` | `rg.secure-service` | rg-logic |
| `IdentityAuthorizationLimitsProperties` | `vg.rg.config.security` | `rg.secure-service.identity` | rg-logic |
| `MapsClientProperties` | `vg.rg.frontend.vaadin.config` | `google.maps` | rg-frontend-vaadin |
| `DevSecureServiceProperties` | `vg.rg.config.security` | `rg.dev-secure-service` | rg-logic |

`spring-boot-configuration-processor` runs in both modules, so these keys land in
`spring-configuration-metadata.json` and an IDE completes them in `application.properties`. **The
processor takes each key's description from the matching *field's* javadoc** (or a record's `@param`) —
not from the constructor's `@param`, which is why the field names track the property names even where the
accessor does not (`SecureAuthorizationLimitsProperties.maxInitDataSize` holds bytes and is read through
`maxInitDataBytes()`).

## Why the constructors take `String`

Most holders parse their own values through `ConfigurationBounds` instead of letting the binder convert
to `int` or `DataSize`. That is deliberate and costs real convenience, so it is worth stating plainly:

**Spring's conversion failure echoes the offending value** — `Failed to convert … for value [<value>]` —
into the startup exception and the logs. The `rg.secure-service.*` limits are covered by tests named
`failWithoutDisclosure`, asserting that a rejected value never appears in the message, and the other
holders use the same non-disclosing validation. Binding the component as `String`
keeps the conversion — and therefore the message — in our hands: a bad value is rejected by naming the
**key** and nothing else.

Two consequences follow, both visible in the code:

- **These holders cannot be records.** A record accessor's type is its component's type, so a `String`
  component could not expose an `int`. They are final classes with final fields — the same immutability
  by a longer route. `MapsClientProperties` *is* a record, because both its components are `String`s and
  there is nothing to parse; `EncryptionProperties` is a record because it binds a map.
- **The generated metadata types the keys as `String`.** An IDE will not flag `max-per-user=abc` as a
  type error; startup will.

## Absent and blank are the same thing

`ConfigurationBounds` treats a blank value exactly as an absent one, applying the default. This is not
tidiness — it is required. `application.properties` relays every key through a `${ENV_VAR:default}`
placeholder, so the key is *always present* at runtime, and an environment variable exported empty
resolves to a blank value rather than an absent one.

`@DefaultValue` is not an alternative, and this is the reason these holders do not use it: it does not
cover blank. Spring converts a blank value to `null`, and a primitive component then fails with *"a null
value cannot be assigned to a primitive type"* — taking the whole application down over an empty
environment variable.

`GeoPropertiesTest.blankValue_fallsBackToDefault` and its `WorkspaceProperties` counterpart exist to keep
that from regressing.

## Relaxed binding

Because these are bound rather than read from `Environment`, a deployment can set
`RG_GEO_MATCH_RADIUS_METERS` directly and it will be picked up with no placeholder relaying it. The
placeholders in `application.properties` are kept anyway, as a discoverable list of the tunables and
their defaults.

The env-var spelling is only recognised in a `SystemEnvironmentPropertySource`.
`ApplicationContextRunner.withPropertyValues` registers a plain map source, which does **not** do that
name mapping — a test asserting relaxed binding has to install a real
`SystemEnvironmentPropertySource`, as `GeoPropertiesTest.relaxedBinding_acceptsEnvironmentVariableForm`
does.

## Google Maps browser integration

`MapsClientProperties` exposes `google.maps.browser-api-key` and optional
`google.maps.map-id` to the browser connector. The browser key is public by design
and referrer-restricted; actual configured values are not logged or documented here.
A blank key disables the picker and leads to the coordinate-free add form.

A Vector Map ID enables the WebGL map; no ID uses the raster map. The integration
requires Maps JavaScript API and Places API (New), using `PlaceAutocompleteElement`,
`Place.fetchFields` (`displayName`, `location`), and point-of-interest `placeId` clicks.
There is no server-side geocoding dependency. Client entry is
`rgInitGoogleMapsConnector`; view callbacks are `onCoordinatesAcquired` and
`onMapsUnavailable`, with the view element passed explicitly for `$0.$server` calls.

The connector uses fixed code constants, not configuration properties: an 8-second
Maps-load timeout, an 8-second Telegram initialization timeout, a 30-second Telegram
location timeout, and an 8-second browser-location timeout. Failed Maps loading uses
the [UI fallback](../ui/locations.md); no coordinate is fabricated.

The Mini App needs HTTPS for browser/Telegram geolocation. Behind TLS termination,
`server.forward-headers-strategy=framework` preserves the forwarded HTTPS scheme on
redirects. The optional map-centering flow is described in the location UI contract.

## Authorization adapter configuration

`rg.dev-secure-service.enabled` selects the development adapter when true and the
identity-service adapter otherwise. The development holder exposes `bot-token`,
positive `auth-date-ttl` (default one hour), and nonnegative `allowed-clock-skew`
(default 30 seconds). A real bot token belongs in environment/runtime configuration,
not documentation or logs. Business validation limits are defined in
[identity and access](../logic/identity-access.md).

## Evidence

- [ConfigurationBounds.java](../../../rg-logic/src/main/java/vg/rg/config/ConfigurationBounds.java)
- [GeoPropertiesTest.java](../../../rg-logic/src/test/java/vg/rg/config/GeoPropertiesTest.java)
- [WorkspacePropertiesTest.java](../../../rg-logic/src/test/java/vg/rg/config/WorkspacePropertiesTest.java)
- [SecureAuthorizationLimitsPropertiesTest.java](../../../rg-logic/src/test/java/vg/rg/config/security/SecureAuthorizationLimitsPropertiesTest.java)
- [MapsClientProperties.java](../../../rg-frontend-vaadin/src/main/java/vg/rg/frontend/vaadin/config/MapsClientProperties.java)
- [google-maps-connector.ts](../../../rg-frontend-vaadin/src/main/frontend/ts/maps/google-maps-connector.ts)
