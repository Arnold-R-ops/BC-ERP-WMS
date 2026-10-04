package com.wms.system.platform.service;

import com.wms.system.exception.BusinessException;
import com.wms.system.platform.dto.PlatformAdminInvitationCreateRequest;
import com.wms.system.platform.model.PlatformMfaChallenge;
import com.wms.system.platform.model.PlatformRole;
import com.wms.system.platform.model.PlatformUser;
import com.wms.system.platform.model.PlatformUserRole;
import com.wms.system.platform.repository.PlatformAdminInvitationRepository;
import com.wms.system.platform.repository.PlatformMfaChallengeRepository;
import com.wms.system.platform.repository.PlatformRoleRepository;
import com.wms.system.platform.repository.PlatformUserRepository;
import com.wms.system.platform.repository.PlatformUserRoleRepository;
import com.wms.system.security.PlatformSecurityUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@SpringBootTest(properties="spring.config.location=classpath:platform-r2-isolation.properties")
@org.junit.jupiter.api.condition.EnabledIfSystemProperty(named="p3.r2.database-authorized",matches="true")
@org.springframework.test.context.ContextConfiguration(initializers=com.wms.platformverification.PlatformR2SpringInitializer.class)
class PlatformAdminInvitationMfaTransactionIntegrationTest {
    @Autowired PlatformAdminInvitationService invitationsService;
    @Autowired PlatformAdminInvitationRepository invitations;
    @Autowired PlatformMfaChallengeRepository challenges;
    @Autowired PlatformUserRepository users;
    @Autowired PlatformRoleRepository roles;
    @Autowired PlatformUserRoleRepository userRoles;
    @Autowired PlatformMfaCrypto crypto;
    @Autowired PasswordEncoder passwordEncoder;
    @MockBean PlatformAuditService audit;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactions;
    private com.wms.platformverification.PlatformFixtureScope fixtures;

    private Long actorId;
    private Long challengeId;
    private Long actorRoleId;
    private Long roleId;
    private String invitedEmail;
    private String rawChallenge;

    @BeforeEach
    void setUp() {
        PlatformRole role = PlatformAccessGrantRoleRaceIntegrationTest.requireBaselineRole(roles, "PLATFORM_SUPER_ADMIN");
        roleId = role.getId();
        fixtures=com.wms.platformverification.PlatformFixtureScope.open(jdbc,transactions,getClass().getName(),"credentialFailureFixture");

        String suffix = UUID.randomUUID().toString();
        PlatformUser actor = users.saveAndFlush(PlatformUser.builder()
            .normalizedEmail("mfa-rollback-" + suffix + "@example.com")
            .passwordHash(passwordEncoder.encode("Correct-Password-42!"))
            .displayName("MFA rollback owner")
            .enabled(true)
            .securityVersion(1L)
            .mfaEnabled(true)
            .mfaSecretEncrypted("not-read-when-password-is-wrong")
            .mfaFailedAttempts(4)
            .build());
        actorId = actor.getId();
        fixtures.ownUser(actorId);
        actorRoleId = userRoles.saveAndFlush(PlatformUserRole.builder()
            .platformUserId(actorId)
            .platformRoleId(roleId)
            .build()).getId();

        rawChallenge = "admin-invite-" + suffix;
        PlatformMfaChallenge challenge = challenges.saveAndFlush(PlatformMfaChallenge.builder()
            .tokenHash(crypto.hashToken(rawChallenge))
            .platformUserId(actorId)
            .purpose("ADMIN_INVITE")
            .attemptCount(4)
            .expiresAt(OffsetDateTime.now().plusMinutes(5))
            .build());
        challengeId = challenge.getId();
        invitedEmail = "invited-" + suffix + "@example.com";
        fixtures.expectInvitation(actorId, invitedEmail);

        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(
                new PlatformSecurityUser(actor),
                null,
                List.of(new SimpleGrantedAuthority("ROLE_PLATFORM_SUPER_ADMIN"))
            )
        );
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        if (fixtures != null) fixtures.close();
    }

    @Test
    @Timeout(15)
    void credentialFailureSurvivesRollbackOfInvitationCreationTransaction() {
        PlatformAdminInvitationCreateRequest request = new PlatformAdminInvitationCreateRequest();
        request.setChallengeToken(rawChallenge);
        request.setPassword("Wrong-Password-42!");
        request.setCode("000000");
        request.setEmail(invitedEmail);
        request.setDisplayName("Second owner");
        request.setReason("Business continuity");
        request.setInvitationType("SUPER_ADMIN");
        request.setRoleCode("PLATFORM_SUPER_ADMIN");

        fixtures.requireOwnedUser(actorId);
        PlatformMfaFailureFixture.requireReady(PlatformMfaFailureFixture.inspect(jdbc, actorId, challengeId), System.out::println);
        Throwable failure = catchThrowable(() -> invitationsService.create(request, null));
        PlatformMfaFailureFixture.assertCredentialFailure(failure, actorId, crypto.hashToken(rawChallenge), System.out::println);

        PlatformUser persistedActor = users.findById(actorId).orElseThrow();
        PlatformMfaChallenge persistedChallenge = challenges.findById(challengeId).orElseThrow();
        assertThat(persistedActor.getMfaFailedAttempts()).isEqualTo(5);
        assertThat(persistedActor.getMfaLockedUntil())
            .isAfter(OffsetDateTime.now().plusMinutes(14));
        assertThat(persistedChallenge.getAttemptCount()).isEqualTo(5);
        assertThat(invitations.findAll())
            .noneMatch(invitation -> invitedEmail.equals(invitation.getNormalizedEmail()));
        verify(audit, times(1)).recordInCurrentTransaction(
            eq(actorId), isNull(), eq("MFA_FAILED"), eq("platform_identity"),
            eq("FAILED"), anyMap(), isNull());
    }

}
