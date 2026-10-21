import { useEffect, useRef, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Alert, App, Button, Divider, Modal, Select, Space } from 'antd';
import { useTranslation } from 'react-i18next';
import { getErrorMessage } from '../../api/errors';
import { arrangePackaging, emptyPackaging, getPackaging, packagingTotal, type ArrangePackagingPayload, type PackagingView } from '../../api/packaging';
import { listLocations } from '../../api/warehouseSetup';
import { PackagingEditor } from './PackagingEditor';

interface Props { batchIds: number[]; onClose: () => void; onSaved: () => void }
export function PackagingArrangeModal({ batchIds, onClose, onSaved }: Props): JSX.Element {
  const { t } = useTranslation();
  const { message } = App.useApp();
  const [sources, setSources] = useState<PackagingView[]>([]);
  const [results, setResults] = useState<ArrangePackagingPayload['results']>([]);
  const [busy, setBusy] = useState(false);
  const attempt = useRef<{ fingerprint: string; key: string }>();
  const sourceQuery = useQuery({ queryKey: ['packaging', batchIds], queryFn: () => Promise.all(batchIds.map(getPackaging)), staleTime: 0, refetchOnWindowFocus: false });
  const first = sources[0];
  const verify = sources.some(s => !s.packaging);
  const locations = useQuery({ queryKey: ['packaging-locations', first?.warehouseId],
    queryFn: () => listLocations(first!.warehouseId), enabled: !!first?.warehouseId });
  useEffect(() => {
    if (!sourceQuery.data) return;
    setSources(sourceQuery.data);
    const grouped = new Map<number, ArrangePackagingPayload['results'][number]>();
    for (const source of sourceQuery.data) {
      const existing = grouped.get(source.locationId);
      const p = source.packaging ?? emptyPackaging(source.standardPackSize);
      grouped.set(source.locationId, { locationId: source.locationId, packaging: existing ? {
        packSize: p.packSize, sealedBoxes: existing.packaging.sealedBoxes + p.sealedBoxes,
        openedQuantities: [...existing.packaging.openedQuantities, ...p.openedQuantities],
      } : { ...p, openedQuantities: [...p.openedQuantities] } });
    }
    setResults([...grouped.values()]);
    attempt.current = undefined;
  }, [sourceQuery.data]);
  const before = sources.reduce((sum, source) => sum + source.quantity, 0);
  const after = results.reduce((sum, result) => sum + packagingTotal(result.packaging), 0);
  const invalid = !first || (verify && sources.length !== 1) || before !== after
    || results.some(r => !r.locationId || r.packaging.openedQuantities.some(q => q <= 0))
    || new Set(results.map(r => r.locationId)).size !== results.length;
  const save = async (): Promise<void> => {
    if (invalid) return;
    const payload: ArrangePackagingPayload = { mode: verify ? 'VERIFY' : 'ARRANGE',
      sources: sources.map(s => ({ batchId: s.batchId, version: s.version })), results };
    const fingerprint = JSON.stringify(payload);
    if (attempt.current?.fingerprint !== fingerprint) attempt.current = { fingerprint, key: `packaging-${crypto.randomUUID()}` };
    setBusy(true);
    try {
      await arrangePackaging(payload, attempt.current.key);
      message.success(t('common.saveSuccess', { defaultValue: '已保存' }));
      onSaved(); onClose();
    } catch (error) { message.error(getErrorMessage(error, t)); }
    finally { setBusy(false); }
  };
  return <Modal open title={t(verify ? 'packaging.verify' : 'packaging.arrange')} onCancel={onClose}
    onOk={() => void save()} confirmLoading={busy} okButtonProps={{ disabled: invalid || sourceQuery.isFetching }}
    okText={t('common.save')} cancelText={t('common.cancel')} width={700}>
    {sourceQuery.error && <Alert type="error" message={getErrorMessage(sourceQuery.error, t)} />}
    <p>{t('packaging.arrangeNotice')}</p>
    {sources.map(s => <div key={s.batchId}>{s.batchCode} · {s.locationCode} · {s.quantity} {s.baseUnit}
      {s.packaging ? ` / ${t('packaging.sealed')} ${s.packaging.sealedBoxes} / ${t('packaging.openedQuantity')} ${s.packaging.openedQuantities.join('、') || '—'}` : ` / ${t('packaging.unverified')}`}</div>)}
    {verify && sources.length > 1 && <Alert type="warning" message={t('packaging.verifyOne')} />}
    <Divider />
    {results.map((result, index) => <div key={index} style={{ marginBottom: 20 }}>
      <Space><span>{t('packaging.location')}</span><Select style={{ minWidth: 240 }} disabled={verify || busy}
        value={result.locationId} options={(locations.data ?? []).filter(l => l.enabled !== false).map(l => ({ value: l.id, label: l.locationCode }))}
        onChange={locationId => setResults(results.map((r, i) => i === index ? { ...r, locationId } : r))} />
        {!verify && results.length > 1 && <Button type="link" onClick={() => setResults(results.filter((_, i) => i !== index))}>{t('common.remove', { defaultValue: '移除' })}</Button>}</Space>
      <PackagingEditor value={result.packaging} unit={first?.baseUnit} disabled={busy}
        onChange={packaging => setResults(results.map((r, i) => i === index ? { ...r, packaging } : r))} />
    </div>)}
    {!verify && <Button onClick={() => setResults([...results, { locationId: 0, packaging: emptyPackaging(first?.standardPackSize ?? 1) }])}>{t('packaging.addLocation')}</Button>}
    <Alert style={{ marginTop: 16 }} type={before === after ? 'info' : 'warning'}
      message={t('packaging.balance', { before, after, unit: first?.baseUnit ?? '' })} />
    <Button type="link" onClick={() => void sourceQuery.refetch()}>{t('common.refresh')}</Button>
  </Modal>;
}
