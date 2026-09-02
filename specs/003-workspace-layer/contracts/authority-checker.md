# Contract: Resource-Scoped Authority Check

**Module**: `rg-logic` | **Spec**: [../spec.md](../spec.md) | **Research**: [../research.md](../research.md) (R1–R3, R11)

This is the security boundary of the feature. It is the only place that decides whether a caller may act on
a workspace or on anything inside one, and every workspace-scoped service method delegates to it.

## `vg.rg.security.AuthorityChecker`

### Existing (unchanged signature)

```java
public boolean hasAuthority(String permission);
public Optional<UniqueId> currentUserUniqueId();
public Optional<AuthenticationFlow> currentAuthenticationFlow();
public Optional<AuthenticatedUserPrincipal> currentPrincipal();
```

`hasAuthority(String)` remains the **app-wide** check. Behaviour is unchanged except that it now accepts
only **app-wide** permissions and denies a local one (FR-043) — a local permission is meaningless without a
resource. Used for `workspace:owner`, `reports:read`, `request:submit`.

### New — the one workspace-scoped check

```java
public boolean hasAuthority(UniqueId resourceId, String permission);
```

There is exactly **one** such check (FR-045). An earlier draft proposed a second, workspace-only method;
it is **dropped**, because workspace capabilities are now local permissions too, so this single method
covers the workspace and everything inside it.

Returns `true` if and only if **all** of the following hold:

| # | Condition | Enforced? | Requirement |
|---|---|---|---|
| 1 | `permission` is non-null and `LocalPermissions.isRecognized(permission)` | **yes** | FR-015, FR-043 |
| 2 | An authenticated principal exists with a non-null `userUniqueId` | **yes** | FR-023 |
| 3 | The principal holds the app-wide `workspace:owner` | **yes** | FR-028, FR-033 |
| 4 | `resourceId` resolves — via the permission-dispatched lookup — to a workspace whose `ownerUniqueId` equals the principal's `userUniqueId` | **yes** | FR-014, FR-034, FR-035 |
| 5 | The principal *holds* `permission` | **no — ignored** | FR-015 |

**Ownership overrides the permission value.** Once condition 4 holds, the caller owns the workspace and the
declared `permission` is **not** consulted against what the principal holds. A workspace owner has complete
authority over their workspace and every object inside it at any depth, whatever the operation.

The distinction in condition 1 vs condition 5 is deliberate and worth stating precisely:

- the permission must be **declared** — a recognized local permission — or the check denies. This catches
  call-site typos and mis-scoped permissions and keeps FR-043/FR-044 meaningful;
- the permission need not be **held**. That is what "formal for now" means.

When granular non-owner access arrives, condition 5 becomes enforced for non-owners. Because the permission
is already passed at every call site, that change touches this method only — no call site moves.

**Fail closed.** A `null` `resourceId`, a permission no registered provider claims, an identifier with no
row in the dispatched store, and a row that does not join to a workspace all return `false`. There is **no**
fallback to a permission-only decision and no fallback probe of other stores: a resource whose workspace
cannot be established is never treated as unscoped (research R1, R2).

The method **never throws** for a denial and never reveals whether the resource exists — callers translate
`false` into the localized denial of FR-023.

### What the caller passes

The identifier of the **resource being acted upon** — never a workspace the caller resolved itself (FR-045):

```java
// Update a location: pass the WORKSPACE-LOCATION's id. Resolution finds its workspace.
@PreAuthorize("@authorityChecker.hasAuthority(#model.uniqueId, '" + LocalPermissions.Location.UPDATE + "')")

// Rename a workspace: the workspace IS the resource, so pass its id.
@PreAuthorize("@authorityChecker.hasAuthority(#workspaceId, '" + LocalPermissions.Workspace.UPDATE + "')")

// Create inside a workspace: no resource exists yet, so pass the container.
@PreAuthorize("@authorityChecker.hasAuthority(#workspaceId, '" + LocalPermissions.Location.CREATE + "')")

// App-wide capability: flat check, app-wide permission only.
@PreAuthorize("@authorityChecker.hasAuthority('" + Permissions.Workspace.OWNER + "')")
```

The parameter name referenced by `#model.uniqueId` / `#workspaceId` must match the method signature, so
renaming a parameter is a contract change.

## `vg.rg.security.WorkspaceScopeResolver`

```java
public interface WorkspaceScopeResolver {
    Optional<WorkspaceScope> resolve(UniqueId resourceId, String permission);
}

public record WorkspaceScope(UniqueId workspaceUniqueId, UniqueId ownerUniqueId) { }
```

Resolves an identifier to the workspace that scopes it, with that workspace's owner. **Replaces** the
earlier `WorkspaceResolver` / `ResourceAncestryResolver`; neither a containment-index table nor a probe
loop survives (research R1).

### Resolution: the permission names the type, the verb names the lookup

Identifiers are globally unique — one central generator issues them all (`UniqueIdService.getNext()`,
verified in `unique-id-api-0.0.2`) — so an identifier names at most one resource. The **declared
permission**, which the caller already passes, supplies the type hint (FR-035):

1. **Resource part to type.** `location:...` means a workspace-location; `workspace:...` means a workspace.
   Dispatch asks each registered provider `supports(permission)`, implemented as a set-membership test
   against the declared constants — `LocalPermissions.Location.contains(permission)` — so a malformed value
   such as `locationn:update` matches nothing and denies, rather than being parsed into a type.
