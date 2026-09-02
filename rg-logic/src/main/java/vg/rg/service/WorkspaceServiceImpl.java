package vg.rg.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import vg.rg.config.WorkspaceProperties;
import vg.rg.entity.WorkspaceEntity;
import vg.rg.mapper.WorkspaceMapper;
import vg.rg.model.WorkspaceModel;
import vg.rg.repository.WorkspaceRepository;
import vg.rg.repository.WorkspaceSelectionRepository;
import vg.rg.security.AuthorityChecker;
import vg.rg.security.model.LocalPermissions;
import vg.rg.security.model.Permissions;
import vg.unique.id.model.UniqueId;
import vg.unique.id.service.UniqueIdService;

import jakarta.persistence.EntityNotFoundException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Slf4j
@RequiredArgsConstructor
@Service
class WorkspaceServiceImpl implements WorkspaceService {

    /**
     * Stable outcome codes for validation failures; the UI owns their translation. The workspace-limit
     * code lives on {@link WorkspaceLimitReachedException}, which is the thing that carries it.
     */
    static final String WORKSPACE_NAME_REQUIRED = "workspace.error.name-required";
    static final String WORKSPACE_NAME_TOO_LONG = "workspace.error.name-too-long";
    static final String WORKSPACE_DESCRIPTION_TOO_LONG = "workspace.error.description-too-long";

    private final UniqueIdService uniqueIdService;
    private final WorkspaceRepository repository;
    private final WorkspaceMapper mapper;
    private final WorkspaceProperties properties;
    private final AuthorityChecker authorityChecker;
    private final WorkspaceSelectionRepository selectionRepository;
    /**
     * Every contained type's removal hook. Injected as a list so a new type joins removal without this
     * class knowing it exists.
     */
    private final List<WorkspaceContentContributor> contentContributors;

    @Override
    @Transactional
    @PreAuthorize("@authorityChecker.hasAuthority('" + Permissions.Workspace.OWNER + "')")
    public WorkspaceModel create(WorkspaceModel model) {
        Objects.requireNonNull(model, "model");
        var owner = currentUser();
        validateUserSuppliedName(model.getName());
        validateDescription(model.getDescription());

        if (repository.countByOwnerUniqueId(owner) >= properties.maxPerUser()) {
            throw new WorkspaceLimitReachedException(properties.maxPerUser());
        }

        var entity = WorkspaceEntity.builder()
                .name(model.getName().trim())
                .description(trimToNull(model.getDescription()))
                // A user-created workspace is never a default: only ensureDefault produces one.
                .defaultForOwner(null)
                .ownerUniqueId(owner)
                .build();
        return mapper.toModel(repository.saveWithNewUniqueId(entity, uniqueIdService));
    }

    @Override
    @Transactional
    @PreAuthorize("@authorityChecker.hasAuthority(#model.uniqueId, '"
            + LocalPermissions.Workspace.UPDATE + "')")
    public WorkspaceModel update(WorkspaceModel model) {
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(model.getUniqueId(), "uniqueId");
        // A name is required here even though a system-created workspace stores none: this is the path
        // by which one acquires a real name, and the transition is one-way.
        validateUserSuppliedName(model.getName());
        validateDescription(model.getDescription());

        var entity = repository.findById(model.getUniqueId())
                .orElseThrow(EntityNotFoundException::new);
        if (entity.getVersion() != model.getVersion()) {
            // Stale edit: the record advanced since the client loaded it (optimistic concurrency).
            throw new ObjectOptimisticLockingFailureException(
                    WorkspaceEntity.class, model.getUniqueId());
        }
        // Editable fields only. ownerUniqueId, defaultForOwner, author and createdAt are immutable, so
        // renaming a workspace can neither transfer it nor change which one is the default.
        entity.setName(model.getName().trim());
        entity.setDescription(trimToNull(model.getDescription()));
        return mapper.toModel(repository.save(entity));
    }

