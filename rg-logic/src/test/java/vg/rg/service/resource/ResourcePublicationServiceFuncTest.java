package vg.rg.service.resource;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import vg.rg.BaseFuncTest;
import vg.rg.model.resource.ResourcePublication;
import vg.rg.model.resource.ResourceVisibilityEffect;
import vg.rg.model.resource.ResourceVisibilityMode;
import vg.rg.model.resource.ResourceVisibilityRule;
import vg.rg.model.resource.ResourceVisibilitySubjectKind;
import vg.rg.repository.resource.ResourcePublicationRepository;
import vg.rg.repository.resource.ResourceVisibilityRuleRepository;
import vg.unique.id.model.UniqueId;

import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static vg.test.TestHelper.nextUniqueId;

class ResourcePublicationServiceFuncTest extends BaseFuncTest {

    private static final UniqueId OWNER = nextUniqueId();
    private static final UniqueId ALICE = nextUniqueId();
    private static final UniqueId BOB = nextUniqueId();

    @Autowired ResourcePublicationService service;
    @Autowired ResourcePublicationRepository publications;
    @Autowired ResourceVisibilityRuleRepository rules;
    @Autowired PlatformTransactionManager transactionManager;

    private final UniqueId resource = nextUniqueId();

    @AfterEach
    void removePublication() {
        service.delete(resource);
    }

    @Test
    void changingAnExistingTargetEffect_preservesTheTargetAndAdvancesTheVersion() {
        var before = createRestricted(List.of(allow(ALICE)));
        var deny = new ResourceVisibilityRule(ResourceVisibilityEffect.DENY,
                ResourceVisibilitySubjectKind.USER, ALICE);

        var updated = service.replaceVisibilityPolicy(resource, before.version(),
                ResourceVisibilityMode.RESTRICTED, List.of(deny));

        assertThat(updated.version()).isEqualTo(before.version() + 1);
        assertThat(service.findByResourceUniqueId(resource)).contains(updated);
        assertThat(updated.rules()).containsExactly(deny);
    }

    @Test
    void ruleOnlyChange_rejectsThePreviousVersionAndPreservesTheWinner() {
        var before = createRestricted(List.of(allow(ALICE)));
        var updated = service.replaceVisibilityPolicy(resource, before.version(),
                ResourceVisibilityMode.RESTRICTED, List.of(allow(BOB)));

        assertThat(updated.version()).isEqualTo(before.version() + 1);
        assertThatThrownBy(() -> service.replaceVisibilityPolicy(resource, before.version(),
                ResourceVisibilityMode.RESTRICTED, List.of(allow(ALICE))))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
        assertThat(service.findByResourceUniqueId(resource)).contains(updated);
        assertThat(publications.isResourceVisibleForUser(resource, ALICE)).isFalse();
        assertThat(publications.isResourceVisibleForUser(resource, BOB)).isTrue();
    }

    @Test
    void identicalRulesInAnotherOrder_doNotRewriteRowsOrAdvanceVersion() {
        var before = createRestricted(List.of(allow(ALICE), allow(BOB)));
        var ruleIds = rules.findByResourceUniqueId(resource).stream().map(rule -> rule.getUniqueId()).toList();

        var result = service.replaceVisibilityPolicy(resource, before.version(),
                ResourceVisibilityMode.RESTRICTED, List.of(allow(BOB), allow(ALICE)));

        assertThat(result.version()).isEqualTo(before.version());
        assertThat(rules.findByResourceUniqueId(resource)).extracting(rule -> rule.getUniqueId())
                .containsExactlyInAnyOrderElementsOf(ruleIds);
        assertThat(publications.findById(resource.getLongValue()).orElseThrow().getVersion())
                .isEqualTo(before.version());
    }

    @Test
    void modeChangesAndSuccessivePolicyWrites_returnUsableVersionsAndKeepAuditMetadata() {
        var before = createRestricted(List.of(allow(ALICE)));
        var publicPolicy = service.replaceVisibilityPolicy(resource, before.version(),
                ResourceVisibilityMode.PUBLIC, List.of());
        var restricted = service.replaceVisibilityPolicy(resource, publicPolicy.version(),
                ResourceVisibilityMode.RESTRICTED, List.of(allow(BOB)));

        assertThat(publicPolicy.version()).isEqualTo(before.version() + 1);
        assertThat(restricted.version()).isEqualTo(publicPolicy.version() + 1);
        assertThat(service.findByResourceUniqueId(resource)).contains(restricted);
        var entity = publications.findById(resource.getLongValue()).orElseThrow();
        assertThat(entity.getAuthor()).isEqualTo(OWNER);
        assertThat(entity.getLastEditor()).isEqualTo(OWNER);
        assertThat(entity.getUpdatedAt()).isAfterOrEqualTo(entity.getCreatedAt());
    }

    @Test
    void concurrentRuleOnlyChanges_allowExactlyOneWriter() throws Exception {
        var before = createRestricted(List.of(allow(ALICE)));
        var ready = new CyclicBarrier(2);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> replaceConcurrently(before.version(), ALICE, ready));
            var second = executor.submit(() -> replaceConcurrently(before.version(), BOB, ready));
            assertThat(List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
        }
        var after = service.findByResourceUniqueId(resource).orElseThrow();
        assertThat(after.version()).isEqualTo(before.version() + 1);
        assertThat(after.rules()).hasSize(1);
    }

    private boolean replaceConcurrently(int version, UniqueId subject, CyclicBarrier ready) {
        authenticate(OWNER);
        try {
            return Boolean.TRUE.equals(new TransactionTemplate(transactionManager).execute(status -> {
                service.findByResourceUniqueId(resource).orElseThrow();
                try {
                    ready.await(10, TimeUnit.SECONDS);
                } catch (Exception failure) {
                    throw new IllegalStateException(failure);
                }
                service.replaceVisibilityPolicy(resource, version, ResourceVisibilityMode.RESTRICTED,
                        List.of(new ResourceVisibilityRule(ResourceVisibilityEffect.DENY,
                                ResourceVisibilitySubjectKind.USER, subject)));
                return true;
            }));
        } catch (ObjectOptimisticLockingFailureException stale) {
            return false;
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private ResourcePublication createRestricted(List<ResourceVisibilityRule> audience) {
        authenticate(OWNER);
        return service.create(resource, true, ResourceVisibilityMode.RESTRICTED, audience);
    }

    private static ResourceVisibilityRule allow(UniqueId subject) {
        return new ResourceVisibilityRule(ResourceVisibilityEffect.ALLOW, ResourceVisibilitySubjectKind.USER, subject);
    }
}
