# Domain logic

The application owns workspace content and delegates production Telegram identity
authentication to an external identity service.

- [Identity and access](identity-access.md): principals, authentication outcomes, and access checks.
- [Workspace domain](workspace/README.md): vocabulary, ownership scope, and capability relationships.
- [Lifecycle](workspace/lifecycle.md): workspace creation, naming, selection, and removal.
- [Locations](workspace/locations.md): saved coordinates, name search, and proximity.
- [Participants](workspace/participants.md): local person records and protected contact data.
- [Events](workspace/events.md): scheduled sports records and publication state.
- [Registrations](workspace/registrations.md): ordered participant-to-event relationships.

These are documentation responsibilities, not claims that each directory is an
independently deployed bounded context or aggregate. [UI contracts](../ui/README.md)
describe presentation; [engineering](../engineering/configuration.md) describes
technical mechanisms.
