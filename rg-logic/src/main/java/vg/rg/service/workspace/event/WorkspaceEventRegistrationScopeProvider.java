package vg.rg.service.workspace.event;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import vg.rg.model.security.LocalPermissions;
import vg.rg.model.security.WorkspaceScope;
import vg.rg.repository.workspace.WorkspaceEventRegistrationRepository;
import vg.rg.service.workspace.WorkspaceScopeProvider;
import vg.unique.id.model.UniqueId;

import java.util.Optional;

/**
 * Resolves a registration-addressed permission ({@code delete}, {@code reorder}) to its owning
 * workspace, through the registration → event's-workspace join.
 *
 * <p>Registering and listing are addressed by the <em>event</em> and resolve through
 * {@link WorkspaceEventScopeProvider}; only the verbs whose call site holds a registration identifier
 * come here. There is no container verb for a registration — {@code create} is expressed as
 * {@link LocalPermissions.WorkspaceEvent#MANAGE_PARTICIPANTS} on the event — so
 * {@link #findByContainer} returns empty.
 */
@Component
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
class WorkspaceEventRegistrationScopeProvider implements WorkspaceScopeProvider {

    private final WorkspaceEventRegistrationRepository registrationRepository;

    @Override
    public boolean supports(String permission) {
        return LocalPermissions.WorkspaceEventRegistration.contains(permission);
    }

    @Override
    public Optional<WorkspaceScope> findByResource(UniqueId resourceId) {
        return registrationRepository.findWorkspaceScopeByUniqueId(resourceId.getLongValue())
                .map(row -> new WorkspaceScope(row.getWorkspaceUniqueId(), row.getOwnerUniqueId()));
    }

    @Override
    public Optional<WorkspaceScope> findByContainer(UniqueId containerId) {
        return Optional.empty();
    }
}
