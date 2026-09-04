# Configuration holders

How this application reads its own tunables. Domain-specific keys and their meanings live in the domain
files ([workspace.md](./workspace.md), [geolocation.md](./geolocation.md),
[encryption.md](./encryption.md)); this file is about the shared mechanism.

## The convention

Every configuration holder is a `@ConfigurationProperties` type with final fields, registered explicitly:
`RgLogicConfig` for the `rg-logic` ones, `AppConfiguration` for the `rg-frontend-vaadin` one.
**`@ConfigurationProperties` types are not component-scanned** — a holder that is only annotated and never
registered simply does not exist as a bean, and `@Import` does not work either: it registers the type as
an ordinary bean whose constructor Spring then tries to autowire, failing with *"No qualifying bean of
type 'String'"*. Use `@EnableConfigurationProperties`, in tests too.

| Holder | Prefix | Module |
|---|---|---|
| `GeoProperties` | `rg.geo` | rg-logic |
| `WorkspaceProperties` | `rg.workspace` | rg-logic |
| `EncryptionProperties` | `rg.encryption` | rg-logic |
| `SecureAuthorizationLimitsProperties` | `rg.secure-service` | rg-logic |
| `IdentityAuthorizationLimitsProperties` | `rg.secure-service.identity` | rg-logic |
| `MapsClientProperties` | `google.maps` | rg-frontend-vaadin |
| `DevSecureServiceProperties` | `rg.dev-secure-service` | rg-logic |

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
holders behaved the same way when they read `Environment` directly. Binding the component as `String`
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
