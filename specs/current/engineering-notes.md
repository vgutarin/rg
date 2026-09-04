# Engineering notes

Facts about **building, testing and deploying this repository** that are not derivable from reading the
code, and that have each cost real debugging time at least once.

This file is deliberately not about behaviour — that lives in the domain files beside it. Add an entry
here when something surprised you and the surprise was not visible in any source file.

## Production frontend bundle

**`rg-frontend-vaadin/src/main/bundles/prod.bundle` is a local build artifact, not in version control.
It is cached and reused between builds, and the reuse decision does not consider your Java routes.**

Vaadin decides whether the cached bundle is still valid from npm packages, `@JsModule`/`@CssImport`
declarations, theme files and frontend file hashes. **The set of `@Route` classes is not part of that
decision.** But the route set *does* determine the keys the server sends to the client at runtime. So
adding, moving or deleting a route leaves a cached bundle quietly mismatched, while every build still
reports `BUILD SUCCESSFUL`.

**What a mismatch looks like.** Components compiled into a lazily-loaded chunk — `vaadin-tabs`,
`vaadin-tabsheet` — are never fetched. Their captions are already in the DOM from server-side rendering,
so you see them, but the custom element never upgrades and nothing responds to a click. Everything
eagerly loaded (app-layout, buttons, side-nav, select, text fields) keeps working, which makes it read as
a CSS or event-wiring bug rather than a build problem.

The mechanism is `window.Vaadin.Flow.loadOnDemand(<64-hex key>)`, a switch over route keys baked in at
build time. Compare what the current sources need against what a bundle contains:

```bash
grep -oE "key === '[a-f0-9]{64}'" rg-frontend-vaadin/src/main/frontend/generated/flow/generated-flow-imports.js
```

```bash
unzip -p rg-frontend-vaadin/src/main/bundles/prod.bundle 'webapp/VAADIN/build/generated-flow-imports*.js' | grep -oE 'e===.[a-f0-9]{64}'
```

A key present in the first and absent from the second is a dead route: its lazily-chunked components will
not load.

**A machine without node/npm cannot rebuild the bundle**, and will silently keep serving the cache —
`-Pvaadin.forceProductionBuild=true --rerun-tasks` included. It reports success either way. The only
reliable check that a rebuild happened is that the file's timestamp moved; deleting it first is safer.

Note there is no ignore rule for it, so a rebuilt bundle shows up as an untracked file.

## The application stylesheet is cached by the client, and that failure is asymmetric

`styles.css` is a static resource, so how fresh it is depends entirely on the client. **Telegram's
in-app webview keeps an old copy even though the server sends `Cache-Control: no-cache`**, and it caches
separately from a desktop browser — so the same running server can serve a correct layout to one and a
stale one to the other at the same moment.

**What makes this expensive is the asymmetry.** Server-side Java changes take effect immediately while
CSS changes appear not to, so the screen looks half-updated and the CSS looks *broken* rather than
*stale*. Diagnosing it as a CSS bug is the natural first move and it is wrong.

`FrontendApplication` therefore registers the stylesheet from `configurePage` with a token that changes
once per JVM start, rather than through `@StyleSheet` — an annotation value has to be a compile-time
constant, so it cannot carry one. A restart now guarantees fresh CSS. Two things to preserve:

- The link must stay in `configurePage`, because links added there land *after* annotated ones, which
  keeps this stylesheet after `Aura.STYLESHEET` in the cascade. That order is what lets these rules
  override the theme.
- The token must be a `static final` computed once, not per call, or every page load busts the cache.

**Before concluding a style rule is wrong, check that the client actually has the current file.** Fetch
`/styles.css` from the failing client and look for the rule. A rule with an absolute value — a fixed
`width`, say — that renders as something else entirely is near-proof the stylesheet is stale, because no
media query or cascade can turn `8rem` into `100%`.

## A `vaadin-select` ignores `width: auto`

It renders at its own default width — **measured at 192px, regardless of how short its labels are** — so
`width: auto` leaves that default in place instead of shrinking to content. On a 375px screen that was
enough to squeeze the header's view title down to 16px.

Give these fields an explicit width. Shortening the label text does not help, because the default is not
content-derived.

