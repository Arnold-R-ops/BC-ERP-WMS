import { beforeEach, describe, expect, it, vi } from 'vitest';
import {
  activateRolePackage,
  copyRole,
  createPermissionRequest,
  createRolePackageDraft,
  createUser,
  deactivateRolePackage,
  deleteUser,
  getRoleCopyPreview,
  getRoleGovernanceHistory,
  listPermissions,
  listPermissionRequests,
  listAssignableWarehouses,
  listRolePermissions,
  listRoles,
  replaceUserRoles,
  resetUserPassword,
  reviewPermissionRequest,
  revokePermissionRequest,
  reviewRolePackage,
  submitRolePackageReview,
  updateUser,
  updateRolePackageDraft,
} from './iam';

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(status === 204 ? null : JSON.stringify(body), {
    headers: { 'Content-Type': 'application/json' },
    status,
  });
}

describe('IAM API contracts', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    vi.spyOn(globalThis, 'fetch').mockImplementation(() => Promise.resolve(jsonResponse({})));
  });

  it('creates an account with role IDs and an initial password', async () => {
    await createUser({
      username: 'warehouse.operator', password: 'TempPass2026', displayName: 'Warehouse Operator',
      roleIds: [3], enabled: true, remark: 'Night shift',
    });

    expect(fetch).toHaveBeenCalledWith('/api/users', expect.objectContaining({ method: 'POST' }));
    expect(JSON.parse(String(vi.mocked(fetch).mock.calls[0]?.[1]?.body))).toEqual(expect.objectContaining({
      username: 'warehouse.operator', roleIds: [3], enabled: true,
    }));
  });

  it('keeps profile and role replacement as explicit commands', async () => {
    await updateUser(8, { displayName: 'Updated User', enabled: false, defaultRoleId: 4 });
    await replaceUserRoles(8, [4, 5], 5);

    expect(fetch).toHaveBeenNthCalledWith(1, '/api/users/8', expect.objectContaining({ method: 'PUT' }));
    expect(fetch).toHaveBeenNthCalledWith(2, '/api/users/8/roles', expect.objectContaining({ method: 'POST' }));
    expect(JSON.parse(String(vi.mocked(fetch).mock.calls[1]?.[1]?.body))).toEqual({
      roleIds: [4, 5], defaultRoleId: 5, warehouseIds: [],
    });
  });

  it('loads assignable warehouses and sends warehouse scope with role replacement', async () => {
    vi.mocked(fetch).mockResolvedValueOnce(jsonResponse([]));
    await listAssignableWarehouses();
    await replaceUserRoles(8, [6], 6, [1, 3]);

    expect(fetch).toHaveBeenNthCalledWith(1, '/api/users/assignable-warehouses', expect.any(Object));
    expect(JSON.parse(String(vi.mocked(fetch).mock.calls[1]?.[1]?.body))).toEqual({
      roleIds: [6], defaultRoleId: 6, warehouseIds: [1, 3],
    });
  });

  it('uses dedicated permission request, review, and immediate revocation endpoints', async () => {
    vi.mocked(fetch).mockImplementation(() => Promise.resolve(jsonResponse({})));

    await listPermissionRequests({ status: 'PENDING_REVIEW', targetUserId: 8, page: 1, size: 10 });
    await createPermissionRequest({ targetUserId: 8, requestedRoleId: 4, warehouseIds: [48], requestReason: 'Night shift' });
    await reviewPermissionRequest(12, true, 'Approved schedule');
    await revokePermissionRequest(12, 'Shift ended');

    expect(fetch).toHaveBeenNthCalledWith(1, '/api/permission-requests?status=PENDING_REVIEW&targetUserId=8&page=1&size=10', expect.any(Object));
    expect(fetch).toHaveBeenNthCalledWith(2, '/api/permission-requests', expect.objectContaining({ method: 'POST' }));
    expect(JSON.parse(String(vi.mocked(fetch).mock.calls[1]?.[1]?.body))).toEqual({
      targetUserId: 8, requestedRoleId: 4, warehouseIds: [48], requestReason: 'Night shift',
    });
    expect(fetch).toHaveBeenNthCalledWith(3, '/api/permission-requests/12/review', expect.objectContaining({ method: 'POST' }));
    expect(JSON.parse(String(vi.mocked(fetch).mock.calls[2]?.[1]?.body))).toEqual({ approved: true, comment: 'Approved schedule' });
    expect(fetch).toHaveBeenNthCalledWith(4, '/api/permission-requests/12/revoke', expect.objectContaining({ method: 'POST' }));
    expect(JSON.parse(String(vi.mocked(fetch).mock.calls[3]?.[1]?.body))).toEqual({ comment: 'Shift ended' });
  });

  it('uses dedicated password reset and logical deletion endpoints', async () => {
    vi.mocked(fetch)
      .mockResolvedValueOnce(jsonResponse({ temporaryPassword: 'TempPass2026' }))
      .mockResolvedValueOnce(jsonResponse(undefined, 204));

    await resetUserPassword(8);
    await deleteUser(8);

    expect(fetch).toHaveBeenNthCalledWith(1, '/api/users/8/reset-password', expect.objectContaining({ method: 'POST' }));
    expect(fetch).toHaveBeenNthCalledWith(2, '/api/users/8', expect.objectContaining({ method: 'DELETE' }));
  });

  it('queries role and permission catalogues without mutation', async () => {
    vi.mocked(fetch).mockImplementation(() => Promise.resolve(jsonResponse([])));
    await listRoles(true);
    await listPermissions({ activeOnly: true, type: 'API' });
    await listRolePermissions(3);

    expect(fetch).toHaveBeenNthCalledWith(1, '/api/roles?activeOnly=true', expect.any(Object));
    expect(fetch).toHaveBeenNthCalledWith(2, '/api/permissions?activeOnly=true&type=API', expect.any(Object));
    expect(fetch).toHaveBeenNthCalledWith(3, '/api/roles/3/permissions', expect.any(Object));
  });

  it('previews and submits an independent role permission snapshot', async () => {
    vi.mocked(fetch)
      .mockResolvedValueOnce(jsonResponse({ sourceRoleId: 3, snapshotFingerprint: 'a'.repeat(64) }))
      .mockResolvedValueOnce(jsonResponse({ role: { id: 9, roleCode: 'NIGHT_SHIFT' }, auditId: 12 }));

    await getRoleCopyPreview(3);
    await copyRole(3, {
      roleCode: 'NIGHT_SHIFT',
      roleName: 'Night shift',
      description: 'Warehouse night-shift operating package',
      snapshotFingerprint: 'a'.repeat(64),
      riskAcknowledged: true,
      confirmationCode: 'NIGHT_SHIFT',
      operationReason: 'Night shift requires mobile receiving and picking.',
    });

    expect(fetch).toHaveBeenNthCalledWith(1, '/api/roles/3/copy-preview', expect.any(Object));
    expect(fetch).toHaveBeenNthCalledWith(2, '/api/roles/3/copies', expect.objectContaining({ method: 'POST' }));
    expect(JSON.parse(String(vi.mocked(fetch).mock.calls[1]?.[1]?.body))).toEqual(expect.objectContaining({
      roleCode: 'NIGHT_SHIFT', snapshotFingerprint: 'a'.repeat(64), riskAcknowledged: true,
    }));
  });

  it('creates a blank permission-package draft from checked permissions', async () => {
    vi.mocked(fetch).mockResolvedValueOnce(jsonResponse({ action: 'CREATE_DRAFT', permissionCount: 2 }));

    await createRolePackageDraft({
      roleCode: 'CUSTOM_SALES',
      roleName: 'Custom sales',
      description: 'Sales order entry',
      permissionIds: [11, 12],
    });

    expect(fetch).toHaveBeenCalledWith('/api/roles/drafts', expect.objectContaining({ method: 'POST' }));
    expect(JSON.parse(String(vi.mocked(fetch).mock.calls[0]?.[1]?.body))).toEqual({
      roleCode: 'CUSTOM_SALES',
      roleName: 'Custom sales',
      description: 'Sales order entry',
      permissionIds: [11, 12],
    });
  });

  it('uses explicit commands for the permission-package approval lifecycle', async () => {
    vi.mocked(fetch).mockImplementation(() => Promise.resolve(jsonResponse({ action: 'UPDATE_DRAFT' })));

    await updateRolePackageDraft(9, {
      roleName: 'Warehouse v2',
      description: 'Warehouse purpose',
      permissionIds: [11, 12],
      reason: 'Controlled update',
    });
    await submitRolePackageReview(9, { reason: 'High-risk review' });
    await reviewRolePackage(9, { approved: true, comment: 'Approved' });
    await activateRolePackage(9, { reason: 'Release', confirmationCode: 'WAREHOUSE_V2' });
    await deactivateRolePackage(9, { reason: 'Retire', confirmationCode: 'WAREHOUSE_V2' });
    await getRoleGovernanceHistory(9);

    expect(fetch).toHaveBeenNthCalledWith(1, '/api/roles/9/draft', expect.objectContaining({ method: 'PUT' }));
    expect(fetch).toHaveBeenNthCalledWith(2, '/api/roles/9/submit-review', expect.objectContaining({ method: 'POST' }));
    expect(fetch).toHaveBeenNthCalledWith(3, '/api/roles/9/review', expect.objectContaining({ method: 'POST' }));
    expect(fetch).toHaveBeenNthCalledWith(4, '/api/roles/9/activate', expect.objectContaining({ method: 'POST' }));
    expect(fetch).toHaveBeenNthCalledWith(5, '/api/roles/9/deactivate', expect.objectContaining({ method: 'POST' }));
    expect(fetch).toHaveBeenNthCalledWith(6, '/api/roles/9/governance-history', expect.any(Object));
  });
});
