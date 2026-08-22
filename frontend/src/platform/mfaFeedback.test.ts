import { describe, expect, it } from 'vitest';
import { PlatformMfaError } from './api';
import { platformMfaFailureMessage, shouldRestartPlatformMfa } from './mfaFeedback';

describe('platform MFA feedback', () => {
  it('allows another authenticator attempt after a mismatched code', () => {
    const error = new PlatformMfaError('code', 4);

    expect(platformMfaFailureMessage(error, false)).toBe('验证码不匹配，临时锁定前还可尝试 4 次。');
    expect(shouldRestartPlatformMfa(error)).toBe(false);
  });

  it('requires a fresh sign-in only when the verification step expired', () => {
    const error = new PlatformMfaError('expired');

    expect(platformMfaFailureMessage(error, false)).toBe('本次验证步骤已过期，请重新登录后再验证。');
    expect(shouldRestartPlatformMfa(error)).toBe(true);
  });

  it('states the temporary lock duration separately', () => {
    const error = new PlatformMfaError('locked', 0, 900);

    expect(platformMfaFailureMessage(error, false)).toBe('失败次数已达上限，账号将临时锁定约 15 分钟。');
    expect(shouldRestartPlatformMfa(error)).toBe(true);
  });
});
