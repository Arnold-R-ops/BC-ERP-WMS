export interface IamCapabilities {
  canManage: boolean;
  canManageProtectedIdentities: boolean;
  canApproveHighRiskPackages: boolean;
}

export function getIamCapabilities(role: string): IamCapabilities {
  return {
    canManage: role === 'TENANT_ADMIN' || role === 'SECURITY_ADMIN',
    canManageProtectedIdentities: role === 'TENANT_ADMIN',
    canApproveHighRiskPackages: role === 'TENANT_ADMIN',
  };
}
