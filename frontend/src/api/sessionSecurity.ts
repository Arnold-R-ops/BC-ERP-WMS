import { apiRequest } from './http';

export type SessionSecurityAuditAction =
  | 'SELF_REVOKE_ALL_SESSIONS'
  | 'ADMIN_REVOKE_ALL_SESSIONS';

export interface SessionSecurityAudit {
  id: number;
  action: SessionSecurityAuditAction;
  operatorId: number;
  operatorUsername: string;
  targetUserId: number;
  targetUsername: string;
  reason: string;
  result: 'SUCCESS' | 'FAILED';
  createdAt: string;
}

export const SESSION_SECURITY_AUDITS_QUERY_KEY = ['session-security', 'audits'] as const;

export function listSessionSecurityAudits(limit = 100): Promise<SessionSecurityAudit[]> {
  return apiRequest<SessionSecurityAudit[]>(`/api/session-security/audits?limit=${limit}`);
}
