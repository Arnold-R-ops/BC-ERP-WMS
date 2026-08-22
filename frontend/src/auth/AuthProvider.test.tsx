import { App as AntdApp, ConfigProvider } from 'antd';
import { act, cleanup, fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import '../locales/i18n';
import { AuthProvider, TENANT_IDLE_TIMEOUT_MS, useAuth } from './AuthProvider';
import {
  AUTH_STORAGE_KEY,
  recordUserActivity,
  writeAuthSession,
} from './storage';

const BASE_TIME = new Date('2026-08-18T08:00:00Z').getTime();

function renderProvider(): void {
  render(
    <MemoryRouter initialEntries={['/']}>
      <ConfigProvider>
        <AntdApp>
          <AuthProvider>
            <Routes>
              <Route element={<div>tenant-session-active</div>} path="/" />
              <Route element={<div>tenant-login</div>} path="/login" />
            </Routes>
          </AuthProvider>
        </AntdApp>
      </ConfigProvider>
    </MemoryRouter>,
  );
}

function seedSession(expiresIn = 24 * 60 * 60 * 1000): void {
  writeAuthSession({
    token: 'tenant-token-1',
    tokenType: 'Bearer',
    username: 'worker',
    currentRole: 'WAREHOUSE_STAFF',
    availableRoles: ['WAREHOUSE_STAFF'],
    permissionCodes: [],
    expiresAt: BASE_TIME + expiresIn,
    sessionEndsAt: BASE_TIME + 7 * 24 * 60 * 60 * 1000,
    mustChangePassword: false,
  });
  recordUserActivity(BASE_TIME);
}

function SessionProbe(): JSX.Element {
  const { session } = useAuth();
  return <div>{session ? 'tenant-session-present' : 'tenant-session-absent'}</div>;
}

describe('tenant ERP session lifecycle', () => {
  beforeEach(() => {
    localStorage.clear();
    vi.useFakeTimers();
    vi.setSystemTime(BASE_TIME);
  });

  afterEach(() => {
    cleanup();
    vi.restoreAllMocks();
    vi.useRealTimers();
  });

  it('keeps the session at 59:59 and signs out exactly at 60 minutes idle', async () => {
    seedSession();
    renderProvider();

    await act(async () => vi.advanceTimersByTimeAsync(TENANT_IDLE_TIMEOUT_MS - 1_000));
    expect(screen.getByText('tenant-session-active')).toBeVisible();

    await act(async () => vi.advanceTimersByTimeAsync(1_000));
    expect(screen.getAllByText('tenant-login').at(-1)).toBeVisible();
    expect(localStorage.getItem(AUTH_STORAGE_KEY)).toBeNull();
  });

  it('counts keyboard activity but does not count mouse movement', async () => {
    seedSession();
    renderProvider();

    await act(async () => vi.advanceTimersByTimeAsync(50 * 60 * 1000));
    fireEvent.mouseMove(window);
    fireEvent.keyDown(window, { key: 'Tab' });
    await act(async () => vi.advanceTimersByTimeAsync(59 * 60 * 1000));
    expect(screen.getByText('tenant-session-active')).toBeVisible();

    fireEvent.mouseMove(window);
    await act(async () => vi.advanceTimersByTimeAsync(60 * 1000));
    expect(screen.getAllByText('tenant-login').at(-1)).toBeVisible();
  });

  it('refreshes an active near-expiry token once and atomically replaces storage', async () => {
    seedSession(30 * 60 * 1000);
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response(JSON.stringify({
      token: 'tenant-token-2',
      tokenType: 'Bearer',
      username: 'worker',
      currentRole: 'WAREHOUSE_STAFF',
      availableRoles: ['WAREHOUSE_STAFF'],
      permissionCodes: [],
      expiresIn: 24 * 60 * 60 * 1000,
      sessionEndsAt: BASE_TIME + 7 * 24 * 60 * 60 * 1000,
      mustChangePassword: false,
    }), { status: 200 }));

    renderProvider();
    await act(async () => {
      await vi.advanceTimersByTimeAsync(1);
      await Promise.resolve();
    });
    expect(fetchMock).toHaveBeenCalledOnce();
    expect(localStorage.getItem(AUTH_STORAGE_KEY)).toContain('tenant-token-2');

    await act(async () => vi.advanceTimersByTimeAsync(60 * 1000));
    expect(fetchMock).toHaveBeenCalledOnce();
  });

  it('ends the other tab when the shared tenant session is removed', async () => {
    seedSession();
    renderProvider();

    localStorage.removeItem(AUTH_STORAGE_KEY);
    act(() => {
      window.dispatchEvent(new StorageEvent('storage', {
        key: AUTH_STORAGE_KEY,
        newValue: null,
        storageArea: localStorage,
      }));
    });

    expect(screen.getByText('tenant-login')).toBeVisible();
  });

  it('does not sign in a tab that is already showing the login page', () => {
    render(
      <MemoryRouter initialEntries={['/login']}>
        <ConfigProvider>
          <AntdApp>
            <AuthProvider>
              <SessionProbe />
              <Routes>
                <Route element={<div>tenant-login</div>} path="/login" />
              </Routes>
            </AuthProvider>
          </AntdApp>
        </ConfigProvider>
      </MemoryRouter>,
    );
    expect(screen.getByText('tenant-session-absent')).toBeVisible();

    seedSession();
    act(() => {
      window.dispatchEvent(new StorageEvent('storage', {
        key: AUTH_STORAGE_KEY,
        newValue: localStorage.getItem(AUTH_STORAGE_KEY),
        storageArea: localStorage,
      }));
    });

    expect(screen.getByText('tenant-login')).toBeVisible();
    expect(screen.getByText('tenant-session-absent')).toBeVisible();
  });

  it('signs out after one failed refresh without retrying', async () => {
    seedSession(30 * 60 * 1000);
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response(
      JSON.stringify({ errorKey: 'AUTH_TOKEN_INVALID' }), { status: 401 },
    ));

    renderProvider();
    await act(async () => {
      await vi.advanceTimersByTimeAsync(1);
      await Promise.resolve();
    });
    expect(screen.getByText('tenant-login')).toBeVisible();

    await act(async () => vi.advanceTimersByTimeAsync(2 * 60 * 1000));
    expect(fetchMock).toHaveBeenCalledOnce();
  });

  it('does not resurrect a session removed while refresh is in flight', async () => {
    seedSession(30 * 60 * 1000);
    let resolveRefresh: ((response: Response) => void) | undefined;
    vi.spyOn(globalThis, 'fetch').mockReturnValue(new Promise<Response>((resolve) => {
      resolveRefresh = resolve;
    }));

    renderProvider();
    await act(async () => vi.advanceTimersByTimeAsync(1));
    localStorage.removeItem(AUTH_STORAGE_KEY);
    await act(async () => {
      resolveRefresh?.(new Response(JSON.stringify({
        token: 'must-not-be-restored',
        tokenType: 'Bearer',
        username: 'worker',
        currentRole: 'WAREHOUSE_STAFF',
        availableRoles: ['WAREHOUSE_STAFF'],
        permissionCodes: [],
        expiresIn: 86_400_000,
        sessionEndsAt: BASE_TIME + 604_800_000,
      }), { status: 200 }));
      await Promise.resolve();
    });

    expect(localStorage.getItem(AUTH_STORAGE_KEY)).toBeNull();
  });
});
