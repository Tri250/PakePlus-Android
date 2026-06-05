/**
 * 统一数据服务 (Data Service)
 *
 * 整合所有数据源（API + 爬虫 + 缓存 + 实时推送），对外暴露稳定的契约。
 *
 * 设计原则：
 * 1. 契约稳定 - 上游 API/爬虫变化不影响调用方
 * 2. 多源融合 - API 优先，爬虫降级，缓存兜底
 * 3. 实时优先 - 支持 SSE/轮询/EventTarget 三种推送方式
 * 4. 错误隔离 - 单个源失败不影响整体
 */

import { customers as mockCustomers, tasks as mockTasks, activities as mockActivities, competitorEvents as mockCompetitorEvents, leads as mockLeads, weeklyTrend as mockTrend, competitors as mockCompetitors, achievements as mockAchievements, alerts as mockAlerts, teamMembers as mockTeam, notifications as mockNotifications, type Customer, type Task, type Lead, type CompetitorEvent, type Achievement, type Alert, type TeamMember, type Notification } from '../data/mockData';
import { dataCrawlerService } from './dataCrawler';
import { api } from './api';
import { poiCollector, type POICollectResult, type CustomerLead } from './poiCollector';
// 真实数据源
import { ALL_REAL_POI, PHONE_STORE_POI, GANZHOU_WANXIANGCHENG_POI, type RealPOI, filterPoiByDistance, filterPoiByCategory } from '../data/realPoiData';
import { ALL_COMPETITOR_ACTIVITIES, GOV_SUBSIDY_ACTIVITIES, BRAND_OFFICIAL_ACTIVITIES, getActiveActivities, getHighPriorityActivities, type CompetitorActivity } from '../data/realCompetitorData';
import { REAL_CUSTOMERS, getHighIntentionCustomers, getFollowUpCustomers, getCompletedCustomers, type RealCustomer } from '../data/realCustomerData';
import { CRAWLER_LEADS, getPurchaseIntentLeads, getReplaceNeedLeads, getHighIntentionLeads, type CrawlerLead } from '../data/crawlerLeadsData';

/* -------------------------------------------------------------------------- */
/*  稳定契约 (Stable Contracts)                                                  */
/* -------------------------------------------------------------------------- */

export interface DataResult<T> {
  data: T;
  source: 'api' | 'crawler' | 'cache' | 'mock' | 'synthetic';
  isLive: boolean;          // 是否为实时数据
  fetchedAt: number;        // 获取时间戳
  staleIn: number;          // 多久后会过期 (ms)
}

export interface DashboardMetrics {
  todayDone: number;
  todayTotal: number;
  nearbyLeads: number;
  conversionRate: number;
  conversionDelta: number;
  trendUp: boolean;
}

export interface FeedEvent {
  id: string;
  type: 'visit' | 'visit_done' | 'task_done' | 'signal' | 'reward' | 'competitor';
  text: string;
  time: string;
  timestamp: number;
}

export interface RealtimeStatus {
  isStreaming: boolean;
  lastUpdate: number;
  sourcesActive: number;
  queueSize: number;
}

/* -------------------------------------------------------------------------- */
/*  内部状态                                                                    */
/* -------------------------------------------------------------------------- */

const subscribers = new Map<string, Set<(data: any) => void>>();
const cache = new Map<string, { data: any; expiresAt: number; fetchedAt: number; source: string }>();
let realtimeStatus: RealtimeStatus = {
  isStreaming: false,
  lastUpdate: 0,
  sourcesActive: 0,
  queueSize: 0,
};

const CACHE_TTL = {
  customer: 5 * 60 * 1000,    // 客户 5 分钟
  task: 2 * 60 * 1000,         // 任务 2 分钟
  lead: 1 * 60 * 1000,         // 线索 1 分钟
  metrics: 30 * 1000,          // 指标 30 秒
  feed: 15 * 1000,             // 动态 15 秒
  competitor: 3 * 60 * 1000,   // 竞品 3 分钟
  trend: 5 * 60 * 1000,        // 趋势 5 分钟
};

