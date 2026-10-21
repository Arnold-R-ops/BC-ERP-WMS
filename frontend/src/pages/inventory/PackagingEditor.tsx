import { Button, InputNumber, Space, Typography } from 'antd';
import { useTranslation } from 'react-i18next';
import { packagingTotal, type StockPackaging } from '../../api/packaging';
import { PackagingSurface } from './PackagingSurface';

interface Props { value: StockPackaging; onChange: (value: StockPackaging) => void; unit?: string; disabled?: boolean }
export function PackagingEditor({ value, onChange, unit, disabled }: Props): JSX.Element {
  const { t } = useTranslation();
  return <PackagingSurface><Space direction="vertical" style={{ width: '100%' }}>
    <Typography.Text type="secondary">{t('packaging.standard', { size: value.packSize, unit: unit ?? '' })}</Typography.Text>
    <Space wrap>
      <label>{t('packaging.sealed')} <InputNumber aria-label={t('packaging.sealed')} disabled={disabled}
        min={0} max={2147483647} precision={0} value={value.sealedBoxes}
        onChange={n => onChange({ ...value, sealedBoxes: n ?? 0 })} /></label>
      <span>{t('packaging.openedCount', { count: value.openedQuantities.length })}</span>
    </Space>
    {value.openedQuantities.map((quantity, index) => <Space key={index} wrap>
      <span>{t('packaging.openedQuantity')}</span>
      <InputNumber aria-label={`${t('packaging.openedQuantity')} ${index + 1}`} disabled={disabled}
        min={1} max={2147483647} precision={0} value={quantity}
        onChange={n => onChange({ ...value, openedQuantities: value.openedQuantities.map((q, i) => i === index ? n ?? 0 : q) })} />
      <span>{unit}</span>
      {quantity > value.packSize && <Typography.Text type="secondary">{t('packaging.overStandard')}</Typography.Text>}
      <Button disabled={disabled} type="link" onClick={() => onChange({ ...value, openedQuantities: value.openedQuantities.filter((_, i) => i !== index) })}>{t('common.remove', { defaultValue: '移除' })}</Button>
    </Space>)}
    <Button disabled={disabled} onClick={() => onChange({ ...value, openedQuantities: [...value.openedQuantities, 1] })}>{t('packaging.addOpened')}</Button>
    <strong>{t('packaging.total', { quantity: packagingTotal(value), unit: unit ?? '' })}</strong>
  </Space></PackagingSurface>;
}