## The page title of a nested route is not on `getContent()`

`AppLayout.getContent()` returns the **layout**, not the view, for anything nested: everything under
`WorkspaceLayout` makes that layout the content, and it declares no `@PageTitle`. Reading the annotation
from `getContent()` therefore fell through to the application name on every workspace screen.

`MainView` takes it from `AfterNavigationEvent.getActiveChain()` instead, using the first element that
declares a title — which works whichever end of the chain the leaf sits at. The resolved key is
remembered because a locale change has to retranslate it and carries no navigation event to ask.

Nothing fails when this is wrong; the title is simply the wrong string. `MainViewTest` covers it.

## Running in production mode locally

The Gradle flag alone is not enough — the runtime needs the property too, or the app logs
`Vaadin is running in DEVELOPMENT mode` and you are not testing what you think:

```bash
./gradlew :rg-frontend-vaadin:bootRun -Pvaadin.productionMode=true --args='--spring.profiles.active=local --vaadin.productionMode=true'
```

`bootRun` must run with the module as its working directory (the `vaadin {}` block in
`rg-frontend-vaadin/build.gradle` anchors the npm workspace there); `:rg-frontend-vaadin:bootRun` does
that already. The local H2 file database lives under `rg-frontend-vaadin/local-db/`, so a local run holds
a file lock — stop your own instance before starting another.

## Tests

**Method security is enabled in `RgLogicConfig`**, the module that declares the `@PreAuthorize` guards —
not in each consuming application. So guards are active in every Spring context that scans `vg.rg`,
including `BaseFuncTest`, with no per-test setup.

That placement is deliberate and worth preserving: a guard that is never activated is not a weaker guard
but *no* guard, and nothing fails. The annotation is ignored, every call succeeds, and every test
asserting a denial passes **vacuously**. Removing the annotation from `RgLogicConfig` currently fails 12
denial tests across five functional test classes, which is the check that those assertions mean something.

The exception: a test that builds its **own** `AnnotationConfigApplicationContext` does not scan
`RgLogicConfig` and must enable method security itself. Three do
(`*MethodSecurityTest`), and they are the reason to keep the distinction in mind. Do not put `@Import` on
a `@SpringBootTest` class to achieve this — that fails with `No auto-configuration attributes found`;
a nested `@TestConfiguration` is auto-detected instead.

**`@SpringBootTest` executes `ApplicationRunner` beans.** `SpringBootContextLoader` calls
`SpringApplication.run`, which calls the runners, so a startup runner fires inside every functional test
context before any test body executes. A runner with side effects needs a test-profile switch.

**Vaadin holds the *current* UI behind a weak reference.** A test that opens a `Dialog` must keep its
`UI` in a field, not just call `UI.setCurrent(new UI())` — otherwise the UI can be collected mid-test and
`dialog.open()` fails intermittently with `No currently active UI found`. Also note
`dialog.getFooter().getChildren()` throws (`getChildren is not supported for non-Component
HasComponentsOfType`), so a flow that needs its footer buttons asserted must hand them back to the test.

**`rg_workspace.author` is `NOT NULL` in the schema although the entity does not declare it.** Auditing
fills it from the current auditor, and an unauthenticated test has none — so a test that writes a
workspace row directly must set `author` explicitly or hit a constraint violation.

## Persistence

**Never return `UniqueId` from a repository method** (nor a collection of it). Spring Data treats a
non-entity return type as a DTO projection and tries to match constructor parameters to the selected
columns; `UniqueId` comes from a published jar compiled without `-parameters`, so every single call logs

```
No constructor parameter names discovered ... vg.unique.id.model.UniqueId
```

Select `as uniqueId` into the `UniqueIdRow` interface projection instead. Recompiling with `-parameters`
is not an option — the affected class is not ours.

