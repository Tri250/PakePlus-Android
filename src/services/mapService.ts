/**
 * 地图服务 REST API 客户端
 *
 * 接口规范（对接 PakePlus Android 原生地图组件）：
 * - 地图服务初始化 POST /map/init
 * - POI 批量采集任务创建 POST /poi/task/create
 * - 采集任务状态查询 GET /poi/task/status/{taskId}
 * - 采集结果导出 GET /poi/task/export/{taskId}
 * - 地理围栏创建 POST /geofence/create
 * - 离线地图城市列表 GET /map/offline/cities
 * - 离线地图下载 POST /map/offline/download
 *
 * 设计原则：
 * - 与 Android 端 PakePlus MapView 组件 1:1 对齐
 * - 支持三平台（高德/百度/腾讯）自动切换
 * - 任务状态实时轮询 + 断点续传
 * - 地理围栏 POI 实时监控
 */

import { getEnv, safeLocalStorageGet, safeLocalStorageSet } from './env';
import { poiCollector, type POICategory, type POIProvider } from './poiCollector';

/* -------------------------------------------------------------------------- */
/*  类型定义                                                                    */
/* -------------------------------------------------------------------------- */

export type MapPlatform = 'amap' | 'baidu' | 'tencent';

export type MapType = 'normal' | 'satellite' | 'terrain';

export type TaskStatus = 'pending' | 'running' | 'completed' | 'failed' | 'cancelled';

export type ExportFormat = 'json' | 'csv' | 'excel' | 'geojson';

export type GeofenceType = 'circle' | 'polygon';

export interface MapInitRequest {
  platform: MapPlatform;
  apiKey: string;
  /** 离线模式（优先使用本地缓存） */
  offlineMode?: boolean;
}

export interface MapInitResponse {
  success: boolean;
  platform: MapPlatform;
  /** Key 有效性验证结果 */
  keyValid: boolean;
  /** 支持的功能列表 */
  features: string[];
  /** 离线城市数量 */
  offlineCitiesCount: number;
  /** 初始化时间戳 */
  initializedAt: number;
  /** 错误信息（失败时） */
  error?: string;
}

export interface POITaskCreateRequest {
  /** 任务名称 */
  name: string;
  /** 中心点坐标（WGS84） */
  center: { lat: number; lng: number };
  /** 搜索半径（米） */
  radius: number;
  /** 搜索关键字 */
  keywords: string[];
  /** POI 类别过滤 */
  categories?: POICategory[];
  /** 数据源优先级 */
  providers?: POIProvider[];
  /** 采集频率（毫秒），0 表示一次性采集 */
  frequencyMs?: number;
  /** 城市代码（高德 adcode） */
  cityCode?: string;
  /** 最大采集数量 */
  maxCount?: number;
}

export interface POITaskCreateResponse {
  success: boolean;
  taskId: string;
  /** 预计完成时间（毫秒） */
  estimatedDurationMs: number;
  /** 预计采集数量 */
  estimatedCount: number;
  /** 分配的数据源 */
  assignedProviders: POIProvider[];
  /** 创建时间戳 */
  createdAt: number;
  error?: string;
}

export interface POITaskStatus {
  taskId: string;
  name: string;
  status: TaskStatus;
  /** 已采集数量 */
  collectedCount: number;
  /** 目标数量 */
  targetCount: number;
  /** 成功率（0-100） */
  successRate: number;
  /** 剩余时间（毫秒） */
  remainingMs: number;
  /** 已耗时（毫秒） */
  elapsedMs: number;
  /** 当前数据源 */
  currentProvider: POIProvider;
  /** 采集进度（0-100） */
  progress: number;
  /** 错误信息 */
  error?: string;
  /** 创建时间 */
  createdAt: number;
  /** 最后更新时间 */
  updatedAt: number;
}

export interface POITaskExportResponse {
  success: boolean;
  taskId: string;
  format: ExportFormat;
  /** 文件下载链接 */
  downloadUrl: string;
  /** 文件大小（字节） */
  fileSize: number;
  /** 记录数量 */
  recordCount: number;
  /** 导出时间戳 */
  exportedAt: number;
  /** 断点续传支持 */
  supportsResume: boolean;
  error?: string;
}

export interface GeofenceCreateRequest {
  name: string;
  type: GeofenceType;
  /** 圆形围栏参数 */
  circle?: {
    center: { lat: number; lng: number };
    radius: number;
  };
  /** 多边形围栏参数（顶点数组） */
  polygon?: Array<{ lat: number; lng: number }>;
  /** 监控的 POI 类别 */
  monitorCategories?: POICategory[];
  /** 监控频率（毫秒） */
  monitorFrequencyMs?: number;
  /** 触发条件：进入/离开/停留 */
  triggerType?: 'enter' | 'exit' | 'stay' | 'all';
}

