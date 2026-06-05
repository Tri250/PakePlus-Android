/**
 * 5.4 NFC 碰一碰数据追踪接口 Mock
 * POST /api/nfc/tap_event
 */

export type NFCAction = 'coupon_claim' | 'member_join' | 'page_view';

export interface NFCTapEvent {
  tag_id: string;
  action: NFCAction;
  user_openid: string;
  timestamp: string; // ISO
  lat?: number;
  lng?: number;
  device_id?: string;
  staff_id?: string; // 触达店员（地推场景）
}

export interface NFCTapResponse {
  ok: boolean;
  event_id: string;
  attribution?: {
    channel: string;          // 归因渠道
    campaign_id?: string;     // 活动 ID
    staff_id?: string;        // 触达店员
    conversion_path: string[]; // 完整归因路径
  };
}

const actionLabelMap: Record<NFCAction, string> = {
  coupon_claim: '领券',
  member_join: '入会',
  page_view: '查看',
};

const tagPool = [
  { tag_id: 'NFC-BJ-001', location: '国贸地铁站出口', staff: 'STAFF-001' },
  { tag_id: 'NFC-BJ-002', location: '三里屯太古里', staff: 'STAFF-002' },
  { tag_id: 'NFC-BJ-003', location: '望京 SOHO 楼下', staff: 'STAFF-003' },
  { tag_id: 'NFC-BJ-004', location: '西单大悦城门口', staff: 'STAFF-004' },
  { tag_id: 'NFC-BJ-005', location: '中关村鼎好大厦', staff: 'STAFF-005' },
];

// 模拟事件流
let eventCounter = 1000;
const events: Array<NFCTapEvent & { event_id: string; receivedAt: number }> = [];

export async function postNFCTapEvent(input: NFCTapEvent): Promise<NFCTapResponse> {
  await new Promise((r) => setTimeout(r, 250));

  const event_id = `EVT-${(++eventCounter).toString().padStart(6, '0')}`;
  const tagInfo = tagPool.find((t) => t.tag_id === input.tag_id);

  events.push({ ...input, event_id, receivedAt: Date.now() });

  // 归因链路
  const conversion_path: string[] = ['地推触达'];
  if (input.action === 'coupon_claim') conversion_path.push('查看优惠券');
  if (input.action === 'page_view') conversion_path.push('浏览活动页');
  if (input.action === 'member_join') conversion_path.push('完成注册');

  return {
    ok: true,
    event_id,
    attribution: {
      channel: '地推-NFC 触达',
      staff_id: tagInfo?.staff,
      campaign_id: 'CAMP-2026Q2-NFC',
      conversion_path,
    },
  };
}

export function listRecentTapEvents(limit = 20) {
  return events.slice(-limit).reverse();
}

export function listTagPool() {
  return tagPool;
}

export function getActionLabel(action: NFCAction): string {
  return actionLabelMap[action];
}
