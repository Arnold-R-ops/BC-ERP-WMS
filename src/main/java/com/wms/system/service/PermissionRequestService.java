package com.wms.system.service;

import com.wms.system.dto.AssignableWarehouseDTO;
import com.wms.system.dto.PermissionDTO;
import com.wms.system.dto.PermissionRequestAuditDTO;
import com.wms.system.dto.PermissionRequestCreateRequest;
import com.wms.system.dto.PermissionRequestDTO;
import com.wms.system.dto.PermissionRequestPageResponse;
import com.wms.system.dto.PermissionRequestReviewRequest;
import com.wms.system.dto.PermissionRequestRevokeRequest;
import com.wms.system.entity.SysPermission;
import com.wms.system.entity.SysPermissionRequest;
import com.wms.system.entity.SysPermissionRequestAudit;
import com.wms.system.entity.SysPermissionRequestWarehouse;
import com.wms.system.entity.SysRole;
import com.wms.system.entity.User;
import com.wms.system.entity.Warehouse;
import com.wms.system.exception.BusinessException;
import com.wms.system.exception.ErrorKeys;
import com.wms.system.repository.SysPermissionRequestAuditRepository;
import com.wms.system.repository.SysPermissionRequestRepository;
import com.wms.system.repository.SysPermissionRequestWarehouseRepository;
import com.wms.system.repository.SysRoleRepository;
import com.wms.system.repository.SysUserRoleRepository;
import com.wms.system.repository.UserRepository;
import com.wms.system.repository.WarehouseRepository;
import com.wms.system.tenant.context.CompanyScope;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PermissionRequestService {
    private static final String ACTION_CREATE = "CREATE";
    private static final String ACTION_APPROVE = "APPROVE";
    private static final String ACTION_REJECT = "REJECT";
    private static final String ACTION_REVOKE = "REVOKE";
    private static final Set<String> VALID_STATUSES = Set.of(
        SysPermissionRequest.STATUS_PENDING_REVIEW,
        SysPermissionRequest.STATUS_APPROVED,
        SysPermissionRequest.STATUS_REJECTED,
        SysPermissionRequest.STATUS_REVOKED
    );

    private final SysPermissionRequestRepository requestRepository;
    private final SysPermissionRequestWarehouseRepository requestWarehouseRepository;
    private final SysPermissionRequestAuditRepository auditRepository;
    private final UserRepository userRepository;
    private final SysRoleRepository roleRepository;
    private final SysUserRoleRepository userRoleRepository;
    private final WarehouseRepository warehouseRepository;
    private final DynamicPermissionService dynamicPermissionService;
    private final UserRoleService userRoleService;
    private final UserWarehouseService userWarehouseService;
    private final UserManagementService userManagementService;

    public PermissionRequestPageResponse list(
        String status,
        Long targetUserId,
        Long requestedRoleId,
        int page,
        int size,
        String operatorRoleCode
    ) {
        requireIamOperator(operatorRoleCode);
        String normalizedStatus = normalizeStatus(status);
        PageRequest pageable = PageRequest.of(
            Math.max(0, page),
            Math.min(Math.max(size, 1), 100),
            Sort.by(Sort.Direction.DESC, "submittedAt")
        );
        Page<SysPermissionRequest> result = requestRepository.search(
            CompanyScope.currentCompanyId(),
            normalizedStatus,
            targetUserId,
            requestedRoleId,
            isTenantAdmin(operatorRoleCode),
            pageable
        );
        return PermissionRequestPageResponse.builder()
            .items(result.getContent().stream().map(this::toDTO).toList())
            .total(result.getTotalElements())
            .page(result.getNumber())
            .size(result.getSize())
            .build();
    }

    @Transactional(rollbackFor = Exception.class)
    public PermissionRequestDTO create(
        PermissionRequestCreateRequest input,
        Long operatorId,
        String operatorUsername,
        String operatorRoleCode
    ) {
        requireIamOperator(operatorRoleCode);
        requireRealOperator(operatorId);
        User target = requireActiveTarget(input.getTargetUserId());
        requireNotSelfGrant(target.getId(), operatorId);
        requireTargetAllowed(target, operatorRoleCode);

        Long companyId = CompanyScope.currentCompanyId();
        SysRole role = roleRepository.findByCompanyIdAndId(companyId, input.getRequestedRoleId())
            .orElseThrow(() -> notFound(ErrorKeys.ROLE_NOT_FOUND, "roleId", input.getRequestedRoleId()));
        requireRequestableRole(role);
        if (userRoleRepository.existsByCompanyIdAndUserIdAndRoleId(
                companyId, target.getId(), role.getId())) {
            throw new BusinessException(ErrorKeys.PERMISSION_REQUEST_ROLE_ALREADY_ASSIGNED, Map.of(
                "targetUserId", target.getId(),
                "requestedRoleId", role.getId()
            ));
        }
        if (requestRepository.existsByCompanyIdAndTargetUserIdAndRequestedRoleIdAndStatus(
            companyId, target.getId(), role.getId(), SysPermissionRequest.STATUS_PENDING_REVIEW
        )) {
            throw pendingRequestExists(target.getId(), role.getId());
        }

        List<Long> warehouseIds = normalizeWarehouseIds(input.getWarehouseIds());
        validateRequestWarehouses(role, warehouseIds);
        PermissionSnapshot snapshot = snapshot(role);
        SysPermissionRequest request = SysPermissionRequest.builder()
            .companyId(companyId)
            .targetUserId(target.getId())
            .targetUsername(target.getUsername())
            .requestedRoleId(role.getId())
            .requestedRoleCode(role.getRoleCode())
            .requestedRoleName(role.getRoleName())
            .requestReason(normalizeOptional(input.getRequestReason()))
            .status(SysPermissionRequest.STATUS_PENDING_REVIEW)
            .highRiskPermissionCount(snapshot.highRiskCount())
            .permissionCodes(snapshot.permissionCodes())
            .snapshotFingerprint(snapshot.fingerprint())
            .submittedBy(operatorId)
            .submittedByUsername(normalizeOperator(operatorUsername))
            .build();
        try {
            request = requestRepository.saveAndFlush(request);
        } catch (DataIntegrityViolationException exception) {
            throw pendingRequestExists(target.getId(), role.getId());
        }
        saveWarehouses(request.getId(), warehouseIds);
        saveAudit(
            request,
            ACTION_CREATE,
            operatorId,
            operatorUsername,
            operatorRoleCode,
            null,
            request.getStatus(),
            warehouseIds,
            request.getRequestReason()
        );
        log.info("Permission request created: requestId={}, targetUserId={}, roleId={}",
            request.getId(), target.getId(), role.getId());
        return toDTO(request);
    }

    @Transactional(rollbackFor = Exception.class)
    public PermissionRequestDTO review(
        Long requestId,
        PermissionRequestReviewRequest input,
        Long operatorId,
        String operatorUsername,
        String operatorRoleCode
    ) {
        requireIamOperator(operatorRoleCode);
        requireRealOperator(operatorId);
        SysPermissionRequest request = requireForUpdate(requestId);
        requireStatus(request, SysPermissionRequest.STATUS_PENDING_REVIEW);
        Long companyId = CompanyScope.currentCompanyId();
        User target = userRepository.findByCompanyIdAndIdForUpdate(
                companyId, request.getTargetUserId())
            .orElseThrow(() -> notFound(ErrorKeys.USER_NOT_FOUND, "userId", request.getTargetUserId()));
        requireTargetAllowed(target, operatorRoleCode);

        if (!Boolean.TRUE.equals(input.getApproved())) {
            String comment = requireComment(input.getComment(), ErrorKeys.PERMISSION_REQUEST_REJECTION_COMMENT_REQUIRED);
            String fromStatus = request.getStatus();
            request.setStatus(SysPermissionRequest.STATUS_REJECTED);
            request.setReviewedBy(operatorId);
            request.setReviewedByUsername(normalizeOperator(operatorUsername));
            request.setReviewedAt(LocalDateTime.now());
            request.setReviewComment(comment);
            requestRepository.save(request);
            saveAudit(request, ACTION_REJECT, operatorId, operatorUsername, operatorRoleCode,
                fromStatus, request.getStatus(), warehouseIds(request.getId()), comment);
            return toDTO(request);
        }

        requireNotSelfGrant(target.getId(), operatorId);
        requireActiveTarget(target);
        SysRole role = roleRepository.findByCompanyIdAndIdForUpdate(
                companyId, request.getRequestedRoleId())
            .orElseThrow(() -> notFound(ErrorKeys.ROLE_NOT_FOUND, "roleId", request.getRequestedRoleId()));
        requireRequestableRole(role);
        PermissionSnapshot currentSnapshot = snapshot(role);
        if (!currentSnapshot.fingerprint().equals(request.getSnapshotFingerprint())) {
            throw new BusinessException(ErrorKeys.PERMISSION_REQUEST_SNAPSHOT_STALE, Map.of(
                "permissionRequestId", requestId,
                "requestedRoleId", role.getId()
            ));
        }
        if (currentSnapshot.highRiskCount() > 0
            && (!isTenantAdmin(operatorRoleCode) || operatorId.equals(request.getSubmittedBy()))) {
            throw new BusinessException(ErrorKeys.PERMISSION_REQUEST_SECOND_REVIEWER_REQUIRED, Map.of(
                "permissionRequestId", requestId,
                "highRiskPermissionCount", currentSnapshot.highRiskCount(),
                "requiredReviewerRole", SysRole.TENANT_ADMIN_ROLE_CODE
            ));
        }
        if (userRoleRepository.existsByCompanyIdAndUserIdAndRoleId(
                companyId, target.getId(), role.getId())) {
            throw new BusinessException(ErrorKeys.PERMISSION_REQUEST_ROLE_ALREADY_ASSIGNED, Map.of(
                "permissionRequestId", requestId,
                "targetUserId", target.getId(),
                "requestedRoleId", role.getId()
            ));
        }

        List<SysRole> existingRoles = userRoleService.getUserRoles(target.getId());
        List<SysRole> rolesAfterGrant = new ArrayList<>(existingRoles);
        rolesAfterGrant.add(role);
        List<Long> effectiveWarehouseIds = warehousesAfterGrant(request, role, existingRoles, target.getId());
        userWarehouseService.validateSelection(rolesAfterGrant, effectiveWarehouseIds);
        userRoleService.assignRoleToUser(target.getId(), role.getId(), operatorId);
        userWarehouseService.replaceAssignments(
            target.getId(), rolesAfterGrant, effectiveWarehouseIds, operatorId
        );

        String fromStatus = request.getStatus();
        request.setStatus(SysPermissionRequest.STATUS_APPROVED);
        request.setReviewedBy(operatorId);
        request.setReviewedByUsername(normalizeOperator(operatorUsername));
        request.setReviewedAt(LocalDateTime.now());
        request.setReviewComment(normalizeOptional(input.getComment()));
        requestRepository.save(request);
        saveAudit(request, ACTION_APPROVE, operatorId, operatorUsername, operatorRoleCode,
            fromStatus, request.getStatus(), effectiveWarehouseIds, request.getReviewComment());
        log.info("Permission request approved: requestId={}, targetUserId={}, roleId={}",
            requestId, target.getId(), role.getId());
        return toDTO(request);
    }

    @Transactional(rollbackFor = Exception.class)
    public PermissionRequestDTO revoke(
        Long requestId,
        PermissionRequestRevokeRequest input,
        Long operatorId,
        String operatorUsername,
        String operatorRoleCode
    ) {
        requireIamOperator(operatorRoleCode);
        requireRealOperator(operatorId);
        SysPermissionRequest request = requireForUpdate(requestId);
        requireStatus(request, SysPermissionRequest.STATUS_APPROVED);
        Long companyId = CompanyScope.currentCompanyId();
        User target = userRepository.findByCompanyIdAndIdForUpdate(
                companyId, request.getTargetUserId())
            .orElseThrow(() -> notFound(ErrorKeys.USER_NOT_FOUND, "userId", request.getTargetUserId()));
        requireTargetAllowed(target, operatorRoleCode);
        String comment = requireComment(input.getComment(), ErrorKeys.PERMISSION_REQUEST_REVOCATION_COMMENT_REQUIRED);

        SysRole role = roleRepository.findByCompanyIdAndId(
                companyId, request.getRequestedRoleId())
            .orElseThrow(() -> notFound(ErrorKeys.ROLE_NOT_FOUND, "roleId", request.getRequestedRoleId()));
        if (userRoleRepository.existsByCompanyIdAndUserIdAndRoleId(
                companyId, target.getId(), role.getId())) {
            List<SysRole> currentRoles = userRoleService.getUserRoles(target.getId());
            Set<Long> remainingRoleIds = currentRoles.stream()
                .map(SysRole::getId)
                .filter(roleId -> !roleId.equals(role.getId()))
                .collect(Collectors.toCollection(LinkedHashSet::new));
            if (remainingRoleIds.isEmpty()) {
                throw new BusinessException(ErrorKeys.OPERATION_NOT_ALLOWED, Map.of(
                    "operation", "Revoke requested role",
                    "reason", "User must retain at least one role"
                ));
            }
            userManagementService.validateRoleReplacement(
                target.getId(), operatorId, remainingRoleIds, operatorRoleCode
            );
            userRoleService.removeRoleFromUser(target.getId(), role.getId());
            if (UserWarehouseService.WAREHOUSE_STAFF.equals(role.getRoleCode())) {
                userWarehouseService.clearAssignments(target.getId());
            }
            if (role.getId().equals(target.getDefaultRoleId())) {
                target.setDefaultRoleId(remainingRoleIds.iterator().next());
                userRepository.save(target);
            }
        }

        String fromStatus = request.getStatus();
        request.setStatus(SysPermissionRequest.STATUS_REVOKED);
        request.setRevokedBy(operatorId);
        request.setRevokedByUsername(normalizeOperator(operatorUsername));
        request.setRevokedAt(LocalDateTime.now());
        request.setRevocationComment(comment);
        requestRepository.save(request);
        saveAudit(request, ACTION_REVOKE, operatorId, operatorUsername, operatorRoleCode,
            fromStatus, request.getStatus(), warehouseIds(request.getId()), comment);
        log.warn("Permission request revoked: requestId={}, targetUserId={}, roleId={}",
            requestId, target.getId(), role.getId());
        return toDTO(request);
    }

    public List<PermissionRequestAuditDTO> history(Long requestId, String operatorRoleCode) {
        requireIamOperator(operatorRoleCode);
        Long companyId = CompanyScope.currentCompanyId();
        SysPermissionRequest request = requestRepository.findByCompanyIdAndId(companyId, requestId)
            .orElseThrow(() -> notFound(ErrorKeys.PERMISSION_REQUEST_NOT_FOUND, "permissionRequestId", requestId));
        // Audit history is a durable snapshot and must remain readable after the
        // target account is logically deleted. Protected-target isolation still
        // uses the retained role assignment records and does not require an
        // active User entity.
        requireTargetAllowed(request.getTargetUserId(), operatorRoleCode);
        return auditRepository
            .findByCompanyIdAndPermissionRequestIdOrderByCreatedAtDesc(companyId, requestId).stream()
            .map(this::toAuditDTO)
            .toList();
    }

    private List<Long> warehousesAfterGrant(
        SysPermissionRequest request,
        SysRole requestedRole,
        List<SysRole> existingRoles,
        Long targetUserId
    ) {
        if (UserWarehouseService.WAREHOUSE_STAFF.equals(requestedRole.getRoleCode())) {
            return warehouseIds(request.getId());
        }
        boolean alreadyWarehouseStaff = existingRoles.stream()
            .anyMatch(role -> UserWarehouseService.WAREHOUSE_STAFF.equals(role.getRoleCode()));
        if (!alreadyWarehouseStaff) {
            return List.of();
        }
        return userWarehouseService.getAssignedWarehouseIds(targetUserId).stream().sorted().toList();
    }

    private void validateRequestWarehouses(SysRole role, List<Long> warehouseIds) {
        if (!UserWarehouseService.WAREHOUSE_STAFF.equals(role.getRoleCode())) {
            if (!warehouseIds.isEmpty()) {
                throw new BusinessException(ErrorKeys.PERMISSION_REQUEST_WAREHOUSE_NOT_ALLOWED, Map.of(
                    "requestedRoleCode", role.getRoleCode()
                ));
            }
            return;
        }
        userWarehouseService.validateSelection(List.of(role), warehouseIds);
    }

    private PermissionSnapshot snapshot(SysRole role) {
        Set<Long> effectiveRoleIds = dynamicPermissionService.getInheritedRoleIds(Set.of(role.getId()));
        List<PermissionDTO> permissions = new ArrayList<>(
            dynamicPermissionService.getPermissionsByRoleIds(effectiveRoleIds)
        );
        permissions.sort(Comparator.comparing(PermissionDTO::getPermissionCode));
        List<String> blockedCodes = permissions.stream()
            .filter(permission -> SysPermission.RISK_LEVEL_CRITICAL.equals(permission.getRiskLevel())
                || SysPermission.isReservedPermissionCode(permission.getPermissionCode()))
            .map(PermissionDTO::getPermissionCode)
            .toList();
        if (!blockedCodes.isEmpty()) {
            throw new BusinessException(ErrorKeys.PERMISSION_REQUEST_ROLE_NOT_ALLOWED, Map.of(
                "requestedRoleId", role.getId(),
                "blockedPermissionCodes", blockedCodes
            ));
        }
        int highRiskCount = (int) permissions.stream()
            .filter(permission -> SysPermission.RISK_LEVEL_HIGH.equals(permission.getRiskLevel()))
            .count();
        String permissionCodes = permissions.stream()
            .map(PermissionDTO::getPermissionCode)
            .collect(Collectors.joining(","));
        StringBuilder material = new StringBuilder().append(role.getId()).append('|');
        permissions.forEach(permission -> material.append(permission.getId()).append(':')
            .append(permission.getPermissionCode()).append(':')
            .append(permission.getRiskLevel()).append(':')
            .append(Boolean.TRUE.equals(permission.getCustomAssignable())).append('|'));
        return new PermissionSnapshot(highRiskCount, permissionCodes, sha256(material.toString()));
    }

    private void requireRequestableRole(SysRole role) {
        if (!role.isAssignableToUsers() || role.isPrivilegedRole()) {
            throw new BusinessException(ErrorKeys.PERMISSION_REQUEST_ROLE_NOT_ALLOWED, Map.of(
                "requestedRoleId", role.getId(),
                "requestedRoleCode", role.getRoleCode(),
                "status", role.getStatus(),
                "reviewStatus", role.getReviewStatus()
            ));
        }
    }

    private User requireActiveTarget(Long userId) {
        return requireActiveTarget(userRepository.findByIdAndCompanyId(
                userId, CompanyScope.currentCompanyId())
            .orElseThrow(() -> notFound(ErrorKeys.USER_NOT_FOUND, "userId", userId)));
    }

    private User requireActiveTarget(User target) {
        if (!Boolean.TRUE.equals(target.getEnabled()) || Boolean.TRUE.equals(target.getIsDeleted())) {
            throw new BusinessException(ErrorKeys.PERMISSION_REQUEST_TARGET_INACTIVE, Map.of(
                "targetUserId", target.getId()
            ));
        }
        return target;
    }

    private void requireTargetAllowed(User target, String operatorRoleCode) {
        requireTargetAllowed(target.getId(), operatorRoleCode);
    }

    private void requireTargetAllowed(Long targetUserId, String operatorRoleCode) {
        if (!isSecurityAdmin(operatorRoleCode) || !hasProtectedIdentity(targetUserId)) {
            return;
        }
        throw new BusinessException(ErrorKeys.PERMISSION_REQUEST_PROTECTED_TARGET, Map.of(
            "targetUserId", targetUserId
        ));
    }

    private boolean hasProtectedIdentity(Long userId) {
        Long companyId = CompanyScope.currentCompanyId();
        Set<Long> roleIds = userRoleRepository
            .findRoleIdsByCompanyIdAndUserId(companyId, userId);
        return !roleIds.isEmpty()
            && roleRepository.findByCompanyIdAndIdIn(companyId, roleIds)
                .stream().anyMatch(SysRole::isPrivilegedRole);
    }

    private void requireNotSelfGrant(Long targetUserId, Long operatorId) {
        if (targetUserId.equals(operatorId)) {
            throw new BusinessException(ErrorKeys.PERMISSION_REQUEST_SELF_GRANT_FORBIDDEN, Map.of(
                "targetUserId", targetUserId
            ));
        }
    }

    private void requireIamOperator(String operatorRoleCode) {
        if (!isTenantAdmin(operatorRoleCode) && !isSecurityAdmin(operatorRoleCode)) {
            throw new BusinessException(ErrorKeys.AUTH_ACCESS_DENIED, Map.of(
                "requiredRoles", List.of(SysRole.TENANT_ADMIN_ROLE_CODE, SysRole.SECURITY_ADMIN_ROLE_CODE)
            ));
        }
    }

    private void requireRealOperator(Long operatorId) {
        if (operatorId == null || operatorId <= 0) {
            throw new BusinessException(ErrorKeys.AUTH_ACCESS_DENIED, Map.of("reason", "Missing operator identity"));
        }
    }

    private boolean isTenantAdmin(String roleCode) {
        return SysRole.TENANT_ADMIN_ROLE_CODE.equals(roleCode);
    }

    private boolean isSecurityAdmin(String roleCode) {
        return SysRole.SECURITY_ADMIN_ROLE_CODE.equals(roleCode);
    }

    private SysPermissionRequest requireForUpdate(Long requestId) {
        return requestRepository.findByCompanyIdAndIdForUpdate(
                CompanyScope.currentCompanyId(), requestId)
            .orElseThrow(() -> notFound(ErrorKeys.PERMISSION_REQUEST_NOT_FOUND, "permissionRequestId", requestId));
    }

    private void requireStatus(SysPermissionRequest request, String expectedStatus) {
        if (!expectedStatus.equals(request.getStatus())) {
            throw new BusinessException(ErrorKeys.PERMISSION_REQUEST_INVALID_STATUS, Map.of(
                "permissionRequestId", request.getId(),
                "expectedStatus", expectedStatus,
                "actualStatus", request.getStatus()
            ));
        }
    }

    private BusinessException pendingRequestExists(Long targetUserId, Long roleId) {
        return new BusinessException(ErrorKeys.PERMISSION_REQUEST_ALREADY_PENDING, Map.of(
            "targetUserId", targetUserId,
            "requestedRoleId", roleId
        ));
    }

    private BusinessException notFound(String errorKey, String key, Long value) {
        return new BusinessException(errorKey, Map.of(key, value));
    }

    private void saveWarehouses(Long requestId, List<Long> warehouseIds) {
        if (warehouseIds.isEmpty()) {
            return;
        }
        requestWarehouseRepository.saveAll(warehouseIds.stream()
            .map(warehouseId -> SysPermissionRequestWarehouse.builder()
                .companyId(CompanyScope.currentCompanyId())
                .permissionRequestId(requestId)
                .warehouseId(warehouseId)
                .build())
            .toList());
    }

    private List<Long> warehouseIds(Long requestId) {
        return requestWarehouseRepository
            .findByCompanyIdAndPermissionRequestIdOrderByWarehouseIdAsc(
                CompanyScope.currentCompanyId(), requestId).stream()
            .map(SysPermissionRequestWarehouse::getWarehouseId)
            .toList();
    }

    private PermissionRequestDTO toDTO(SysPermissionRequest request) {
        List<Long> warehouseIds = warehouseIds(request.getId());
        Map<Long, Warehouse> warehousesById = warehouseRepository.findAllById(warehouseIds).stream()
            .collect(Collectors.toMap(Warehouse::getId, warehouse -> warehouse));
        List<AssignableWarehouseDTO> warehouses = warehouseIds.stream()
            .map(warehousesById::get)
            .filter(java.util.Objects::nonNull)
            .map(warehouse -> AssignableWarehouseDTO.builder()
                .id(warehouse.getId())
                .code(warehouse.getCode())
                .name(warehouse.getName())
                .build())
            .toList();
        return PermissionRequestDTO.builder()
            .id(request.getId())
            .targetUserId(request.getTargetUserId())
            .targetUsername(request.getTargetUsername())
            .requestedRoleId(request.getRequestedRoleId())
            .requestedRoleCode(request.getRequestedRoleCode())
            .requestedRoleName(request.getRequestedRoleName())
            .warehouses(warehouses)
            .requestReason(request.getRequestReason())
            .status(request.getStatus())
            .highRiskPermissionCount(request.getHighRiskPermissionCount())
            .submittedByUsername(request.getSubmittedByUsername())
            .submittedAt(request.getSubmittedAt())
            .reviewedByUsername(request.getReviewedByUsername())
            .reviewedAt(request.getReviewedAt())
            .reviewComment(request.getReviewComment())
            .revokedByUsername(request.getRevokedByUsername())
            .revokedAt(request.getRevokedAt())
            .revocationComment(request.getRevocationComment())
            .build();
    }

    private void saveAudit(
        SysPermissionRequest request,
        String action,
        Long operatorId,
        String operatorUsername,
        String operatorRoleCode,
        String fromStatus,
        String toStatus,
        List<Long> warehouseIds,
        String reason
    ) {
        auditRepository.save(SysPermissionRequestAudit.builder()
            .companyId(request.getCompanyId())
            .permissionRequestId(request.getId())
            .action(action)
            .operatorId(operatorId)
            .operatorUsername(normalizeOperator(operatorUsername))
            .operatorRoleCode(operatorRoleCode)
            .targetUserId(request.getTargetUserId())
            .targetUsername(request.getTargetUsername())
            .requestedRoleId(request.getRequestedRoleId())
            .requestedRoleCode(request.getRequestedRoleCode())
            .fromStatus(fromStatus)
            .toStatus(toStatus)
            .warehouseIds(warehouseIds.stream().map(String::valueOf).collect(Collectors.joining(",")))
            .highRiskPermissionCount(request.getHighRiskPermissionCount())
            .permissionCodes(request.getPermissionCodes())
            .reason(normalizeOptional(reason))
            .build());
    }

    private PermissionRequestAuditDTO toAuditDTO(SysPermissionRequestAudit audit) {
        return PermissionRequestAuditDTO.builder()
            .id(audit.getId())
            .permissionRequestId(audit.getPermissionRequestId())
            .action(audit.getAction())
            .operatorId(audit.getOperatorId())
            .operatorUsername(audit.getOperatorUsername())
            .operatorRoleCode(audit.getOperatorRoleCode())
            .targetUserId(audit.getTargetUserId())
            .targetUsername(audit.getTargetUsername())
            .requestedRoleId(audit.getRequestedRoleId())
            .requestedRoleCode(audit.getRequestedRoleCode())
            .fromStatus(audit.getFromStatus())
            .toStatus(audit.getToStatus())
            .warehouseIds(audit.getWarehouseIds())
            .highRiskPermissionCount(audit.getHighRiskPermissionCount())
            .permissionCodes(audit.getPermissionCodes())
            .reason(audit.getReason())
            .createdAt(audit.getCreatedAt())
            .build();
    }

    private String normalizeStatus(String status) {
        String normalized = normalizeOptional(status);
        if (normalized == null) {
            return null;
        }
        normalized = normalized.toUpperCase();
        if (!VALID_STATUSES.contains(normalized)) {
            throw new BusinessException(ErrorKeys.VALIDATION_FAILED, Map.of("status", status));
        }
        return normalized;
    }

    private List<Long> normalizeWarehouseIds(List<Long> warehouseIds) {
        if (warehouseIds == null) {
            return List.of();
        }
        return warehouseIds.stream()
            .filter(id -> id != null && id > 0)
            .distinct()
            .sorted()
            .toList();
    }

    private String requireComment(String comment, String errorKey) {
        String normalized = normalizeOptional(comment);
        if (normalized == null) {
            throw new BusinessException(errorKey, Map.of());
        }
        return normalized;
    }

    private String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String normalizeOperator(String username) {
        return username == null || username.isBlank() ? "unknown" : username.trim();
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private record PermissionSnapshot(int highRiskCount, String permissionCodes, String fingerprint) {
    }
}
