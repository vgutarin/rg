# Agent Instructions

## Definition of done (mandatory)

- **Keep `specs/current/` actual at all times.** Every change that alters behaviour, contracts,
  configuration, or data flow MUST update the corresponding logical domain md file(s) in `specs/current/`
  within the same change — never as a follow-up. If no domain file covers the affected area yet, create
  one (grouped by logical domain, not by feature or ticket). Code and `specs/current/` must never drift
  apart: a change is not complete until the spec reflects it. Do not mark work done, hand off, or open a
  PR while the current specification still describes the old behaviour. When creating or refreshing a
  `specs/current/` domain file, start from `specs/current/_TEMPLATE.md`: describe current state only (no
  history/migration/evolution language), grounded in the actual code.
- **Cover every change in logic with tests.** New or changed behaviour MUST be accompanied by unit tests
  for the logic in isolation and integration tests for its wiring and interaction with collaborators (persistence,
  messaging, HTTP, Spring context), added in the same change. A change is not complete until
  both levels of tests exist and pass; do not mark work done or open a PR with untested logic.

## Scope & specs

- Work only in this repository for project tasks; never switch to or use the `knowledge-storage` repository.
- Current-state behaviour lives in `specs/current/`. Read the relevant domain file before changing
  behaviour in that area. A feature directory under `specs/NNN-*/`
  is primarily a decision record capturing the system as of that feature; it may be updated when genuinely needed, but
  there is no obligation to review the `specs/NNN-*/` directories, or reconcile them with later changes, just to apply a
  change.
- Before your first build or test run in a session, read `specs/current/engineering-notes.md`: build,
  test and deployment facts that are not derivable from the code and have each cost debugging time.
  `specs/current/open-decisions.md` lists work that is neither finished nor abandoned.

## Tooling

- Never use `tango-cli` for this project.
- Never use tango skills for this project.
- When suitable, use the IntelliJ MCP tools for project-aware Java and Gradle work, including searching code, opening files, running configurations, and executing tests.
- Prefer IntelliJ MCP refactoring tools for symbol/package renames when they are available and appropriate.
- When working in the `rg-frontend-vaadin` module, use the Vaadin MCP tools when available for Vaadin-specific code, UI,
  routing, and frontend verification work.

## Java conventions

- Use Lombok for boilerplate rather than writing it by hand: `@Data`/`@Getter`/`@Setter` for accessors,
  `@RequiredArgsConstructor` for dependency injection, `@Builder` with
  `@NoArgsConstructor`/`@AllArgsConstructor` on models and entities, `@Slf4j` for loggers. Two limits.
  Keep the generated member's visibility the same as the code it replaces — `@RequiredArgsConstructor`
  defaults to `public`, so a package-private constructor needs `(access = AccessLevel.PACKAGE)`. And
  leave a constructor hand-written when it does real work: validating configuration, copying an injected
  collection defensively, or enforcing a value-object invariant. Null-checking an injected Spring
  collaborator is not real work — a single-constructor `@Component` fails context startup rather than
  ever being handed a null.
- Construct instances through their builder in both production code and tests
  (`WorkspaceParticipantModel.builder()...build()`), not through positional constructors: a positional
  call reads as a row of unlabelled values and silently reorders when a field is added.
- Keep package declarations, imports, filesystem paths, and Spring metadata aligned after Java package changes.

## Frontend (`rg-frontend-vaadin`)

- The `rg-frontend-vaadin` module uses Vaadin `25.2.2`; use Vaadin MCP/docs for version `25.2` when checking Vaadin
  guidance.
- Keep Vaadin views strictly mobile-first: default Java layout choices and CSS must work on narrow screens, with wider
  layouts added through `min-width` media queries.

## Testing & verification

- Verify code changes with the narrowest relevant Gradle task first, then broaden to `./gradlew test` when the change affects multiple modules.
- In Mockito-based unit tests, use `MockitoExtension` for mock initialization.
- Do not verify methods that were already explicitly stubbed unless the interaction itself is the behavior under test.
