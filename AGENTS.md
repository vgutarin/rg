# Agent Instructions

- Work only in this repository for project tasks; never switch to or use the `knowledge-storage` repository.
- Never use `tango-cli` for this project.
- When suitable, use the IntelliJ MCP tools for project-aware Java and Gradle work, including searching code, opening files, running configurations, and executing tests.
- When working in the `rg-frontend-vaadin` module, use the Vaadin MCP tools when available for Vaadin-specific code, UI, routing, and frontend verification work.
- The `rg-frontend-vaadin` module uses Vaadin `25.2.2`; use Vaadin MCP/docs for version `25.2` when checking Vaadin guidance.
- Keep Vaadin views strictly mobile-first: default Java layout choices and CSS must work on narrow screens, with wider layouts added through `min-width` media queries.
- Prefer IntelliJ MCP refactoring tools for symbol/package renames when they are available and appropriate.
- Keep package declarations, imports, filesystem paths, and Spring metadata aligned after Java package changes.
- Verify code changes with the narrowest relevant Gradle task first, then broaden to `./gradlew test` when the change affects multiple modules.
- In Mockito-based unit tests, use `MockitoExtension` for mock initialization.
- Do not verify methods that were already explicitly stubbed unless the interaction itself is the behavior under test.