2. **Verb to lookup** (FR-048):

   | Verb | The identifier names | Provider method |
   |---|---|---|
   | `create` | the **container** the new object goes into | `findByContainer(id)` |
   | `list` | the **container** whose collection is being read | `findByContainer(id)` |
   | `read`, `update`, `delete` | the **resource** itself | `findByResource(id)` |

   `list` is separate from `read` on purpose. A collection read is scoped to its container, so it travels
   with the container's identifier; reading one item travels with the item's. Folding both into `read`
   would make one permission address two kinds of identifier — and a workspace-addressed listing guarded
   by a resource-addressed verb resolves nothing and denies.

3. **One query.** The selected provider issues a single query, joining through however many levels separate
   its type from the workspace. Cost is flat in depth, not linear.

**Why the verb rule exists**: creation has no resource identifier yet, so
`hasAuthority(#workspaceId, LocalPermissions.Location.CREATE)` passes a *workspace* id with a `location:*`
permission. Dispatching on the resource part alone would look that id up in the location store, miss, and
deny every location creation. The verb resolves it without a second method and without a fallback probe
(research R1).

## `vg.rg.security.WorkspaceScopeProvider`

```java
public interface WorkspaceScopeProvider {
    boolean supports(String permission);
    Optional<WorkspaceScope> findByResource(UniqueId resourceId);
    Optional<WorkspaceScope> findByContainer(UniqueId containerId);
}
```

The per-type seam. Two implementations ship, each beside the type it serves in `vg.rg.service`:

| Provider | Claims | `findByResource` | `findByContainer` |
|---|---|---|---|
| `WorkspaceSelfScopeProvider` | `workspace:*` | the workspace itself, by id | unsupported — a workspace has no container |
| `LocationScopeProvider` | `location:*` | join `rg_workspace_location` to `rg_workspace` | the workspace, by id |

A future `workspace → group → event → comment` chain registers one provider per type, whose
`findByResource` joins its full path in a single query. The dispatch, the access rules, and every existing
call site stay unchanged — that registration is the *only* cost of adding a type (FR-017, SC-011, SC-015).

**Placement rationale**: `security` owns the seam interface and the dispatch, so it depends on no concrete
domain type — which is what keeps adding a type out of the security package. Providers are `@Component`s
with behaviour, so they live in `service` rather than `repository`, leaving `repository` purely declarative
Spring Data interfaces as it is today.

### Contract

- Returns the workspace and owner when the dispatched lookup finds a row that joins to a workspace.
- Returns `Optional.empty()` when `resourceId` is `null`, `permission` is claimed by no registered
  provider, the identifier has no row in the dispatched store, or the row does not join to a workspace.
- **No depth bound and no cycle handling** — there is no traversal to bound, so
  `rg.workspace.max-ancestry-depth` does not exist (research R3).
- Read-only and side-effect free apart from diagnostic logging.
- Never consults the SecurityContext — it answers "which workspace scopes this resource", not "may this
  caller act". Separating the two keeps it testable against a real database with no authenticated principal.
- Logs at **warn** when an identifier is not resolvable, carrying only identifiers and the declared
  permission. It MUST NOT log a principal, a display name, or any resource content (Principle I).

**Coupling to be aware of**: the permission and the identifier must describe the same resource. A mismatch
— a workspace id with a non-create location permission, say — resolves nothing and denies (SC-023). It
fails closed, but passing a matching pair is the call site's responsibility, and it is no longer a
compile-time guarantee.

## Failure and observability

| Situation | Result | Surfaced as |
|---|---|---|
| Not authenticated | `false` | Denial per FR-023 |
| Missing `workspace:owner` | `false` | Denial per FR-023, FR-028 |
| Resource in another user's workspace | `false` | Denial that does not disclose existence (FR-014, FR-023) |
| Undeclared / app-wide permission passed to the scoped check | `false` | Denial, **even for the owner** (FR-043, SC-020) |
| Local permission passed to the flat check | `false` | Denial (FR-043, SC-020) |
| Permission claimed by no provider, or identifier has no row in the dispatched store | `false` + **warn log** | Denial; never an allow (FR-036, SC-016) |
| Permission and identifier describe different types | `false` + warn log | Denial; no fallback probe (SC-023) |
| Owner holding no local capabilities | **`true`** | Permitted — ownership overrides (FR-014, SC-006) |
| `workspace:owner` revoked after content exists | `false` | Denial; stored data untouched (FR-033) |

## Required test coverage

**Unit** (`MockitoExtension`, mocked resolver): conditions 1–4 each deny in isolation and allow together;
an owner holding **no** local capabilities is allowed (SC-006); `null` resource id denies; an undeclared or
app-wide permission denies **even for an owner** (SC-020); a local permission passed to the flat overload
denies; the flat overload is otherwise unchanged.

**Integration** (MySQL 8 via `BaseFuncTest`):

- **Depth ≥ 5 at constant cost** — register a test-only `WorkspaceScopeProvider` claiming a test permission
  and resolving a workspace → group → event → comments → comment chain in one query; assert the owner is
  allowed passing **any** identifier in the chain, a non-owner is denied for every one, and each check
  issues exactly **one** query (SC-015).
- **Production join path** — a real workspace-location identifier with `location:update` resolves to its
  workspace in one query.
- **Container path** — a workspace identifier with `location:create` resolves via `findByContainer`
  (FR-048), and the same identifier with `location:update` **denies** (SC-023).
- **Self path** — a workspace identifier with `workspace:update` resolves to itself.
- **Fail-closed** — unknown identifier, a permission no provider claims, and a row that does not join to a
  workspace all deny **and** emit a warning (SC-016).

**Architecture**: no location or workspace capability is referenced from the app-wide declaration; no
production call site passes a local permission to the flat overload; no service resolves a workspace by
hand instead of passing a resource identifier (FR-045).
