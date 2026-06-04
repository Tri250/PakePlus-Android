/**
 * 前端共享类型 - 与 BFF /api/* 响应保持一致
 */
export type Role = 'owner' | 'manager' | 'bd';
export type LeadStatus = 'pending' | 'added' | 'visited' | 'won' | 'lost';
export type CampaignStatus = 'draft' | 'running' | 'paused' | 'done';
export type RadiusKm = 3 | 5 | 8 | 10;
export type Channel = 'sms' | 'wechat' | 'douyin' | 'card';

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

export interface FlowNode {
  id: string;
  type: 'copy' | 'wait' | 'channel' | 'card';
  channel?: Channel;
  min?: number;
  text?: string;
}

export interface Campaign {
  id: string;
  storeId: string;
  name: string;
  radiusKm: RadiusKm;
  flow: FlowNode[];
  scheduleAt?: string;
  status: CampaignStatus;
  createdAt: string;
}

export interface Lead {
  id: string;
  storeId: string;
  campaignId?: string;
  fromRadius: RadiusKm;
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

export interface RadiusStats {
  km: RadiusKm;
  population: number;
  hotSpots: number;
  avgScore: number;
  competitorCount: number;
}

export interface POI {
  id: string;
  name: string;
  category: 'office' | 'mall' | 'school' | 'residence' | 'subway' | 'park';
  lng: number;
  lat: number;
  hotScore: number;
  radiusKm: RadiusKm;
}

export interface Persona {
  summary: string;
  radar: { dim: string; value: number }[];
  keywords: string[];
  highlights: string[];
}

export interface Copy {
  title: string;
  body: string;
  cta: string;
}

export interface Overview {
  reach: number;
  addedWechat: number;
  visited: number;
  won: number;
  roi: number;
  trend: { date: string; reach: number; added: number; visited: number }[];
  radiusCompare: { km: RadiusKm; cost: number; conv: number; count: number }[];
}
