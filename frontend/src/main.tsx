import React from 'react';
import ReactDOM from 'react-dom/client';
import 'antd/dist/reset.css';
import './locales/i18n';
import './styles.css';
import { App } from './app';
import i18n from './locales/i18n';
import { restoreLegacyRoute } from './auth/ledgerEntry';

const root = ReactDOM.createRoot(document.getElementById('root')!);
restoreLegacyRoute();

async function start(): Promise<void> {
  try {
    const response = await fetch('/public/v1/entry', { cache: 'no-store', credentials: 'same-origin' });
    if (!response.ok) throw new Error('Entry unavailable');
    const entry = await response.json() as { surface: string; baseDomain: string };
    if (!['APP', 'TENANT', 'LOCAL_DEVELOPMENT', 'PLATFORM'].includes(entry.surface)) {
      throw new Error('Invalid entry surface');
    }
    if (entry.surface === 'PLATFORM') {
      window.location.replace('/platform.html');
      return;
    }
    root.render(<React.StrictMode><App publicEntry={entry.surface === 'APP'} baseDomain={entry.baseDomain} /></React.StrictMode>);
    if ('serviceWorker' in navigator && import.meta.env.PROD && entry.surface !== 'APP') {
      void navigator.serviceWorker.register('/service-worker.js');
    }
  } catch {
    root.render(<main className="login-page"><section className="login-panel" role="alert">
      {i18n.t('ledgerEntry.entryFailed')}
    </section></main>);
  }
}
void start();
