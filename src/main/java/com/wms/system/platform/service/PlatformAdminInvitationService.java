package com.wms.system.platform.service;

import com.wms.system.exception.*;
import com.wms.system.platform.dto.*;
import com.wms.system.platform.model.*;
import com.wms.system.platform.repository.*;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.OffsetDateTime;
import java.util.*;

@Service @RequiredArgsConstructor
public class PlatformAdminInvitationService {
    private static final String SUPER_ADMIN="PLATFORM_SUPER_ADMIN";
    private final PlatformAdminInvitationRepository invitations;
    private final PlatformUserRepository users;
    private final PlatformRoleRepository roles;
    private final PlatformUserRoleRepository userRoles;
    private final PlatformMfaService mfa;
    private final PlatformMfaCrypto crypto;
    private final PasswordEncoder passwordEncoder;
    private final PlatformAccessGuard guard;
    private final PlatformAuditService audit;

    public PlatformReauthenticationChallengeResponse startChallenge(){
        guard.requireSuperAdmin();
        return mfa.startAdminInvitation();
    }

    @Transactional(noRollbackFor=BusinessException.class)
    public PlatformAdminInvitationResponse create(PlatformAdminInvitationCreateRequest request,HttpServletRequest http){
        String email=request.getEmail().trim().toLowerCase(Locale.ROOT);
        String displayName=request.getDisplayName().trim();
        String reason=request.getReason().trim();
        if(users.findByNormalizedEmail(email).isPresent())throw new IllegalArgumentException("Platform administrator already exists");
        if(invitations.hasActiveInvitation(email,OffsetDateTime.now()))throw new IllegalArgumentException("Active invitation already exists");
        requireSecondSuperAdminSlot();
        PlatformUser actor=mfa.verifyAdminInvitation(request.getChallengeToken(),request.getPassword(),request.getCode(),http);
        String token=crypto.newChallengeToken();
        PlatformAdminInvitation saved=invitations.save(PlatformAdminInvitation.builder()
            .tokenHash(crypto.hashToken(token)).normalizedEmail(email).displayName(displayName).roleCode(SUPER_ADMIN)
            .invitedByPlatformUserId(actor.getId()).reason(reason).expiresAt(OffsetDateTime.now().plusHours(24)).build());
        audit.record(actor.getId(),null,"ADMIN_INVITED","platform_identity","SUCCESS",
            Map.of("invitationId",saved.getId(),"roleCode",SUPER_ADMIN,"expiresAt",saved.getExpiresAt().toString()),http);
        return response(saved,"/platform.html#/activate?token="+token);
    }

    @Transactional(readOnly=true)
    public List<PlatformAdminInvitationResponse> list(){
        guard.requireSuperAdmin();
        return invitations.findAllByOrderByCreatedAtDesc().stream().map(i->response(i,null)).toList();
    }

    @Transactional
    public void revoke(Long id,HttpServletRequest http){
        PlatformUser actor=guard.requireSuperAdmin().getUser();
        PlatformAdminInvitation invitation=invitations.findById(id).orElseThrow(()->new NoSuchElementException("Invitation not found"));
        if(invitation.getAcceptedAt()!=null)throw new IllegalArgumentException("Accepted invitation cannot be revoked");
        if(invitation.getRevokedAt()==null){
            invitation.setRevokedAt(OffsetDateTime.now()); invitation.setRevokedByPlatformUserId(actor.getId()); invitations.save(invitation);
            audit.record(actor.getId(),null,"ADMIN_INVITATION_REVOKED","platform_identity","SUCCESS",
                Map.of("invitationId",invitation.getId(),"roleCode",invitation.getRoleCode()),http);
        }
    }

    @Transactional(readOnly=true)
    public PlatformInvitationStatusResponse status(PlatformInvitationTokenRequest request){
        PlatformAdminInvitation invitation=active(request.getToken(),false);
        return new PlatformInvitationStatusResponse(invitation.getNormalizedEmail(),invitation.getDisplayName(),
            invitation.getRoleCode(),invitation.getExpiresAt());
    }

