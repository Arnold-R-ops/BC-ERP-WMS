import { readPlatformSession } from './storage';

export const PLATFORM_AUTH_UNAUTHORIZED_EVENT = 'wms:platform-auth:unauthorized';

export interface PlatformLoginRequest {
  email: string;
  password: string;
}

export type PlatformTenantStatus = 'PROVISIONING' | 'ACTIVE' | 'SUSPENDED' | 'CLOSED' | 'PURGE_PENDING' | 'PURGED';

export interface PlatformTenant {
  id: number;
  tenantCode: string;
  displayName: string;
  slug: string;
  status: PlatformTenantStatus;
  closedAt?: string | null;
  purgeDueAt?: string | null;
  createdAt: string;
}

export interface PlatformTenantPage {
  content: PlatformTenant[];
  number: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface PlatformDataPage {
  resource: string;
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  content: Record<string, unknown>[];
}

export interface PlatformAuditLog {
  id: number;
  createdAt: string;
  actorEmail: string;
  targetTenantId: number | null;
  targetTenantName: string;
  action: string;
  resourceType: string | null;
  result: string;
  summary: Record<string, string | number | boolean | null>;
}

export interface PlatformAuditLogPage {
  content: PlatformAuditLog[];
  number: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

interface PlatformErrorBody {
  errorKey?: string;
  params?: {
    reason?: string;
    mfaReason?: string;
    remainingAttempts?: number;
    retryAfterSeconds?: number;
  };
}

export class PlatformInvitationError extends Error {
  constructor(public readonly reason: 'invalid' | 'password' | 'service') {
    super(`PLATFORM_INVITATION_${reason.toUpperCase()}`);
    this.name = 'PlatformInvitationError';
  }
}

export class PlatformRequestError extends Error {
  constructor(public readonly reason: 'reauthentication' | 'request') {
    super(`PLATFORM_REQUEST_${reason.toUpperCase()}`);
    this.name = 'PlatformRequestError';
  }
}

async function isReauthenticationFailure(response: Response): Promise<boolean> {
  if (response.status !== 401) return false;
  try {
    const body = await response.clone().json() as PlatformErrorBody;
    return body.errorKey === 'AUTH_FAILED' && body.params?.reason === 'MFA_VERIFICATION_FAILED';
  } catch {
    return false;
  }
}

async function platformResponse(path: string, init?: RequestInit): Promise<Response> {
  const session = readPlatformSession();
  if (!session) {
    window.dispatchEvent(new CustomEvent(PLATFORM_AUTH_UNAUTHORIZED_EVENT));
    throw new Error('PLATFORM_SESSION_REQUIRED');
  }

  const response = await fetch(path, {
    ...init,
    headers: {
      ...(init?.headers as Record<string, string> | undefined),
      Authorization: `${session.tokenType} ${session.token}`,
    },
  });
  const reauthenticationFailure = await isReauthenticationFailure(response);
  if (response.status === 401 && !reauthenticationFailure) {
    window.dispatchEvent(new CustomEvent(PLATFORM_AUTH_UNAUTHORIZED_EVENT));
  }
  if (!response.ok) throw new PlatformRequestError(reauthenticationFailure ? 'reauthentication' : 'request');
  return response;
}

async function platformRequest<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await platformResponse(path, init);
  return response.json() as Promise<T>;
}

export interface PlatformAuthResponse {
  status: 'MFA_ENROLLMENT_REQUIRED' | 'MFA_REQUIRED' | 'AUTHENTICATED';
  challengeToken?: string | null;
  enrollmentSecret?: string | null;
  otpauthUri?: string | null;
  token?: string | null;
  tokenType?: string | null;
  email: string;
  roles: string[];
  expiresIn: number;
  recoveryCodes?: string[] | null;
}

export interface PlatformExportJob {
  id: string;
  companyId: number;
  status: 'PENDING' | 'RUNNING' | 'COMPLETED' | 'FAILED' | 'EXPIRED';
  resource: string;
  recordCount: number | null;
  sha256: string | null;
  expiresAt: string;
  createdAt: string;
  completedAt: string | null;
}
export interface PlatformManagedUser { id: number; email: string; displayName: string; enabled: boolean; superAdmin: boolean; mfaEnabled: boolean; }
export type PlatformAdminRoleType = 'JOB_ROLE' | 'TECHNICAL_CAPABILITY' | 'UNCLASSIFIED';
export type PlatformAdminMfaStatus = 'NOT_ENROLLED' | 'ENROLLED' | 'TEMPORARILY_LOCKED';
export interface PlatformAdminRole {
  code: string;
  name: string;
  type: PlatformAdminRoleType;
}
export interface PlatformAdminSummary {
  id: number;
  displayName: string;
  email: string;
  enabled: boolean;
  roles: PlatformAdminRole[];
  mfaStatus: PlatformAdminMfaStatus;
  mfaEnrolledAt: string | null;
  mfaLockedUntil: string | null;
  createdAt: string;
  updatedAt: string;
  activeGrantCount: number;
  currentUser: boolean;
  lastEnabledSuperAdmin: boolean;
}
export interface PlatformAdminDetail extends PlatformAdminSummary {
  activeGrantCounts: { READ: number; EXPORT: number };
}
export interface PlatformAdminPage {
  content: PlatformAdminSummary[];
  number: number;
  size: number;
  totalElements: number;
  totalPages: number;
}
export interface PlatformAdminFilters {
  keyword?: string;
  enabled?: boolean;
  role?: string;
  mfaStatus?: PlatformAdminMfaStatus;
}
export interface PlatformAccessGrant { id: number; platformUserId: number; platformUserEmail: string; capability: 'READ' | 'EXPORT'; tenantId: number; tenantName: string; datasetCode: string; effectiveFrom: string; expiresAt: string; revokedAt: string | null; }
export interface PlatformAccessGrantRequest { platformUserId: number; capabilities: ('READ' | 'EXPORT')[]; tenantIds: number[]; datasets: string[]; effectiveFrom?: string; expiresAt?: string; }
export interface PlatformEffectiveAccessScope { tenantId: number; datasetCode: string; read: boolean; export: boolean; }
export interface PlatformEffectiveAccess {
  superAdmin: boolean;
  tenantDirectoryScope: 'ALL' | 'GRANTED' | 'NONE';
  scopes: PlatformEffectiveAccessScope[];
}
export interface PlatformCurrentSession { email: string; roles: string[]; mfaEnabled: boolean; expiresAt: string; }
export interface PlatformReauthenticationChallenge { challengeToken: string; expiresIn: number; }
export interface PlatformAdminSecurityChallenge extends PlatformReauthenticationChallenge { targetSecurityVersion: number; }
export interface PlatformAdminSecurityMutation {
  targetUserId: number;
  action: 'ADMIN_SESSIONS_REVOKED' | 'MFA_RESET';
  changed: boolean;
  enabled: boolean;
  mfaStatus: PlatformAdminMfaStatus;
  roles: string[];
  activeGrantCount: number;
  securityVersion: number;
  completedAt: string;
}
export interface PlatformAdminStatusMutation {
  targetUserId: number;
  action: 'ADMIN_ENABLED' | 'ADMIN_DISABLED';
  changed: boolean;
  enabled: boolean;
  roles: string[];
  activeGrantCount: number;
  securityVersion: number;
  completedAt: string;
}
export interface PlatformAdminRoleChangeMutation {
  targetUserId: number;
  changed: boolean;
  enabled: boolean;
  beforeRoles: string[];
  afterRoles: string[];
  activeGrantCount: number;
  securityVersion: number;
  completedAt: string;
}
export interface PlatformRecoveryCodeStatus { remaining: number; }
export interface PlatformRecoveryCodes { recoveryCodes: string[]; remaining: number; }
export interface PlatformAdminInvitation {
  id: number;
  email: string;
  displayName: string;
  invitationType: 'SUPER_ADMIN' | 'ORDINARY_ADMIN';
  roleCode: 'PLATFORM_SUPER_ADMIN' | 'PLATFORM_OPERATIONS_ADMIN' | 'PLATFORM_SECURITY_AUDITOR';
  status: 'ACTIVE' | 'ACCEPTED' | 'REVOKED' | 'EXPIRED';
  createdAt: string;
  expiresAt: string;
  acceptedAt: string | null;
  revokedAt: string | null;
  activationPath: string | null;
}
export interface PlatformInvitationStatus {
  email: string;
  displayName: string;
  invitationType: 'SUPER_ADMIN' | 'ORDINARY_ADMIN';
  roleCode: 'PLATFORM_SUPER_ADMIN' | 'PLATFORM_OPERATIONS_ADMIN' | 'PLATFORM_SECURITY_AUDITOR';
  expiresAt: string;
}

export class PlatformLoginError extends Error {
  constructor(public readonly reason: 'credentials' | 'service') {
    super(`PLATFORM_LOGIN_${reason.toUpperCase()}`);
    this.name = 'PlatformLoginError';
  }
}

export class PlatformMfaError extends Error {
  constructor(
    public readonly reason: 'code' | 'expired' | 'locked' | 'challenge' | 'verification' | 'service',
    public readonly remainingAttempts?: number,
    public readonly retryAfterSeconds?: number,
  ) {
    super(`PLATFORM_MFA_${reason.toUpperCase()}`);
    this.name = 'PlatformMfaError';
  }
}

async function platformAuthRequest(path: string, request: object, mfaRequest = false): Promise<PlatformAuthResponse> {
  let response: Response;
  try {
    response = await fetch(path, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(request),
    });
  } catch {
    if (mfaRequest) throw new PlatformMfaError('service');
    throw new PlatformLoginError('service');
  }

