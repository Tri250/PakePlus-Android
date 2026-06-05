/**
 * 动态加载服务
 * 按需加载模块、数据、组件
 * 支持懒加载、预加载、缓存
 */

import { safeLocalStorageGet, safeLocalStorageSet } from './env';

/* -------------------------------------------------------------------------- */
/*  类型定义                                                                    */
/* -------------------------------------------------------------------------- */

export type LoadStatus = 'idle' | 'loading' | 'loaded' | 'error';
export type LoadPriority = 'critical' | 'high' | 'medium' | 'low';

export interface ModuleConfig {
  id: string;
  name: string;
  path: string;
  priority: LoadPriority;
  dependencies: string[];
  preload: boolean;
  cacheEnabled: boolean;
  timeout: number;
}

export interface ModuleState {
  id: string;
  status: LoadStatus;
  module: any;
  error?: string;
  loadTime: number;
  lastAccess: number;
}

export interface DataChunk {
  id: string;
  data: any;
  size: number;
  timestamp: number;
  ttl: number;
}

export interface LoadProgress {
  total: number;
  loaded: number;
  failed: number;
  percentage: number;
  currentModule?: string;
}

/* -------------------------------------------------------------------------- */
/*  模块配置                                                                    */
/* -------------------------------------------------------------------------- */

const MODULE_CONFIGS: Record<string, ModuleConfig> = {
  // 核心模块（立即加载）
  'core': {
    id: 'core',
    name: '核心模块',
    path: './index',
    priority: 'critical',
    dependencies: [],
    preload: true,
    cacheEnabled: true,
    timeout: 5000,
  },
  
  // 地图模块（高优先级）
  'map': {
    id: 'map',
    name: '地图服务',
    path: './mapService',
    priority: 'high',
    dependencies: [],
    preload: true,
    cacheEnabled: true,
    timeout: 10000,
  },
  
  // 数据采集模块
  'collector': {
    id: 'collector',
    name: '数据采集',
    path: './dataCollector',
    priority: 'high',
    dependencies: [],
    preload: true,
    cacheEnabled: true,
    timeout: 15000,
  },
  
  // LBS雷达模块
  'lbs-radar': {
    id: 'lbs-radar',
    name: 'LBS雷达',
    path: './lbsRadar',
    priority: 'high',
    dependencies: [],
    preload: false,
    cacheEnabled: true,
    timeout: 10000,
  },
  
  // 竞品监控模块
  'competitor': {
    id: 'competitor',
    name: '竞品监控',
    path: './competitorMonitor',
    priority: 'medium',
    dependencies: [],
    preload: false,
    cacheEnabled: true,
    timeout: 10000,
  },
  
  // GEO优化模块
  'geo': {
    id: 'geo',
    name: 'GEO优化',
    path: './geoOptimization',
    priority: 'medium',
    dependencies: [],
    preload: false,
    cacheEnabled: true,
    timeout: 10000,
  },
  
  // 图片服务模块
  'image': {
    id: 'image',
    name: '图片服务',
    path: './imageService',
    priority: 'medium',
    dependencies: [],
    preload: false,
    cacheEnabled: true,
    timeout: 10000,
  },
  
  // AI服务模块
  'ai': {
    id: 'ai',
    name: 'AI服务',
    path: './ai',
    priority: 'medium',
    dependencies: [],
    preload: false,
    cacheEnabled: true,
    timeout: 15000,
  },
  
  // 数据同步模块
  'sync': {
    id: 'sync',
    name: '数据同步',
    path: './dataSync',
    priority: 'low',
    dependencies: [],
    preload: false,
    cacheEnabled: true,
    timeout: 20000,
  },
};

/* -------------------------------------------------------------------------- */
/*  动态加载服务                                                                 */
/* -------------------------------------------------------------------------- */

class DynamicLoader {
  private moduleStates: Map<string, ModuleState> = new Map();
  private dataCache: Map<string, DataChunk> = new Map();
  private loadingQueue: string[] = [];
  private isLoading = false;
  
  private progressListeners: Array<(progress: LoadProgress) => void> = [];
  private moduleListeners: Array<(id: string, state: ModuleState) => void> = [];