    @Transactional
    public PlatformAuthResponse activate(PlatformInvitationActivateRequest request,HttpServletRequest http){
        validatePassword(request.getPassword());
        PlatformAdminInvitation invitation=active(request.getToken(),true);
        if(users.findByNormalizedEmail(invitation.getNormalizedEmail()).isPresent())throw invalidInvitation();
        if(userRoles.countEnabledUsersByRoleCode(SUPER_ADMIN)>=2)throw invalidInvitation();
        PlatformRole role=roles.findByRoleCode(SUPER_ADMIN).orElseThrow(()->new IllegalStateException("Required platform role is missing"));
        PlatformUser user=users.save(PlatformUser.builder().normalizedEmail(invitation.getNormalizedEmail())
            .passwordHash(passwordEncoder.encode(request.getPassword())).displayName(invitation.getDisplayName())
            .enabled(true).securityVersion(1L).mfaEnabled(false).mfaFailedAttempts(0).build());
        userRoles.saveAndFlush(PlatformUserRole.builder().platformUserId(user.getId()).platformRoleId(role.getId()).build());
        invitation.setAcceptedAt(OffsetDateTime.now()); invitation.setAcceptedPlatformUserId(user.getId()); invitations.save(invitation);
        audit.recordInCurrentTransaction(user.getId(),null,"ADMIN_INVITATION_ACCEPTED","platform_identity","SUCCESS",
            Map.of("invitationId",invitation.getId(),"roleCode",SUPER_ADMIN),http);
        return mfa.startInvitedEnrollment(user);
    }

    private PlatformAdminInvitation active(String token,boolean lock){
        if(token==null||token.isBlank())throw invalidInvitation();
        Optional<PlatformAdminInvitation> found=lock?invitations.findByTokenHashForUpdate(crypto.hashToken(token)):
            invitations.findByTokenHash(crypto.hashToken(token));
        return found.filter(i->i.getAcceptedAt()==null&&i.getRevokedAt()==null&&OffsetDateTime.now().isBefore(i.getExpiresAt()))
            .orElseThrow(this::invalidInvitation);
    }

    private PlatformAdminInvitationResponse response(PlatformAdminInvitation i,String path){
        String status=i.getAcceptedAt()!=null?"ACCEPTED":i.getRevokedAt()!=null?"REVOKED":
            OffsetDateTime.now().isAfter(i.getExpiresAt())?"EXPIRED":"ACTIVE";
        return new PlatformAdminInvitationResponse(i.getId(),i.getNormalizedEmail(),i.getDisplayName(),i.getRoleCode(),status,
            i.getCreatedAt(),i.getExpiresAt(),i.getAcceptedAt(),i.getRevokedAt(),path);
    }

    private void validatePassword(String p){
        boolean valid=p!=null&&p.length()>=12&&p.length()<=64&&p.chars().anyMatch(Character::isUpperCase)
            &&p.chars().anyMatch(Character::isLowerCase)&&p.chars().anyMatch(Character::isDigit)
            &&p.chars().anyMatch(c->!Character.isLetterOrDigit(c)&&!Character.isWhitespace(c));
        if(!valid)throw new BusinessException(ErrorKeys.PASSWORD_TOO_WEAK,Map.of("minimumLength",12));
    }
    private void requireSecondSuperAdminSlot(){
        long enabled=userRoles.countEnabledUsersByRoleCode(SUPER_ADMIN);
        long pending=invitations.countActiveByRoleCode(SUPER_ADMIN,OffsetDateTime.now());
        if(enabled+pending>=2)throw new IllegalArgumentException("The first release permits at most two active or invited super administrators");
    }
    private BusinessException invalidInvitation(){return new BusinessException(ErrorKeys.AUTH_FAILED,Map.of("reason","PLATFORM_INVITATION_INVALID"));}
}
