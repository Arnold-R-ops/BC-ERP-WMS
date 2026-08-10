import type { Role, UserAccount } from '../../api/iam';

export function isCurrentUser(user: UserAccount, username?: string): boolean {
  return Boolean(username && user.username === username);
}

export function orderRoleIds(roleIds: number[], defaultRoleId?: number): number[] {
  const uniqueIds = [...new Set(roleIds)];
  if (defaultRoleId === undefined || !uniqueIds.includes(defaultRoleId)) return uniqueIds;
  return [defaultRoleId, ...uniqueIds.filter((id) => id !== defaultRoleId)];
}

export function findDefaultRoleId(user: UserAccount, roles: Role[]): number | undefined {
  return roles.find((role) => role.roleCode === user.defaultRoleCode)?.id;
}

export function roleLabel(role: Role): string {
  const name = role.roleName?.trim();
  const code = role.roleCode?.trim();
  if (name && code && name !== code) return `${name} (${code})`;
  return name || code || `#${role.id ?? '-'}`;
}

export function isRoleAssignable(role: Role): boolean {
  return role.status === 'ACTIVE'
    && (role.roleType !== 'CUSTOM' || role.reviewStatus === 'APPROVED');
}
