import { SafetyCertificateOutlined, ToolOutlined } from '@ant-design/icons';
import { useMutation, useQuery } from '@tanstack/react-query';
import {
  Alert,
  App as AntdApp,
  Button,
  Form,
  InputNumber,
  Select,
  Space,
  Statistic,
  Table,
  Tag,
  Typography,
  type TableColumnsType,
} from 'antd';
import type { Key } from 'react';
import { useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { getErrorMessage } from '../../api/errors';
import {
  INTEGRATION_CONFIGS_QUERY_KEY,
  listIntegrationConfigs,
  reconcileShopifyOrders,
  repairShopifyOrders,
  type ShopifyMissingOrder,
  type ShopifyReconciliationReport,
  type ShopifyReconciliationRepairResult,
} from '../../api/integrations';

interface ReconciliationFormValues {
  configId: number;
  days: number;
}

function rowKey(row: ShopifyMissingOrder): Key {
  return row.rawEventId ?? row.externalOrderId ?? row.externalOrderNo ?? 'missing-order';
}

export function ReconciliationPage(): JSX.Element {
  const [report, setReport] = useState<ShopifyReconciliationReport>();
  const [repairResult, setRepairResult] = useState<ShopifyReconciliationRepairResult>();
  const [selectedRowKeys, setSelectedRowKeys] = useState<Key[]>([]);
  const { t } = useTranslation();
  const { message, modal } = AntdApp.useApp();

  const configsQuery = useQuery({
    queryKey: INTEGRATION_CONFIGS_QUERY_KEY,
    queryFn: listIntegrationConfigs,
  });
  const reconcileMutation = useMutation({ mutationFn: reconcileShopifyOrders });
  const repairMutation = useMutation({ mutationFn: repairShopifyOrders });

  const columns = useMemo<TableColumnsType<ShopifyMissingOrder>>(() => [
    { title: t('integrations.reconciliation.fields.externalOrderNo'), dataIndex: 'externalOrderNo', width: 180 },
    { title: t('integrations.reconciliation.fields.externalOrderId'), dataIndex: 'externalOrderId', width: 190, ellipsis: true },
    { title: t('integrations.reconciliation.fields.financialStatus'), dataIndex: 'financialStatus', width: 145, render: (value) => value ?? '-' },
    { title: t('integrations.reconciliation.fields.fulfillmentStatus'), dataIndex: 'fulfillmentStatus', width: 155, render: (value) => value ?? '-' },
    {
      title: t('integrations.reconciliation.fields.repairEligibility'),
      dataIndex: 'repairEligible',
      width: 135,
      render: (value: boolean | undefined) => (
        <Tag color={value ? 'success' : 'warning'}>
          {t(value ? 'integrations.reconciliation.eligible' : 'integrations.reconciliation.blocked')}
        </Tag>
      ),
    },
    { title: t('integrations.reconciliation.fields.reason'), dataIndex: 'reason', ellipsis: true, render: (value) => value ?? '-' },
  ], [t]);

  const runReconciliation = async (values: ReconciliationFormValues): Promise<void> => {
    try {
      const nextReport = await reconcileMutation.mutateAsync(values);
      setReport(nextReport);
      setRepairResult(undefined);
      setSelectedRowKeys([]);
      message.success(t('integrations.reconciliation.messages.completed'));
    } catch (error) {
      message.error(getErrorMessage(error, t));
    }
  };

  const repairSelected = (): void => {
    const rawEventIds = selectedRowKeys.filter((key): key is number => typeof key === 'number');
    if (rawEventIds.length === 0) return;

    modal.confirm({
      title: t('integrations.reconciliation.repairConfirmTitle'),
      content: t('integrations.reconciliation.repairConfirmContent'),
      okText: t('integrations.reconciliation.actions.repair'),
      cancelText: t('common.cancel'),
      async onOk() {
        try {
          const result = await repairMutation.mutateAsync({ rawEventIds });
          setRepairResult(result);
          setSelectedRowKeys([]);
          message.success(t('integrations.reconciliation.messages.repaired', {
            blocked: result.blocked ?? 0,
            created: result.created ?? 0,
            skipped: result.skipped ?? 0,
          }));
        } catch (error) {
          message.error(getErrorMessage(error, t));
          throw error;
        }
      },
    });
  };

  const missingOrders = report?.missingOrders ?? [];

  return (
    <section className="data-page reconciliation-page">
      <header className="page-heading">
        <div>
          <Typography.Title level={2}>{t('integrations.reconciliation.title')}</Typography.Title>
          <Typography.Paragraph type="secondary">{t('integrations.reconciliation.description')}</Typography.Paragraph>
        </div>
      </header>

      <Alert
        description={t('integrations.reconciliation.readOnlyDescription')}
        message={t('integrations.reconciliation.readOnlyTitle')}
        showIcon
        type="info"
      />

      <Form<ReconciliationFormValues>
        className="reconciliation-toolbar"
        initialValues={{ days: 7 }}
        layout="inline"
        onFinish={(values) => void runReconciliation(values)}
      >
        <Form.Item label={t('integrations.reconciliation.fields.store')} name="configId" rules={[{ required: true }]}>
          <Select
            loading={configsQuery.isLoading}
            options={(configsQuery.data ?? []).map((config) => ({
              disabled: config.isActive !== true,
              label: config.storeUrl ?? `#${config.id}`,
              value: config.id,
            }))}
            placeholder={t('integrations.reconciliation.storePlaceholder')}
            style={{ minWidth: 280 }}
          />
        </Form.Item>
        <Form.Item label={t('integrations.reconciliation.fields.days')} name="days" rules={[{ required: true }]}>
          <InputNumber max={30} min={1} precision={0} />
        </Form.Item>
        <Form.Item>
          <Button htmlType="submit" icon={<SafetyCertificateOutlined />} loading={reconcileMutation.isPending} type="primary">
            {t('integrations.reconciliation.actions.run')}
          </Button>
        </Form.Item>
      </Form>

      {report && (
        <>
          <div className="reconciliation-summary">
            <Statistic title={t('integrations.reconciliation.summary.local')} value={report.localOrderCount ?? 0} />
            <Statistic title={t('integrations.reconciliation.summary.remote')} value={report.remoteOrderCount ?? 0} />
            <Statistic title={t('integrations.reconciliation.summary.missing')} value={report.missingOrderCount ?? 0} valueStyle={{ color: report.missingOrderCount ? '#b45309' : undefined }} />
            <Statistic title={t('integrations.reconciliation.summary.window')} suffix={t('integrations.reconciliation.daysUnit')} value={report.windowDays ?? 0} />
          </div>

          {repairResult && (
            <Alert
              className="reconciliation-result"
              message={t('integrations.reconciliation.repairResult', {
                blocked: repairResult.blocked ?? 0,
                created: repairResult.created ?? 0,
                skipped: repairResult.skipped ?? 0,
              })}
              showIcon
              type={(repairResult.blocked ?? 0) > 0 ? 'warning' : 'success'}
            />
          )}

          <div className="reconciliation-table-heading">
            <div>
              <Typography.Title level={4}>{t('integrations.reconciliation.missingOrdersTitle')}</Typography.Title>
              <Typography.Text type="secondary">{t('integrations.reconciliation.missingOrdersDescription')}</Typography.Text>
            </div>
            <Space>
              <Typography.Text type="secondary">
                {t('integrations.reconciliation.selectedCount', { count: selectedRowKeys.length })}
              </Typography.Text>
              <Button
                disabled={selectedRowKeys.length === 0}
                icon={<ToolOutlined />}
                loading={repairMutation.isPending}
                onClick={repairSelected}
                type="primary"
              >
                {t('integrations.reconciliation.actions.repair')}
              </Button>
            </Space>
          </div>
          <Table<ShopifyMissingOrder>
            columns={columns}
            dataSource={missingOrders}
            locale={{ emptyText: t('integrations.reconciliation.empty') }}
            pagination={{ pageSize: 20, showSizeChanger: true }}
            rowKey={rowKey}
            rowSelection={{
              getCheckboxProps: (row) => ({ disabled: row.repairEligible !== true || row.rawEventId === undefined }),
              onChange: setSelectedRowKeys,
              selectedRowKeys,
            }}
            scroll={{ x: 1050 }}
          />
        </>
      )}
    </section>
  );
}
