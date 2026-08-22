import { cleanup, render, screen, waitFor } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { SessionHandoffPage } from './SessionHandoffPage';

const completeHandoff = vi.fn();

vi.mock('../auth/AuthProvider', () => ({
  useAuth: () => ({ completeHandoff }),
}));

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string) => key }),
}));

describe('company session handoff page', () => {
  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  it('consumes the fragment code once and enters the dashboard', async () => {
    completeHandoff.mockResolvedValue({ token: 'company-token' });

    render(
      <MemoryRouter initialEntries={['/session/handoff?code=single-use-code']}>
        <Routes>
          <Route path="/session/handoff" element={<SessionHandoffPage />} />
          <Route path="/" element={<div>dashboard-ready</div>} />
        </Routes>
      </MemoryRouter>,
    );

    await waitFor(() => expect(completeHandoff).toHaveBeenCalledWith('single-use-code'));
    expect(await screen.findByText('dashboard-ready')).toBeInTheDocument();
    expect(window.location.href).not.toContain('single-use-code');
  });

  it('does not call the backend when the code is missing', async () => {
    render(
      <MemoryRouter initialEntries={['/session/handoff']}>
        <Routes>
          <Route path="/session/handoff" element={<SessionHandoffPage />} />
        </Routes>
      </MemoryRouter>,
    );

    expect(await screen.findByText('auth.handoffFailed')).toBeInTheDocument();
    expect(completeHandoff).not.toHaveBeenCalled();
  });
});
