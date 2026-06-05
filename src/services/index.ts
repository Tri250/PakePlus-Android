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
  ReplacementPrediction as AIReplacementPrediction,
  CustomerSegment as AICustomerSegment,
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

// GEO 搜索优化引擎
export { geoOptimizationEngine } from './geoOptimization';
export type {
  BrandKeyword,
  StoreDescription,
  AISearchRankResult,
  AttributionRecord,
  GEORankingReport,
  AISearchPlatform,
} from './geoOptimization';

// 竞品热力监控
export { competitorMonitorService } from './competitorMonitor';
export type {
  CompetitorStore,
  CompetitorActivity,
  HeatmapData,
  InterceptionPlan,
  CompetitorMonitorReport,
  CompetitorBrand,
} from './competitorMonitor';

// LBS 雷达扫描
export { lbsRadarService } from './lbsRadar';
export type {
  SalesLead,
  POIData,
  CRMData,
  ReplacementPrediction as LBSReplacementPrediction,
  TradeInQuote,
  LBSRadarScanResult,
  HeatmapPoint,
  CustomerSegment as LBSCustomerSegment,
  AlertLevel as LBSAlertLevel,
} from './lbsRadar';

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

// 数据爬虫采集
export { dataCrawlerService } from './dataCrawler';
export type {
  CrawlerSource,
  CrawlerConfig,
  CrawlerResult,
  POICrawlData,
  ProductCrawlData,
  SubsidyCrawlData,
} from './dataCrawler';

// 网络服务管理
export { networkManager } from './networkManager';
export type {
  NetworkStatus,
  NetworkType,
  NetworkInfo,
  NetworkConfig,
} from './networkManager';

// 分辨率适配
export { resolutionAdapter } from './resolutionAdapter';
export type {
  DeviceType,
  Orientation,
  PixelDensity,
  ScreenInfo,
  AdaptiveValue,
  Breakpoints,
} from './resolutionAdapter';

// 动态加载
export { dynamicLoader } from './dynamicLoader';
export type {
  LoadStatus,
  LoadPriority,
  ModuleConfig,
  ModuleState,
  LoadProgress,
} from './dynamicLoader';

// 模块完整性检查
export { moduleChecker } from './moduleChecker';
export type {
  CheckStatus,
  ModuleCheckResult as ModuleQualityResult,
  CheckItem as ModuleCheckItem,
  CoverageReport,
  QualityReport,
} from './moduleChecker';

// API路由配置
export { apiRouterService } from './apiRouter';
export type {
  ApiRoute,
  CrawlerEndpoint,
  RouteGroup,
  HttpMethod,
  RouteCategory,
} from './apiRouter';

// 权限隐私安全
export { securityService } from './security';
export type {
  PrivacyPolicy,
  SecurityAudit,
  DataEncryption,
  AccessControl,
  PermissionLevel,
  DataCategory,
} from './security';

// UI/UX统一
export { uiuxService } from './uiux';
export type {
  ThemeMode,
  ThemeConfig,
  CardStyle,
  CardConfig,
  AnimationType,
  AnimationConfig,
  ComponentStyle,
} from './uiux';
