export interface IamCapabilities {
  canManage: boolean;
}

export function getIamCapabilities(role: string): IamCapabilities {
  return { canManage: role === 'SUPER_ADMIN' };
}