  if (!response.ok) {
    if (mfaRequest) {
      let errorBody: PlatformErrorBody | undefined;
      try {
        errorBody = await response.clone().json() as PlatformErrorBody;
      } catch {
        // Keep a service-level failure when an unavailable proxy returns non-JSON.
      }
      const mfaReason = errorBody?.params?.mfaReason;
      const reason = mfaReason === 'CODE_INVALID' ? 'code'
        : mfaReason === 'CHALLENGE_EXPIRED' ? 'expired'
          : mfaReason === 'TEMPORARILY_LOCKED' ? 'locked'
            : mfaReason === 'CHALLENGE_INVALID' ? 'challenge'
              : errorBody?.errorKey === 'AUTH_FAILED' ? 'verification'
                : 'service';
      throw new PlatformMfaError(reason, errorBody?.params?.remainingAttempts, errorBody?.params?.retryAfterSeconds);
    }
    throw new PlatformLoginError(response.status === 401 || response.status === 403 ? 'credentials' : 'service');
  }

  const body = await response.json() as PlatformAuthResponse;
  const validChallenge = ['MFA_ENROLLMENT_REQUIRED', 'MFA_REQUIRED'].includes(body.status)
    && Boolean(body.challengeToken);
  const validAuthentication = body.status === 'AUTHENTICATED'
    && Boolean(body.token) && Boolean(body.expiresIn);
  if (!body.email || !body.roles?.length || (!validChallenge && !validAuthentication)) {
    throw new PlatformLoginError('service');
  }
  return body;
}

