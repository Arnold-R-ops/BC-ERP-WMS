import {
  ModalForm,
  ProFormDigit,
  ProFormSelect,
  ProFormSwitch,
  ProFormText,
  ProFormTextArea,
} from '@ant-design/pro-components';
import { GlobalOutlined, IdcardOutlined, MailOutlined, PhoneOutlined, PrinterOutlined, ProfileOutlined, TagsOutlined, UserOutlined } from '@ant-design/icons';
import { Tabs } from 'antd';
import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import type { Customer, CustomerPayload } from '../../api/masterData';

function InputIcon({ children }: { children: React.ReactNode }): JSX.Element {
  return <span className="client-input-icon">{children}</span>;
}

interface ClientFormModalProps {
  customer?: Customer;
  loading: boolean;
  open: boolean;
  onClose: () => void;
  onSubmit: (payload: CustomerPayload) => Promise<boolean>;
}

export function ClientFormModal({
  customer,
  loading,
  open,
  onClose,
  onSubmit,
}: ClientFormModalProps): JSX.Element {
  const { t } = useTranslation();
  const [activeTab, setActiveTab] = useState('details');
  const editing = customer?.id !== undefined;

  useEffect(() => {
    if (open) setActiveTab('details');
  }, [customer?.id, open]);

  return (
    <ModalForm<CustomerPayload>
      key={customer?.id ?? 'new-client'}
      initialValues={{
        name: customer?.name ?? '',
        contact: customer?.contact ?? '',
        phone: customer?.phone ?? '',
        email: customer?.email ?? '',
        address: customer?.address ?? '',
        creditLimit: customer?.creditLimit ?? 0,
        vatRate: customer?.vatRate,
        secondaryTaxRate: customer?.secondaryTaxRate,
        vatNumber: customer?.vatNumber ?? '',
        isActive: customer?.isActive ?? true,
      }}
      modalProps={{
        centered: true,
        className: 'client-form-modal',
        destroyOnHidden: true,
        maskClosable: false,
        styles: {
          body: { overflowY: 'auto' },
        },
      }}
      onFinish={onSubmit}
      onOpenChange={(nextOpen) => { if (!nextOpen) onClose(); }}
      open={open}
      submitter={{
        searchConfig: { resetText: t('common.cancel'), submitText: editing ? t('common.save') : t('common.create') },
        submitButtonProps: { loading },
      }}
      title={editing ? t('clients.form.editTitle') : t('clients.form.createTitle')}
      width={810}
    >
      <Tabs activeKey={activeTab} centered className="client-form-tabs" items={[
        { key: 'details', label: t('clients.form.tabs.details') },
        { key: 'tax', label: t('clients.form.tabs.tax') },
      ]} onChange={setActiveTab} />
      {activeTab === 'details' && <>
      <h2 className="client-form-section">{t('clients.form.sections.details')}</h2>
      <ProFormText
        fieldProps={{ maxLength: 100, prefix: <InputIcon><UserOutlined /></InputIcon> }}
        label={t('clients.fields.name')}
        name="name"
        rules={[{ required: true, message: t('clients.validation.nameRequired') }]}
      />
      <div className="client-form-two-columns">
        <ProFormText fieldProps={{ maxLength: 100, prefix: <InputIcon><MailOutlined /></InputIcon>, type: 'email' }} label={t('clients.fields.email')} name="email" />
        <ProFormText fieldProps={{ maxLength: 30, prefix: <InputIcon><PhoneOutlined /></InputIcon> }} label={t('clients.fields.phone')} name="phone" />
        <ProFormText disabled fieldProps={{ placeholder: 'www.something.com', prefix: <InputIcon><GlobalOutlined /></InputIcon> }} label={t('clients.form.preview.website')} name="websitePreview" />
        <ProFormText disabled fieldProps={{ placeholder: t('clients.form.preview.faxPlaceholder'), prefix: <InputIcon><PrinterOutlined /></InputIcon> }} label={t('clients.form.preview.fax')} name="faxPreview" />
        <ProFormSelect disabled fieldProps={{ placeholder: t('clients.form.preview.none'), prefix: <InputIcon><IdcardOutlined /></InputIcon> }} label={t('clients.form.preview.manager')} name="managerPreview" />
        <ProFormSelect disabled fieldProps={{ placeholder: t('clients.form.preview.none'), prefix: <InputIcon><TagsOutlined /></InputIcon> }} label={t('clients.form.preview.tags')} name="tagPreview" />
      </div>
      <ProFormText fieldProps={{ maxLength: 100 }} label={t('clients.fields.contact')} name="contact" />
      <ProFormDigit fieldProps={{ min: 0, precision: 2 }} label={t('clients.fields.creditLimit')} name="creditLimit" />
      <h2 className="client-form-section">{t('clients.form.sections.billingAddress')}</h2>
      <ProFormTextArea fieldProps={{ maxLength: 500, placeholder: t('clients.form.preview.addressPlaceholder'), showCount: true }} label={t('clients.fields.address')} name="address" />
      <div className="client-form-two-columns">
        <ProFormText disabled fieldProps={{ placeholder: t('clients.form.preview.city') }} label={t('clients.form.preview.city')} name="billingCityPreview" />
        <ProFormText disabled fieldProps={{ placeholder: t('clients.form.preview.province') }} label={t('clients.form.preview.province')} name="billingProvincePreview" />
        <ProFormText disabled fieldProps={{ placeholder: t('clients.form.preview.postcode') }} label={t('clients.form.preview.postcode')} name="billingPostcodePreview" />
        <ProFormText disabled fieldProps={{ placeholder: t('clients.form.preview.country') }} label={t('clients.form.preview.country')} name="billingCountryPreview" />
      </div>
      <h2 className="client-form-section">{t('clients.form.sections.shippingAddress')}</h2>
      <ProFormSelect disabled fieldProps={{ placeholder: t('clients.form.preview.sameAsBilling') }} label={t('clients.form.preview.shippingAddress')} name="shippingAddressPreview" />
      <h2 className="client-form-section">{t('clients.form.sections.pricingTier')}</h2>
      <p className="client-form-hint">{t('clients.form.hints.pricingTier')}</p>
      <ProFormSelect disabled fieldProps={{ placeholder: t('clients.form.preview.selectPricingTier') }} label={t('clients.form.preview.pricingTier')} name="priceTierPreview" />
      <ProFormSwitch label={t('clients.fields.active')} name="isActive" />
      </>}
      {activeTab === 'tax' && <>
        <h2 className="client-form-section">{t('clients.form.sections.customerTax')}</h2>
        <p className="client-form-hint">{t('clients.form.hints.customerTax')}</p>
        <div className="client-form-two-columns">
          <ProFormDigit fieldProps={{ min: 0, max: 100, precision: 2, addonAfter: '%' }} label={t('clients.form.tax.vat')} name="vatRate" />
          <ProFormDigit fieldProps={{ min: 0, max: 100, precision: 2, addonAfter: '%' }} label={t('clients.form.tax.secondaryTax')} name="secondaryTaxRate" />
        </div>
        <h2 className="client-form-section">{t('clients.form.sections.vatRegistration')}</h2>
        <p className="client-form-hint">{t('clients.form.hints.vatRegistration')}</p>
        <ProFormText fieldProps={{ maxLength: 100, placeholder: t('clients.form.tax.vatNumberPlaceholder'), prefix: <InputIcon><ProfileOutlined /></InputIcon> }} label={t('clients.form.tax.vatNumber')} name="vatNumber" />
      </>}
    </ModalForm>
  );
}
