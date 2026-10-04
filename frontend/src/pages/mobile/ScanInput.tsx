import { BarcodePhotoError, decodeBarcodePhoto } from './barcodePhoto';
import { CheckOutlined, ScanOutlined } from '@ant-design/icons';
import { App as AntdApp, Button, Input, type InputRef } from 'antd';
import { useEffect, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { LiveScanDialog } from './LiveScanDialog';

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
  const [scannerOpen, setScannerOpen] = useState(false);
  const captureRef = useRef<HTMLInputElement>(null);
  const albumRef = useRef<HTMLInputElement>(null);
  const photoGeneration = useRef(0);
  const inputRef = useRef<InputRef>(null);
  const { message } = AntdApp.useApp();
  const { t } = useTranslation();

  useEffect(() => {
    if (disabled) {
      setScannerOpen(false);
      setDecoding(false);
    }
    return () => { photoGeneration.current += 1; };
  }, [disabled]);

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
    if (!file || disabled) return;
    const generation = ++photoGeneration.current;
    const objectUrl = URL.createObjectURL(file);
    setDecoding(true);
    let text: string | undefined;
    try {
      text = await decodeBarcodePhoto(objectUrl);
    } catch (error) {
      const key = error instanceof BarcodePhotoError && error.kind !== 'not-found'
        ? 'mobile.cameraPhotoUnreadable'
        : 'mobile.cameraScanFailed';
      if (generation === photoGeneration.current) message.error(t(key));
    } finally {
      URL.revokeObjectURL(objectUrl);
      if (generation === photoGeneration.current) setDecoding(false);
      if (captureRef.current) captureRef.current.value = '';
      if (albumRef.current) albumRef.current.value = '';
      if (generation === photoGeneration.current) window.setTimeout(() => inputRef.current?.focus(), 0);
    }
    if (text && generation === photoGeneration.current) {
      setValue('');
      message.success(t('mobile.cameraScanSuccess'));
      onScan(text);
    }
  };

  return (
    <div className="mobile-scan-field">
      <span>{label}</span>
      <input
        accept="image/*"
        aria-label={t('mobile.scanTakePhoto')}
        capture="environment"
        hidden
        onChange={(event) => void decodeCapture(event.target.files?.[0])}
        ref={captureRef}
        type="file"
      />
      <input accept="image/*" aria-label={t('mobile.scanFromAlbum')} hidden type="file" ref={albumRef}
        onChange={(event) => void decodeCapture(event.target.files?.[0])} />
      <Input
        aria-label={label}
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
              setScannerOpen(true);
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
      {scannerOpen && !disabled ? <LiveScanDialog
        onClose={() => setScannerOpen(false)}
        onPhoto={(source) => {
          setScannerOpen(false);
          (source === 'album' ? albumRef : captureRef).current?.click();
        }}
        onScan={(text) => {
          setScannerOpen(false);
          setValue('');
          message.success(t('mobile.cameraScanSuccess'));
          onScan(text);
        }}
      /> : null}
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
    </div>
  );
}
