/**
 * API 路由配置服务
 * 多点爬虫API入口、路由管理、请求分发
 */

import { safeLocalStorageGet, safeLocalStorageSet } from './env';

/* -------------------------------------------------------------------------- */
/*  类型定义                                                                    */
/* -------------------------------------------------------------------------- */

export type HttpMethod = 'GET' | 'POST' | 'PUT' | 'DELETE' | 'PATCH';
export type RouteCategory = 'map' | 'crawler' | 'lbs' | 'competitor' | 'geo' | 'ai' | 'sync' | 'auth' | 'storage';

export interface ApiRoute {
  id: string;
  path: string;
  method: HttpMethod;
  category: RouteCategory;
  description: string;
  handler: string;
  middleware: string[];
  rateLimit: {
    requestsPerMinute: number;
    requestsPerDay: number;
  };
  cache: {
    enabled: boolean;
    ttl: number;
  };
  auth: {
    required: boolean;
    roles?: string[];
  };
  deprecated?: boolean;
}

export interface CrawlerEndpoint {
  id: string;
  name: string;
  source: string;
  endpoint: string;
  params: Record<string, { type: string; required: boolean; description: string }>;
  response: string;
  example: any;
}

export interface RouteGroup {
  category: RouteCategory;
  name: string;
  description: string;
  routes: ApiRoute[];
  crawlers: CrawlerEndpoint[];
}

/* -------------------------------------------------------------------------- */
/*  API 路由配置                                                                */
/* -------------------------------------------------------------------------- */

