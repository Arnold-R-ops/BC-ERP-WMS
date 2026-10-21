import { ConfigProvider, theme } from 'antd';
import enUS from 'antd/locale/en_US';
import zhCN from 'antd/locale/zh_CN';
import type { ReactNode } from 'react';
import { useTranslation } from 'react-i18next';

// These existing white work surfaces must not inherit ProLayout's dark navigation tokens.
export function PackagingSurface({ children }: { children: ReactNode }): JSX.Element {
  const { i18n } = useTranslation();
  return <ConfigProvider locale={i18n.language.startsWith('en') ? enUS : zhCN}
    theme={{ inherit: false, algorithm: theme.defaultAlgorithm,
      token: { colorPrimary: '#115095', colorText: '#172525', colorTextSecondary: '#526866', borderRadius: 6 } }}>
    {children}
  </ConfigProvider>;
}