export function platformLogin(request: PlatformLoginRequest): Promise<PlatformAuthResponse> {
  return platformAuthRequest('/api/platform/auth/login', request);
}

export function confirmPlatformMfaEnrollment(challengeToken: string, code: string): Promise<PlatformAuthResponse> {
  return platformAuthRequest('/api/platform/auth/mfa/enroll/confirm', { challengeToken, code }, true);
}

export function verifyPlatformMfa(challengeToken: string, code?: string, recoveryCode?: string): Promise<PlatformAuthResponse> {
  return platformAuthRequest('/api/platform/auth/mfa/verify', { challengeToken, code, recoveryCode }, true);
}

export function getPlatformCurrentSession(): Promise<PlatformCurrentSession> {
  return platformRequest('/api/platform/auth/me');
}

export function startPlatformLogoutAll(): Promise<PlatformReauthenticationChallenge> {
  return platformRequest('/api/platform/auth/logout-all/challenge', { method: 'POST' });
}

export function confirmPlatformLogoutAll(challengeToken: string, code: string): Promise<void> {
  return platformResponse('/api/platform/auth/logout-all', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ challengeToken, code }),
  }).then(() => undefined);
}

export function getPlatformRecoveryCodeStatus(): Promise<PlatformRecoveryCodeStatus> {
  return platformRequest('/api/platform/auth/recovery-codes/status');
}

export function startPlatformRecoveryCodeRegeneration(): Promise<PlatformReauthenticationChallenge> {
  return platformRequest('/api/platform/auth/recovery-codes/challenge', { method: 'POST' });
}

export function regeneratePlatformRecoveryCodes(
  challengeToken: string,
  password: string,
  code: string,
): Promise<PlatformRecoveryCodes> {
  return platformRequest('/api/platform/auth/recovery-codes/regenerate', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ challengeToken, password, code }),
  });
}

