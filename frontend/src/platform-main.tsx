import ReactDOM from 'react-dom/client';
import 'antd/dist/reset.css';
import './locales/i18n';
import './styles.css';
import { PlatformApp } from './platform/PlatformApp';

ReactDOM.createRoot(document.getElementById('root')!).render(
  <PlatformApp />,
);
