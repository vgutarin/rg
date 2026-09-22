package vg.rg.service.resource;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vg.rg.entity.resource.ResourcePublicationEntity;
import vg.rg.entity.resource.ResourceVisibilityRuleEntity;
import vg.rg.model.geo.LocationAudienceModel;
import vg.rg.model.resource.ResourcePublication;
import vg.rg.model.resource.ResourceVisibilityMode;
import vg.rg.model.resource.ResourceVisibilityPolicy;
import vg.rg.model.resource.ResourceVisibilityRule;
import vg.rg.model.workspace.WorkspaceEventAudienceModel;
import vg.rg.repository.resource.ResourcePublicationRepository;
import vg.rg.repository.resource.ResourceVisibilityRuleRepository;
import vg.rg.repository.resource.WorkspaceEventAudienceRow;
import vg.rg.service.security.AuthorityChecker;
import vg.unique.id.model.UniqueId;
import vg.unique.id.service.UniqueIdService;

import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
@RequiredArgsConstructor
class ResourcePublicationServiceImpl implements ResourcePublicationService {

    private final ResourcePublicationRepository publicationRepository;
    private final ResourceVisibilityRuleRepository ruleRepository;
    private final UniqueIdService uniqueIdService;
    private final Clock clock;
    private final AuthorityChecker authorityChecker;
    private final EntityManager entityManager;

