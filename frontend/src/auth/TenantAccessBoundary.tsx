import { Alert, Button, Space, Spin } from 'antd';
import { useEffect, useState, type PropsWithChildren } from 'react';
import { apiRequest } from '../api/http';
import { useAuth } from './AuthProvider';

interface Access { status: string; businessAvailable: boolean; message: string; }
export function TenantAccessBoundary({ children }: PropsWithChildren): JSX.Element {
  const { session, logout } = useAuth();
  const [result, setResult] = useState<{ token: string; access?: Access; failed?: boolean }>();
  const [attempt, setAttempt] = useState(0);
  useEffect(() => {
    if (!session) return;
    let active = true;
    let running = false;
    const refresh = async () => {
      if (running) return;
      running = true;
      try {
        const access = await apiRequest<Access>('/api/company-access/status');
        if (active) setResult({ token: session.token, access });
      } catch {
        if (active) setResult({ token: session.token, failed: true });
      } finally { running = false; }
    };
    void refresh();
    const timer = window.setInterval(() => void refresh(), 15_000);
    return () => { active = false; window.clearInterval(timer); };
  }, [session?.token, attempt]);
  if (!session || result?.token !== session.token) return <Spin tip="正在检查租户服务状态" />;
  if (!result.failed && result.access?.status === 'ACTIVE' && result.access.businessAvailable) return <>{children}</>;
  return <div style={{ maxWidth: 640, margin: '64px auto', padding: 24 }}>
    <Alert showIcon type={result.failed ? 'error' : 'warning'}
      message={result.failed ? '暂时无法确认租户服务状态' : '租户服务已暂停'}
      description="当前不能开展业务、导出或同步。恢复服务后需要重新登录；如有疑问，请联系平台。" />
    <Space style={{ marginTop: 16 }}><Button onClick={() => setAttempt(value => value + 1)}>重新检查</Button><Button onClick={logout}>退出登录</Button></Space>
  </div>;
}