  constructor() {
    // 初始化模块状态
    Object.keys(MODULE_CONFIGS).forEach(id => {
      this.moduleStates.set(id, {
        id,
        status: 'idle',
        module: null,
        loadTime: 0,
        lastAccess: 0,
      });
    });
    
    // 加载数据缓存
    this.loadDataCache();
    
    // 预加载关键模块（仅在浏览器环境）
    if (typeof window !== 'undefined') {
      this.preloadCritical();
    }
  }

  /**
   * 预加载关键模块
   */
  private async preloadCritical(): Promise<void> {
    const criticalModules = Object.values(MODULE_CONFIGS)
      .filter(m => m.preload && m.priority === 'critical')
      .map(m => m.id);

    for (const id of criticalModules) {
      await this.loadModule(id);
    }
  }

  /**
   * 加载模块
   */
  async loadModule(id: string): Promise<any> {
    const config = MODULE_CONFIGS[id];
    if (!config) {
      throw new Error(`[DynamicLoader] 未知模块: ${id}`);
    }

    const state = this.moduleStates.get(id);
    if (!state) {
      throw new Error(`[DynamicLoader] 模块状态不存在: ${id}`);
    }

    // 已加载
    if (state.status === 'loaded') {
      state.lastAccess = Date.now();
      return state.module;
    }

    // 正在加载
    if (state.status === 'loading') {
      return this.waitForLoad(id);
    }

    // 加载依赖
    for (const depId of config.dependencies) {
      await this.loadModule(depId);
    }

    // 开始加载
    state.status = 'loading';
    this.notifyModuleListeners(id, state);

    try {
      const startTime = Date.now();
      
      // 动态导入
      const module = await this.importModule(config.path, config.timeout);
      
      state.status = 'loaded';
      state.module = module;
      state.loadTime = Date.now() - startTime;
      state.lastAccess = Date.now();
      
      this.notifyModuleListeners(id, state);
      console.log(`[DynamicLoader] 模块加载成功: ${id} (${state.loadTime}ms)`);
      
      return module;
    } catch (err: any) {
      state.status = 'error';
      state.error = err.message;
      this.notifyModuleListeners(id, state);
      console.error(`[DynamicLoader] 模块加载失败: ${id}`, err);
      throw err;
    }
  }

  /**
   * 动态导入模块
   */
  private async importModule(path: string, timeout: number): Promise<any> {
    // 使用动态 import（兼容浏览器和 Node.js ESM）
    const controller = new AbortController();
    const timeoutId = setTimeout(() => controller.abort(), timeout);
    
    try {
      const module = await import(path);
      clearTimeout(timeoutId);
      return module;
    } catch (err) {
      clearTimeout(timeoutId);
      throw err;
    }
  }

  /**
   * 等待模块加载完成
   */
  private async waitForLoad(id: string): Promise<any> {
    return new Promise((resolve, reject) => {
      const unsubscribe = this.onModuleLoad((loadedId, state) => {
        if (loadedId === id) {
          unsubscribe();
          if (state.status === 'loaded') {
            resolve(state.module);
          } else {
            reject(new Error(state.error || '加载失败'));
          }
        }
      });
    });
  }

  /**
   * 批量加载模块
   */
  async loadModules(ids: string[]): Promise<Record<string, any>> {
    const results: Record<string, any> = {};
    
    // 按优先级排序
    const sorted = ids.sort((a, b) => {
      const configA = MODULE_CONFIGS[a];
      const configB = MODULE_CONFIGS[b];
      const priorityOrder = { critical: 0, high: 1, medium: 2, low: 3 };
      return priorityOrder[configA?.priority || 'low'] - priorityOrder[configB?.priority || 'low'];
    });

    let loaded = 0;
    let failed = 0;

    for (const id of sorted) {
      try {
        results[id] = await this.loadModule(id);
        loaded++;
      } catch {
        failed++;
      }
      
      this.notifyProgressListeners({
        total: ids.length,
        loaded,
        failed,
        percentage: Math.round((loaded + failed) / ids.length * 100),
        currentModule: id,
      });
    }

    return results;
  }

