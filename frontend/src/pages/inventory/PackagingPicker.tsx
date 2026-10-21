import { useEffect } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Alert, Button, InputNumber, Space, Spin } from 'antd';
import { useTranslation } from 'react-i18next';
import { getErrorMessage } from '../../api/errors';
import { PackagingSurface } from './PackagingSurface';
import { getPackaging, selectionTotal, suggestPackagingTake, type PackagingSelection, type PackagingView } from '../../api/packaging';

interface Props { batchId?: number; suppliedView?: PackagingView; quantity: number; value?: PackagingSelection;
  onChange: (value: PackagingSelection | undefined) => void; onValid: (valid: boolean) => void }
export function PackagingPicker({ batchId, suppliedView, quantity, value, onChange, onValid }: Props): JSX.Element {
  const { t } = useTranslation();
  const query = useQuery({ queryKey: ['pick-packaging', batchId], queryFn: () => getPackaging(batchId!), enabled: !!batchId && !suppliedView, staleTime: 0, refetchOnWindowFocus: false });
  const view = suppliedView ?? query.data;
  const p = view?.packaging;
  useEffect(() => { onChange(view ? suggestPackagingTake(view, quantity) : undefined); }, [view, quantity]);
  const total = p && value ? selectionTotal(p, value) : 0;
  const valid = !!view && (!!suppliedView || (!query.isFetching && !query.isError)) && !view.stocktakeTaskId
    && (!p || (!!value && total === quantity && value.version === view.version
      && value.sealedBoxes + value.takeFromNewlyOpened.length <= p.sealedBoxes
      && value.openedQuantities.every((q, i) => q >= 0 && q <= p.openedQuantities[i])
      && value.takeFromNewlyOpened.every(q => q > 0 && q < p.packSize)));
  useEffect(() => { onValid(valid); }, [valid, onValid]);
  if (!suppliedView && query.isLoading) return <Spin />;
  if (!suppliedView && query.error) return <PackagingSurface><Alert type="error" message={getErrorMessage(query.error, t)} action={<Button onClick={() => void query.refetch()}>{t('common.refresh')}</Button>} /></PackagingSurface>;
  if (!p || !value) return <PackagingSurface><Alert type="info" message={t('packaging.unverified')} /></PackagingSurface>;
  return <PackagingSurface><Space direction="vertical" style={{ width: '100%', marginBottom: 16 }}>
    <strong>{t('packaging.selectSource')}</strong>
    <label>{t('packaging.takeSealed')} <InputNumber aria-label={t('packaging.takeSealed')} min={0} max={p.sealedBoxes}
      precision={0} value={value.sealedBoxes} onChange={n => onChange({ ...value, sealedBoxes: n ?? 0 })} /></label>
    {p.openedQuantities.map((quantityInBox, index) => <label key={index}>
      {t('packaging.takeOpened', { quantity: quantityInBox, unit: view.baseUnit })}{' '}
      <InputNumber aria-label={`${t('packaging.openedQuantity')} ${index + 1}`} min={0} max={quantityInBox}
        precision={0} value={value.openedQuantities[index]} onChange={n => onChange({ ...value,
          openedQuantities: value.openedQuantities.map((q, i) => i === index ? n ?? 0 : q) })} />
    </label>)}
    {value.takeFromNewlyOpened.map((amount, index) => <Space key={index}>
      <span>{t('packaging.openAndTake')}</span><InputNumber min={1} max={p.packSize - 1} precision={0} value={amount}
        onChange={n => onChange({ ...value, takeFromNewlyOpened: value.takeFromNewlyOpened.map((q, i) => i === index ? n ?? 0 : q) })} />
      <Button type="link" onClick={() => onChange({ ...value, takeFromNewlyOpened: value.takeFromNewlyOpened.filter((_, i) => i !== index) })}>{t('common.remove', { defaultValue: '移除' })}</Button>
    </Space>)}
    <Button disabled={p.packSize <= 1 || value.sealedBoxes + value.takeFromNewlyOpened.length >= p.sealedBoxes}
      onClick={() => onChange({ ...value, takeFromNewlyOpened: [...value.takeFromNewlyOpened, 1] })}>{t('packaging.openAndTake')}</Button>
    <Alert type={valid ? 'info' : 'warning'} message={t('packaging.selected', { quantity: total, expected: quantity, unit: view.baseUnit })} />
    {!suppliedView && <Button type="link" onClick={() => void query.refetch()}>{t('common.refresh')}</Button>}
  </Space></PackagingSurface>;
}
