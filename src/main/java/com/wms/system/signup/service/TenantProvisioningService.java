package com.wms.system.signup.service;

import com.wms.system.entity.SysRole;
import com.wms.system.entity.SysUserRole;
import com.wms.system.entity.User;
import com.wms.system.exception.BusinessException;
import com.wms.system.exception.ErrorKeys;
import com.wms.system.identity.model.MembershipStatus;
import com.wms.system.identity.model.TenantMembership;
import com.wms.system.identity.model.UserIdentity;
import com.wms.system.identity.repository.TenantMembershipRepository;
import com.wms.system.identity.repository.UserIdentityRepository;
import com.wms.system.repository.SysRoleRepository;
import com.wms.system.repository.SysUserRoleRepository;
import com.wms.system.repository.UserRepository;
import com.wms.system.signup.config.SignupProperties;
import com.wms.system.signup.dto.CompleteSignupRequest;
import com.wms.system.signup.dto.SignupResponse;
import com.wms.system.signup.model.*;
import com.wms.system.signup.repository.SessionHandoffCodeRepository;
import com.wms.system.signup.repository.SignupRequestRepository;
import com.wms.system.signup.repository.TenantProvisioningJobRepository;
import com.wms.system.signup.security.SignupTokenHasher;
import com.wms.system.subscription.model.*;
import com.wms.system.subscription.repository.SubscriptionPlanRepository;
import com.wms.system.subscription.repository.TenantSubscriptionRepository;
import com.wms.system.tenant.config.TenancyProperties;
import com.wms.system.tenant.context.RequestSurface;
import com.wms.system.tenant.context.TenantContext;
import com.wms.system.tenant.context.TenantContextHolder;
import com.wms.system.tenant.model.*;
import com.wms.system.tenant.repository.TenantDomainRepository;
import com.wms.system.tenant.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TenantProvisioningService {
    private final SignupRequestRepository signupRepository;
    private final TenantProvisioningJobRepository jobRepository;
    private final TenantRepository tenantRepository;
    private final TenantDomainRepository domainRepository;
    private final UserIdentityRepository identityRepository;
    private final TenantMembershipRepository membershipRepository;
    private final SubscriptionPlanRepository planRepository;
    private final TenantSubscriptionRepository subscriptionRepository;
    private final UserRepository userRepository;
    private final SysRoleRepository roleRepository;
    private final SysUserRoleRepository userRoleRepository;
    private final SessionHandoffCodeRepository handoffRepository;
    private final PasswordEncoder passwordEncoder;
    private final SignupTokenHasher tokenHasher;
    private final SignupProperties signupProperties;
    private final TenancyProperties tenancyProperties;
    private final PlatformTransactionManager transactionManager;

    public SignupResponse complete(String publicId, CompleteSignupRequest command) {
        SignupSeed seed = requiresNew().execute(status -> prepareDetails(publicId, command));
        if (seed == null) throw new BusinessException(ErrorKeys.SIGNUP_PROVISIONING_FAILED);
        if (seed.active()) {
            SignupResponse active = requiresNew().execute(status -> activeResponse(
                signupRepository.findByPublicIdForUpdate(publicId)
                    .orElseThrow(() -> new BusinessException(ErrorKeys.SIGNUP_REQUEST_NOT_FOUND))));
            if (active == null) throw new BusinessException(ErrorKeys.SIGNUP_PROVISIONING_FAILED);
            return active;
        }
        requiresNew().executeWithoutResult(status -> provisionControlPlane(publicId));
        SignupRequest signup = requiresNew().execute(status -> signupRepository.findByPublicId(publicId)
            .orElseThrow(() -> new BusinessException(ErrorKeys.SIGNUP_REQUEST_NOT_FOUND)));
        if (signup == null || signup.getTenantId() == null) {
            throw new BusinessException(ErrorKeys.SIGNUP_PROVISIONING_FAILED);
        }
        Long tenantId = signup.getTenantId();
        Tenant tenant = requiresNew().execute(status -> tenantRepository.findById(tenantId)
            .orElseThrow(() -> new BusinessException(ErrorKeys.SIGNUP_PROVISIONING_FAILED)));
        TenantContext context = new TenantContext(tenantId, tenant.getSlug(),
            tenant.getSlug() + "." + tenancyProperties.getBaseDomain(),
            RequestSurface.TENANT, TenantStatus.PROVISIONING);
        TenantContextHolder.runWithResolvedTenant(context,
            () -> {
                requiresNew().executeWithoutResult(
                    status -> provisionTenantData(signup.getId(), tenantId));
                return null;
            });
        SignupResponse completed = requiresNew().execute(status -> finalizeProvisioning(publicId));
        if (completed == null) throw new BusinessException(ErrorKeys.SIGNUP_PROVISIONING_FAILED);
        return completed;
    }

    private SignupSeed prepareDetails(String publicId, CompleteSignupRequest command) {
        SignupRequest signup = signupRepository.findByPublicIdForUpdate(publicId)
            .orElseThrow(() -> new BusinessException(ErrorKeys.SIGNUP_REQUEST_NOT_FOUND));
        requireVerifiedAndLive(signup);
        String slug = normalizeSlug(command.slug());
        String detailsFingerprint = tokenHasher.hash("signup-details",
            command.firstName().trim() + ":" + command.lastName().trim() + ":"
                + command.companyName().trim() + ":" + slug + ":" + command.password());
        if (signup.getDetailsFingerprint() != null
                && !signup.getDetailsFingerprint().equals(detailsFingerprint)) {
            throw new BusinessException(ErrorKeys.SIGNUP_IDEMPOTENCY_CONFLICT);
        }
        if (signup.getStatus() == SignupStatus.ACTIVE) {
            return new SignupSeed(signup.getId(), true);
        }
        if (signup.getDetailsFingerprint() == null) {
            rejectSlug(slug);
            signup.setFirstName(command.firstName().trim());
            signup.setLastName(command.lastName().trim());
            signup.setCompanyName(command.companyName().trim());
            signup.setSlug(slug);
            signup.setPasswordHash(passwordEncoder.encode(command.password()));
            signup.setDetailsFingerprint(detailsFingerprint);
            signup.setStatus(SignupStatus.DETAILS_COMPLETED);
            signupRepository.save(signup);
        }
        return new SignupSeed(signup.getId(), false);
    }

    private void provisionControlPlane(String publicId) {
        SignupRequest locked = signupRepository.findByPublicIdForUpdate(publicId)
            .orElseThrow(() -> new BusinessException(ErrorKeys.SIGNUP_REQUEST_NOT_FOUND));
        if (locked.getTenantId() != null) return;
        rejectSlug(locked.getSlug());
        try {
            Tenant tenant = tenantRepository.saveAndFlush(Tenant.builder()
                .tenantCode("COMPANY-" + UUID.randomUUID().toString().replace("-", "")
                    .substring(0, 12).toUpperCase(Locale.ROOT))
                .displayName(locked.getCompanyName())
                .slug(locked.getSlug())
                .status(TenantStatus.PROVISIONING)
                .build());
            String hostname = locked.getSlug() + "." + tenancyProperties.getBaseDomain();
            domainRepository.save(TenantDomain.builder()
                .tenantId(tenant.getId()).hostname(hostname)
                .domainType(TenantDomainType.PLATFORM_SUBDOMAIN)
                .status(TenantDomainStatus.ACTIVE).primary(true).verifiedAt(now()).build());
            UserIdentity identity = identityRepository.save(UserIdentity.builder()
                .normalizedEmail(locked.getNormalizedEmail())
                .passwordHash(locked.getPasswordHash()).emailVerifiedAt(now()).build());
            SubscriptionPlan plan = planRepository.findByPlanCodeAndActiveTrue(
                PlanCode.valueOf(locked.getRequestedPlanCode()))
                .orElseThrow(() -> new BusinessException(ErrorKeys.SIGNUP_PROVISIONING_FAILED));
            OffsetDateTime trialStart = plan.getPlanCode() == PlanCode.TRIAL ? now() : null;
            subscriptionRepository.save(TenantSubscription.builder()
                .tenantId(tenant.getId()).planId(plan.getId())
                .status(plan.getPlanCode() == PlanCode.TRIAL
                    ? SubscriptionStatus.TRIALING : SubscriptionStatus.FREE)
                .trialStartedAt(trialStart)
                .trialEndsAt(trialStart == null ? null : trialStart.plusDays(30)).build());
            locked.setTenantId(tenant.getId());
            locked.setIdentityId(identity.getId());
            locked.setStatus(SignupStatus.PROVISIONING);
            signupRepository.save(locked);
            jobRepository.save(TenantProvisioningJob.builder()
                .signupRequestId(locked.getId()).tenantId(tenant.getId())
                .status(ProvisioningStatus.RUNNING).currentStep("TENANT_DATA").build());
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException(ErrorKeys.SIGNUP_SLUG_UNAVAILABLE);
        }
    }

    private void provisionTenantData(Long signupId, Long tenantId) {
        SignupRequest signup = signupRepository.findById(signupId)
            .orElseThrow(() -> new BusinessException(ErrorKeys.SIGNUP_REQUEST_NOT_FOUND));
        String internalUsername = "owner-" + signup.getIdentityId();
        User user = userRepository.findByCompanyIdAndUsername(tenantId, internalUsername)
            .orElseGet(() -> userRepository.save(User.builder()
                .username(internalUsername).password(signup.getPasswordHash())
                .displayName((signup.getFirstName() + " " + signup.getLastName()).trim())
                .enabled(true).mustChangePassword(false).isDeleted(false).build()));
        SysRole role = roleRepository.findByCompanyIdAndRoleCode(tenantId, SysRole.TENANT_ADMIN_ROLE_CODE)
            .orElseGet(() -> roleRepository.save(SysRole.builder()
                .roleCode(SysRole.TENANT_ADMIN_ROLE_CODE).roleName("公司管理员")
                .description("首位注册用户的公司管理角色")
                .roleType(SysRole.ROLE_TYPE_SYSTEM)
                .systemCategory(SysRole.SYSTEM_CATEGORY_PRIVILEGED)
                .importAllowed(false).reviewStatus(SysRole.REVIEW_STATUS_APPROVED)
                .status("ACTIVE").sortOrder(0).build()));
        if (!userRoleRepository.existsByCompanyIdAndUserIdAndRoleId(tenantId, user.getId(), role.getId())) {
            userRoleRepository.save(SysUserRole.builder()
                .companyId(tenantId).userId(user.getId()).roleId(role.getId())
                .assignedBy(user.getId()).build());
        }
        if (user.getDefaultRoleId() == null) {
            user.setDefaultRoleId(role.getId());
            userRepository.save(user);
        }
        if (membershipRepository.findByIdentityIdAndTenantId(signup.getIdentityId(), tenantId).isEmpty()) {
            membershipRepository.save(TenantMembership.builder()
                .identityId(signup.getIdentityId()).tenantId(tenantId)
                .tenantUserId(user.getId()).status(MembershipStatus.ACTIVE).build());
        }
    }

    private SignupResponse finalizeProvisioning(String publicId) {
        SignupRequest signup = signupRepository.findByPublicIdForUpdate(publicId)
            .orElseThrow(() -> new BusinessException(ErrorKeys.SIGNUP_REQUEST_NOT_FOUND));
        if (signup.getStatus() == SignupStatus.ACTIVE) return activeResponse(signup);
        TenantMembership membership = membershipRepository
            .findByIdentityIdAndTenantId(signup.getIdentityId(), signup.getTenantId())
            .orElseThrow(() -> new BusinessException(ErrorKeys.SIGNUP_PROVISIONING_FAILED));
        Tenant tenant = tenantRepository.findById(signup.getTenantId())
            .orElseThrow(() -> new BusinessException(ErrorKeys.SIGNUP_PROVISIONING_FAILED));
        tenant.setStatus(TenantStatus.ACTIVE);
        tenantRepository.save(tenant);
        signup.setStatus(SignupStatus.ACTIVE);
        signupRepository.save(signup);
        TenantProvisioningJob job = jobRepository.findBySignupRequestId(signup.getId())
            .orElseThrow(() -> new BusinessException(ErrorKeys.SIGNUP_PROVISIONING_FAILED));
        job.setStatus(ProvisioningStatus.COMPLETED);
        job.setCurrentStep("COMPLETED");
        jobRepository.save(job);
        return activeResponse(signup);
    }

    private SignupResponse activeResponse(SignupRequest signup) {
        SessionHandoffCode existing = handoffRepository.findBySignupRequestId(signup.getId()).orElse(null);
        String raw = tokenHasher.opaqueCode();
        if (existing != null) {
            // Raw values are never stored, so every retry rotates the one DB row.
            existing.setCodeHash(tokenHasher.hash("session-handoff", raw));
            existing.setExpiresAt(now().plus(signupProperties.handoffTtl()));
            existing.setConsumedAt(null);
            handoffRepository.save(existing);
        } else {
            TenantMembership membership = membershipRepository
                .findByIdentityIdAndTenantId(signup.getIdentityId(), signup.getTenantId())
                .orElseThrow(() -> new BusinessException(ErrorKeys.SIGNUP_PROVISIONING_FAILED));
            handoffRepository.save(SessionHandoffCode.builder()
                .signupRequestId(signup.getId()).codeHash(tokenHasher.hash("session-handoff", raw))
                .tenantId(signup.getTenantId()).identityId(signup.getIdentityId())
                .tenantUserId(membership.getTenantUserId())
                .expiresAt(now().plus(signupProperties.handoffTtl())).build());
        }
        String redirect = "https://" + signup.getSlug() + "." + tenancyProperties.getBaseDomain()
            + "/#/session/handoff?code=" + raw;
        return new SignupResponse(signup.getPublicId(), signup.getStatus(), null, null,
            signup.getSlug(), redirect, raw, signupProperties.getHandoffSeconds());
    }

    private void requireVerifiedAndLive(SignupRequest signup) {
        if (!signup.getExpiresAt().isAfter(now())) throw new BusinessException(ErrorKeys.SIGNUP_REQUEST_EXPIRED);
        if (signup.getStatus() == SignupStatus.EMAIL_PENDING)
            throw new BusinessException(ErrorKeys.SIGNUP_EMAIL_VERIFICATION_REQUIRED);
        if (signup.getStatus() == SignupStatus.EXPIRED)
            throw new BusinessException(ErrorKeys.SIGNUP_REQUEST_EXPIRED);
    }

    private void rejectSlug(String slug) {
        String normalized = normalizeSlug(slug);
        if (tenancyProperties.getReservedSlugs().stream().anyMatch(s -> s.equalsIgnoreCase(normalized))
                || tenantRepository.existsBySlug(normalized)
                || domainRepository.existsByHostname(normalized + "." + tenancyProperties.getBaseDomain())) {
            throw new BusinessException(ErrorKeys.SIGNUP_SLUG_UNAVAILABLE);
        }
    }

    private String normalizeSlug(String slug) { return slug.trim().toLowerCase(Locale.ROOT); }
    private OffsetDateTime now() { return OffsetDateTime.now(ZoneOffset.UTC); }

    private TransactionTemplate requiresNew() {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return template;
    }

    private record SignupSeed(Long signupId, boolean active) {}
}