**A stateful `AttributeConverter` is resolved by Hibernate from Spring's bean registry, and that path
only runs when an entity actually carries the annotation.** It fails at runtime, not at compile time, so
a context that starts cleanly proves nothing about it. `@Component` plus the `vg.rg` component scan is
what arranges the resolution; `ParticipantDescriptorPersistenceFuncTest` is the test that exercises it
(persist, flush, **clear**, reload — without the clear, the assertion reads the same instance back out of
the persistence context and the converter's read direction never runs).

A second, quieter trap in the same area: a round trip alone cannot tell encryption from no encryption.
Assert the raw column with `JdbcTemplate` as well, or a converter that silently stored plaintext would
pass.

**Two tables are retained but entirely unmapped**: `rg_location` and `rg_migration_marker`. No entity, no
repository, nothing reaching them. `PermissionDeclarationArchitectureTest` fails if any production file so
much as names `rg_location`, so do not "helpfully" map it back. See
[workspace.md](./workspace.md#the-retired-global-scope) for why each is kept.

**Liquibase** runs from `rg-frontend-vaadin/src/main/resources/liquibase-changelog.yml`, which is just
`includeAll: db/liquibase/`. Both modules' resources land on one classpath, so a new changeset dropped
into `rg-logic/src/main/resources/db/liquibase/` is picked up automatically with no registration step.
Functional tests use their own master changelog with the same `includeAll`.

## The Java version is pinned as a toolchain, and must stay that way

`gradle.properties` carries `java_version=21`, and `build.gradle` applies it as a **toolchain** — so
javac itself is a Java 21 javac and the tests run on that JVM. `options.release` sits alongside it as
defence in depth.

**Without the toolchain, Gradle compiles with whatever JDK its daemon happens to run** (it provisions its
own newer one under `~/.gradle/jdks`), and every module inherits that version. On a daemon running JDK 25
that produces class file 69, which a Java 21 runtime refuses to load:

```
UnsupportedClassVersionError: vg/rg/geo/GeoDistance has been compiled by a more recent version of the
Java Runtime (class file version 69.0) ... only recognizes class file versions up to 65.0
```

Nothing in the build fails when this happens. It surfaces only when the application is *run* on 21 — so
it looks like a sudden, unexplained runtime error after a JDK upgrade on the machine.

Two specific traps, both of which this project fell into:

- **`java { ... }` inside `allprojects` reaches only the root project.** The subprojects have no java
  plugin at the time that block runs, so the setting silently applies to nothing. Configure through
  `pluginManager.withPlugin('java')`, which reacts to the plugin actually being applied.
- **Do not derive `options.release` from `java.sourceCompatibility`.** If the compatibility level was
  never set (see above), the "guard" resolves to the daemon's own version and does nothing — which is the
  exact failure it exists to prevent. Take it from the explicit constant.

**Compiling at 21 for the first time will surface latent code**, because Java 25 accepts things 21 does
not. The one this project had: `component instanceof HasStyle style` where `component` is a
`Component` — Vaadin 25's `Component` already implements `HasStyle`, making the pattern unconditional,
which is an error on 21 (*"expression type Component is a subtype of pattern type HasStyle"*) and
accepted on 25. Call `component.hasClassName(...)` directly.

**Switching the toolchain leaves stale incremental-compile state.** The first build afterwards can fail
with a spurious `cannot access <SomeClass>` against a perfectly good source file. `./gradlew clean` (or
`--rerun-tasks`) clears it; the failure does not recur.

## Jackson is version 3

`spring-boot-starter-json` on Spring Boot 4.1 brings **`tools.jackson`**, not
`com.fasterxml.jackson.databind`. Annotations stayed where they were — `com.fasterxml.jackson.annotation`
— so a file can legitimately import from both, and an import that "looks wrong" may not be.

The practical difference: **Jackson 3's `ObjectMapper` is immutable.** The 2.x mutators are gone, so
`new ObjectMapper().setSerializationInclusion(...)` does not compile; configuration goes through
`JsonMapper.builder()…build()`. Inclusion is `changeDefaultPropertyInclusion(UnaryOperator<JsonInclude.Value>)`
rather than a setter. Also worth knowing before writing a defensive `catch`:
`writeValueAsString`/`readValue` now throw the **unchecked** `JacksonException`.

## Referring to history

Prefer describing a change ("the commit that removed the one-time migration") over quoting a short hash.
This branch's history has been rewritten at least once, and hashes quoted in earlier revisions of these
documents already point at commits reachable from no branch or tag — alive in the reflog and due to be
garbage-collected.
