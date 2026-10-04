import { Alert, Button, Modal, Spin } from 'antd';
import { useEffect, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { startLiveBarcodeScanner, type LiveScanFailure } from './liveBarcodeScanner';

interface LiveScanDialogProps {
  onClose: () => void;
  onScan: (text: string) => void;
  onPhoto: (source: 'camera' | 'album') => void;
}

const FAILURE_KEYS: Record<LiveScanFailure, string> = {
  'secure-required': 'mobile.liveScanSecureRequired',
  unsupported: 'mobile.liveScanUnsupported',
  permission: 'mobile.liveScanPermission',
  unavailable: 'mobile.liveScanUnavailable',
  interrupted: 'mobile.liveScanInterrupted',
};

function CameraView({ onScan, onPhoto }: LiveScanDialogProps): JSX.Element {
  const videoRef = useRef<HTMLVideoElement>(null);
  const sessionRef = useRef<{ stop: () => void }>();
  const resultRef = useRef(onScan);
  resultRef.current = onScan;
  const [attempt, setAttempt] = useState(0);
  const [ready, setReady] = useState(false);
  const [failure, setFailure] = useState<LiveScanFailure>();
  const { t } = useTranslation();

  useEffect(() => {
    setReady(false);
    setFailure(undefined);
    if (!videoRef.current) return;
    const session = startLiveBarcodeScanner(videoRef.current, {
      onReady: () => setReady(true),
      onResult: (text) => resultRef.current(text),
      onError: setFailure,
    });
    sessionRef.current = session;
    return session.stop;
  }, [attempt]);

  const pickPhoto = (source: 'camera' | 'album'): void => {
    // Release before invoking the system picker, even if React batches unmount.
    sessionRef.current?.stop();
    onPhoto(source);
  };

  return (
    <div className="mobile-live-scanner">
      <p className="mobile-live-instructions">{t('mobile.liveScanHint')}</p>
      <div className="mobile-live-preview" hidden={!!failure}>
        <video aria-label={t('mobile.liveScanPreview')} autoPlay muted playsInline ref={videoRef} />
        <div aria-hidden="true" className="mobile-live-guide" />
        {!ready && !failure ? <div className="mobile-live-starting"><Spin /><span>{t('mobile.liveScanStarting')}</span></div> : null}
      </div>
      {failure ? <Alert role="alert" showIcon type="warning" message={t(FAILURE_KEYS[failure])} /> : null}
      <p aria-live="polite" className="mobile-live-status">{ready && !failure ? t('mobile.liveScanSearching') : t('mobile.liveScanPrivacy')}</p>
      <div className="mobile-live-actions">
        {failure && !['secure-required', 'unsupported'].includes(failure)
          ? <Button size="large" type="primary" onClick={() => setAttempt((value) => value + 1)}>{t('mobile.liveScanRetry')}</Button> : null}
        <Button size="large" onClick={() => pickPhoto('album')}>{t('mobile.scanFromAlbum')}</Button>
        <Button size="large" onClick={() => pickPhoto('camera')}>{t('mobile.scanTakePhoto')}</Button>
      </div>
    </div>
  );
}

export function LiveScanDialog(props: LiveScanDialogProps): JSX.Element {
  const { t } = useTranslation();
  return (
    <Modal open className="mobile-live-modal" title={t('mobile.liveScanTitle')} footer={null}
      onCancel={props.onClose} maskClosable={false} width="100%" style={{ top: 0, paddingBottom: 0 }}>
      <CameraView {...props} />
    </Modal>
  );
}
