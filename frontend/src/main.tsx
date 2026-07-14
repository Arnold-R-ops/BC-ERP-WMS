import React from 'react';
import ReactDOM from 'react-dom/client';
import 'antd/dist/reset.css';
import './locales/i18n';
import './styles.css';
import { App } from './app';

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <App />
  </React.StrictMode>,
);
