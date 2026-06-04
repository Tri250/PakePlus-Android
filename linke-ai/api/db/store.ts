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
      // ============ 华北 ============
      {
        id: 's_bj_cy',
        name: '邻客 AI · 北京朝阳体验店',
        lng: 116.480885,
        lat: 39.989410,
        address: '北京市朝阳区光华路 9 号',
        category: '精品咖啡 · 烘焙',
        ownerUserId: 'u_demo',
        createdAt: iso(50),
      },
      {
        id: 's_bj_hd',
        name: '邻客 AI · 北京海淀分店',
        lng: 116.310316,
        lat: 39.992718,
        address: '北京市海淀区中关村大街 27 号',
        category: '轻食简餐',
        ownerUserId: 'u_demo',
        createdAt: iso(20),
      },
      {
        id: 's_tj',
        name: '邻客 AI · 天津和平店',
        lng: 117.2030,
        lat: 39.1310,
        address: '天津市和平区南京路 128 号',
        category: '精品咖啡',
        ownerUserId: 'u_demo',
        createdAt: iso(45),
      },
      {
        id: 's_sjz',
        name: '邻客 AI · 石家庄桥西店',
        lng: 114.4900,
        lat: 38.0450,
        address: '石家庄市桥西区中山路 100 号',
        category: '烘焙 · 甜品',
        ownerUserId: 'u_demo',
        createdAt: iso(35),
      },
      // ============ 华东 ============
      {
        id: 's_sh_pd',
        name: '邻客 AI · 上海浦东店',
        lng: 121.5063,
        lat: 31.2304,
        address: '上海市浦东新区陆家嘴环路 999 号',
        category: '精品咖啡 · 轻食',
        ownerUserId: 'u_demo',
        createdAt: iso(48),
      },
      {
        id: 's_sh_xh',
        name: '邻客 AI · 上海徐汇店',
        lng: 121.4450,
        lat: 31.1680,
        address: '上海市徐汇区漕溪北路 88 号',
        category: '简餐 · 沙拉',
        ownerUserId: 'u_demo',
        createdAt: iso(38),
      },
      {
        id: 's_hz',
        name: '邻客 AI · 杭州西湖店',
        lng: 120.1560,
        lat: 30.2750,
        address: '杭州市西湖区延安路 500 号',
        category: '精品咖啡 · 茶',
        ownerUserId: 'u_demo',
        createdAt: iso(42),
      },
      {
        id: 's_nj',
        name: '邻客 AI · 南京新街口店',
        lng: 118.7900,
        lat: 32.0620,
        address: '南京市玄武区中山路 188 号',
        category: '烘焙 · 咖啡',
        ownerUserId: 'u_demo',
        createdAt: iso(40),
      },
      {
        id: 's_sz',
        name: '邻客 AI · 苏州园区店',
        lng: 120.6350,
        lat: 31.3100,
        address: '苏州市工业园区金鸡湖大道 999 号',
        category: '轻食 · 健康餐',
        ownerUserId: 'u_demo',
        createdAt: iso(32),
      },
      // ============ 华南 ============
      {
        id: 's_gz',
        name: '邻客 AI · 广州天河店',
        lng: 113.3265,
        lat: 23.1238,
        address: '广州市天河区珠江新城兴盛路 8 号',
        category: '精品咖啡 · 简餐',
        ownerUserId: 'u_demo',
        createdAt: iso(46),
      },
      {
        id: 's_sz_sz',
        name: '邻客 AI · 深圳福田店',
        lng: 114.0583,
        lat: 22.5431,
        address: '深圳市福田区福华三路 88 号',
        category: '咖啡 · 轻食',
        ownerUserId: 'u_demo',
        createdAt: iso(44),
      },
      {
        id: 's_fz',
        name: '邻客 AI · 福州鼓楼店',
        lng: 119.3100,
        lat: 26.0650,
        address: '福州市鼓楼区东街口 168 号',
        category: '茶饮 · 甜品',
        ownerUserId: 'u_demo',
        createdAt: iso(30),
      },
      {
        id: 's_nn',
        name: '邻客 AI · 南宁青秀店',
        lng: 108.3550,
        lat: 22.8200,
        address: '南宁市青秀区民族大道 168 号',
        category: '咖啡 · 烘焙',
        ownerUserId: 'u_demo',
        createdAt: iso(28),
      },
      // ============ 华中 ============
      {
        id: 's_wh',
        name: '邻客 AI · 武汉江汉店',
        lng: 114.2830,
        lat: 30.5860,
        address: '武汉市江汉区解放大道 688 号',
        category: '精品咖啡 · 简餐',
        ownerUserId: 'u_demo',
        createdAt: iso(43),
      },
      {
        id: 's_cs',
        name: '邻客 AI · 长沙芙蓉店',
        lng: 112.9400,
        lat: 28.2300,
        address: '长沙市芙蓉区五一广场 188 号',
        category: '咖啡 · 甜品',
        ownerUserId: 'u_demo',
        createdAt: iso(36),
      },
      {
        id: 's_zz',
        name: '邻客 AI · 郑州金水店',
        lng: 113.6500,
        lat: 34.7550,
        address: '郑州市金水区花园路 100 号',
        category: '轻食 · 沙拉',
        ownerUserId: 'u_demo',
        createdAt: iso(34),
      },
      // ============ 西南 ============
      {
        id: 's_cd',
        name: '邻客 AI · 成都锦江店',
        lng: 104.0680,
        lat: 30.5780,
        address: '成都市锦江区春熙路 168 号',
        category: '精品咖啡 · 烘焙',
        ownerUserId: 'u_demo',
        createdAt: iso(47),
      },
      {
        id: 's_km',
        name: '邻客 AI · 昆明五华店',
        lng: 102.7200,
        lat: 25.0450,
        address: '昆明市五华区正义路 99 号',
        category: '茶饮 · 咖啡',
        ownerUserId: 'u_demo',
        createdAt: iso(26),
      },
      {
        id: 's_gy',
        name: '邻客 AI · 贵阳云岩店',
        lng: 106.7100,
        lat: 26.5850,
        address: '贵阳市云岩区中华北路 88 号',
        category: '咖啡 · 甜品',
        ownerUserId: 'u_demo',
        createdAt: iso(24),
      },
      // ============ 西北 ============
      {
        id: 's_xa',
        name: '邻客 AI · 西安雁塔店',
        lng: 108.9500,
        lat: 34.2100,
        address: '西安市雁塔区小寨路 100 号',
        category: '精品咖啡 · 轻食',
        ownerUserId: 'u_demo',
        createdAt: iso(37),
      },
      // ============ 东北 ============
      {
        id: 's_sy',
        name: '邻客 AI · 沈阳和平店',
        lng: 123.4350,
        lat: 41.8050,
        address: '沈阳市和平区太原街 68 号',
        category: '咖啡 · 烘焙',
        ownerUserId: 'u_demo',
        createdAt: iso(33),
      },
      {
        id: 's_dl',
        name: '邻客 AI · 大连中山店',
        lng: 121.6200,
        lat: 38.9200,
        address: '大连市中山区人民路 58 号',
        category: '轻食 · 简餐',
        ownerUserId: 'u_demo',
        createdAt: iso(29),
      },
      {
        id: 's_hrb',
        name: '邻客 AI · 哈尔滨南岗店',
        lng: 126.6400,
        lat: 45.8000,
        address: '哈尔滨市南岗区果戈里大街 168 号',
        category: '咖啡 · 甜品',
        ownerUserId: 'u_demo',
        createdAt: iso(22),
      },
      // ============ 重庆 ============
      {
        id: 's_cq',
        name: '邻客 AI · 重庆渝中店',
        lng: 106.5880,
        lat: 29.5630,
        address: '重庆市渝中区解放碑步行街 88 号',
        category: '精品咖啡 · 轻食',
        ownerUserId: 'u_demo',
        createdAt: iso(41),
      },
      // ============ 厦门 ============
      {
        id: 's_xm',
        name: '邻客 AI · 厦门思明店',
        lng: 118.0880,
        lat: 24.4750,
        address: '厦门市思明区中山路 198 号',
        category: '茶饮 · 咖啡',
        ownerUserId: 'u_demo',
        createdAt: iso(31),
      },
    ],
    members: [
      // 北京朝阳店
      { storeId: 's_bj_cy', userId: 'u_demo', role: 'owner' },
      { storeId: 's_bj_cy', userId: 'u_bd1', role: 'bd' },
      { storeId: 's_bj_cy', userId: 'u_bd2', role: 'bd' },
      // 北京海淀店
      { storeId: 's_bj_hd', userId: 'u_demo', role: 'owner' },
      { storeId: 's_bj_hd', userId: 'u_bd1', role: 'bd' },
      // 上海浦东店
      { storeId: 's_sh_pd', userId: 'u_demo', role: 'owner' },
      // 广州天河店
      { storeId: 's_gz', userId: 'u_demo', role: 'owner' },
      // 成都锦江店
      { storeId: 's_cd', userId: 'u_demo', role: 'owner' },
      // 杭州西湖店
      { storeId: 's_hz', userId: 'u_demo', role: 'owner' },
      // 深圳福田店
      { storeId: 's_sz_sz', userId: 'u_demo', role: 'owner' },
    ],
    campaigns: [
      {
        id: 'c_bj_3km',
        storeId: 's_bj_cy',
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
        id: 'c_bj_5km',
        storeId: 's_bj_cy',
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
        id: 'c_bj_8km',
        storeId: 's_bj_cy',
        name: '8 公里企业下午茶合作',
        radiusKm: 8,
        flow: [{ id: 'n1', type: 'channel', channel: 'wechat' }],
        status: 'paused',
        createdAt: iso(7),
      },
      {
        id: 'c_sh_5km',
        storeId: 's_sh_pd',
        name: '上海陆家嘴 5km 白领下午茶',
        radiusKm: 5,
        flow: [{ id: 'n1', type: 'channel', channel: 'wechat' }],
        status: 'running',
        createdAt: iso(5),
      },
      {
        id: 'c_gz_3km',
        storeId: 's_gz',
        name: '广州珠江新城 3km 午间特惠',
        radiusKm: 3,
        flow: [
          { id: 'n1', type: 'channel', channel: 'sms' },
          { id: 'n2', type: 'card' },
        ],
        status: 'running',
        createdAt: iso(3),
      },
    ],
    leads: [
      // 北京朝阳店线索
      { id: 'l_bj_001', storeId: 's_bj_cy', campaignId: 'c_bj_3km', fromRadius: 3, name: '王女士', phone: '13900000001', status: 'added', ownerId: 'u_bd1', createdAt: iso(5) },
      { id: 'l_bj_002', storeId: 's_bj_cy', campaignId: 'c_bj_3km', fromRadius: 3, name: '李先生', phone: '13900000002', status: 'visited', ownerId: 'u_bd1', createdAt: iso(4) },
      { id: 'l_bj_003', storeId: 's_bj_cy', campaignId: 'c_bj_5km', fromRadius: 5, name: '张小姐', phone: '13900000003', status: 'pending', createdAt: iso(3) },
      { id: 'l_bj_004', storeId: 's_bj_cy', campaignId: 'c_bj_3km', fromRadius: 3, name: '陈先生', phone: '13900000004', status: 'won', ownerId: 'u_bd2', createdAt: iso(2) },
      { id: 'l_bj_005', storeId: 's_bj_cy', campaignId: 'c_bj_5km', fromRadius: 5, name: '刘女士', phone: '13900000005', status: 'added', ownerId: 'u_bd2', createdAt: iso(2) },
      { id: 'l_bj_006', storeId: 's_bj_cy', campaignId: 'c_bj_3km', fromRadius: 3, name: '赵先生', phone: '13900000006', status: 'pending', createdAt: iso(1) },
      { id: 'l_bj_007', storeId: 's_bj_cy', campaignId: 'c_bj_8km', fromRadius: 8, name: '孙女士', phone: '13900000007', status: 'lost', createdAt: iso(1) },
      { id: 'l_bj_008', storeId: 's_bj_cy', campaignId: 'c_bj_5km', fromRadius: 5, name: '周先生', phone: '13900000008', status: 'visited', ownerId: 'u_bd1', createdAt: iso(0) },
      // 上海浦东店线索
      { id: 'l_sh_001', storeId: 's_sh_pd', campaignId: 'c_sh_5km', fromRadius: 5, name: '吴女士', phone: '13900000009', status: 'added', createdAt: iso(4) },
      { id: 'l_sh_002', storeId: 's_sh_pd', campaignId: 'c_sh_5km', fromRadius: 5, name: '郑先生', phone: '13900000010', status: 'pending', createdAt: iso(3) },
      { id: 'l_sh_003', storeId: 's_sh_pd', campaignId: 'c_sh_5km', fromRadius: 5, name: '黄女士', phone: '13900000011', status: 'visited', createdAt: iso(2) },
      // 广州天河店线索
      { id: 'l_gz_001', storeId: 's_gz', campaignId: 'c_gz_3km', fromRadius: 3, name: '马先生', phone: '13900000012', status: 'added', createdAt: iso(3) },
      { id: 'l_gz_002', storeId: 's_gz', campaignId: 'c_gz_3km', fromRadius: 3, name: '林女士', phone: '13900000013', status: 'pending', createdAt: iso(2) },
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
