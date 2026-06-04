/**
 * JSON-based in-memory data store with seed data.
 * 替代 SQLite 的轻量数据层,无原生依赖,适合演示。
 */
import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const DATA_DIR = path.join(__dirname, '..', '..', '.data');
const DATA_FILE = path.join(DATA_DIR, 'linke-ai.json');

export type Role = 'owner' | 'manager' | 'bd';
export type LeadStatus = 'pending' | 'added' | 'visited' | 'won' | 'lost';
export type CampaignStatus = 'draft' | 'running' | 'paused' | 'done';

export interface User {
  id: string;
  phone: string;
  name: string;
  createdAt: string;
}

export interface Store {
  id: string;
  name: string;
  lng: number;
  lat: number;
  address: string;
  category: string;
  ownerUserId: string;
  createdAt: string;
}

export interface StoreMember {
  storeId: string;
  userId: string;
  role: Role;
}

export interface Campaign {
  id: string;
  storeId: string;
  name: string;
  radiusKm: 3 | 5 | 8 | 10;
  flow: FlowNode[];
  scheduleAt?: string;
  status: CampaignStatus;
  createdAt: string;
}

export interface FlowNode {
  id: string;
  type: 'copy' | 'wait' | 'channel' | 'card';
  channel?: 'sms' | 'wechat' | 'douyin' | 'card';
  min?: number;
  text?: string;
}

export interface Lead {
  id: string;
  storeId: string;
  campaignId?: string;
  fromRadius: 3 | 5 | 8 | 10;
  name: string;
  phone: string;
  status: LeadStatus;
  ownerId?: string;
  note?: string;
  createdAt: string;
}

export interface LeadEvent {
  id: string;
  leadId: string;
  type: 'touch' | 'added' | 'visited' | 'note' | 'won' | 'lost';
  payload?: Record<string, unknown>;
  createdAt: string;
}

export interface Data {
  users: User[];
  stores: Store[];
  members: StoreMember[];
  campaigns: Campaign[];
  leads: Lead[];
  events: LeadEvent[];
}

const seed = (): Data => {
  const now = new Date();
  const iso = (offsetDays = 0) => {
    const d = new Date(now);
    d.setDate(d.getDate() - offsetDays);
    return d.toISOString();
  };
  return {
    users: [
      { id: 'u_demo', phone: '13800000000', name: '林店长', createdAt: iso(60) },
      { id: 'u_bd1', phone: '13800000001', name: '小赵 BD', createdAt: iso(40) },
      { id: 'u_bd2', phone: '13800000002', name: '小王 BD', createdAt: iso(30) },
    ],
    stores: [
      {
        id: 's_demo',
        name: '邻客 AI · 朝阳体验店',
        lng: 116.480885,
        lat: 39.989410,
        address: '北京市朝阳区光华路 9 号',
        category: '精品咖啡 · 烘焙',
        ownerUserId: 'u_demo',
        createdAt: iso(50),
      },
      {
        id: 's_demo2',
        name: '邻客 AI · 海淀分店',
        lng: 116.310316,
        lat: 39.992718,
        address: '北京市海淀区中关村大街 27 号',
        category: '轻食简餐',
        ownerUserId: 'u_demo',
        createdAt: iso(20),
      },
    ],
    members: [
      { storeId: 's_demo', userId: 'u_demo', role: 'owner' },
      { storeId: 's_demo', userId: 'u_bd1', role: 'bd' },
      { storeId: 's_demo', userId: 'u_bd2', role: 'bd' },
      { storeId: 's_demo2', userId: 'u_demo', role: 'owner' },
    ],
    campaigns: [
      {
        id: 'c_3km',
        storeId: 's_demo',
        name: '3 公里写字楼早午餐拓客',
        radiusKm: 3,
        flow: [
          { id: 'n1', type: 'channel', channel: 'wechat' },
          { id: 'n2', type: 'wait', min: 1440 },
          { id: 'n3', type: 'channel', channel: 'sms' },
        ],
        status: 'running',
        createdAt: iso(15),
      },
      {
        id: 'c_5km',
        storeId: 's_demo',
        name: '5 公里住宅区周末秒杀',
        radiusKm: 5,
        flow: [
          { id: 'n1', type: 'channel', channel: 'douyin' },
          { id: 'n2', type: 'card' },
        ],
        status: 'running',
        createdAt: iso(10),
      },
      {
        id: 'c_8km',
        storeId: 's_demo',
        name: '8 公里企业下午茶合作',
        radiusKm: 8,
        flow: [{ id: 'n1', type: 'channel', channel: 'wechat' }],
        status: 'paused',
        createdAt: iso(7),
      },
    ],
    leads: [
      { id: 'l_001', storeId: 's_demo', campaignId: 'c_3km', fromRadius: 3, name: '王女士', phone: '13900000001', status: 'added', ownerId: 'u_bd1', createdAt: iso(5) },
      { id: 'l_002', storeId: 's_demo', campaignId: 'c_3km', fromRadius: 3, name: '李先生', phone: '13900000002', status: 'visited', ownerId: 'u_bd1', createdAt: iso(4) },
      { id: 'l_003', storeId: 's_demo', campaignId: 'c_5km', fromRadius: 5, name: '张小姐', phone: '13900000003', status: 'pending', createdAt: iso(3) },
      { id: 'l_004', storeId: 's_demo', campaignId: 'c_3km', fromRadius: 3, name: '陈先生', phone: '13900000004', status: 'won', ownerId: 'u_bd2', createdAt: iso(2) },
      { id: 'l_005', storeId: 's_demo', campaignId: 'c_5km', fromRadius: 5, name: '刘女士', phone: '13900000005', status: 'added', ownerId: 'u_bd2', createdAt: iso(2) },
      { id: 'l_006', storeId: 's_demo', campaignId: 'c_3km', fromRadius: 3, name: '赵先生', phone: '13900000006', status: 'pending', createdAt: iso(1) },
      { id: 'l_007', storeId: 's_demo', campaignId: 'c_8km', fromRadius: 8, name: '孙女士', phone: '13900000007', status: 'lost', createdAt: iso(1) },
      { id: 'l_008', storeId: 's_demo', campaignId: 'c_5km', fromRadius: 5, name: '周先生', phone: '13900000008', status: 'visited', ownerId: 'u_bd1', createdAt: iso(0) },
    ],
    events: [],
  };
};

let cache: Data | null = null;

const load = (): Data => {
  if (cache) return cache;
  try {
    if (!fs.existsSync(DATA_DIR)) fs.mkdirSync(DATA_DIR, { recursive: true });
    if (fs.existsSync(DATA_FILE)) {
      const raw = fs.readFileSync(DATA_FILE, 'utf-8');
      cache = JSON.parse(raw) as Data;
      return cache;
    }
  } catch (e) {
    console.warn('data load failed, use seed', e);
  }
  cache = seed();
  persist();
  return cache;
};

const persist = () => {
  if (!cache) return;
  try {
    if (!fs.existsSync(DATA_DIR)) fs.mkdirSync(DATA_DIR, { recursive: true });
    fs.writeFileSync(DATA_FILE, JSON.stringify(cache, null, 2), 'utf-8');
  } catch (e) {
    console.warn('data persist failed', e);
  }
};

export const db = {
  read: (): Data => load(),
  write: (mutator: (d: Data) => void) => {
    const d = load();
    mutator(d);
    persist();
  },
  reset: () => {
    cache = seed();
    persist();
  },
  id: (prefix: string) => `${prefix}_${Math.random().toString(36).slice(2, 9)}`,
};
