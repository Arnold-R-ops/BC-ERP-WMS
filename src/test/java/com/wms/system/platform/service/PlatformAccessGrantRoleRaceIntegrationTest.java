package com.wms.system.platform.service;

import com.wms.system.exception.BusinessException;
import com.wms.system.exception.ErrorKeys;
import com.wms.system.platform.dto.PlatformAccessGrantRequest;
import com.wms.system.platform.model.PlatformRole;
import com.wms.system.platform.model.PlatformUser;
import com.wms.system.platform.model.PlatformUserRole;
import com.wms.system.platform.repository.PlatformAccessGrantRepository;
import com.wms.system.platform.repository.PlatformRoleRepository;
import com.wms.system.platform.repository.PlatformUserRepository;
import com.wms.system.platform.repository.PlatformUserRoleRepository;
import com.wms.system.security.PlatformSecurityUser;
import com.wms.system.tenant.model.Tenant;
import com.wms.system.tenant.repository.TenantRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
class PlatformAccessGrantRoleRaceIntegrationTest {
    @Autowired PlatformAccessGrantService accessGrantService;
    @Autowired PlatformAccessGrantRepository grants;
    @Autowired PlatformUserRepository users;
    @Autowired PlatformUserRoleRepository userRoles;
    @Autowired PlatformRoleRepository roles;
    @Autowired TenantRepository tenants;
    @Autowired PlatformTransactionManager transactionManager;
    @MockBean PlatformAccessGuard guard;
    @MockBean PlatformAuditService audit;

    private Long targetUserId;
    private Long tenantId;
    private Long operationsRoleId;
    private Long auditorRoleId;
    private ExecutorService executor;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString();
        PlatformRole operationsRole = roles.saveAndFlush(PlatformRole.builder()
            .roleCode(PlatformAdminInvitationService.OPERATIONS_ADMIN)
            .displayName("Operations administrator")
            .build());
        PlatformRole auditorRole = roles.saveAndFlush(PlatformRole.builder()
            .roleCode(PlatformAdminInvitationService.SECURITY_AUDITOR)
            .displayName("Security auditor")
            .build());
        operationsRoleId = operationsRole.getId();
        auditorRoleId = auditorRole.getId();

        PlatformUser target = users.saveAndFlush(PlatformUser.builder()
            .normalizedEmail("grant-race-" + suffix + "@example.com")
            .passwordHash("not-used")
            .displayName("Grant race target")
            .enabled(true)
            .securityVersion(1L)
            .mfaEnabled(true)
            .mfaFailedAttempts(0)
            .build());
        targetUserId = target.getId();
        userRoles.saveAndFlush(PlatformUserRole.builder()
            .platformUserId(targetUserId)
            .platformRoleId(operationsRoleId)
            .build());

        Tenant tenant = tenants.saveAndFlush(Tenant.builder()
            .tenantCode("RACE-" + suffix.substring(0, 12))
            .displayName("Grant race tenant")
            .slug("race-" + suffix.substring(0, 8))
            .build());
        tenantId = tenant.getId();

        PlatformUser actor = PlatformUser.builder().id(targetUserId + 10_000L)
            .normalizedEmail("grant-race-owner@example.com")
            .displayName("Grant race owner")
            .enabled(true)
            .build();
        when(guard.requireSuperAdmin()).thenReturn(new PlatformSecurityUser(actor));
        executor = Executors.newFixedThreadPool(2);
    }

    @AfterEach
    void tearDown() {
        if (executor != null) executor.shutdownNow();
        if (targetUserId != null) {
            grants.deleteAll(grants.findByGranteePlatformUserIdOrderByCreatedAtDesc(targetUserId));
            userRoles.deleteAll(userRoles.findByPlatformUserId(targetUserId));
            users.deleteById(targetUserId);
        }
        if (tenantId != null) tenants.deleteById(tenantId);
        if (operationsRoleId != null) roles.deleteById(operationsRoleId);
        if (auditorRoleId != null) roles.deleteById(auditorRoleId);
    }

    @Test
    @Timeout(20)
    void grantWaitsForRoleMutationAndRejectsAuditorAfterCommit() throws Exception {
        CountDownLatch targetLocked = new CountDownLatch(1);
        CountDownLatch allowRoleCommit = new CountDownLatch(1);
        CountDownLatch grantStarted = new CountDownLatch(1);

        Future<?> roleMutation = executor.submit(() ->
            new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                users.findByIdForUpdate(targetUserId).orElseThrow();
                targetLocked.countDown();
                await(allowRoleCommit);
                userRoles.deleteAllInBatch(userRoles.findByPlatformUserId(targetUserId));
                userRoles.saveAndFlush(PlatformUserRole.builder()
                    .platformUserId(targetUserId)
                    .platformRoleId(auditorRoleId)
                    .build());
            }));
        assertThat(targetLocked.await(5, TimeUnit.SECONDS)).isTrue();

        PlatformAccessGrantRequest request = new PlatformAccessGrantRequest(
            targetUserId, List.of("READ"), List.of(tenantId), List.of("users"), null, null);
        Future<?> grantCreation = executor.submit(() -> {
            grantStarted.countDown();
            return accessGrantService.create(request, null);
        });
        assertThat(grantStarted.await(5, TimeUnit.SECONDS)).isTrue();
        assertThatThrownBy(() -> grantCreation.get(300, TimeUnit.MILLISECONDS))
            .isInstanceOf(TimeoutException.class);

        allowRoleCommit.countDown();
        roleMutation.get(5, TimeUnit.SECONDS);
        assertThatThrownBy(() -> grantCreation.get(5, TimeUnit.SECONDS))
            .isInstanceOfSatisfying(ExecutionException.class, exception ->
                assertThat(exception.getCause())
                    .isInstanceOfSatisfying(BusinessException.class, businessException ->
                        assertThat(businessException.getErrorKey())
                            .isEqualTo(ErrorKeys.PLATFORM_ADMIN_ROLE_CHANGE_FORBIDDEN)));

        assertThat(grants.findByGranteePlatformUserIdOrderByCreatedAtDesc(targetUserId)).isEmpty();
        assertThat(userRoles.findRoleCodesByPlatformUserId(targetUserId))
            .containsExactly(PlatformAdminInvitationService.SECURITY_AUDITOR);
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting to release role mutation");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Role mutation was interrupted", exception);
        }
    }
}
