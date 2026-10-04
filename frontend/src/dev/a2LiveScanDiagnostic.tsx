// Development-only HTML entry; intentionally absent from the production build.
// Exercise the real shared UI without credentials, tasks or inventory writes.
import { App as AntdApp, Alert } from 'antd';
import { createRoot } from 'react-dom/client';
import { useState } from 'react';
import 'antd/dist/reset.css';
import '../locales/i18n';
import '../styles.css';
import { ScanInput } from '../pages/mobile/ScanInput';

function Diagnostic(): JSX.Element {
  const [result, setResult] = useState('尚未识别');
  const [count, setCount] = useState(0);
  return <AntdApp><main style={{ maxWidth: 720, margin: '24px auto', padding: 20 }}>
    <h1>A2 实时扫码预验收</h1>
    <p>A2-LIVE-0906-1 · 使用与盘点、收货、拣货相同的扫码组件。</p>
    <Alert type="info" showIcon message="无需登录。仅显示识别结果，不提交业务数据，不改变库存。" />
    <p>安全页面：{window.isSecureContext ? '是' : '否（HTTP 仅可拍照或相册识别）'}<br />
      实时相机接口：{typeof navigator.mediaDevices?.getUserMedia === 'function' ? '可用（仍需允许相机权限）' : '不可用'}</p>
    <ScanInput label="扫描一枚二维码或条形码" placeholder="识别结果也可以手工输入" onScan={(text) => {
      setResult(text);
      setCount((value) => value + 1);
    }} />
    <p role="status">识别结果：{result} · 已接收 {count} 次</p>
    <p>每次打开扫码只接收一次结果；关闭后可再次扫码。请记录不同距离、角度和光线下的结果。</p>
    <p style={{ overflowWrap: 'anywhere', fontSize: 12 }}>{navigator.userAgent}</p>
  </main></AntdApp>;
}
createRoot(document.getElementById('root')!).render(<Diagnostic />);
