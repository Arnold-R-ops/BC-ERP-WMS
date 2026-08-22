package com.wms.system.signup;

import com.wms.system.entity.SysRole;
import com.wms.system.dto.LoginResponse;
import com.wms.system.exception.BusinessException;
import com.wms.system.exception.ErrorKeys;
import com.wms.system.identity.model.MembershipStatus;
import com.wms.system.identity.repository.TenantMembershipRepository;
import com.wms.system.identity.repository.UserIdentityRepository;
import com.wms.system.repository.SysRoleRepository;
import com.wms.system.repository.SysUserRoleRepository;
import com.wms.system.repository.UserRepository;
import com.wms.system.signup.dto.*;
import com.wms.system.signup.mail.DevelopmentVerificationMailbox;
import com.wms.system.signup.model.SignupStatus;
import com.wms.system.signup.repository.SignupRequestRepository;
import com.wms.system.signup.service.SessionHandoffService;
import com.wms.system.signup.service.SignupVerificationService;
import com.wms.system.signup.service.TenantProvisioningService;
import com.wms.system.subscription.model.PlanCode;
import com.wms.system.subscription.model.SubscriptionPlan;
import com.wms.system.subscription.model.SubscriptionStatus;
import com.wms.system.subscription.repository.SubscriptionPlanRepository;
import com.wms.system.subscription.repository.TenantSubscriptionRepository;
import com.wms.system.tenant.context.RequestSurface;
import com.wms.system.tenant.context.TenantContext;
import com.wms.system.tenant.context.TenantContextHolder;
import com.wms.system.tenant.model.TenantStatus;
import com.wms.system.tenant.repository.TenantDomainRepository;
import com.wms.system.tenant.repository.TenantRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class SignupProvisioningIntegrationTest {
    @Autowired SignupVerificationService verificationService;
    @Autowired TenantProvisioningService provisioningService;
    @Autowired SessionHandoffService handoffService;
    @Autowired DevelopmentVerificationMailbox mailbox;
    @Autowired SignupRequestRepository signupRepository;
    @Autowired TenantRepository tenantRepository;
    @Autowired TenantDomainRepository domainRepository;
    @Autowired UserIdentityRepository identityRepository;
    @Autowired TenantMembershipRepository membershipRepository;
    @Autowired UserRepository userRepository;
    @Autowired SysRoleRepository roleRepository;
    @Autowired SysUserRoleRepository userRoleRepository;
    @Autowired SubscriptionPlanRepository planRepository;
    @Autowired TenantSubscriptionRepository subscriptionRepository;
    @Autowired PasswordEncoder passwordEncoder;

    @AfterEach
    void clearContext() { TenantContextHolder.clear(); }

    @Test
    void verifiedSignupProvisionsOneCompanyAndOneUseHandoffIdempotently() {
        planRepository.save(SubscriptionPlan.builder()
            .planCode(PlanCode.TRIAL).displayName("30 day trial")
            .active(true).trialDays(30).build());
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String email = "owner-" + suffix + "@example.com";
        String slug = "company-" + suffix;
        String key = "signup-" + UUID.randomUUID();

        SignupResponse begun = verificationService.begin(
            new BeginSignupRequest(email, PlanCode.TRIAL), key);
        assertThat(begun.status()).isEqualTo(SignupStatus.EMAIL_PENDING);
        assertThat(begun.maskedEmail()).doesNotContain(email);
        SignupResponse repeatedBegin = verificationService.begin(
            new BeginSignupRequest(email, PlanCode.TRIAL), key);
        assertThat(repeatedBegin.signupId()).isEqualTo(begun.signupId());

        String code = mailbox.latest(email).orElseThrow().code();
        SignupResponse verified = verificationService.verify(
            new VerifyEmailRequest(begun.signupId(), code));
        assertThat(verified.status()).isEqualTo(SignupStatus.EMAIL_VERIFIED);

        CompleteSignupRequest details = new CompleteSignupRequest(
            "Ada", "Lovelace", "Analytical Warehousing", slug, "Secure12345");
        SignupResponse completed = provisioningService.complete(begun.signupId(), details);
        assertThat(completed.status()).isEqualTo(SignupStatus.ACTIVE);
        assertThat(completed.redirectUrl()).isEqualTo(
            "https://" + slug + ".bcwms.com/#/session/handoff?code="
                + completed.handoffCode());
        assertThat(completed.handoffCode()).isNotBlank();
        assertThat(completed.handoffExpiresInSeconds()).isEqualTo(60);

        var signup = signupRepository.findByPublicId(begun.signupId()).orElseThrow();
        var tenant = tenantRepository.findById(signup.getTenantId()).orElseThrow();
        assertThat(tenant.getStatus()).isEqualTo(TenantStatus.ACTIVE);
        assertThat(domainRepository.findByTenantIdAndPrimaryTrue(tenant.getId()))
            .get().extracting(domain -> domain.getHostname())
            .isEqualTo(slug + ".bcwms.com");
        var identity = identityRepository.findByNormalizedEmail(email).orElseThrow();
        assertThat(identity.getPasswordHash()).isNotEqualTo("Secure12345");
        assertThat(passwordEncoder.matches("Secure12345", identity.getPasswordHash())).isTrue();
        var membership = membershipRepository
            .findByIdentityIdAndTenantId(identity.getId(), tenant.getId()).orElseThrow();
        assertThat(membership.getStatus()).isEqualTo(MembershipStatus.ACTIVE);
        var tenantUser = withTenant(tenant.getId(), slug,
            () -> userRepository.findByIdAndCompanyId(membership.getTenantUserId(), tenant.getId()).orElseThrow());
        SysRole admin = withTenant(tenant.getId(), slug,
            () -> roleRepository.findByCompanyIdAndRoleCode(tenant.getId(), SysRole.TENANT_ADMIN_ROLE_CODE)
                .orElseThrow());
        assertThat(withTenant(tenant.getId(), slug,
            () -> userRoleRepository.existsByCompanyIdAndUserIdAndRoleId(
                tenant.getId(), tenantUser.getId(), admin.getId()))).isTrue();
        assertThat(subscriptionRepository.findByTenantId(tenant.getId()).orElseThrow().getStatus())
            .isEqualTo(SubscriptionStatus.TRIALING);

        SignupResponse retried = provisioningService.complete(begun.signupId(), details);
        assertThat(retried.handoffCode()).isNotEqualTo(completed.handoffCode());
        assertThat(tenantRepository.findBySlug(slug)).isPresent();
        assertThat(identityRepository.findByNormalizedEmail(email)).isPresent();

        LoginResponse handoff = withTenant(tenant.getId(), slug,
            () -> handoffService.consume(retried.handoffCode()));
        assertThat(handoff.getToken()).isNotBlank();
        assertThat(handoff.getCurrentRole()).isEqualTo(SysRole.TENANT_ADMIN_ROLE_CODE);
        assertThat(handoff.getAvailableRoles()).contains(SysRole.TENANT_ADMIN_ROLE_CODE);
        assertThat(handoff.getExpiresIn()).isPositive();
        assertThatThrownBy(() -> withTenant(tenant.getId(), slug,
            () -> handoffService.consume(retried.handoffCode())))
            .isInstanceOf(BusinessException.class)
            .extracting(error -> ((BusinessException) error).getErrorKey())
            .isEqualTo(ErrorKeys.SESSION_HANDOFF_INVALID);

        assertThatThrownBy(() -> withTenant(tenant.getId() + 1, "wrong-company",
            () -> handoffService.consume(completed.handoffCode())))
            .isInstanceOf(BusinessException.class)
            .extracting(error -> ((BusinessException) error).getErrorKey())
            .isEqualTo(ErrorKeys.SESSION_HANDOFF_INVALID);
    }

    private <T> T withTenant(Long tenantId, String slug, java.util.function.Supplier<T> action) {
        return TenantContextHolder.runWithTenant(new TenantContext(
            tenantId, slug, slug + ".bcwms.com", RequestSurface.TENANT, TenantStatus.ACTIVE), action);
    }
}
