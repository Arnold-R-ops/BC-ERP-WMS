import { App as AntdApp, Button, ConfigProvider } from 'antd';
import { cleanup, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import i18n from '../../locales/i18n';

vi.mock('../../auth/AuthProvider', () => ({
  useAuth: () => ({
    logout: vi.fn(),
    session: { currentRole: 'WAREHOUSE_STAFF', username: 'warehouse-user' },
  }),
}));

import { useMobileOperation, WarehouseMobileLayout } from './WarehouseMobileLayout';

function OperationProbe(): JSX.Element {
  const { online } = useMobileOperation();
  return <Button disabled={!online}>Submit inventory change</Button>;
}

function renderLayout(): void {
  render(
    <ConfigProvider>
      <AntdApp>
        <MemoryRouter initialEntries={['/mobile/picking']}>
          <Routes>
            <Route element={<WarehouseMobileLayout />} path="/mobile">
              <Route element={<OperationProbe />} path="picking" />
            </Route>
          </Routes>
        </MemoryRouter>
      </AntdApp>
    </ConfigProvider>,
  );
}

describe('warehouse mobile offline boundary', () => {
  beforeEach(async () => {
    await i18n.changeLanguage('en-US');
  });

  afterEach(() => {
    cleanup();
    Object.defineProperty(window.navigator, 'onLine', { configurable: true, value: true });
    vi.unstubAllGlobals();
  });

  it('keeps inventory writes disabled while the device is offline', () => {
    vi.stubGlobal('fetch', vi.fn());
    Object.defineProperty(window.navigator, 'onLine', { configurable: true, value: false });
    renderLayout();

    expect(screen.getByText(/network is unavailable/i)).toBeVisible();
    expect(screen.getByRole('button', { name: 'Submit inventory change' })).toBeDisabled();
  });

  it('reenables the operation surface after connectivity returns', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: true }));
    Object.defineProperty(window.navigator, 'onLine', { configurable: true, value: false });
    renderLayout();

    Object.defineProperty(window.navigator, 'onLine', { configurable: true, value: true });
    window.dispatchEvent(new Event('online'));

    await waitFor(() => expect(screen.getByRole('button', { name: 'Submit inventory change' })).toBeEnabled());
  });

  it('detects an unreachable backend when the browser still reports online', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('Failed to fetch')));
    Object.defineProperty(window.navigator, 'onLine', { configurable: true, value: true });
    renderLayout();

    await waitFor(() => expect(screen.getByText(/network is unavailable/i)).toBeVisible());
    expect(screen.getByRole('button', { name: 'Submit inventory change' })).toBeDisabled();
  });
});