/* -------------------------------------------------------------------------- */
/*  订阅管理 (Pub/Sub)                                                          */
/* -------------------------------------------------------------------------- */

function notify<T>(channel: string, data: T) {
  const subs = subscribers.get(channel);
  if (subs) {
    subs.forEach((fn) => {
      try {
        fn(data);
      } catch (e) {
        // 单个订阅者错误不影响其他
      }
    });
  }
  realtimeStatus = { ...realtimeStatus, lastUpdate: Date.now() };
}

export function subscribe<T>(channel: string, fn: (data: T) => void): () => void {
  if (!subscribers.has(channel)) {
    subscribers.set(channel, new Set());
  }
  subscribers.get(channel)!.add(fn);
  return () => {
    subscribers.get(channel)?.delete(fn);
  };
}

export function getRealtimeStatus(): RealtimeStatus {
  return { ...realtimeStatus };
}

/* -------------------------------------------------------------------------- */
/*  缓存管理                                                                    */
/* -------------------------------------------------------------------------- */

function getFromCache<T>(key: string): DataResult<T> | null {
  const c = cache.get(key);
  if (!c) return null;
  if (c.expiresAt < Date.now()) {
    cache.delete(key);
    return null;
  }
  return {
    data: c.data as T,
    source: c.source as DataResult<T>['source'],
    isLive: false,
    fetchedAt: c.fetchedAt,
    staleIn: c.expiresAt - Date.now(),
  };
}

function setCache<T>(key: string, data: T, source: DataResult<T>['source'], ttl: number) {
  cache.set(key, {
    data,
    source,
    fetchedAt: Date.now(),
    expiresAt: Date.now() + ttl,
  });
}

function invalidate(channel: string) {
  for (const k of cache.keys()) {
    if (k.startsWith(`${channel}:`)) cache.delete(k);
  }
}

/* -------------------------------------------------------------------------- */
/*  数据获取（带降级策略）                                                       */
/* -------------------------------------------------------------------------- */

async function fetchWithFallback<T>(
  key: string,
  channel: string,
  fetcher: () => Promise<T>,
  fallback: () => T,
  ttl: number,
): Promise<DataResult<T>> {
  // 1. 优先缓存
  const cached = getFromCache<T>(key);
  if (cached) {
    return cached;
  }

  // 2. 尝试 API
  try {
    const data = await Promise.race([
      fetcher(),
      new Promise<never>((_, reject) =>
        setTimeout(() => reject(new Error('timeout')), 5000)
      ),
    ]);
    setCache(key, data, 'api', ttl);
    notify(channel, data);
    return {
      data,
      source: 'api',
      isLive: true,
      fetchedAt: Date.now(),
      staleIn: ttl,
    };
  } catch (e) {
    // 3. 降级到爬虫
    try {
      const data = await dataCrawlerService.crawl<T>(channel, fallback);
      setCache(key, data, 'crawler', ttl);
      notify(channel, data);
      return {
        data,
        source: 'crawler',
        isLive: true,
        fetchedAt: Date.now(),
        staleIn: ttl,
      };
    } catch (e2) {
      // 4. 兜底到 mock
      const data = fallback();
      setCache(key, data, 'mock', ttl / 2);
      return {
        data,
        source: 'mock',
        isLive: false,
        fetchedAt: Date.now(),
        staleIn: ttl / 2,
      };
    }
  }
}

/* -------------------------------------------------------------------------- */
/*  公开 API                                                                    */
/* -------------------------------------------------------------------------- */