const API_ROUTES: ApiRoute[] = [
  // 地图服务路由
  {
    id: 'map-geocode',
    path: '/api/map/geocode',
    method: 'GET',
    category: 'map',
    description: '地理编码：地址转坐标',
    handler: 'mapService.geocode',
    middleware: ['rateLimit', 'cache'],
    rateLimit: { requestsPerMinute: 50, requestsPerDay: 5000 },
    cache: { enabled: true, ttl: 86400000 },
    auth: { required: false },
  },
  {
    id: 'map-reverse-geocode',
    path: '/api/map/reverse-geocode',
    method: 'GET',
    category: 'map',
    description: '逆地理编码：坐标转地址',
    handler: 'mapService.reverseGeocode',
    middleware: ['rateLimit', 'cache'],
    rateLimit: { requestsPerMinute: 50, requestsPerDay: 5000 },
    cache: { enabled: true, ttl: 86400000 },
    auth: { required: false },
  },
  {
    id: 'map-poi-search',
    path: '/api/map/poi/search',
    method: 'GET',
    category: 'map',
    description: 'POI搜索：周边兴趣点',
    handler: 'mapService.searchPOI',
    middleware: ['rateLimit', 'cache'],
    rateLimit: { requestsPerMinute: 30, requestsPerDay: 3000 },
    cache: { enabled: true, ttl: 3600000 },
    auth: { required: false },
  },

  // 数据爬虫路由
  {
    id: 'crawler-poi',
    path: '/api/crawler/poi',
    method: 'POST',
    category: 'crawler',
    description: 'POI数据爬取（多源融合）',
    handler: 'dataCrawlerService.crawlPOI',
    middleware: ['rateLimit', 'auth', 'cache'],
    rateLimit: { requestsPerMinute: 10, requestsPerDay: 500 },
    cache: { enabled: true, ttl: 86400000 },
    auth: { required: true, roles: ['admin', 'manager'] },
  },
  {
    id: 'crawler-product',
    path: '/api/crawler/product',
    method: 'POST',
    category: 'crawler',
    description: '产品数据爬取（电商多源）',
    handler: 'dataCrawlerService.crawlProduct',
    middleware: ['rateLimit', 'auth', 'cache'],
    rateLimit: { requestsPerMinute: 10, requestsPerDay: 500 },
    cache: { enabled: true, ttl: 3600000 },
    auth: { required: true, roles: ['admin', 'manager'] },
  },
  {
    id: 'crawler-subsidy',
    path: '/api/crawler/subsidy',
    method: 'GET',
    category: 'crawler',
    description: '补贴政策数据爬取',
    handler: 'dataCrawlerService.crawlSubsidy',
    middleware: ['rateLimit', 'cache'],
    rateLimit: { requestsPerMinute: 5, requestsPerDay: 100 },
    cache: { enabled: true, ttl: 86400000 },
    auth: { required: false },
  },

  // LBS雷达路由
  {
    id: 'lbs-scan',
    path: '/api/lbs/scan',
    method: 'POST',
    category: 'lbs',
    description: 'LBS雷达四层融合扫描',
    handler: 'lbsRadarService.scan',
    middleware: ['rateLimit', 'auth'],
    rateLimit: { requestsPerMinute: 5, requestsPerDay: 200 },
    cache: { enabled: false, ttl: 0 },
    auth: { required: true, roles: ['admin', 'manager', 'staff'] },
  },
  {
    id: 'lbs-quick-scan',
    path: '/api/lbs/quick-scan',
    method: 'POST',
    category: 'lbs',
    description: 'LBS快速扫描',
    handler: 'lbsRadarService.quickScan',
    middleware: ['rateLimit', 'auth'],
    rateLimit: { requestsPerMinute: 10, requestsPerDay: 500 },
    cache: { enabled: true, ttl: 600000 },
    auth: { required: true, roles: ['admin', 'manager', 'staff'] },
  },

  // 竞品监控路由
  {
    id: 'competitor-scan',
    path: '/api/competitor/scan',
    method: 'POST',
    category: 'competitor',
    description: '竞品门店扫描',
    handler: 'competitorMonitorService.scanCompetitors',
    middleware: ['rateLimit', 'auth'],
    rateLimit: { requestsPerMinute: 5, requestsPerDay: 100 },
    cache: { enabled: true, ttl: 3600000 },
    auth: { required: true, roles: ['admin', 'manager'] },
  },

  // GEO优化路由
  {
    id: 'geo-keywords',
    path: '/api/geo/keywords',
    method: 'GET',
    category: 'geo',
    description: '获取品牌关键词矩阵',
    handler: 'geoOptimizationEngine.getKeywords',
    middleware: ['cache'],
    rateLimit: { requestsPerMinute: 100, requestsPerDay: 10000 },
    cache: { enabled: true, ttl: 86400000 },
    auth: { required: false },
  },
  {
    id: 'geo-check-rank',
    path: '/api/geo/check-rank',
    method: 'POST',
    category: 'geo',
    description: '检查AI搜索排名',
    handler: 'geoOptimizationEngine.checkAllPlatforms',
    middleware: ['rateLimit', 'auth'],
    rateLimit: { requestsPerMinute: 5, requestsPerDay: 50 },
    cache: { enabled: false, ttl: 0 },
    auth: { required: true, roles: ['admin', 'manager'] },
  },

  // AI服务路由
  {
    id: 'ai-predict-replacement',
    path: '/api/ai/predict-replacement',
    method: 'POST',
    category: 'ai',
    description: '换机周期预测',
    handler: 'predictReplacement',
    middleware: ['rateLimit', 'auth'],
    rateLimit: { requestsPerMinute: 20, requestsPerDay: 1000 },
    cache: { enabled: true, ttl: 3600000 },
    auth: { required: true, roles: ['admin', 'manager', 'staff'] },
  },
  {
    id: 'ai-generate-script',
    path: '/api/ai/generate-script',
    method: 'POST',
    category: 'ai',
    description: '智能话术生成',
    handler: 'generateSmartScript',
    middleware: ['rateLimit', 'auth'],
    rateLimit: { requestsPerMinute: 10, requestsPerDay: 500 },
    cache: { enabled: false, ttl: 0 },
    auth: { required: true, roles: ['admin', 'manager', 'staff'] },
  },

  // 数据同步路由
  {
    id: 'sync-all',
    path: '/api/sync/all',
    method: 'POST',
    category: 'sync',
    description: '全量数据同步',
    handler: 'dataSyncService.syncAll',
    middleware: ['auth'],
    rateLimit: { requestsPerMinute: 1, requestsPerDay: 10 },
    cache: { enabled: false, ttl: 0 },
    auth: { required: true, roles: ['admin'] },
  },
  {
    id: 'sync-test',
    path: '/api/sync/test',
    method: 'POST',
    category: 'sync',
    description: '测试同步连接',
    handler: 'dataSyncService.testConnection',
    middleware: ['auth'],
    rateLimit: { requestsPerMinute: 10, requestsPerDay: 100 },
    cache: { enabled: false, ttl: 0 },
    auth: { required: true, roles: ['admin'] },
  },

  // 认证路由
  {
    id: 'auth-login',
    path: '/api/auth/login',
    method: 'POST',
    category: 'auth',
    description: '用户登录',
    handler: 'authStore.login',
    middleware: [],
    rateLimit: { requestsPerMinute: 5, requestsPerDay: 50 },
    cache: { enabled: false, ttl: 0 },
    auth: { required: false },
  },
  {
    id: 'auth-check',
    path: '/api/auth/check',
    method: 'GET',
    category: 'auth',
    description: '检查认证状态',
    handler: 'authStore.checkAuth',
    middleware: [],
    rateLimit: { requestsPerMinute: 100, requestsPerDay: 10000 },
    cache: { enabled: false, ttl: 0 },
    auth: { required: false },
  },

  // 存储路由
  {
    id: 'storage-get',
    path: '/api/storage/get',
    method: 'GET',
    category: 'storage',
    description: '获取存储数据',
    handler: 'repository.get',
    middleware: ['auth'],
    rateLimit: { requestsPerMinute: 100, requestsPerDay: 10000 },
    cache: { enabled: true, ttl: 60000 },
    auth: { required: true },
  },
  {
    id: 'storage-save',
    path: '/api/storage/save',
    method: 'POST',
    category: 'storage',
    description: '保存存储数据',
    handler: 'repository.save',
    middleware: ['auth'],
    rateLimit: { requestsPerMinute: 50, requestsPerDay: 5000 },
    cache: { enabled: false, ttl: 0 },
    auth: { required: true },
  },
];

