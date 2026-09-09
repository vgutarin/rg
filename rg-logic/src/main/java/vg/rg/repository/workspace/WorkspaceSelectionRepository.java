package vg.rg.repository.workspace;

import org.springframework.data.jpa.repository.JpaRepository;
import vg.rg.entity.workspace.WorkspaceSelectionEntity;

/**
 * One row per user, keyed by the user's abstract identity, so "exactly one active workspace per user"
 * needs no additional constraint. Not a {@code UniqueIdJpaRepository}: the key is the user's identity
 * rather than an identifier issued for the row.
 */
public interface WorkspaceSelectionRepository extends JpaRepository<WorkspaceSelectionEntity, Long> {
}
