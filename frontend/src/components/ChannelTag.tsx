import { Tag } from 'antd';
import { getChannelColor } from '../pages/integrations/integrationUtils';

export function ChannelTag({ channel }: { channel?: string }): JSX.Element {
  return <Tag color={getChannelColor(channel)}>{channel ?? '-'}</Tag>;
}