export interface GeofenceCreateResponse {
  success: boolean;
  geofenceId: string;
  name: string;
  type: GeofenceType;
  /** 围栏内当前 POI 数量 */
  currentPOICount: number;
  /** 监控状态 */
  monitoring: boolean;
  createdAt: number;
  error?: string;
}

export interface GeofenceStatus {
  geofenceId: string;
  name: string;
  type: GeofenceType;
  monitoring: boolean;
  /** 围栏内 POI 数量 */
  poiCount: number;
  /** 最近触发事件 */
  lastTrigger?: {
    type: 'enter' | 'exit' | 'stay';
    poiName: string;
    timestamp: number;
  };
  createdAt: number;
  updatedAt: number;
}

export interface OfflineCity {
  cityCode: string;
  cityName: string;
  province: string;
  /** 数据大小（MB） */
  dataSizeMB: number;
  /** 下载状态 */
  downloadStatus: 'not_downloaded' | 'downloading' | 'completed' | 'failed';
  /** 下载进度（0-100） */
  downloadProgress?: number;
  /** 版本号 */
  version: string;
  /** 更新时间 */
  updatedAt: string;
}

export interface OfflineDownloadRequest {
  cityCode: string;
  /** 下载质量（标准/高清） */
  quality?: 'standard' | 'high';
}

export interface OfflineDownloadResponse {
  success: boolean;
  cityCode: string;
  cityName: string;
  downloadStatus: 'downloading';
  /** 预计下载时间（秒） */
  estimatedSeconds: number;
  error?: string;
}

export interface MapMarker {
  id: string;
  lat: number;
  lng: number;
  title: string;
  snippet: string;
  /** 标签数据（POI 对象） */
  tag?: any;
  /** 图标颜色 */
  color?: string;
  /** 是否可点击 */
  clickable?: boolean;
}

export interface MapCameraPosition {
  lat: number;
  lng: number;
  /** 缩放级别（3-20） */
  zoom: number;
  /** 倾斜角度（0-45） */
  tilt?: number;
  /** 旋转角度（0-360） */
  rotation?: number;
}

/* -------------------------------------------------------------------------- */
/*  配置                                                                        */
/* -------------------------------------------------------------------------- */

const API_BASE_URL = getEnv('VITE_MAP_API_BASE') || '/api/map';

const TASK_CACHE_PREFIX = 'map_task_';
const GEOFENCE_CACHE_PREFIX = 'geofence_';
const OFFLINE_CACHE_PREFIX = 'offline_';

/* -------------------------------------------------------------------------- */
/*  缓存工具                                                                    */
/* -------------------------------------------------------------------------- */

function cacheGet<T>(prefix: string, key: string): T | null {
  try {
    const raw = safeLocalStorageGet(`${prefix}${key}`);
    if (!raw) return null;
    return JSON.parse(raw) as T;
  } catch {
    return null;
  }
}

function cacheSet<T>(prefix: string, key: string, data: T): void {
  try {
    safeLocalStorageSet(`${prefix}${key}`, JSON.stringify(data));
  } catch {}
}

function cacheDel(prefix: string, key: string): void {
  try {
    localStorage.removeItem(`${prefix}${key}`);
  } catch {}
}

/* -------------------------------------------------------------------------- */
/*  模拟 API 实现（对接真实后端时替换为 fetch）                                    */
/* -------------------------------------------------------------------------- */

/**
 * 模拟网络延迟
 */
async function mockDelay(minMs = 200, maxMs = 800): Promise<void> {
  await new Promise((r) => setTimeout(r, minMs + Math.random() * (maxMs - minMs)));
}

/**
 * 地图服务初始化
 * POST /map/init
 */
async function mapInit(req: MapInitRequest): Promise<MapInitResponse> {
  await mockDelay(300, 600);

  // 验证 Key（模拟）
  const keyValid = req.apiKey && req.apiKey.length >= 8;

  const features = keyValid
    ? ['poi_search', 'poi_around', 'geocode', 'offline_map', 'geofence', 'marker', 'navigation']
    : ['poi_search'];

  return {
    success: keyValid,
    platform: req.platform,
    keyValid,
    features,
    offlineCitiesCount: keyValid ? 342 : 0,
    initializedAt: Date.now(),
    error: keyValid ? undefined : 'API Key 无效或未配置',
  };
}