export function listPlatformManagedUsers(): Promise<PlatformManagedUser[]> { return platformRequest('/api/platform/access-grants/users'); }
export function listPlatformAdmins(
  page = 0,
  size = 20,
  filters: PlatformAdminFilters = {},
): Promise<PlatformAdminPage> {
  const query = new URLSearchParams({ page: String(page), size: String(size) });
  const keyword = filters.keyword?.trim();
  if (keyword) query.set('keyword', keyword);
  if (filters.enabled !== undefined) query.set('enabled', String(filters.enabled));
  if (filters.role) query.set('role', filters.role);
  if (filters.mfaStatus) query.set('mfaStatus', filters.mfaStatus);
  return platformRequest<PlatformAdminPage>(`/api/platform/admins?${query.toString()}`);
}
export function getPlatformAdmin(targetUserId: number): Promise<PlatformAdminDetail> {
  return platformRequest<PlatformAdminDetail>(`/api/platform/admins/${targetUserId}`);
}
export function getMyPlatformEffectiveAccess(): Promise<PlatformEffectiveAccess> { return platformRequest('/api/platform/access-grants/me'); }
export function listPlatformAccessGrants(platformUserId: number): Promise<PlatformAccessGrant[]> { return platformRequest(`/api/platform/access-grants?platformUserId=${platformUserId}`); }
export function createPlatformAccessGrants(body: PlatformAccessGrantRequest): Promise<PlatformAccessGrant[]> { return platformRequest('/api/platform/access-grants', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) }); }
export function revokePlatformAccessGrant(id: number): Promise<void> { return platformResponse(`/api/platform/access-grants/${id}`, { method: 'DELETE' }).then(() => undefined); }

export function startPlatformAdminSessionRevoke(targetUserId: number): Promise<PlatformAdminSecurityChallenge> {
  return platformRequest(`/api/platform/admins/${targetUserId}/sessions/revoke/challenge`, { method: 'POST' });
}

export function revokePlatformAdminSessions(
  targetUserId: number,
  challengeToken: string,
  password: string,
  code: string,
  reason: string,
  idempotencyKey: string,
): Promise<PlatformAdminSecurityMutation> {
  return platformRequest(`/api/platform/admins/${targetUserId}/sessions/revoke`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', 'Idempotency-Key': idempotencyKey },
    body: JSON.stringify({ challengeToken, password, code, reason }),
  });
}

export function startPlatformAdminMfaReset(targetUserId: number): Promise<PlatformAdminSecurityChallenge> {
  return platformRequest(`/api/platform/admins/${targetUserId}/mfa-reset/challenge`, { method: 'POST' });
}

export function resetPlatformAdminMfa(
  targetUserId: number,
  challengeToken: string,
  password: string,
  code: string,
  reason: string,
  idempotencyKey: string,
): Promise<PlatformAdminSecurityMutation> {
  return platformRequest(`/api/platform/admins/${targetUserId}/mfa-reset`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', 'Idempotency-Key': idempotencyKey },
    body: JSON.stringify({ challengeToken, password, code, reason }),
  });
}

export function startPlatformAdminStatusChange(
  targetUserId: number,
  desiredEnabled: boolean,
): Promise<PlatformAdminSecurityChallenge> {
  return platformRequest(`/api/platform/admins/${targetUserId}/status-change/challenge`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ desiredEnabled }),
  });
}

export function changePlatformAdminStatus(
  targetUserId: number,
  desiredEnabled: boolean,
  challengeToken: string,
  password: string,
  code: string,
  reason: string,
  idempotencyKey: string,
): Promise<PlatformAdminStatusMutation> {
  return platformRequest(`/api/platform/admins/${targetUserId}/${desiredEnabled ? 'enable' : 'disable'}`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', 'Idempotency-Key': idempotencyKey },
    body: JSON.stringify({ challengeToken, password, code, reason }),
  });
}

export function startPlatformAdminRoleChange(
  targetUserId: number,
  jobRoleCode: 'PLATFORM_OPERATIONS_ADMIN' | 'PLATFORM_SECURITY_AUDITOR',
): Promise<PlatformAdminSecurityChallenge> {
  return platformRequest(`/api/platform/admins/${targetUserId}/roles/change/challenge`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ jobRoleCode }),
  });
}

export function changePlatformAdminRole(
  targetUserId: number,
  jobRoleCode: 'PLATFORM_OPERATIONS_ADMIN' | 'PLATFORM_SECURITY_AUDITOR',
  challengeToken: string,
  password: string,
  code: string,
  reason: string,
  idempotencyKey: string,
): Promise<PlatformAdminRoleChangeMutation> {
  return platformRequest(`/api/platform/admins/${targetUserId}/roles`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json', 'Idempotency-Key': idempotencyKey },
    body: JSON.stringify({ jobRoleCode, challengeToken, password, code, reason }),
  });
}

export function startPlatformAdminInvitation(): Promise<PlatformReauthenticationChallenge> {
  return platformRequest('/api/platform/admins/invitations/challenge', { method: 'POST' });
}

