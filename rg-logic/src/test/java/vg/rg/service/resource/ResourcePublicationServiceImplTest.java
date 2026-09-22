package vg.rg.service.resource;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import vg.rg.entity.resource.ResourcePublicationEntity;
import vg.rg.entity.resource.ResourceVisibilityRuleEntity;
import vg.rg.model.geo.LocationAudienceModel;
import vg.rg.model.resource.ResourceVisibilityEffect;
import vg.rg.model.resource.ResourceVisibilityMode;
import vg.rg.model.resource.ResourceVisibilityRule;
import vg.rg.model.resource.ResourceVisibilitySubjectKind;
import vg.rg.model.security.AuthenticatedUserPrincipal;
import vg.rg.model.security.AuthenticationFlow;
import vg.rg.model.workspace.WorkspaceEventType;
import vg.rg.repository.resource.ResourcePublicationRepository;
import vg.rg.repository.resource.ResourceVisibilityRuleRepository;
import vg.rg.repository.resource.WorkspaceEventAudienceRow;
import vg.rg.service.security.AuthorityChecker;
import vg.unique.id.model.UniqueId;
import vg.unique.id.service.UniqueIdService;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResourcePublicationServiceImplTest {

    private static final UniqueId RESOURCE = new UniqueId(7001L);
    private static final UniqueId VIEWER = new UniqueId(7003L);
    private static final Instant NOW = Instant.parse("2026-09-29T12:00:00Z");

    @Mock ResourcePublicationRepository publicationRepository;
    @Mock ResourceVisibilityRuleRepository ruleRepository;
    @Mock UniqueIdService uniqueIdService;
    @Mock AuthorityChecker authorityChecker;
    @Mock EntityManager entityManager;
    private ResourcePublicationService service;

    @BeforeEach
    void setUp() {
        service = new ResourcePublicationServiceImpl(publicationRepository, ruleRepository, uniqueIdService,
                Clock.fixed(NOW, ZoneOffset.UTC), authorityChecker, entityManager);
    }

    @Test
    void ruleOnlyChange_returnsTheAdvancedVersionAndDeletesBeforeReinserting() {
        var entity = publicationEntity(3);
        var replacement = new ResourceVisibilityRule(ResourceVisibilityEffect.DENY,
                ResourceVisibilitySubjectKind.USER, VIEWER);
        when(publicationRepository.findById(RESOURCE.getLongValue())).thenReturn(Optional.of(entity));
        when(ruleRepository.findByResourceUniqueId(RESOURCE)).thenReturn(List.of(allowRule(VIEWER)));
        when(authorityChecker.currentUserUniqueId()).thenReturn(Optional.of(VIEWER));
        when(publicationRepository.replacePolicy(RESOURCE.getLongValue(), 3,
                ResourceVisibilityMode.RESTRICTED, VIEWER, NOW)).thenReturn(1);
        doAnswer(invocation -> {
            entity.setVersion(4);
            return null;
        }).when(entityManager).refresh(entity);

        var result = service.replaceVisibilityPolicy(RESOURCE, 3, ResourceVisibilityMode.RESTRICTED,
                List.of(replacement));

        assertThat(result.version()).isEqualTo(4);
        assertThat(result.rules()).containsExactly(replacement);
        var order = inOrder(ruleRepository);
        order.verify(ruleRepository).deleteByResourceUniqueId(RESOURCE);
        order.verify(ruleRepository).flush();
        order.verify(ruleRepository).saveWithNewUniqueId(any(ResourceVisibilityRuleEntity.class),
                eq(uniqueIdService));
    }

    @Test
    void identicalPolicyInADifferentRuleOrder_isANoOp() {
        var other = new UniqueId(7004L);
        when(publicationRepository.findById(RESOURCE.getLongValue())).thenReturn(Optional.of(publicationEntity(3)));
        when(ruleRepository.findByResourceUniqueId(RESOURCE)).thenReturn(List.of(allowRule(VIEWER), allowRule(other)));

        var result = service.replaceVisibilityPolicy(RESOURCE, 3, ResourceVisibilityMode.RESTRICTED,
                List.of(allow(other), allow(VIEWER)));

        assertThat(result.version()).isEqualTo(3);
        assertThat(result.rules()).containsExactly(allow(VIEWER), allow(other));
        verify(publicationRepository, never()).replacePolicy(any(), anyInt(),
                any(), any(), any());
        verify(ruleRepository, never()).deleteByResourceUniqueId(any());
    }

    @Test
    void competingWriter_isRejectedBeforeAnyRulesAreDeleted() {
        when(publicationRepository.findById(RESOURCE.getLongValue())).thenReturn(Optional.of(publicationEntity(3)));
        when(ruleRepository.findByResourceUniqueId(RESOURCE)).thenReturn(List.of(allowRule(VIEWER)));
        // The conditional repository update returns zero if another transaction advanced the version.
        assertThatThrownBy(() -> service.replaceVisibilityPolicy(RESOURCE, 3,
                ResourceVisibilityMode.PUBLIC, List.of()))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
        verify(ruleRepository, never()).deleteByResourceUniqueId(any());
        verify(entityManager, never()).refresh(any());
    }

    @Test
    void stalePolicyVersion_isRejectedBeforeReadingOrReplacingRules() {
        when(publicationRepository.findById(RESOURCE.getLongValue())).thenReturn(Optional.of(publicationEntity(4)));

        assertThatThrownBy(() -> service.replaceVisibilityPolicy(RESOURCE, 3,
                ResourceVisibilityMode.PUBLIC, List.of()))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
        verifyNoInteractions(ruleRepository);
    }

    @Test
    void invalidPolicy_isRejectedBeforePersistence() {
        assertThatThrownBy(() -> service.replaceVisibilityPolicy(RESOURCE, 0,
                ResourceVisibilityMode.PUBLIC, List.of(allow(VIEWER))))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(publicationRepository, ruleRepository);
    }

    private static ResourcePublicationEntity publicationEntity(int version) {
        return ResourcePublicationEntity.builder().resourceUniqueId(RESOURCE.getLongValue()).version(version)
                .visibilityMode(ResourceVisibilityMode.RESTRICTED).visibleSince(NOW).build();
    }

    private static ResourceVisibilityRule allow(UniqueId subject) {
        return new ResourceVisibilityRule(ResourceVisibilityEffect.ALLOW, ResourceVisibilitySubjectKind.USER, subject);
    }

    private static ResourceVisibilityRuleEntity allowRule(UniqueId subject) {
        return ResourceVisibilityRuleEntity.builder().resourceUniqueId(RESOURCE)
                .effect(ResourceVisibilityEffect.ALLOW).subjectKind(ResourceVisibilitySubjectKind.USER)
                .subjectUniqueId(subject).build();
    }

    @Test
    void create_recordsTheInjectedPublicationInstantAndGenericMode() {
        when(publicationRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        var publication = service.create(RESOURCE, true, ResourceVisibilityMode.PUBLIC, List.of());

        assertThat(publication.resourceUniqueId()).isEqualTo(RESOURCE);
        assertThat(publication.visibleSince()).isEqualTo(NOW);
        assertThat(publication.visibilityMode()).isEqualTo(ResourceVisibilityMode.PUBLIC);
    }

    @Test
    void missingPublication_isRepairedAsAPublicDraft() {
        when(publicationRepository.findById(RESOURCE.getLongValue())).thenReturn(Optional.empty());
        when(publicationRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var publication = service.ensureDraftPublication(RESOURCE);

        assertThat(publication.resourceUniqueId()).isEqualTo(RESOURCE);
        assertThat(publication.visibleSince()).isNull();
        assertThat(publication.visibilityMode()).isEqualTo(ResourceVisibilityMode.PUBLIC);
        assertThat(publication.isPublished()).isFalse();
    }

    @Test
    void unpublish_clearsVisibleSinceWhenThePublicationVersionMatches() {
        var entity = ResourcePublicationEntity.builder().resourceUniqueId(RESOURCE.getLongValue()).version(3)
                .visibleSince(NOW).visibilityMode(ResourceVisibilityMode.PUBLIC).build();
        when(publicationRepository.findById(RESOURCE.getLongValue())).thenReturn(Optional.of(entity));
        when(publicationRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(ruleRepository.findByResourceUniqueId(RESOURCE)).thenReturn(List.of());

        var publication = service.updatePublicationState(RESOURCE, 3, false);

        assertThat(publication.visibleSince()).isNull();
        assertThat(publication.isPublished()).isFalse();
    }

    @Test
    void stalePublicationUpdate_isRejected() {
        when(publicationRepository.findById(RESOURCE.getLongValue())).thenReturn(Optional.of(ResourcePublicationEntity.builder()
                .resourceUniqueId(RESOURCE.getLongValue()).version(4).visibilityMode(ResourceVisibilityMode.PUBLIC).build()));

        assertThatThrownBy(() -> service.updatePublicationState(RESOURCE, 3, true))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
    }

    @Test
    void provisionalPrincipalVisibility_isDelegatedToThePublicationRepository() {
        when(authorityChecker.currentPrincipal()).thenReturn(Optional.of(principal(null)));
        when(publicationRepository.isResourceVisibleForUser(RESOURCE, null)).thenReturn(true);

        assertThat(service.isResourceVisibleForCurrentUser(RESOURCE)).isTrue();

    }

    @Test
    void publicationVisibility_hasNoWorkspaceOwnerBypass() {
        var owner = new UniqueId(7005L);
        when(authorityChecker.currentPrincipal()).thenReturn(Optional.of(principal(owner)));
        when(publicationRepository.isResourceVisibleForUser(RESOURCE, owner)).thenReturn(false);

        assertThat(service.isResourceVisibleForCurrentUser(RESOURCE)).isFalse();

    }

    @Test
    void unauthenticatedOrMissingResource_isNotVisible() {
        assertThat(service.isResourceVisibleForCurrentUser(RESOURCE)).isFalse();
        assertThat(service.isResourceVisibleForCurrentUser(null)).isFalse();
    }

    @Test
    void browseWorkspaceEvents_delegatesAudienceFilteringAndPagingToTheRepository() {
        var pageable = PageRequest.of(1, 20);
        var capturedViewer = new AtomicReference<UniqueId>();
        var capturedPageable = new AtomicReference<org.springframework.data.domain.Pageable>();
        when(authorityChecker.currentPrincipal()).thenReturn(Optional.of(principal(VIEWER)));
        when(publicationRepository.findVisibleWorkspaceEventsForAudience(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any())).thenAnswer(invocation -> {
            capturedViewer.set(invocation.getArgument(0));
            capturedPageable.set(invocation.getArgument(1));
            return new PageImpl<>(List.of(), pageable, 0);
        });

        assertThat(service.browseWorkspaceEvents(pageable)).isEmpty();

        assertThat(capturedViewer.get()).isEqualTo(VIEWER);
        assertThat(capturedPageable.get()).isEqualTo(pageable);
    }

    @Test
    void browseWorkspaceEvents_mapsTheSingleQueryAudienceProjection() {
        var pageable = PageRequest.of(0, 20);
        when(authorityChecker.currentPrincipal()).thenReturn(Optional.of(principal(VIEWER)));
        when(publicationRepository.findVisibleWorkspaceEventsForAudience(VIEWER, pageable))
                .thenReturn(new PageImpl<>(List.of(audienceRow()), pageable, 1));

        var event = service.browseWorkspaceEvents(pageable).getContent().getFirst();

        assertThat(event).extracting(
                value -> value.getUniqueId(),
                value -> value.getTitle(),
                value -> value.isMembershipBound(),
                value -> value.isMembershipPending(),
                value -> value.isPublished())
                .containsExactly(RESOURCE, "Training", true, false, true);
        assertThat(event.getLocation()).usingRecursiveComparison().isEqualTo(LocationAudienceModel.builder()
                .name("Court 1").description("Covered court").latitude(new BigDecimal("50.450100"))
                .longitude(new BigDecimal("30.523400")).googlePlaceId("place-1").build());
    }

    @Test
    void browseWorkspaceEvents_returnsAnEmptyPageWithoutAnAuthenticatedPrincipal() {
        var pageable = PageRequest.of(0, 20);

        assertThat(service.browseWorkspaceEvents(pageable)).isEmpty();
    }

    private static WorkspaceEventAudienceRow audienceRow() {
        return new WorkspaceEventAudienceRow() {
            @Override
            public Long getUniqueId() {
                return RESOURCE.getLongValue();
            }

            @Override
            public String getTitle() {
                return "Training";
            }

            @Override
            public Instant getStartAt() {
                return NOW;
            }

            @Override
            public Instant getEndAt() {
                return null;
            }

            @Override
            public WorkspaceEventType getEventType() {
                return WorkspaceEventType.PADEL;
            }

            @Override
            public Integer getMaxParticipantCount() {
                return 8;
            }

            @Override
            public BigDecimal getLocationLatitude() {
                return new BigDecimal("50.450100");
            }

            @Override
            public BigDecimal getLocationLongitude() {
                return new BigDecimal("30.523400");
            }

            @Override
            public String getLocationName() {
                return "Court 1";
            }

            @Override
            public String getLocationDescription() {
                return "Covered court";
            }

            @Override
            public String getLocationGooglePlaceId() {
                return "place-1";
            }

            @Override
            public boolean isMembershipPending() {
                return false;
            }

            @Override
            public boolean isMembershipBound() {
                return true;
            }
        };
    }

    private static AuthenticatedUserPrincipal principal(UniqueId user) {
        return new AuthenticatedUserPrincipal(user, "Viewer", Set.of(), true, AuthenticationFlow.TELEGRAM);
    }

}