    @Override
    @Transactional
    public ResourcePublication create(UniqueId resourceId, boolean published, ResourceVisibilityMode mode,
                                      List<ResourceVisibilityRule> rules) {
        Objects.requireNonNull(resourceId, "resourceId");
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(rules, "rules");
        var entity = publicationRepository.save(ResourcePublicationEntity.builder()
                .resourceUniqueId(resourceId.getLongValue())
                .visibleSince(published ? Instant.now(clock) : null)
                .visibilityMode(mode)
                .build());
        replaceRules(resourceId, rules);
        return toPublication(entity, rules);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ResourcePublication> findByResourceUniqueId(UniqueId resourceId) {
        Objects.requireNonNull(resourceId, "resourceId");
        return publicationRepository.findById(resourceId.getLongValue()).map(this::toPublication);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<WorkspaceEventAudienceModel> browseWorkspaceEvents(Pageable pageable) {
        Objects.requireNonNull(pageable, "pageable");
        var principal = authorityChecker.currentPrincipal().orElse(null);
        if (principal == null) {
            return Page.empty(pageable);
        }
        return publicationRepository.findVisibleWorkspaceEventsForAudience(principal.userUniqueId(), pageable)
                .map(ResourcePublicationServiceImpl::toAudienceModel);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isResourceVisibleForCurrentUser(UniqueId resourceId) {
        if (resourceId == null) {
            return false;
        }
        var principal = authorityChecker.currentPrincipal().orElse(null);
        return principal != null && publicationRepository.isResourceVisibleForUser(resourceId, principal.userUniqueId());
    }

    @Override
    @Transactional
    public ResourcePublication ensureDraftPublication(UniqueId resourceId) {
        Objects.requireNonNull(resourceId, "resourceId");
        return findByResourceUniqueId(resourceId).orElseGet(() ->
                create(resourceId, false, ResourceVisibilityMode.PUBLIC, List.of()));
    }

    @Override
    @Transactional
    public ResourcePublication updatePublicationState(UniqueId resourceId, int version, boolean published) {
        var entity = required(resourceId, version);
        if (published == (entity.getVisibleSince() != null)) {
            return toPublication(entity);
        }
        entity.setVisibleSince(published ? Instant.now(clock) : null);
        return toPublication(publicationRepository.save(entity));
    }

    @Override
    @Transactional
    public ResourcePublication replaceVisibilityPolicy(UniqueId resourceId, int version, ResourceVisibilityMode mode,
                                                        List<ResourceVisibilityRule> rules) {
        var policy = new ResourceVisibilityPolicy(mode, rules);
        var entity = required(resourceId, version);
        var current = toPublication(entity);
        if (current.visibilityMode() == policy.mode()
                && new HashSet<>(current.rules()).equals(new HashSet<>(policy.rules()))) {
            return current;
        }
        // Rules live in separate rows, so their changes cannot dirty the publication's @Version.
        // Compare and advance it in the database before touching rules to serialize competing writers.
        if (publicationRepository.replacePolicy(resourceId.getLongValue(), version, policy.mode(),
                authorityChecker.currentUserUniqueId().orElse(null), Instant.now(clock)) != 1) {
            throw new ObjectOptimisticLockingFailureException(ResourcePublicationEntity.class, resourceId);
        }
        // The bulk update bypasses the persistence context and entity auditing; refresh its new version.
        entityManager.refresh(entity);
        replaceRules(resourceId, policy.rules());
        return toPublication(entity, policy.rules());
    }

    @Override
    @Transactional
    public void delete(UniqueId resourceId) {
        Objects.requireNonNull(resourceId, "resourceId");
        ruleRepository.deleteByResourceUniqueId(resourceId);
        publicationRepository.deleteById(resourceId.getLongValue());
    }

    @Override
    @Transactional
    public void deleteAll(Collection<UniqueId> resourceIds) {
        Objects.requireNonNull(resourceIds, "resourceIds");
        if (resourceIds.isEmpty()) {
            return;
        }
        ruleRepository.deleteByResourceUniqueIdIn(resourceIds);
        publicationRepository.deleteAllById(resourceIds.stream().map(UniqueId::getLongValue).toList());
    }

    private ResourcePublicationEntity required(UniqueId resourceId, int version) {
        Objects.requireNonNull(resourceId, "resourceId");
        var entity = publicationRepository.findById(resourceId.getLongValue()).orElseThrow(EntityNotFoundException::new);
        if (entity.getVersion() != version) {
            throw new ObjectOptimisticLockingFailureException(ResourcePublicationEntity.class, resourceId);
        }
        return entity;
    }

    private void replaceRules(UniqueId resourceId, List<ResourceVisibilityRule> rules) {
        ruleRepository.deleteByResourceUniqueId(resourceId);
        // Hibernate schedules inserts before entity deletes; release unique target slots first.
        ruleRepository.flush();
        rules.forEach(rule -> ruleRepository.saveWithNewUniqueId(ResourceVisibilityRuleEntity.builder()
                .resourceUniqueId(resourceId)
                .effect(rule.effect())
                .subjectKind(rule.subjectKind())
                .subjectUniqueId(rule.subjectUniqueId())
                .build(), uniqueIdService));
    }

    private ResourcePublication toPublication(ResourcePublicationEntity entity) {
        var resourceId = new UniqueId(entity.getResourceUniqueId());
        var rules = ruleRepository.findByResourceUniqueId(resourceId).stream().map(rule -> new ResourceVisibilityRule(
                rule.getEffect(), rule.getSubjectKind(), rule.getSubjectUniqueId())).toList();
        return toPublication(entity, rules);
    }

    private static ResourcePublication toPublication(ResourcePublicationEntity entity,
                                                      List<ResourceVisibilityRule> rules) {
        return new ResourcePublication(new UniqueId(entity.getResourceUniqueId()), entity.getVersion(), entity.getVisibleSince(),
                entity.getVisibilityMode(), rules);
    }

    private static WorkspaceEventAudienceModel toAudienceModel(WorkspaceEventAudienceRow row) {
        return WorkspaceEventAudienceModel.builder()
                .uniqueId(new UniqueId(row.getUniqueId()))
                .title(row.getTitle())
                .startAt(row.getStartAt())
                .endAt(row.getEndAt())
                .eventType(row.getEventType())
                .maxParticipantCount(row.getMaxParticipantCount())
                .isPublished(true)
                .location(
                        LocationAudienceModel.builder()
                                .latitude(row.getLocationLatitude())
                                .longitude(row.getLocationLongitude())
                                .name(row.getLocationName())
                                .description(row.getLocationDescription())
                                .googlePlaceId(row.getLocationGooglePlaceId()
                                ).build()
                )
                .membershipBound(row.isMembershipBound())
                .membershipPending(row.isMembershipPending())
                .build();
    }

}
