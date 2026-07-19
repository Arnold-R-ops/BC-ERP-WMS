import { CheckOutlined, EyeInvisibleOutlined, StopOutlined } from '@ant-design/icons';
import { useQuery } from '@tanstack/react-query';
import { App as AntdApp, Button, Empty, InputNumber, Radio, Select, Space, Tag } from 'antd';
import { useEffect, useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { getPendingSkuSuggestions, type PendingSkuMapping, type ResolvePendingSkuPayload } from '../../api/integrations';
import type { ProductSku } from '../../api/productSkus';

interface PendingSkuResolutionPanelProps {
  item: PendingSkuMapping;
  loading: boolean;
  products: ProductSku[];
  onResolve: (item: PendingSkuMapping, payload: ResolvePendingSkuPayload) => void;
}

export function PendingSkuResolutionPanel({ item, loading, products, onResolve }: PendingSkuResolutionPanelProps): JSX.Element {
  const [productSkuId, setProductSkuId] = useState<number>();
  const [quantityRatio, setQuantityRatio] = useState(1);
  const { message } = AntdApp.useApp();
  const { t } = useTranslation();
  const suggestionsQuery = useQuery({
    queryKey: ['pending-sku-suggestions', item.id],
    queryFn: () => getPendingSkuSuggestions(item.id as number),
    enabled: item.id !== undefined,
  });

  useEffect(() => {
    setProductSkuId(undefined);
    setQuantityRatio(1);
  }, [item.id]);

  const productOptions = useMemo(() => products.map((productSku) => ({
    label: `${productSku.skuCode ?? productSku.barcode ?? '-'} · ${productSku.name ?? productSku.skuName ?? `#${productSku.id}`}`,
    value: productSku.id as number,
  })), [products]);

  const map = (): void => {
    if (productSkuId === undefined) {
      message.warning(t('integrations.pending.validation.productRequired'));
      return;
    }
    onResolve(item, { action: 'MAP', productSkuId, quantityRatio });
  };

  return (
    <div className="mapping-resolution-panel">
      <section className="mapping-suggestions">
        <div className="mapping-section-heading">
          <strong>{t('integrations.pending.suggestions')}</strong>
          <Tag>{suggestionsQuery.data?.length ?? 0}</Tag>
        </div>
        {suggestionsQuery.data && suggestionsQuery.data.length > 0 ? (
          <Radio.Group onChange={(event) => setProductSkuId(event.target.value as number)} value={productSkuId}>
            <Space direction="vertical" size={8}>
              {suggestionsQuery.data.map((suggestion) => (
                <Radio key={suggestion.productSkuId} value={suggestion.productSkuId}>
                  <strong>{suggestion.name || suggestion.skuName || `#${suggestion.productSkuId}`}</strong>
                  <span className="table-secondary mapping-suggestion-code">{suggestion.barcode || '-'}</span>
                </Radio>
              ))}
            </Space>
          </Radio.Group>
        ) : (
          <Empty description={t('integrations.pending.noSuggestions')} image={Empty.PRESENTED_IMAGE_SIMPLE} />
        )}
      </section>

      <section className="mapping-manual-fields">
        <label htmlFor={`pending-product-${item.id}`}>{t('integrations.pending.manualProduct')}</label>
        <Select
          id={`pending-product-${item.id}`}
          onChange={setProductSkuId}
          optionFilterProp="label"
          options={productOptions}
          placeholder={t('integrations.pending.productPlaceholder')}
          showSearch
          value={productSkuId}
        />
        <label htmlFor={`pending-ratio-${item.id}`}>{t('integrations.fields.quantityRatio')}</label>
        <InputNumber
          id={`pending-ratio-${item.id}`}
          min={1}
          onChange={(value) => setQuantityRatio(value ?? 1)}
          precision={0}
          value={quantityRatio}
        />
        <div className="mapping-ratio-preview">
          {t('integrations.pending.ratioPreview', { quantity: quantityRatio })}
        </div>
      </section>

      <div className="mapping-resolution-actions">
        <Button icon={<CheckOutlined />} loading={loading} onClick={map} type="primary">
          {t('integrations.pending.actions.map')}
        </Button>
        <Button icon={<EyeInvisibleOutlined />} loading={loading} onClick={() => onResolve(item, { action: 'VIRTUAL', quantityRatio: 1 })}>
          {t('integrations.pending.actions.virtual')}
        </Button>
        <Button danger icon={<StopOutlined />} loading={loading} onClick={() => onResolve(item, { action: 'IGNORE', quantityRatio: 1 })}>
          {t('integrations.pending.actions.ignore')}
        </Button>
      </div>
    </div>
  );
}