/**
 * POI 批量采集任务创建
 * POST /poi/task/create
 */
async function poiTaskCreate(req: POITaskCreateRequest): Promise<POITaskCreateResponse> {
  await mockDelay(400, 900);

  const taskId = `TASK-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;
  const estimatedCount = Math.min(req.maxCount || 500, 500);
  const estimatedDurationMs = estimatedCount * 50 + 2000;
  const assignedProviders = req.providers || ['amap', 'baidu', 'tencent', 'synthetic'];

  // 存储任务状态
  const taskStatus: POITaskStatus = {
    taskId,
    name: req.name,
    status: 'pending',
    collectedCount: 0,
    targetCount: estimatedCount,
    successRate: 0,
    remainingMs: estimatedDurationMs,
    elapsedMs: 0,
    currentProvider: assignedProviders[0],
    progress: 0,
    createdAt: Date.now(),
    updatedAt: Date.now(),
  };
  cacheSet(TASK_CACHE_PREFIX, taskId, taskStatus);

  // 异步启动采集（模拟后台任务）
  simulateTaskExecution(taskId, req);

  return {
    success: true,
    taskId,
    estimatedDurationMs,
    estimatedCount,
    assignedProviders,
    createdAt: Date.now(),
  };
}

/**
 * 模拟任务执行（后台采集）
 */
async function simulateTaskExecution(taskId: string, req: POITaskCreateRequest): Promise<void> {
  const task = cacheGet<POITaskStatus>(TASK_CACHE_PREFIX, taskId);
  if (!task) return;

  // 更新状态为 running
  task.status = 'running';
  task.updatedAt = Date.now();
  cacheSet(TASK_CACHE_PREFIX, taskId, task);

  try {
    // 调用 poiCollector 执行采集
    const result = await poiCollector.collect({
      center: req.center,
      rings: [req.radius],
      keyword: req.keywords.join('|'),
      categories: req.categories,
      force: true,
    });

    // 更新进度
    task.collectedCount = result.leads.length;
    task.successRate = Math.min(100, Math.round((result.leads.length / task.targetCount) * 100));
    task.progress = 100;
    task.status = 'completed';
    task.remainingMs = 0;
    task.elapsedMs = result.durationMs;
    task.updatedAt = Date.now();
    cacheSet(TASK_CACHE_PREFIX, taskId, task);

    // 存储采集结果
    cacheSet(TASK_CACHE_PREFIX, `${taskId}_result`, result);
  } catch (e: any) {
    task.status = 'failed';
    task.error = e.message || '采集失败';
    task.updatedAt = Date.now();
    cacheSet(TASK_CACHE_PREFIX, taskId, task);
  }
}

/**
 * 采集任务状态查询
 * GET /poi/task/status/{taskId}
 */
async function poiTaskStatus(taskId: string): Promise<POITaskStatus | null> {
  await mockDelay(100, 300);
  return cacheGet<POITaskStatus>(TASK_CACHE_PREFIX, taskId);
}

/**
 * 采集结果导出
 * GET /poi/task/export/{taskId}
 */
async function poiTaskExport(taskId: string, format: ExportFormat): Promise<POITaskExportResponse> {
  await mockDelay(500, 1200);

  const result = cacheGet<any>(TASK_CACHE_PREFIX, `${taskId}_result`);
  const task = cacheGet<POITaskStatus>(TASK_CACHE_PREFIX, taskId);

  if (!result || !task || task.status !== 'completed') {
    return {
      success: false,
      taskId,
      format,
      downloadUrl: '',
      fileSize: 0,
      recordCount: 0,
      exportedAt: Date.now(),
      supportsResume: false,
      error: '任务未完成或结果不存在',
    };
  }

  // 生成模拟下载链接
  const downloadUrl = `/api/map/poi/task/download/${taskId}?format=${format}`;
  const recordCount = result.leads?.length || 0;
  const fileSize = recordCount * 500; // 模拟文件大小

  return {
    success: true,
    taskId,
    format,
    downloadUrl,
    fileSize,
    recordCount,
    exportedAt: Date.now(),
    supportsResume: true,
  };
}

/**
 * 取消采集任务
 * POST /poi/task/cancel/{taskId}
 */
async function poiTaskCancel(taskId: string): Promise<{ success: boolean; taskId: string }> {
  await mockDelay(200, 400);

  const task = cacheGet<POITaskStatus>(TASK_CACHE_PREFIX, taskId);
  if (!task) {
    return { success: false, taskId };
  }

  task.status = 'cancelled';
  task.updatedAt = Date.now();
  cacheSet(TASK_CACHE_PREFIX, taskId, task);

  return { success: true, taskId };
}

/**
 * 获取所有任务列表
 * GET /poi/task/list
 */
async function poiTaskList(): Promise<POITaskStatus[]> {
  await mockDelay(100, 200);

  const tasks: POITaskStatus[] = [];
  try {
    for (let i = 0; i < localStorage.length; i++) {
      const key = localStorage.key(i);
      if (key && key.startsWith(TASK_CACHE_PREFIX) && !key.includes('_result')) {
        const task = cacheGet<POITaskStatus>(TASK_CACHE_PREFIX, key.replace(TASK_CACHE_PREFIX, ''));
        if (task) tasks.push(task);
      }
    }
  } catch {}

  return tasks.sort((a, b) => b.createdAt - a.createdAt);
}

/**
 * 地理围栏创建
 * POST /geofence/create
 */
async function geofenceCreate(req: GeofenceCreateRequest): Promise<GeofenceCreateResponse> {
  await mockDelay(400, 800);

  const geofenceId = `GF-${Date.now()}-${Math.random().toString(36).slice(2, 6)}`;

  // 计算围栏内 POI 数量（模拟）
  let currentPOICount = 0;
  if (req.type === 'circle' && req.circle) {
    const result = await poiCollector.collect({
      center: req.circle.center,
      rings: [req.circle.radius],
      categories: req.monitorCategories,
    });
    currentPOICount = result.leads.length;
  }

  const geofenceStatus: GeofenceStatus = {
    geofenceId,
    name: req.name,
    type: req.type,
    monitoring: true,
    poiCount: currentPOICount,
    createdAt: Date.now(),
    updatedAt: Date.now(),
  };
  cacheSet(GEOFENCE_CACHE_PREFIX, geofenceId, geofenceStatus);

  return {
    success: true,
    geofenceId,
    name: req.name,
    type: req.type,
    currentPOICount,
    monitoring: true,
    createdAt: Date.now(),
  };
}

/**
 * 地理围栏状态查询
 * GET /geofence/status/{geofenceId}
 */
async function geofenceStatus(geofenceId: string): Promise<GeofenceStatus | null> {
  await mockDelay(100, 300);
  return cacheGet<GeofenceStatus>(GEOFENCE_CACHE_PREFIX, geofenceId);
}

/**
 * 地理围栏列表
 * GET /geofence/list
 */
async function geofenceList(): Promise<GeofenceStatus[]> {
  await mockDelay(100, 200);

  const fences: GeofenceStatus[] = [];
  try {
    for (let i = 0; i < localStorage.length; i++) {
      const key = localStorage.key(i);
      if (key && key.startsWith(GEOFENCE_CACHE_PREFIX)) {
        const fence = cacheGet<GeofenceStatus>(GEOFENCE_CACHE_PREFIX, key.replace(GEOFENCE_CACHE_PREFIX, ''));
        if (fence) fences.push(fence);
      }
    }
  } catch {}

  return fences.sort((a, b) => b.createdAt - a.createdAt);
}

/**
 * 删除地理围栏
 * DELETE /geofence/{geofenceId}
 */
async function geofenceDelete(geofenceId: string): Promise<{ success: boolean }> {
  await mockDelay(200, 400);
  cacheDel(GEOFENCE_CACHE_PREFIX, geofenceId);
  return { success: true };
}

/**
 * 离线地图城市列表
 * GET /map/offline/cities
 */
async function offlineCities(): Promise<OfflineCity[]> {
  await mockDelay(300, 600);

  // 模拟热门城市数据
  const cities: OfflineCity[] = [
    { cityCode: '360700', cityName: '赣州市', province: '江西省', dataSizeMB: 45, downloadStatus: 'not_downloaded', version: '2026Q1', updatedAt: '2026-01-15' },
    { cityCode: '440300', cityName: '深圳市', province: '广东省', dataSizeMB: 120, downloadStatus: 'completed', downloadProgress: 100, version: '2026Q1', updatedAt: '2026-02-01' },
    { cityCode: '440100', cityName: '广州市', province: '广东省', dataSizeMB: 95, downloadStatus: 'not_downloaded', version: '2026Q1', updatedAt: '2026-01-20' },
    { cityCode: '310000', cityName: '上海市', province: '上海市', dataSizeMB: 150, downloadStatus: 'not_downloaded', version: '2026Q1', updatedAt: '2026-01-10' },
    { cityCode: '110000', cityName: '北京市', province: '北京市', dataSizeMB: 180, downloadStatus: 'downloading', downloadProgress: 45, version: '2026Q1', updatedAt: '2026-03-01' },
    { cityCode: '330100', cityName: '杭州市', province: '浙江省', dataSizeMB: 85, downloadStatus: 'not_downloaded', version: '2026Q1', updatedAt: '2026-01-25' },
    { cityCode: '320100', cityName: '南京市', province: '江苏省', dataSizeMB: 78, downloadStatus: 'not_downloaded', version: '2026Q1', updatedAt: '2026-02-05' },
    { cityCode: '420100', cityName: '武汉市', province: '湖北省', dataSizeMB: 92, downloadStatus: 'not_downloaded', version: '2026Q1', updatedAt: '2026-02-10' },
  ];

  return cities;
}

/**
 * 离线地图下载
 * POST /map/offline/download
 */
async function offlineDownload(req: OfflineDownloadRequest): Promise<OfflineDownloadResponse> {
  await mockDelay(500, 1000);

  const cities = await offlineCities();
  const city = cities.find((c) => c.cityCode === req.cityCode);

  if (!city) {
    return {
      success: false,
      cityCode: req.cityCode,
      cityName: '',
      downloadStatus: 'failed',
      estimatedSeconds: 0,
      error: '城市不存在',
    };
  }

  // 模拟开始下载
  city.downloadStatus = 'downloading';
  city.downloadProgress = 0;
  cacheSet(OFFLINE_CACHE_PREFIX, req.cityCode, city);

  return {
    success: true,
    cityCode: req.cityCode,
    cityName: city.cityName,
    downloadStatus: 'downloading',
    estimatedSeconds: Math.round(city.dataSizeMB * 2),
  };
}

/* -------------------------------------------------------------------------- */
/*  公开服务类                                                                  */
/* -------------------------------------------------------------------------- */

class MapService {
  private initialized = false;
  private platform: MapPlatform = 'amap';

  /** 地图服务初始化 */
  async init(req: MapInitRequest): Promise<MapInitResponse> {
    const result = await mapInit(req);
    if (result.success) {
      this.initialized = true;
      this.platform = req.platform;
    }
    return result;
  }

  /** 创建 POI 采集任务 */
  async createPOITask(req: POITaskCreateRequest): Promise<POITaskCreateResponse> {
    return poiTaskCreate(req);
  }

  /** 查询任务状态 */
  async getTaskStatus(taskId: string): Promise<POITaskStatus | null> {
    return poiTaskStatus(taskId);
  }

  /** 导出任务结果 */
  async exportTask(taskId: string, format: ExportFormat): Promise<POITaskExportResponse> {
    return poiTaskExport(taskId, format);
  }

  /** 取消任务 */
  async cancelTask(taskId: string): Promise<{ success: boolean; taskId: string }> {
    return poiTaskCancel(taskId);
  }

  /** 获取任务列表 */
  async listTasks(): Promise<POITaskStatus[]> {
    return poiTaskList();
  }

  /** 创建地理围栏 */
  async createGeofence(req: GeofenceCreateRequest): Promise<GeofenceCreateResponse> {
    return geofenceCreate(req);
  }

  /** 查询围栏状态 */
  async getGeofenceStatus(geofenceId: string): Promise<GeofenceStatus | null> {
    return geofenceStatus(geofenceId);
  }

  /** 获取围栏列表 */
  async listGeofences(): Promise<GeofenceStatus[]> {
    return geofenceList();
  }

  /** 删除围栏 */
  async deleteGeofence(geofenceId: string): Promise<{ success: boolean }> {
    return geofenceDelete(geofenceId);
  }

  /** 离线城市列表 */
  async getOfflineCities(): Promise<OfflineCity[]> {
    return offlineCities();
  }

  /** 下载离线地图 */
  async downloadOfflineMap(req: OfflineDownloadRequest): Promise<OfflineDownloadResponse> {
    return offlineDownload(req);
  }

  /** 获取当前平台 */
  getPlatform(): MapPlatform {
    return this.platform;
  }

  /** 是否已初始化 */
  isInitialized(): boolean {
    return this.initialized;
  }

  /** 清理所有缓存 */
  clearCache(): void {
    try {
      const keysToDelete: string[] = [];
      for (let i = 0; i < localStorage.length; i++) {
        const key = localStorage.key(i);
        if (key && (key.startsWith(TASK_CACHE_PREFIX) || key.startsWith(GEOFENCE_CACHE_PREFIX) || key.startsWith(OFFLINE_CACHE_PREFIX))) {
          keysToDelete.push(key);
        }
      }
      keysToDelete.forEach((k) => localStorage.removeItem(k));
    } catch {}
  }
}

export const mapService = new MapService();
export default mapService;