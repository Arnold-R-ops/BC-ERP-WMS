import { CheckCircleOutlined, SafetyCertificateOutlined, StopOutlined } from '@ant-design/icons';
import { Alert, Checkbox, Descriptions, Form, Input, Modal, Result, Space, Spin, Statistic, Table, Tag, Typography } from 'antd';
import { useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import type { Permission, Role, RoleCopyPreview, RoleCopyRequest, RoleCopyResult } from '../../api/iam';

export type RoleCopyFormValues = Omit<RoleCopyRequest, 'snapshotFingerprint'>;

interface RoleCopyModalProps {
  errorMessage?: string;
  loading: boolean;
  onClose: () => void;
  onSubmit: (values: RoleCopyFormValues) => Promise<boolean>;
  open: boolean;
  preview?: RoleCopyPreview;
  result?: RoleCopyResult;
  source?: Role;
  submitting: boolean;
}

function riskColor(riskLevel?: string): string {
  if (riskLevel === 'CRITICAL') return 'magenta';
  if (riskLevel === 'HIGH') return 'red';
  return 'default';
}

export function RoleCopyModal({
  errorMessage,
  loading,
  onClose,
  onSubmit,
  open,
  preview,
  result,
  source,
  submitting,
}: RoleCopyModalProps): JSX.Element {
  const [form] = Form.useForm<RoleCopyFormValues>();
  const { t } = useTranslation();
  const roleCode = Form.useWatch('roleCode', form);
  const hasHighRisk = (preview?.highRiskCount ?? 0) > 0;
  const copyBlocked = preview?.copyAllowed === false || (preview?.criticalRiskCount ?? 0) > 0;

  useEffect(() => {
    if (open && !result) {
      form.resetFields();
      form.setFieldsValue({ riskAcknowledged: false });
    }
  }, [form, open, result, source?.id]);

  const permissionColumns = [
    {
      title: t('iam.permissions.fields.permission'), dataIndex: 'permissionName', width: 310,
      render: (value: string | undefined, permission: Permission) => (
        <Space direction="vertical" size={0}>
          <Typography.Text>{value || permission.permissionCode || '-'}</Typography.Text>
          <Typography.Text code copyable={Boolean(permission.permissionCode)}>{permission.permissionCode}</Typography.Text>
        </Space>
      ),
    },
    {
      title: t('iam.permissions.fields.type'), dataIndex: 'permissionType', width: 100,
      render: (value: string | undefined) => <Tag>{value ?? '-'}</Tag>,
    },
    {
      title: t('iam.permissions.fields.risk'), dataIndex: 'riskLevel', width: 110,
      render: (value: string | undefined) => <Tag color={riskColor(value)}>{t(`iam.permissions.risks.${value ?? 'NORMAL'}`)}</Tag>,
    },
  ];

  if (result) {
    return (
      <Modal cancelButtonProps={{ style: { display: 'none' } }} destroyOnHidden onCancel={onClose} onOk={onClose} open={open} title={t('iam.roles.copy.resultTitle')} width={660}>
        <Result
          icon={<CheckCircleOutlined />}
          status="success"
          subTitle={t('iam.roles.copy.resultSubtitle')}
          title={t('iam.roles.copy.resultSuccess', { roleCode: result.role?.roleCode ?? '-' })}
        >
          <Descriptions bordered column={1} size="small">
            <Descriptions.Item label={t('iam.roles.fields.name')}>{result.role?.roleName ?? '-'}</Descriptions.Item>
            <Descriptions.Item label={t('iam.roles.fields.status')}>
              <Tag>{t('common.disabled')}</Tag>
              {t('iam.roles.copy.disabledResultHint')}
            </Descriptions.Item>
            <Descriptions.Item label={t('iam.roles.copy.permissionSnapshot')}>
              {t('iam.roles.copy.permissionCountValue', { count: result.permissionCount ?? 0 })}
            </Descriptions.Item>
            <Descriptions.Item label={t('iam.roles.copy.highRiskCount')}>
              <Tag color={(result.highRiskCount ?? 0) > 0 ? 'red' : 'default'}>{result.highRiskCount ?? 0}</Tag>
            </Descriptions.Item>
            <Descriptions.Item label={t('iam.roles.copy.auditId')}>{result.auditId ?? '-'}</Descriptions.Item>
          </Descriptions>
        </Result>
      </Modal>
    );
  }

  return (
    <Modal
      cancelText={t('common.cancel')}
      confirmLoading={submitting}
      destroyOnHidden
      okButtonProps={{ danger: hasHighRisk, disabled: loading || !preview || copyBlocked }}
      okText={hasHighRisk ? t('iam.roles.copy.confirmHighRisk') : t('iam.roles.copy.confirm')}
      onCancel={onClose}
      onOk={() => form.submit()}
      open={open}
      title={t('iam.roles.copy.title', { roleCode: source?.roleCode ?? '-' })}
      width={920}
    >
      <Spin spinning={loading}>
        {errorMessage && <Alert description={errorMessage} message={t('iam.roles.copy.previewFailed')} showIcon type="error" />}
        {preview && (
          <>
            <Alert
              description={copyBlocked ? t('iam.roles.copy.blockedNotice') : t('iam.roles.copy.snapshotNotice')}
              icon={copyBlocked ? <StopOutlined /> : <SafetyCertificateOutlined />}
              message={copyBlocked ? t('iam.roles.copy.blockedTitle') : t('iam.roles.copy.snapshotTitle')}
              showIcon
              type={copyBlocked ? 'error' : hasHighRisk ? 'warning' : 'info'}
            />
            <Space size="large" style={{ margin: '16px 0' }} wrap>
              <Statistic title={t('iam.roles.copy.totalPermissions')} value={preview.permissionCount ?? 0} />
              <Statistic title={t('iam.roles.copy.normalRiskCount')} value={preview.normalRiskCount ?? 0} />
              <Statistic title={t('iam.roles.copy.highRiskCount')} value={preview.highRiskCount ?? 0} valueStyle={hasHighRisk ? { color: '#cf1322' } : undefined} />
              <Statistic title={t('iam.roles.copy.criticalRiskCount')} value={preview.criticalRiskCount ?? 0} valueStyle={copyBlocked ? { color: '#c41d7f' } : undefined} />
            </Space>
            <Table<Permission>
              columns={permissionColumns}
              dataSource={preview.permissions ?? []}
              pagination={{ pageSize: 6, showSizeChanger: false }}
              rowKey={(permission) => String(permission.id ?? permission.permissionCode)}
              scroll={{ x: 620 }}
              size="small"
            />
            {!copyBlocked && (
              <Form<RoleCopyFormValues>
                form={form}
                layout="vertical"
                onFinish={async (values) => { await onSubmit({ ...values, roleCode: values.roleCode.trim().toUpperCase() }); }}
                style={{ marginTop: 20 }}
              >
                <Space align="start" size="middle" style={{ display: 'flex' }}>
                  <Form.Item
                    label={t('iam.roles.fields.code')}
                    name="roleCode"
                    normalize={(value: string) => value?.toUpperCase()}
                    rules={[
                      { required: true, whitespace: true, message: t('iam.roles.copy.validation.codeRequired') },
                      { pattern: /^[A-Za-z][A-Za-z0-9_]{2,49}$/, message: t('iam.roles.copy.validation.codePattern') },
                    ]}
                    style={{ flex: 1 }}
                  >
                    <Input autoComplete="off" maxLength={50} placeholder="WAREHOUSE_NIGHT_SHIFT" />
                  </Form.Item>
                  <Form.Item
                    label={t('iam.roles.fields.name')}
                    name="roleName"
                    rules={[{ required: true, whitespace: true, message: t('iam.roles.copy.validation.nameRequired') }]}
                    style={{ flex: 1 }}
                  >
                    <Input maxLength={100} />
                  </Form.Item>
                </Space>
                <Form.Item
                  extra={t('iam.roles.copy.purposeHint')}
                  label={t('iam.roles.copy.purpose')}
                  name="description"
                  rules={[{ required: true, whitespace: true, message: t('iam.roles.copy.validation.purposeRequired') }]}
                >
                  <Input.TextArea maxLength={500} rows={3} showCount />
                </Form.Item>
                {hasHighRisk && (
                  <>
                    <Alert description={t('iam.roles.copy.highRiskNotice')} message={t('iam.roles.copy.highRiskTitle')} showIcon type="warning" />
                    <Form.Item
                      label={t('iam.roles.copy.operationReason')}
                      name="operationReason"
                      rules={[{ required: true, whitespace: true, message: t('iam.roles.copy.validation.reasonRequired') }]}
                      style={{ marginTop: 16 }}
                    >
                      <Input.TextArea maxLength={500} rows={3} showCount />
                    </Form.Item>
                    <Form.Item
                      name="riskAcknowledged"
                      rules={[{
                        validator: (_, value) => value ? Promise.resolve() : Promise.reject(new Error(t('iam.roles.copy.validation.ackRequired'))),
                      }]}
                      valuePropName="checked"
                    >
                      <Checkbox>{t('iam.roles.copy.riskAcknowledgement', { count: preview.highRiskCount ?? 0 })}</Checkbox>
                    </Form.Item>
                    <Form.Item
                      extra={t('iam.roles.copy.confirmationHint', { roleCode: roleCode || '-' })}
                      label={t('iam.roles.copy.confirmationCode')}
                      name="confirmationCode"
                      rules={[
                        { required: true, whitespace: true, message: t('iam.roles.copy.validation.confirmationRequired') },
                        {
                          validator: (_, value) => value?.trim().toUpperCase() === roleCode?.trim().toUpperCase()
                            ? Promise.resolve()
                            : Promise.reject(new Error(t('iam.roles.copy.validation.confirmationMismatch'))),
                        },
                      ]}
                    >
                      <Input autoComplete="off" maxLength={50} />
                    </Form.Item>
                  </>
                )}
              </Form>
            )}
          </>
        )}
      </Spin>
    </Modal>
  );
}
