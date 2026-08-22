import { PlatformMfaError } from './api';

export function platformMfaFailureMessage(error: unknown, english: boolean): string {
  if (!(error instanceof PlatformMfaError)) {
    return english ? 'Unable to verify the code. Please try again.' : '验证码校验失败，请重试。';
  }
  if (error.reason === 'code') {
    const remaining = error.remainingAttempts;
    if (remaining !== undefined) {
      return english
        ? `The code does not match. ${remaining} attempt${remaining === 1 ? '' : 's'} remaining before a temporary lock.`
        : `验证码不匹配，临时锁定前还可尝试 ${remaining} 次。`;
    }
    return english ? 'The code does not match. Wait for a new code and try again.' : '验证码不匹配，请等待认证器生成新验证码后重试。';
  }
  if (error.reason === 'expired') {
    return english ? 'This verification step has expired. Sign in again to start a new one.' : '本次验证步骤已过期，请重新登录后再验证。';
  }
  if (error.reason === 'locked') {
    const minutes = error.retryAfterSeconds ? Math.ceil(error.retryAfterSeconds / 60) : 15;
    return english
      ? `Too many failed attempts. This account is temporarily locked for about ${minutes} minutes.`
      : `失败次数已达上限，账号将临时锁定约 ${minutes} 分钟。`;
  }
  if (error.reason === 'challenge') {
    return english ? 'This verification step is no longer valid. Sign in again.' : '本次验证步骤已失效，请重新登录。';
  }
  if (error.reason === 'service') {
    return english ? 'Unable to connect to the platform service. Please try again shortly.' : '暂时无法连接平台服务，请稍后重试。';
  }
  return english ? 'The code could not be verified. Please try again.' : '验证码未通过校验，请重试。';
}

export function shouldRestartPlatformMfa(error: unknown): boolean {
  return error instanceof PlatformMfaError
    && ['expired', 'locked', 'challenge'].includes(error.reason);
}
