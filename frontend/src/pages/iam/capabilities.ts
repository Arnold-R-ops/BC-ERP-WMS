export interface IamCapabilities {
  canManage: boolean;
  canManageProtectedIdentities: boolean;
  canApproveHighRiskPackages: boolean;
}

export function getIamCapabilities(role: string): IamCapabilities {
  return {
    canManage: role === 'SUPER_ADMIN' || role === 'SECURITY_ADMIN',
    canManageProtectedIdentities: role === 'SUPER_ADMIN',
    canApproveHighRiskPackages: role === 'SUPER_ADMIN',
  };
}
