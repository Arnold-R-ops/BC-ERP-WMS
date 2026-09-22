import type { PlatformSessionWindow } from './api';

// Scroll intent, not generic scroll notifications (which can be programmatic).
export const PLATFORM_ACTIVITY_EVENTS = ['click', 'keydown', 'wheel', 'touchstart', 'touchmove'] as const;
export function isPlatformActivity(event: Pick<Event, 'type' | 'isTrusted'>, visible: boolean): boolean {
  return visible && event.isTrusted && (PLATFORM_ACTIVITY_EVENTS as readonly string[]).includes(event.type);
}

/** Client scheduling only. Every decision is rechecked by the server; no optimistic renewal. */
export class PlatformActivityClock {
  private deadline = 0;
  private lastReport = -Infinity;
  private pending = false;

  accept(window: PlatformSessionWindow, requestStarted: number): void {
    const remaining = Math.min(Date.parse(window.idleExpiresAt), Date.parse(window.expiresAt)) - Date.parse(window.serverNow);
    if (!Number.isFinite(remaining) || remaining <= 0) throw new Error('PLATFORM_SESSION_EXPIRED');
    // Using request start is conservative about network delay, independent of browser wall-clock skew.
    this.deadline = requestStarted + remaining;
  }
  activity(): void { this.pending = true; }
  due(now: number): 'check' | 'activity' | null {
    // Never send activity on an expired local window: recheck first without renewal.
    if (now >= this.deadline) return 'check';
    if (this.pending && (now - this.lastReport >= 15_000 || this.deadline - now < 15_000)) return 'activity';
    return null;
  }
  sending(now: number): void { this.pending = false; this.lastReport = now; }
}
