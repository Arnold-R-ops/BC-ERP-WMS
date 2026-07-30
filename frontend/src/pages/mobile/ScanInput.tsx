import { BrowserMultiFormatReader } from '@zxing/browser';
import { CheckOutlined, ScanOutlined } from '@ant-design/icons';
import { App as AntdApp, Button, Input, type InputRef } from 'antd';
import { useEffect, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';

interface ScanInputProps {
  autoFocus?: boolean;
  disabled?: boolean;
  label: string;
  manualValue?: string;
  placeholder: string;
  onScan: (value: string) => void;
}

export function ScanInput({ autoFocus = false, disabled, label, manualValue, placeholder, onScan }: ScanInputProps): JSX.Element {
  const [value, setValue] = useState('');
  const [decoding, setDecoding] = useState(false);
  const captureRef = useRef<HTMLInputElement>(null);
  const inputRef = useRef<InputRef>(null);
  const { message } = AntdApp.useApp();
  const { t } = useTranslation();

  useEffect(() => {
    if (autoFocus && !disabled) {
      window.setTimeout(() => inputRef.current?.focus(), 80);
    }
  }, [autoFocus, disabled]);

  const submit = (): void => {
    const normalized = value.trim();
    if (!normalized) return;
    onScan(normalized);
    setValue('');
    window.setTimeout(() => inputRef.current?.focus(), 0);
  };

  const decodeCapture = async (file?: File): Promise<void> => {
    if (!file) return;
    const objectUrl = URL.createObjectURL(file);
    setDecoding(true);
    try {
      const result = await new BrowserMultiFormatReader().decodeFromImageUrl(objectUrl);
      const text = result.getText().trim();
      if (!text) throw new Error('Empty barcode');
      onScan(text);
      setValue('');
      message.success(t('mobile.cameraScanSuccess'));
    } catch {
      message.error(t('mobile.cameraScanFailed'));
    } finally {
      URL.revokeObjectURL(objectUrl);
      setDecoding(false);
      if (captureRef.current) captureRef.current.value = '';
      window.setTimeout(() => inputRef.current?.focus(), 0);
    }
  };

  return (
    <label className="mobile-scan-field">
      <span>{label}</span>
      <input
        accept="image/*"
        aria-label={t('mobile.openCamera')}
        capture="environment"
        hidden
        onChange={(event) => void decodeCapture(event.target.files?.[0])}
        ref={captureRef}
        type="file"
      />
      <Input
        autoComplete="off"
        disabled={disabled}
        onChange={(event) => setValue(event.target.value)}
        onPressEnter={submit}
        placeholder={placeholder}
        prefix={(
          <Button
            aria-label={t('mobile.openCamera')}
            disabled={disabled}
            icon={<ScanOutlined />}
            loading={decoding}
            onClick={(event) => {
              event.preventDefault();
              event.stopPropagation();
              captureRef.current?.click();
            }}
            size="small"
            type="text"
          />
        )}
        ref={inputRef}
        size="large"
        suffix={(
          <Button
            aria-label={t('mobile.confirmInput')}
            disabled={disabled || value.trim().length === 0}
            icon={<CheckOutlined />}
            onClick={(event) => {
              event.preventDefault();
              event.stopPropagation();
              submit();
            }}
            size="small"
            type="primary"
          >
            {t('mobile.confirmInput')}
          </Button>
        )}
        value={value}
      />
      <small>{t('mobile.manualEntryHint')}</small>
      {manualValue ? (
        <Button
          disabled={disabled}
          onClick={(event) => {
            event.preventDefault();
            onScan(manualValue);
          }}
          size="large"
        >
          {t('mobile.manualFillLocation')}
        </Button>
      ) : null}
    </label>
  );
}