    @Override
    @Transactional
    @PreAuthorize("@authorityChecker.hasAuthority(#workspaceId, '"
            + LocalPermissions.Workspace.DELETE + "')")
    public void delete(UniqueId workspaceId) {
        Objects.requireNonNull(workspaceId, "workspaceId");
        var entity = repository.findById(workspaceId).orElseThrow(EntityNotFoundException::new);
        if (entity.isDefaultWorkspace()) {
            // Refused so that a permission holder is never left without an accessible workspace.
            throw new WorkspaceNotRemovableException();
        }

        // ORDER MATTERS, and the database enforces it: rg_workspace_selection has a foreign key to
        // rg_workspace, so the selection must be repointed BEFORE the workspace row is deleted or the
        // delete fails with a constraint violation.
        repointSelectionAwayFrom(workspaceId);

        // Then the contents, through the registered contributors -- this class never names a type.
        contentContributors.forEach(contributor -> contributor.deleteAllInWorkspace(workspaceId));

        repository.delete(entity);

        // The user must never be left with no workspace at all. Refusing to remove the default covers
        // the usual case, but a user whose workspaces were all created explicitly has no default to
        // refuse -- removing the last of those would otherwise leave them with none until their next
        // entry happened to provision one. Provisioning here makes the guarantee unconditional rather
        // than deferred.
        var owner = entity.getOwnerUniqueId();
        if (repository.countByOwnerUniqueId(owner) == 0) {
            provisionDefault(owner);
        }
    }

    /**
     * Moves the owner's active-workspace pointer off the workspace about to be removed, onto their
     * default. Only the owner can have selected it, because a workspace is single-owner.
     */
    private void repointSelectionAwayFrom(UniqueId workspaceId) {
        var owner = currentUser();
        var selection = selectionRepository.findById(owner.getLongValue()).orElse(null);
        if (selection == null || !workspaceId.equals(selection.getWorkspaceUniqueId())) {
            return;
        }
        // ensureDefault provisions one if somehow absent, so the pointer always lands somewhere real.
        var fallback = repository.findByDefaultForOwner(owner)
                .map(existing -> new UniqueId(existing.getUniqueId()))
                .orElseGet(() -> provisionDefault(owner).getUniqueId());
        selection.setWorkspaceUniqueId(fallback);
        selectionRepository.saveAndFlush(selection);
    }

    @Override
    @PreAuthorize("@authorityChecker.hasAuthority('" + Permissions.Workspace.OWNER + "')")
    public List<WorkspaceModel> listOwned() {
        // Filtered by owner rather than checked per row: another user's workspace must never appear in
        // the list in the first place.
        return repository.findByOwnerUniqueIdOrderByCreatedAtAsc(currentUser()).stream()
                .map(mapper::toModel)
                .toList();
    }

    @Override
    @PreAuthorize("@authorityChecker.hasAuthority(#workspaceId, '"
            + LocalPermissions.Workspace.READ + "')")
    public Optional<WorkspaceModel> find(UniqueId workspaceId) {
        Objects.requireNonNull(workspaceId, "workspaceId");
        return repository.findById(workspaceId).map(mapper::toModel);
    }

    @Override
    @Transactional
    @PreAuthorize("@authorityChecker.hasAuthority('" + Permissions.Workspace.OWNER + "')")
    public WorkspaceModel ensureDefault() {
        var owner = currentUser();
        return repository.findByDefaultForOwner(owner)
                .map(mapper::toModel)
                .orElseGet(() -> provisionDefault(owner));
    }

    /**
     * Creates the owner's default workspace with <strong>no stored name</strong>, so its label follows
     * the viewer's locale.
     *
     * <p>Runs in its own transaction so that a lost provisioning race does not mark the caller's
     * transaction rollback-only: the unique index on the default marker means one of two concurrent
     * sessions must fail, and the loser simply re-reads the winner's row rather than surfacing a
     * constraint violation.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    WorkspaceModel provisionDefault(UniqueId owner) {
        try {
            var entity = WorkspaceEntity.builder()
                    .name(null)
                    .defaultForOwner(owner)
                    .ownerUniqueId(owner)
                    .build();
            return mapper.toModel(repository.saveAndFlush(
                    repository.saveWithNewUniqueId(entity, uniqueIdService)));
        } catch (DataIntegrityViolationException raced) {
            log.debug("Default workspace provisioning raced; reading the existing row");
            return repository.findByDefaultForOwner(owner)
                    .map(mapper::toModel)
                    .orElseThrow(() -> raced);
        }
    }

    private UniqueId currentUser() {
        return authorityChecker.currentUserUniqueId()
                .orElseThrow(() -> new IllegalStateException("No authenticated user"));
    }

    private void validateUserSuppliedName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException(WORKSPACE_NAME_REQUIRED);
        }
        if (name.trim().length() > properties.nameMaxLength()) {
            throw new IllegalArgumentException(WORKSPACE_NAME_TOO_LONG);
        }
    }

    private void validateDescription(String description) {
        if (description != null && description.trim().length() > properties.descriptionMaxLength()) {
            throw new IllegalArgumentException(WORKSPACE_DESCRIPTION_TOO_LONG);
        }
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        var trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