  /**
   * 预加载模块（后台加载）
   */
  preloadModules(ids: string[]): void {
    for (const id of ids) {
      const config = MODULE_CONFIGS[id];
      if (config && config.preload) {
        this.loadModule(id).catch(err => {
          console.warn(`[DynamicLoader] 预加载失败: ${id}`, err);
        });
      }
    }
  }

  /**
   * 获取模块状态
   */
  getModuleState(id: string): ModuleState | null {
    return this.moduleStates.get(id) || null;
  }

  /**
   * 获取所有模块状态
   */
  getAllModuleStates(): ModuleState[] {
    return Array.from(this.moduleStates.values());
  }

  /**
   * 卸载模块
   */
  unloadModule(id: string): void {
    const state = this.moduleStates.get(id);
    if (state) {
      state.status = 'idle';
      state.module = null;
      state.error = undefined;
      this.notifyModuleListeners(id, state);
      console.log(`[DynamicLoader] 模块已卸载: ${id}`);
    }
  }

  /**
   * 加载数据块
   */
  async loadData<T>(
    key: string,
    loader: () => Promise<T>,
    options: { ttl?: number; force?: boolean } = {}
  ): Promise<T> {
    const { ttl = 5 * 60 * 1000, force = false } = options;

    // 检查缓存
    if (!force) {
      const cached = this.dataCache.get(key);
      if (cached && Date.now() - cached.timestamp < cached.ttl) {
        return cached.data as T;
      }
    }

    // 加载数据
    const data = await loader();

    // 缓存数据
    const chunk: DataChunk = {
      id: key,
      data,
      size: JSON.stringify(data).length,
      timestamp: Date.now(),
      ttl,
    };
    
    this.dataCache.set(key, chunk);
    this.saveDataCache();

    return data;
  }

  /**
   * 清除数据缓存
   */
  clearDataCache(key?: string): void {
    if (key) {
      this.dataCache.delete(key);
    } else {
      this.dataCache.clear();
    }
    this.saveDataCache();
  }

  /**
   * 获取缓存统计
   */
  getCacheStats(): { count: number; totalSize: number; keys: string[] } {
    let totalSize = 0;
    const keys: string[] = [];
    
    this.dataCache.forEach((chunk, key) => {
      totalSize += chunk.size;
      keys.push(key);
    });

    return {
      count: this.dataCache.size,
      totalSize,
      keys,
    };
  }

  /**
   * 监听加载进度
   */
  onProgress(callback: (progress: LoadProgress) => void): () => void {
    this.progressListeners.push(callback);
    return () => {
      const index = this.progressListeners.indexOf(callback);
      if (index > -1) this.progressListeners.splice(index, 1);
    };
  }

  /**
   * 监听模块加载
   */
  onModuleLoad(callback: (id: string, state: ModuleState) => void): () => void {
    this.moduleListeners.push(callback);
    return () => {
      const index = this.moduleListeners.indexOf(callback);
      if (index > -1) this.moduleListeners.splice(index, 1);
    };
  }

  /**
   * 通知进度监听器
   */
  private notifyProgressListeners(progress: LoadProgress): void {
    this.progressListeners.forEach(cb => cb(progress));
  }

  /**
   * 通知模块监听器
   */
  private notifyModuleListeners(id: string, state: ModuleState): void {
    this.moduleListeners.forEach(cb => cb(id, state));
  }

  /* -------------------------------------------------------------------------- */
  /*  数据持久化                                                                   */
  /* -------------------------------------------------------------------------- */

  private DATA_CACHE_KEY = 'dynamic_loader_cache';

  private loadDataCache(): void {
    try {
      const saved = safeLocalStorageGet(this.DATA_CACHE_KEY);
      if (saved) {
        const data = JSON.parse(saved);
        Object.entries(data).forEach(([key, value]) => {
          this.dataCache.set(key, value as DataChunk);
        });
      }
    } catch {}
  }

  private saveDataCache(): void {
    try {
      const data: Record<string, DataChunk> = {};
      this.dataCache.forEach((value, key) => {
        data[key] = value;
      });
      safeLocalStorageSet(this.DATA_CACHE_KEY, JSON.stringify(data));
    } catch {}
  }
}

export const dynamicLoader = new DynamicLoader();
export default dynamicLoader;
