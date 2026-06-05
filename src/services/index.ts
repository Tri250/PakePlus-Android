/**
 * 服务层统一导出
 */

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
