import { Button, Result, Spin } from 'antd';
import { useEffect, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/AuthProvider';

type HandoffState = 'connecting' | 'failed';

export function SessionHandoffPage(): JSX.Element {
  const { completeHandoff } = useAuth();
  const { t } = useTranslation();
  const location = useLocation();
  const navigate = useNavigate();
  const started = useRef(false);
  const [state, setState] = useState<HandoffState>('connecting');

  useEffect(() => {
    if (started.current) return;
    started.current = true;

    const code = new URLSearchParams(location.search).get('code');
    window.history.replaceState(
      null,
      document.title,
      `${window.location.pathname}${window.location.search}#/session/handoff`,
    );
    if (!code) {
      setState('failed');
      return;
    }

    void completeHandoff(code)
      .then(() => navigate('/', { replace: true }))
      .catch(() => setState('failed'));
  }, [completeHandoff, location.search, navigate]);

  return (
    <main className="login-page">
      <section className="login-panel" aria-live="polite">
        {state === 'connecting' ? (
          <Result
            icon={<Spin size="large" />}
            title={t('auth.handoffConnecting')}
            subTitle={t('auth.handoffConnectingHint')}
          />
        ) : (
          <Result
            status="error"
            title={t('auth.handoffFailed')}
            subTitle={t('auth.handoffFailedHint')}
            extra={(
              <Button type="primary" onClick={() => navigate('/login', { replace: true })}>
                {t('auth.backToLogin')}
              </Button>
            )}
          />
        )}
      </section>
    </main>
  );
}