/* -------------------------------------------------------------------------- */
/*  爬虫端点配置                                                                 */
/* -------------------------------------------------------------------------- */

const CRAWLER_ENDPOINTS: CrawlerEndpoint[] = [
  {
    id: 'crawler-amap-poi',
    name: '高德地图POI',
    source: 'amap',
    endpoint: 'https://restapi.amap.com/v3/place/around',
    params: {
      key: { type: 'string', required: true, description: 'API密钥' },
      location: { type: 'string', required: true, description: '中心点坐标' },
      keywords: { type: 'string', required: false, description: '搜索关键词' },
      radius: { type: 'number', required: false, description: '搜索半径(米)' },
    },
    response: 'POICrawlData[]',
    example: { count: 20, pois: [] },
  },
  {
    id: 'crawler-dianping-store',
    name: '大众点评门店',
    source: 'dianping',
    endpoint: 'https://www.dianping.com/search',
    params: {
      keyword: { type: 'string', required: true, description: '搜索关键词' },
      cityId: { type: 'number', required: true, description: '城市ID' },
      category: { type: 'string', required: false, description: '分类' },
    },
    response: 'StoreData[]',
    example: { shops: [] },
  },
  {
    id: 'crawler-jd-product',
    name: '京东产品',
    source: 'jd',
    endpoint: 'https://api.jd.com/router.json',
    params: {
      method: { type: 'string', required: true, description: 'API方法名' },
      app_key: { type: 'string', required: true, description: '应用Key' },
      keyword: { type: 'string', required: false, description: '搜索关键词' },
    },
    response: 'ProductCrawlData[]',
    example: { products: [] },
  },
  {
    id: 'crawler-gov-subsidy',
    name: '国补政策',
    source: 'gov_subsidy',
    endpoint: 'https://policy.mofcom.gov.cn/api/subsidy',
    params: {
      region: { type: 'string', required: false, description: '地区' },
      type: { type: 'string', required: false, description: '补贴类型' },
    },
    response: 'SubsidyCrawlData[]',
    example: { subsidies: [] },
  },
];

/* -------------------------------------------------------------------------- */
/*  API 路由服务                                                                 */
/* -------------------------------------------------------------------------- */

class ApiRouterService {
  private routes: Map<string, ApiRoute> = new Map();
  private requestCounts: Map<string, { minute: number; day: number; resetAt: number }> = new Map();

  constructor() {
    // 初始化路由
    API_ROUTES.forEach(route => {
      this.routes.set(route.id, route);
    });
  }

  /**
   * 获取所有路由
   */
  getAllRoutes(): ApiRoute[] {
    return API_ROUTES;
  }

  /**
   * 获取路由分组
   */
  getRouteGroups(): RouteGroup[] {
    const categories: Record<RouteCategory, { name: string; description: string }> = {
      map: { name: '地图服务', description: '地理编码、POI搜索、逆地理编码' },
      crawler: { name: '数据爬虫', description: '多源数据采集、POI/产品/政策爬取' },
      lbs: { name: 'LBS雷达', description: '四层融合扫描、销售线索生成' },
      competitor: { name: '竞品监控', description: '竞品门店扫描、热力监控' },
      geo: { name: 'GEO优化', description: '关键词矩阵、AI搜索排名' },
      ai: { name: 'AI服务', description: '换机预测、智能话术、路线优化' },
      sync: { name: '数据同步', description: '品牌CRM同步、数据回传' },
      auth: { name: '认证授权', description: '登录、权限校验' },
      storage: { name: '数据存储', description: '本地存储、IndexedDB' },
    };

    const groups: RouteGroup[] = [];

    Object.entries(categories).forEach(([category, info]) => {
      const routes = API_ROUTES.filter(r => r.category === category);
      const crawlers = CRAWLER_ENDPOINTS.filter(c => 
        routes.some(r => r.handler.includes(c.source))
      );

      if (routes.length > 0) {
        groups.push({
          category: category as RouteCategory,
          name: info.name,
          description: info.description,
          routes,
          crawlers,
        });
      }
    });

    return groups;
  }

