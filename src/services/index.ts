/**
 * 服务层统一导出
 */

// 环境变量辅助
export { getEnv, getEnvNumber, getEnvBoolean } from './env';

// 网络层
export { api, API_ENDPOINTS, addRequestInterceptor, addResponseInterceptor } from './api';
export type { ApiResponse, ApiError, RequestConfig } from './api';

// 权限管理
export {
  useAuthStore,
  requirePermission,
  requireAnyPermission,
  getRoleName,
  getRoleColor,
  getAuditLogs,
  clearAuditLogs,
} from './auth';
export type { User, Role, Permission, AuditLog } from './auth';

// AI 算法
export {
  predictReplacement,
  segmentCustomers,
  generateSmartScript,
  optimizeRoute,
  analyzeHeatmap,
} from './ai';
export type {
  CustomerProfile,
  ReplacementPrediction,
  CustomerSegment,
  SmartScript,
  RoutePoint,
  OptimizedRoute,
  HeatmapCell,
} from './ai';

// 数据持久化
export {
  storageSet,
  storageGet,
  storageRemove,
  storageClear,
  initDB,
  dbGet,
  dbGetAll,
  dbPut,
  dbDelete,
  dbQueryByIndex,
  addToSyncQueue,
  getSyncQueue,
  processSyncQueue,
  repository,
} from './storage';
export type { StoredEntity, SyncQueueItem } from './storage';

// 功能检测
export {
  runFullCheck,
  checkLBSRadar,
  checkCustomerAsset,
  checkGroundCombat,
  checkBrandDataPlatform,
  checkAIService,
  checkStorageLayer,
  checkAuthSystem,
  COMPARISON_2026,
} from './check';
export type { ModuleCheckResult, CheckItem } from './check';

// 地理编码服务
export {
  nominatimGeocode,
  nominatimReverseGeocode,
  nominatimSearchPOI,
  geocodeWithFallback,
} from './nominatimApi';
export type { NominatimResult } from './nominatimApi';

// 高德 Place API
export { searchPlaces, clearPlaceCache } from './amapPlaceApi';
export type { Place } from './amapPlaceApi';

// GEO 排名 API
export { queryGEORanking, listPlatforms } from './geoRankingApi';
export type { GEORankingResponse, GEOPlatform } from './geoRankingApi';

// 换机预测
export { predictReplacement as predictDeviceReplacement, ALERT_COLORS } from './replacementPredictor';
export type { DeviceInput, ReplacementPrediction as DevicePrediction, AlertLevel } from './replacementPredictor';

// NFC API
export {
  postNFCTapEvent,
  listRecentTapEvents,
  listTagPool,
  getActionLabel,
} from './nfcTapApi';
export type { NFCTapEvent, NFCTapResponse, NFCAction } from './nfcTapApi';

// 地图服务
export { mapService, MAP_PROVIDERS } from './mapService';
export type { MapProvider, MapConfig, GeocodingResult, POIResult } from './mapService';

// 地理定位服务
export { geolocationService } from './geolocation';
export type { LocationResult, WatchPositionOptions } from './geolocation';

// 数据采集
export { dataCollector, DataCollector, PRESET_SCANS } from './dataCollector';
export type { ScanRadius, POICategory, ScanConfig, ScanResult, ScanReport } from './dataCollector';

// 图片服务
export { imageService, POSTER_TEMPLATES } from './imageService';
export type { ImageCategory, PosterTemplate, ImageUploadResult, PosterConfig, GeneratedPoster } from './imageService';

// 数据同步
export { dataSyncService, DEFAULT_SYNC_CONFIGS } from './dataSync';
export type { SyncTarget, SyncConfig, SyncPayload, SyncResult, BrandCRMConfig } from './dataSync';

// 接口测试
export {
  runAllTests,
  quickHealthCheck,
  testMapServices,
  testDataCollector,
  testDataSync,
  testImageService,
  testStorage,
  testAIService,
} from './test';
export type { TestResult, TestReport } from './test';
