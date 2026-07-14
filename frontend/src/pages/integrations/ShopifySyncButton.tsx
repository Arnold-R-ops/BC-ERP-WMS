import { SyncOutlined } from '@ant-design/icons';
import { useMutation } from '@tanstack/react-query';
import { App as AntdApp, Button } from 'antd';
import { useTranslation } from 'react-i18next';
import { getErrorMessage } from '../../api/errors';
import { syncShopifyOrders } from '../../api/integrations';

interface ShopifySyncButtonProps {
  block?: boolean;
  onComplete?: () => void;
}

export function ShopifySyncButton({ block = false, onComplete }: ShopifySyncButtonProps): JSX.Element {
  const { message } = AntdApp.useApp();
  const { t } = useTranslation();
  const mutation = useMutation({ mutationFn: syncShopifyOrders });

  const sync = async (): Promise<void> => {
    try {
      const result = await mutation.mutateAsync();
      const text = t('integrations.sync.result', {
        success: result.success,
        skipped: result.skipped,
        failed: result.failed,
      });
      if (result.failed > 0) message.warning(text);
      else message.success(text);
      onComplete?.();
    } catch (error) {
      message.error(getErrorMessage(error, t));
    }
  };

  return (
    <Button block={block} icon={<SyncOutlined spin={mutation.isPending} />} loading={mutation.isPending} onClick={() => void sync()}>
      {t('integrations.sync.action')}
    </Button>
  );
}
