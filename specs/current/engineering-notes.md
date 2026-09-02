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

**Two tables are retained but entirely unmapped**: `rg_location` and `rg_migration_marker`. No entity, no
repository, nothing reaching them. `PermissionDeclarationArchitectureTest` fails if any production file so
much as names `rg_location`, so do not "helpfully" map it back. See
[workspace.md](./workspace.md#the-retired-global-scope) for why each is kept.

**Liquibase** runs from `rg-frontend-vaadin/src/main/resources/liquibase-changelog.yml`, which is just
`includeAll: db/liquibase/`. Both modules' resources land on one classpath, so a new changeset dropped
into `rg-logic/src/main/resources/db/liquibase/` is picked up automatically with no registration step.
Functional tests use their own master changelog with the same `includeAll`.

## Referring to history

Prefer describing a change ("the commit that removed the one-time migration") over quoting a short hash.
This branch's history has been rewritten at least once, and hashes quoted in earlier revisions of these
documents already point at commits reachable from no branch or tag — alive in the reflog and due to be
garbage-collected.