export function createPlatformAdminInvitation(body: {
  challengeToken: string;
  password: string;
  code: string;
  email: string;
  displayName: string;
  reason: string;
  invitationType: 'SUPER_ADMIN' | 'ORDINARY_ADMIN';
  roleCode: 'PLATFORM_SUPER_ADMIN' | 'PLATFORM_OPERATIONS_ADMIN' | 'PLATFORM_SECURITY_AUDITOR';
}): Promise<PlatformAdminInvitation> {
  return platformRequest('/api/platform/admins/invitations', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  });
}

export function listPlatformAdminInvitations(): Promise<PlatformAdminInvitation[]> {
  return platformRequest('/api/platform/admins/invitations');
}

export function revokePlatformAdminInvitation(id: number, reason: string): Promise<void> {
  return platformResponse(`/api/platform/admins/invitations/${id}/revoke`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ reason }),
  }).then(() => undefined);
}

async function publicInvitationRequest<T>(path: string, body: object): Promise<T> {
  let response: Response;
  try {
    response = await fetch(path, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body),
    });
  } catch {
    throw new PlatformInvitationError('service');
  }
  if (!response.ok) {
    let errorBody: PlatformErrorBody | undefined;
    try {
      errorBody = await response.clone().json() as PlatformErrorBody;
    } catch {
      // A proxy or unavailable service may return a non-JSON error page.
    }
    if (errorBody?.errorKey === 'AUTH_FAILED'
      && errorBody.params?.reason === 'PLATFORM_INVITATION_INVALID') {
      throw new PlatformInvitationError('invalid');
    }
    if (errorBody?.errorKey === 'PASSWORD_TOO_WEAK') {
      throw new PlatformInvitationError('password');
    }
    throw new PlatformInvitationError('service');
  }
  return response.json() as Promise<T>;
}

export function getPlatformInvitationStatus(token: string): Promise<PlatformInvitationStatus> {
  return publicInvitationRequest('/api/platform/auth/invitations/status', { token });
}

export function activatePlatformInvitation(token: string, password: string): Promise<PlatformAuthResponse> {
  return publicInvitationRequest('/api/platform/auth/invitations/activate', { token, password });
}

export function confirmPlatformInvitationActivation(
  challengeToken: string,
  code: string,
): Promise<PlatformAuthResponse> {
  return platformAuthRequest(
    '/api/platform/auth/invitations/activate/confirm',
    { challengeToken, code },
    true,
  );
}

export function listPlatformTenants(
  page: number,
  size: number,
  filters: { keyword?: string; status?: PlatformTenantStatus } = {},
): Promise<PlatformTenantPage> {
  const query = new URLSearchParams({ page: String(page), size: String(size) });
  const keyword = filters.keyword?.trim();
  if (keyword) query.set('keyword', keyword);
  if (filters.status) query.set('status', filters.status);
  return platformRequest<PlatformTenantPage>(`/api/platform/companies?${query.toString()}`);
}

export function getPlatformTenant(tenantId: number): Promise<PlatformTenant> {
  return platformRequest<PlatformTenant>(`/api/platform/companies/${tenantId}`);
}

export function readPlatformTenantData(
  tenantId: number,
  resource: string,
  page: number,
  size: number,
): Promise<PlatformDataPage> {
  return platformRequest<PlatformDataPage>(
    `/api/platform/companies/${tenantId}/data/${encodeURIComponent(resource)}?page=${page}&size=${size}`,
  );
}

export function searchPlatformAuditLogs(filters: {
  from: string;
  to: string;
  tenantId?: number;
  action?: string;
  page: number;
  size: number;
}): Promise<PlatformAuditLogPage> {
  const query = new URLSearchParams({
    from: filters.from,
    to: filters.to,
    page: String(filters.page),
    size: String(filters.size),
  });
  if (filters.tenantId !== undefined) query.set('tenantId', String(filters.tenantId));
  if (filters.action) query.set('action', filters.action);
  return platformRequest<PlatformAuditLogPage>(`/api/platform/audit-logs?${query.toString()}`);
}

export function createPlatformExport(tenantId: number, resource: string): Promise<PlatformExportJob> {
  return platformRequest<PlatformExportJob>(`/api/platform/companies/${tenantId}/exports`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ resource }),
  });
}

export function getPlatformExport(tenantId: number, jobId: string): Promise<PlatformExportJob> {
  return platformRequest<PlatformExportJob>(`/api/platform/companies/${tenantId}/exports/${encodeURIComponent(jobId)}`);
}

export async function downloadPlatformExport(tenantId: number, jobId: string): Promise<Blob> {
  const response = await platformResponse(
    `/api/platform/companies/${tenantId}/exports/${encodeURIComponent(jobId)}/download`,
  );
  return response.blob();
}