  /**
   * 获取单个路由
   */
  getRoute(id: string): ApiRoute | null {
    return this.routes.get(id) || null;
  }

  /**
   * 根据路径查找路由
   */
  getRouteByPath(path: string, method: HttpMethod): ApiRoute | null {
    return API_ROUTES.find(r => r.path === path && r.method === method) || null;
  }

  /**
   * 获取爬虫端点
   */
  getCrawlerEndpoints(): CrawlerEndpoint[] {
    return CRAWLER_ENDPOINTS;
  }

  /**
   * 检查路由权限
   */
  checkPermission(routeId: string, userRole: string): boolean {
    const route = this.routes.get(routeId);
    if (!route) return false;
    
    if (!route.auth.required) return true;
    if (!route.auth.roles) return true;
    
    return route.auth.roles.includes(userRole);
  }

  /**
   * 检查速率限制
   */
  checkRateLimit(routeId: string): { allowed: boolean; retryAfter?: number } {
    const route = this.routes.get(routeId);
    if (!route) return { allowed: false };

    const now = Date.now();
    let state = this.requestCounts.get(routeId);
    
    if (!state || now > state.resetAt) {
      state = { minute: 0, day: 0, resetAt: now + 60000 };
      this.requestCounts.set(routeId, state);
    }

    // 检查每分钟限制
    if (state.minute >= route.rateLimit.requestsPerMinute) {
      const retryAfter = Math.ceil((state.resetAt - now) / 1000);
      return { allowed: false, retryAfter };
    }

    // 检查每日限制
    if (state.day >= route.rateLimit.requestsPerDay) {
      return { allowed: false, retryAfter: 86400 };
    }

    // 更新计数
    state.minute++;
    state.day++;

    return { allowed: true };
  }

  /**
   * 生成API文档
   */
  generateDocs(): string {
    const groups = this.getRouteGroups();
    let docs = '# 掌上商客 V2.0 API 文档\n\n';
    docs += `生成时间: ${new Date().toISOString()}\n\n`;
    docs += `总路由数: ${API_ROUTES.length}\n\n`;
    docs += '---\n\n';

    groups.forEach(group => {
      docs += `## ${group.name}\n\n`;
      docs += `${group.description}\n\n`;
      
      docs += '### 路由列表\n\n';
      docs += '| 方法 | 路径 | 描述 | 认证 |\n';
      docs += '|------|------|------|------|\n';
      
      group.routes.forEach(route => {
        const auth = route.auth.required ? '✅' : '❌';
        docs += `| ${route.method} | ${route.path} | ${route.description} | ${auth} |\n`;
      });
      
      docs += '\n';

      if (group.crawlers.length > 0) {
        docs += '### 爬虫端点\n\n';
        group.crawlers.forEach(crawler => {
          docs += `- **${crawler.name}** (${crawler.source})\n`;
          docs += `  - 端点: \`${crawler.endpoint}\`\n`;
          docs += `  - 参数: ${Object.keys(crawler.params).join(', ')}\n`;
        });
        docs += '\n';
      }
    });

    return docs;
  }

  /**
   * 获取路由统计
   */
  getStats(): {
    totalRoutes: number;
    byCategory: Record<RouteCategory, number>;
    authRequired: number;
    cacheEnabled: number;
    deprecated: number;
  } {
    const byCategory: Record<RouteCategory, number> = {
      map: 0, crawler: 0, lbs: 0, competitor: 0, geo: 0, ai: 0, sync: 0, auth: 0, storage: 0,
    };

    API_ROUTES.forEach(route => {
      byCategory[route.category]++;
    });

    return {
      totalRoutes: API_ROUTES.length,
      byCategory,
      authRequired: API_ROUTES.filter(r => r.auth.required).length,
      cacheEnabled: API_ROUTES.filter(r => r.cache.enabled).length,
      deprecated: API_ROUTES.filter(r => r.deprecated).length,
    };
  }
}

export const apiRouterService = new ApiRouterService();
export default apiRouterService;