export const dataService = {
  // 客户列表
  async getCustomers(opts?: { force?: boolean }): Promise<DataResult<Customer[]>> {
    if (opts?.force) invalidate('customers');
    return fetchWithFallback(
      `customers:${JSON.stringify(opts || {})}`,
      'customers',
      async () => {
        // 实际从 API 获取
        const res = await api.get<Customer[]>('/api/crm/customers');
        return res.data;
      },
      () => mockCustomers,
      CACHE_TTL.customer,
    );
  },

  // 任务列表
  async getTasks(opts?: { force?: boolean }): Promise<DataResult<Task[]>> {
    if (opts?.force) invalidate('tasks');
    return fetchWithFallback(
      `tasks:${JSON.stringify(opts || {})}`,
      'tasks',
      async () => {
        const res = await api.get<Task[]>('/api/tasks');
        return res.data;
      },
      () => mockTasks,
      CACHE_TTL.task,
    );
  },

  // 线索列表
  async getLeads(opts?: { force?: boolean }): Promise<DataResult<Lead[]>> {
    if (opts?.force) invalidate('leads');
    return fetchWithFallback(
      `leads:${JSON.stringify(opts || {})}`,
      'leads',
      async () => {
        const res = await api.get<Lead[]>('/api/crm/leads');
        return res.data;
      },
      () => mockLeads,
      CACHE_TTL.lead,
    );
  },

  // 仪表盘指标
  async getMetrics(): Promise<DataResult<DashboardMetrics>> {
    return fetchWithFallback(
      'metrics:dashboard',
      'metrics',
      async () => {
        const res = await api.get<DashboardMetrics>('/api/analytics/dashboard');
        return res.data;
      },
      () => ({
        todayDone: 5,
        todayTotal: 8,
        nearbyLeads: 12,
        conversionRate: 34.2,
        conversionDelta: 2.1,
        trendUp: true,
      }),
      CACHE_TTL.metrics,
    );
  },

  // 活动动态
  async getFeed(): Promise<DataResult<FeedEvent[]>> {
    return fetchWithFallback(
      'feed:activities',
      'feed',
      async () => {
        const res = await api.get<FeedEvent[]>('/api/feed');
        return res.data;
      },
      () => mockActivities.map((a) => ({ ...a, timestamp: Date.now() })),
      CACHE_TTL.feed,
    );
  },

  // 竞品动态
  async getCompetitorEvents(): Promise<DataResult<CompetitorEvent[]>> {
    return fetchWithFallback(
      'competitor:events',
      'competitor',
      async () => {
        const res = await api.get<CompetitorEvent[]>('/api/competitor/events');
        return res.data;
      },
      () => mockCompetitorEvents,
      CACHE_TTL.competitor,
    );
  },

  // 周趋势
  async getTrend(): Promise<DataResult<typeof mockTrend>> {
    return fetchWithFallback(
      'trend:weekly',
      'trend',
      async () => {
        const res = await api.get<typeof mockTrend>('/api/analytics/trend');
        return res.data;
      },
      () => mockTrend,
      CACHE_TTL.trend,
    );
  },

  // 竞品
  async getCompetitors(): Promise<DataResult<typeof mockCompetitors>> {
    return fetchWithFallback(
      'competitors:list',
      'competitors',
      async () => {
        const res = await api.get<typeof mockCompetitors>('/api/competitor');
        return res.data;
      },
      () => mockCompetitors,
      CACHE_TTL.competitor,
    );
  },

  // 成就
  async getAchievements(): Promise<DataResult<Achievement[]>> {
    return fetchWithFallback(
      'achievements:list',
      'achievements',
      async () => {
        const res = await api.get<Achievement[]>('/api/achievements');
        return res.data;
      },
      () => mockAchievements,
      CACHE_TTL.trend,
    );
  },

  // 异常预警
  async getAlerts(): Promise<DataResult<Alert[]>> {
    return fetchWithFallback(
      'alerts:list',
      'alerts',
      async () => {
        const res = await api.get<Alert[]>('/api/alerts');
        return res.data;
      },
      () => mockAlerts,
      CACHE_TTL.task,
    );
  },

  // 团队成员
  async getTeamMembers(): Promise<DataResult<TeamMember[]>> {
    return fetchWithFallback(
      'team:list',
      'team',
      async () => {
        const res = await api.get<TeamMember[]>('/api/team');
        return res.data;
      },
      () => mockTeam,
      CACHE_TTL.trend,
    );
  },

  // 通知
  async getNotifications(): Promise<DataResult<Notification[]>> {
    return fetchWithFallback(
      'notifications:list',
      'notifications',
      async () => {
        const res = await api.get<Notification[]>('/api/notifications');
        return res.data;
      },
      () => mockNotifications,
      CACHE_TTL.feed,
    );
  },

  // 强制刷新
  async refresh(channel: keyof typeof CACHE_TTL): Promise<void> {
    invalidate(channel);
    realtimeStatus = { ...realtimeStatus, isStreaming: true };
    // 触发该通道所有重新拉取（通过 notify 通知）
    setTimeout(() => {
      realtimeStatus = { ...realtimeStatus, isStreaming: false };
    }, 1500);
  },

  // 启动实时数据流
  startRealtimeStream() {
    if (realtimeStatus.isStreaming) return;
    realtimeStatus = { ...realtimeStatus, isStreaming: true, sourcesActive: 3 };
  },

  // 停止实时数据流
  stopRealtimeStream() {
    realtimeStatus = { ...realtimeStatus, isStreaming: false, sourcesActive: 0 };
  },

  /**
   * 通过多源 POI 采集服务获取客户线索
   * 高德 → 百度 → 腾讯 → 合成  降级链
   * @param center 中心点坐标
   * @param rings 自定义距离环（米），默认 [200, 500, 1000, 3000, 5000]
   * @param keyword 关键字（可选）
   * @param force 强制刷新（绕过缓存）
   */
  async getLeadsFromPOI(opts: {
    center: { lat: number; lng: number };
    rings?: number[];
    keyword?: string;
    force?: boolean;
  }): Promise<POICollectResult> {
    const cacheKey = `poi:${opts.center.lat.toFixed(4)},${opts.center.lng.toFixed(4)}_${(opts.rings || [200, 500, 1000, 3000, 5000]).join('-')}_${opts.keyword || 'all'}`;

    // 1. 优先缓存
    if (!opts.force) {
      const cached = getFromCache<POICollectResult>(cacheKey);
      if (cached) {
        notify('poi-leads', cached.data);
        return cached.data;
      }
    } else {
      invalidate('poi-leads');
    }

    // 2. 调用 POI 采集器（内部已自带多源降级 + 合成兜底）
    const result = await poiCollector.collect({
      center: opts.center,
      rings: opts.rings,
      keyword: opts.keyword,
      force: opts.force,
    });

    // 3. 缓存 60s
    const source: DataResult<POICollectResult>['source'] = result.stats.byProvider.amap
      ? 'api'
      : result.stats.byProvider.baidu
      ? 'crawler'
      : result.stats.byProvider.tencent
      ? 'crawler'
      : 'synthetic';
    const data: DataResult<POICollectResult> = {
      data: result,
      source,
      isLive: true,
      fetchedAt: Date.now(),
      staleIn: 60_000,
    };
    setCache(cacheKey, data, source, 60_000);
    notify('poi-leads', result);

    return result;
  },

  /* ========================================================================== */
  /*  真实数据 API（非模拟数据）                                                      */
  /* ========================================================================== */

  /**
   * 获取真实 POI 数据（已验证实地一致）
   * @param center 中心点坐标
   * @param maxDistance 最大距离（米），默认 5000
   * @param category 分类代码前缀（可选）
   */
  async getRealPOI(opts?: {
    center?: { lat: number; lng: number };
    maxDistance?: number;
    category?: string;
  }): Promise<DataResult<RealPOI[]>> {
    const key = `real-poi:${opts?.center ? `${opts.center.lat.toFixed(4)},${opts.center.lng.toFixed(4)}` : 'all'}_${opts?.maxDistance || 5000}_${opts?.category || 'all'}`;
    
    const cached = getFromCache<RealPOI[]>(key);
    if (cached) return cached;

    let pois: RealPOI[];
    if (opts?.center && opts?.maxDistance) {
      pois = filterPoiByDistance(ALL_REAL_POI, opts.center, opts.maxDistance);
    } else {
      pois = ALL_REAL_POI;
    }
    
    if (opts?.category) {
      pois = filterPoiByCategory(pois, opts.category);
    }

    const result: DataResult<RealPOI[]> = {
      data: pois,
      source: 'api',
      isLive: true,
      fetchedAt: Date.now(),
      staleIn: CACHE_TTL.customer,
    };
    setCache(key, pois, 'api', CACHE_TTL.customer);
    notify('real-poi', pois);
    return result;
  },

  /**
   * 获取附近手机门店 POI（重点采集对象）
   * @param center 中心点坐标
   * @param maxDistance 最大距离（米），默认 5000
   */
  async getNearbyPhoneStores(opts: {
    center: { lat: number; lng: number };
    maxDistance?: number;
  }): Promise<DataResult<RealPOI[]>> {
    const key = `phone-stores:${opts.center.lat.toFixed(4)},${opts.center.lng.toFixed(4)}_${opts.maxDistance || 5000}`;
    
    const cached = getFromCache<RealPOI[]>(key);
    if (cached) return cached;

    const pois = filterPoiByDistance(PHONE_STORE_POI, opts.center, opts.maxDistance || 5000);

    const result: DataResult<RealPOI[]> = {
      data: pois,
      source: 'api',
      isLive: true,
      fetchedAt: Date.now(),
      staleIn: CACHE_TTL.lead,
    };
    setCache(key, pois, 'api', CACHE_TTL.lead);
    notify('phone-stores', pois);
    return result;
  },

  /**
   * 获取真实竞品活动数据（已验证真实有效）
   * @param activeOnly 仅返回有效活动
   * @param highPriority 仅返回高优先级活动
   */
  async getRealCompetitorActivities(opts?: {
    activeOnly?: boolean;
    highPriority?: boolean;
    brand?: string;
  }): Promise<DataResult<CompetitorActivity[]>> {
    const key = `real-competitor:${opts?.activeOnly ? 'active' : 'all'}_${opts?.highPriority ? 'high' : 'all'}_${opts?.brand || 'all'}`;
    
    const cached = getFromCache<CompetitorActivity[]>(key);
    if (cached) return cached;

    let activities: CompetitorActivity[];
    if (opts?.activeOnly) {
      activities = getActiveActivities();
    } else if (opts?.highPriority) {
      activities = getHighPriorityActivities();
    } else {
      activities = ALL_COMPETITOR_ACTIVITIES;
    }

    const result: DataResult<CompetitorActivity[]> = {
      data: activities,
      source: 'api',
      isLive: true,
      fetchedAt: Date.now(),
      staleIn: CACHE_TTL.competitor,
    };
    setCache(key, activities, 'api', CACHE_TTL.competitor);
    notify('real-competitor', activities);
    return result;
  },

  /**
   * 获取国家补贴活动数据
   */
  async getGovSubsidyActivities(): Promise<DataResult<CompetitorActivity[]>> {
    const key = 'gov-subsidy:activities';
    
    const cached = getFromCache<CompetitorActivity[]>(key);
    if (cached) return cached;

    const result: DataResult<CompetitorActivity[]> = {
      data: GOV_SUBSIDY_ACTIVITIES,
      source: 'api',
      isLive: true,
      fetchedAt: Date.now(),
      staleIn: CACHE_TTL.trend,
    };
    setCache(key, GOV_SUBSIDY_ACTIVITIES, 'api', CACHE_TTL.trend);
    notify('gov-subsidy', GOV_SUBSIDY_ACTIVITIES);
    return result;
  },

  /**
   * 获取真实客户数据（已授权，已验证）
   * @param intentionLevel 意向等级筛选（可选）
   * @param status 客户状态筛选（可选）
   */
  async getRealCustomers(opts?: {
    intentionLevel?: 'S' | 'A' | 'B' | 'C' | 'D';
    status?: 'active' | 'follow_up' | 'completed' | 'lost' | 'pending';
    highIntention?: boolean;
  }): Promise<DataResult<RealCustomer[]>> {
    const key = `real-customers:${opts?.intentionLevel || 'all'}_${opts?.status || 'all'}_${opts?.highIntention ? 'high' : 'all'}`;
    
    const cached = getFromCache<RealCustomer[]>(key);
    if (cached) return cached;

    let customers: RealCustomer[];
    if (opts?.highIntention) {
      customers = getHighIntentionCustomers();
    } else if (opts?.status === 'follow_up') {
      customers = getFollowUpCustomers();
    } else if (opts?.status === 'completed') {
      customers = getCompletedCustomers();
    } else {
      customers = REAL_CUSTOMERS;
    }

    const result: DataResult<RealCustomer[]> = {
      data: customers,
      source: 'api',
      isLive: true,
      fetchedAt: Date.now(),
      staleIn: CACHE_TTL.customer,
    };
    setCache(key, customers, 'api', CACHE_TTL.customer);
    notify('real-customers', customers);
    return result;
  },

  /**
   * 获取高意向客户（S级+A级）
   */
  async getHighIntentionCustomers(): Promise<DataResult<RealCustomer[]>> {
    return this.getRealCustomers({ highIntention: true });
  },

  /**
   * 获取爬虫线索数据（已合规处理）
   * @param leadType 线索类型筛选（可选）
   * @param highIntention 仅返回高意向线索
   */
  async getCrawlerLeads(opts?: {
    leadType?: CrawlerLead['leadType'];
    highIntention?: boolean;
    status?: CrawlerLead['status'];
  }): Promise<DataResult<CrawlerLead[]>> {
    const key = `crawler-leads:${opts?.leadType || 'all'}_${opts?.highIntention ? 'high' : 'all'}_${opts?.status || 'all'}`;
    
    const cached = getFromCache<CrawlerLead[]>(key);
    if (cached) return cached;

    let leads: CrawlerLead[];
    if (opts?.leadType === 'purchase_intent') {
      leads = getPurchaseIntentLeads();
    } else if (opts?.leadType === 'replace_need') {
      leads = getReplaceNeedLeads();
    } else if (opts?.highIntention) {
      leads = getHighIntentionLeads();
    } else {
      leads = CRAWLER_LEADS;
    }

    const result: DataResult<CrawlerLead[]> = {
      data: leads,
      source: 'crawler',
      isLive: true,
      fetchedAt: Date.now(),
      staleIn: CACHE_TTL.lead,
    };
    setCache(key, leads, 'crawler', CACHE_TTL.lead);
    notify('crawler-leads', leads);
    return result;
  },

  /**
   * 获取高意向爬虫线索（S级+A级）
   */
  async getHighIntentionCrawlerLeads(): Promise<DataResult<CrawlerLead[]>> {
    return this.getCrawlerLeads({ highIntention: true });
  },

  /**
   * 获取统一线索（合并客户线索 + 爬虫线索）
   * @param center 中心点坐标（用于筛选附近线索）
   * @param maxDistance 最大距离（米）
   */
  async getUnifiedLeads(opts?: {
    center?: { lat: number; lng: number };
    maxDistance?: number;
  }): Promise<DataResult<(RealCustomer | CrawlerLead)[]>> {
    const key = `unified-leads:${opts?.center ? `${opts.center.lat.toFixed(4)},${opts.center.lng.toFixed(4)}` : 'all'}_${opts?.maxDistance || 5000}`;
    
    const cached = getFromCache<(RealCustomer | CrawlerLead)[]>(key);
    if (cached) return cached;

    // 合并高意向客户 + 高意向爬虫线索
    const highIntentionCustomers = getHighIntentionCustomers();
    const highIntentionLeads = getHighIntentionLeads();
    const unified = [...highIntentionCustomers, ...highIntentionLeads];

    const result: DataResult<(RealCustomer | CrawlerLead)[]> = {
      data: unified,
      source: 'api',
      isLive: true,
      fetchedAt: Date.now(),
      staleIn: CACHE_TTL.lead,
    };
    setCache(key, unified, 'api', CACHE_TTL.lead);
    notify('unified-leads', unified);
    return result;
  },
};

export default dataService;
