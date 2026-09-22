import { describe, expect, it } from 'vitest';
import { isPlatformActivity, PlatformActivityClock } from './sessionActivity';

const windowAt = (idle: number, absolute = 43_200_000) => ({
  serverNow: new Date(0).toISOString(), idleExpiresAt: new Date(idle).toISOString(), expiresAt: new Date(absolute).toISOString(),
});
describe('platform activity scheduling (server remains authoritative)', () => {
  it.each(['click', 'keydown', 'wheel', 'touchstart', 'touchmove'])('counts trusted visible %s', type => {
    expect(isPlatformActivity({ type, isTrusted: true }, true)).toBe(true);
    expect(isPlatformActivity({ type, isTrusted: false }, true)).toBe(false);
    expect(isPlatformActivity({ type, isTrusted: true }, false)).toBe(false);
  });
  it.each(['mousemove', 'scroll', 'load', 'focus', 'visibilitychange'])('does not count %s', type => {
    expect(isPlatformActivity({ type, isTrusted: true }, true)).toBe(false);
  });
  it('reload and polling do not start a new thirty-minute window', () => {
    const clock = new PlatformActivityClock(); clock.accept(windowAt(5000), 100);
    expect(clock.due(5099)).toBeNull(); expect(clock.due(5100)).toBe('check');
  });
  it('throttles real activity, with no periodic keepalive after the last event', () => {
    const clock = new PlatformActivityClock(); clock.accept(windowAt(1_800_000), 0);
    clock.activity(); expect(clock.due(100)).toBe('activity'); clock.sending(100);
    expect(clock.due(20000)).toBeNull(); clock.activity(); expect(clock.due(20000)).toBe('activity');
  });
  it('never renews locally before server acknowledgement and checks expiry before activity', () => {
    const clock = new PlatformActivityClock(); clock.accept(windowAt(1000), 0);
    clock.activity(); clock.sending(500); expect(clock.due(1000)).toBe('check');
    expect(() => clock.accept(windowAt(0), 1000)).toThrow();
  });
  it('caps at absolute expiry regardless of activity and browser wall clock', () => {
    const clock = new PlatformActivityClock(); clock.accept(windowAt(1_800_000, 1000), 3000);
    clock.activity(); expect(clock.due(4000)).toBe('check');
  });
});
