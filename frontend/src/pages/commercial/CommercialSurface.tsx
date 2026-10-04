import { ConfigProvider, theme } from 'antd';
import enUS from 'antd/locale/en_US';
import zhCN from 'antd/locale/zh_CN';
import type { ReactNode } from 'react';
import { useTranslation } from 'react-i18next';

// ProLayout uses a dark navigation theme; these white document surfaces need
// their own text tokens, including labels, close icons and modal controls.
export function CommercialSurface({ children }: { children: ReactNode }) {
  const { i18n } = useTranslation();
  return <ConfigProvider locale={i18n.language.startsWith('en') ? enUS : zhCN}
    theme={{ inherit: false, algorithm: theme.defaultAlgorithm, token: { colorPrimary: '#115095', borderRadius: 6 } }}>
    {children}
  </ConfigProvider>;
}
