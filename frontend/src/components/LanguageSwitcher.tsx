import { GlobalOutlined } from '@ant-design/icons';
import { Button, Dropdown, type MenuProps, Tooltip } from 'antd';
import { useTranslation } from 'react-i18next';
import { changeLanguage, type SupportedLanguage } from '../locales/i18n';

export function LanguageSwitcher(): JSX.Element {
  const { i18n, t } = useTranslation();

  const items: MenuProps['items'] = [
    { key: 'zh-CN', label: t('common.chinese') },
    { key: 'en-US', label: t('common.english') },
  ];

  const onClick: MenuProps['onClick'] = ({ key }) => {
    void changeLanguage(key as SupportedLanguage);
  };

  return (
    <Dropdown menu={{ items, onClick, selectedKeys: [i18n.language] }} trigger={['click']}>
      <Tooltip placement="left" title={t('common.language')}>
        <Button
          aria-label={t('common.language')}
          className="header-action-button"
          icon={<GlobalOutlined />}
          type="text"
        >
          {i18n.language === 'en-US' ? t('common.englishShort') : t('common.chineseShort')}
        </Button>
      </Tooltip>
    </Dropdown>
  );
}
