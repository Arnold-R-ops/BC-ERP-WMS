import { beforeEach, describe, expect, it, vi } from 'vitest';
import {
  createUser,
  deleteUser,
  listPermissions,
  listRolePermissions,
  listRoles,
  replaceUserRoles,
  resetUserPassword,
  updateUser,
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
    expect(JSON.parse(String(vi.mocked(fetch).mock.calls[1]?.[1]?.body))).toEqual({ roleIds: [4, 5], defaultRoleId: 5 });
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
});
