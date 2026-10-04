package com.wms.system.uat;

import com.wms.system.exception.BusinessException;
import com.wms.system.exception.ErrorKeys;
import com.wms.system.identity.repository.TenantMembershipRepository;
import com.wms.system.identity.repository.UserIdentityRepository;
import com.wms.system.signup.dto.BeginSignupRequest;
import com.wms.system.signup.dto.CompleteSignupRequest;
import com.wms.system.signup.dto.VerifyEmailRequest;
import com.wms.system.signup.mail.DevelopmentVerificationMailbox;
import com.wms.system.signup.service.SignupVerificationService;
import com.wms.system.signup.service.TenantProvisioningService;
import com.wms.system.subscription.model.PlanCode;
import com.wms.system.tenant.model.Tenant;
import com.wms.system.tenant.model.TenantStatus;
import com.wms.system.tenant.repository.TenantRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Explicit, idempotent bootstrap for the isolated A2 UAT database.
 *
 * <p>This class is skipped during normal test runs. It may only run when the
 * caller supplies {@code A2_UAT_PROVISION=true} and an explicit A2 database
 * URL. It uses the public signup services so the control-plane identity,
 * membership, first tenant user and TENANT_ADMIN role are created through the
 * same transaction boundaries as a real signup. It deliberately does not
 * pretend to be the still-pending versioned full tenant baseline initializer.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "A2_UAT_PROVISION", matches = "true")
class A2UatTenantProvisioningTest {

    private static final String TENANT_A_EMAIL = "owner-a@a2-uat.invalid";
    private static final String TENANT_A_SLUG = "a2-ordinary";
    private static final String TENANT_B_EMAIL = "owner-b@a2-uat.invalid";
    private static final String TENANT_B_SLUG = "a2-optional";

    @DynamicPropertySource
    static void configureIsolatedDatabase(DynamicPropertyRegistry registry) {
        String url = required("A2_UAT_DB_URL");
        if (!url.matches("jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/wms_a2_uat_[0-9]{8}.*")) {
            throw new IllegalStateException("A2 UAT provisioning refuses a non-isolated database URL");
        }
        registry.add("spring.datasource.url", () -> url);
        registry.add("spring.datasource.username", () -> required("A2_UAT_DB_USERNAME"));
        registry.add("spring.datasource.password", () -> required("A2_UAT_DB_PASSWORD"));
        registry.add("spring.flyway.enabled", () -> false);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.jpa.properties.hibernate.hbm2ddl.auto", () -> "validate");
        registry.add("wms.legacy-admin-bootstrap-enabled", () -> false);
        registry.add("wms.tenancy.enabled", () -> false);
        registry.add("spring.main.web-application-type", () -> "none");
        registry.add("logging.level.com.wms.system", () -> "INFO");
    }

    @Autowired SignupVerificationService verificationService;
    @Autowired TenantProvisioningService provisioningService;
    @Autowired DevelopmentVerificationMailbox mailbox;
    @Autowired TenantRepository tenantRepository;
    @Autowired UserIdentityRepository identityRepository;
    @Autowired TenantMembershipRepository membershipRepository;

    @Test
    void provisionAndVerifyTwoIsolatedUatTenants() {
        Tenant tenantA = provisionIfMissing(
            TENANT_A_EMAIL, TENANT_A_SLUG, "A2 Ordinary Company",
            required("A2_UAT_OWNER_A_PASSWORD"));
        Tenant tenantB = provisionIfMissing(
            TENANT_B_EMAIL, TENANT_B_SLUG, "A2 Optional Governance Company",
            required("A2_UAT_OWNER_B_PASSWORD"));

        assertThat(tenantA.getId()).isNotEqualTo(tenantB.getId());
        assertThat(tenantA.getStatus()).isEqualTo(TenantStatus.ACTIVE);
        assertThat(tenantB.getStatus()).isEqualTo(TenantStatus.ACTIVE);
        assertThat(identityRepository.findByNormalizedEmail(TENANT_A_EMAIL)).isPresent();
        assertThat(identityRepository.findByNormalizedEmail(TENANT_B_EMAIL)).isPresent();

        var identityA = identityRepository.findByNormalizedEmail(TENANT_A_EMAIL).orElseThrow();
        var identityB = identityRepository.findByNormalizedEmail(TENANT_B_EMAIL).orElseThrow();
        assertThat(membershipRepository.findByIdentityIdAndTenantId(identityA.getId(), tenantA.getId()))
            .isPresent();
        assertThat(membershipRepository.findByIdentityIdAndTenantId(identityB.getId(), tenantB.getId()))
            .isPresent();

        assertThatThrownBy(() -> verificationService.begin(
            new BeginSignupRequest(TENANT_A_EMAIL.toUpperCase(), PlanCode.TRIAL),
            "a2-duplicate-email-" + UUID.randomUUID()))
            .isInstanceOf(BusinessException.class)
            .extracting(error -> ((BusinessException) error).getErrorKey())
            .isEqualTo(ErrorKeys.SIGNUP_EMAIL_UNAVAILABLE);
    }

    private Tenant provisionIfMissing(
            String email,
            String slug,
            String companyName,
            String password) {
        Tenant existing = tenantRepository.findBySlug(slug).orElse(null);
        if (existing != null) {
            var identity = identityRepository.findByNormalizedEmail(email).orElseThrow(
                () -> new IllegalStateException("Existing A2 tenant has no expected owner identity"));
            assertThat(membershipRepository.findByIdentityIdAndTenantId(identity.getId(), existing.getId()))
                .isPresent();
            return enforceUatDefaults(existing);
        }

        var begun = verificationService.begin(
            new BeginSignupRequest(email, PlanCode.TRIAL),
            "a2-provision-" + slug);
        String code = mailbox.latest(email).orElseThrow().code();
        verificationService.verify(new VerifyEmailRequest(begun.signupId(), code));
        provisioningService.complete(begun.signupId(), new CompleteSignupRequest(
            "A2", "Owner", companyName, slug, password));
        return enforceUatDefaults(tenantRepository.findBySlug(slug).orElseThrow());
    }

    private Tenant enforceUatDefaults(Tenant tenant) {
        if (!"Europe/London".equals(tenant.getTimezone())) {
            tenant.setTimezone("Europe/London");
            return tenantRepository.saveAndFlush(tenant);
        }
        return tenant;
    }

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " is required for explicit A2 provisioning");
        }
        return value;
    }
}
