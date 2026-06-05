/**
 * 网络服务管理
 * 动态切换、容错、离线支持、请求队列
 */

import { safeLocalStorageGet, safeLocalStorageSet } from './env';

/* -------------------------------------------------------------------------- */
/*  类型定义                                                                    */
/* -------------------------------------------------------------------------- */

export type NetworkStatus = 'online' | 'offline' | 'slow' | 'unstable';
export type NetworkType = 'wifi' | '4g' | '5g' | '3g' | '2g' | 'unknown';

export interface NetworkInfo {
  status: NetworkStatus;
  type: NetworkType;
  effectiveType: string;
  downlink: number; // Mbps
  rtt: number; // ms
  saveData: boolean;
}

export interface RequestQueueItem {
  id: string;
  url: string;
  method: 'GET' | 'POST' | 'PUT' | 'DELETE';
  headers: Record<string, string>;
  body?: any;
  priority: 'high' | 'medium' | 'low';
  retryCount: number;
  maxRetry: number;
  createdAt: number;
  lastError?: string;
}

export interface NetworkConfig {
  timeout: number;
  retryCount: number;
  retryDelay: number;
  cacheEnabled: boolean;
  cacheTTL: number;
  offlineMode: boolean;
}

/* -------------------------------------------------------------------------- */
/*  网络服务管理器                                                               */
/* -------------------------------------------------------------------------- */

class NetworkManager {
  private networkInfo: NetworkInfo = {
    status: 'online',
    type: 'unknown',
    effectiveType: '4g',
    downlink: 10,
    rtt: 100,
    saveData: false,
  };
  
  private requestQueue: RequestQueueItem[] = [];
  private isProcessingQueue = false;
  private listeners: Array<(info: NetworkInfo) => void> = [];
  
  private config: NetworkConfig = {
    timeout: 30000,
    retryCount: 3,
    retryDelay: 1000,
    cacheEnabled: true,
    cacheTTL: 5 * 60 * 1000,
    offlineMode: false,
  };

  constructor() {
    this.initNetworkMonitoring();
    this.loadRequestQueue();
  }

  /**
   * 初始化网络监控
   */
  private initNetworkMonitoring(): void {
    // 浏览器环境
    if (typeof navigator !== 'undefined' && typeof window !== 'undefined') {
      // 初始状态
      this.updateNetworkInfo();

      // 监听网络变化
      if ('connection' in navigator) {
        const connection = (navigator as any).connection;
        connection?.addEventListener('change', () => this.updateNetworkInfo());
      }

      // 监听在线/离线事件
      window.addEventListener('online', () => {
        this.networkInfo.status = 'online';
        this.notifyListeners();
        this.processQueue();
      });

      window.addEventListener('offline', () => {
        this.networkInfo.status = 'offline';
        this.notifyListeners();
      });
    } else {
      // Node.js 环境，默认在线
      this.networkInfo.status = 'online';
      this.networkInfo.type = 'wifi';
    }
  }

  /**
   * 更新网络信息
   */
  private updateNetworkInfo(): void {
    if (typeof navigator === 'undefined') return;

    const nav = navigator as any;
    
    if (nav.connection) {
      const conn = nav.connection;
      this.networkInfo = {
        status: navigator.onLine ? 'online' : 'offline',
        type: this.parseNetworkType(conn.type),
        effectiveType: conn.effectiveType || '4g',
        downlink: conn.downlink || 10,
        rtt: conn.rtt || 100,
        saveData: conn.saveData || false,
      };
    } else {
      this.networkInfo.status = navigator.onLine ? 'online' : 'offline';
    }

    // 判断网络质量
    if (this.networkInfo.status === 'online') {
      if (this.networkInfo.rtt > 500 || this.networkInfo.downlink < 1) {
        this.networkInfo.status = 'slow';
      } else if (this.networkInfo.rtt > 200) {
        this.networkInfo.status = 'unstable';
      }
    }

    this.notifyListeners();
  }

  /**
   * 解析网络类型
   */
  private parseNetworkType(type: string): NetworkType {
    const typeMap: Record<string, NetworkType> = {
      'wifi': 'wifi',
      'cellular': '4g',
      '4g': '4g',
      '3g': '3g',
      '2g': '2g',
      '5g': '5g',
    };
    return typeMap[type] || 'unknown';
  }

  /**
   * 获取网络信息
   */
  getNetworkInfo(): NetworkInfo {
    return { ...this.networkInfo };
  }

  /**
   * 是否在线
   */
  isOnline(): boolean {
    return this.networkInfo.status !== 'offline';
  }

  /**
   * 是否高速网络
   */
  isFastNetwork(): boolean {
    return this.networkInfo.status === 'online' && 
           this.networkInfo.downlink >= 5 &&
           this.networkInfo.rtt < 200;
  }

  /**
   * 设置离线模式
   */
  setOfflineMode(enabled: boolean): void {
    this.config.offlineMode = enabled;
    console.log(`[NetworkManager] 离线模式: ${enabled}`);
  }

  /**
   * 智能请求（自动容错、缓存、重试）
   */
  async smartRequest<T>(
    url: string,
    options: {
      method?: 'GET' | 'POST' | 'PUT' | 'DELETE';
      headers?: Record<string, string>;
      body?: any;
      priority?: 'high' | 'medium' | 'low';
      cacheKey?: string;
      cacheTTL?: number;
      timeout?: number;
    } = {}
  ): Promise<{ data: T | null; error: string | null; cached: boolean }> {
    const {
      method = 'GET',
      headers = {},
      body,
      priority = 'medium',
      cacheKey,
      cacheTTL = this.config.cacheTTL,
      timeout = this.config.timeout,
    } = options;

    // 检查缓存
    if (this.config.cacheEnabled && cacheKey) {
      const cached = this.getFromCache(cacheKey);
      if (cached) {
        return { data: cached as T, error: null, cached: true };
      }
    }

    // 离线模式
    if (this.config.offlineMode || !this.isOnline()) {
      // 加入队列等待恢复
      if (priority === 'high') {
        this.addToQueue({
          url,
          method,
          headers,
          body,
          priority,
        });
      }
      return { data: null, error: '网络不可用', cached: false };
    }

    // 发送请求
    try {
      const controller = new AbortController();
      const timeoutId = setTimeout(() => controller.abort(), timeout);

      const response = await fetch(url, {
        method,
        headers,
        body: body ? JSON.stringify(body) : undefined,
        signal: controller.signal,
      });

      clearTimeout(timeoutId);

      if (!response.ok) {
        throw new Error(`HTTP ${response.status}: ${response.statusText}`);
      }

      const data = await response.json();

      // 缓存结果
      if (this.config.cacheEnabled && cacheKey) {
        this.setCache(cacheKey, data, cacheTTL);
      }

      return { data, error: null, cached: false };
    } catch (err: any) {
      // 重试逻辑
      if (priority === 'high') {
        this.addToQueue({
          url,
          method,
          headers,
          body,
          priority,
        });
      }

      return { data: null, error: err.message, cached: false };
    }
  }

  /**
   * 添加到请求队列
   */
  private addToQueue(item: Omit<RequestQueueItem, 'id' | 'retryCount' | 'createdAt' | 'maxRetry'>): void {
    const queueItem: RequestQueueItem = {
      ...item,
      id: `REQ-${Date.now()}-${Math.random().toString(36).slice(2, 6)}`,
      retryCount: 0,
      maxRetry: this.config.retryCount,
      createdAt: Date.now(),
    };

    this.requestQueue.push(queueItem);
    this.saveRequestQueue();

    console.log(`[NetworkManager] 请求加入队列: ${item.url}`);
  }

  /**
   * 处理请求队列
   */
  private async processQueue(): Promise<void> {
    if (this.isProcessingQueue || !this.isOnline()) return;
    this.isProcessingQueue = true;

    while (this.requestQueue.length > 0) {
      const item = this.requestQueue.shift();
      if (!item) break;

      try {
        await fetch(item.url, {
          method: item.method,
          headers: item.headers,
          body: item.body ? JSON.stringify(item.body) : undefined,
        });

        console.log(`[NetworkManager] 队列请求成功: ${item.url}`);
      } catch (err) {
        item.retryCount++;
        if (item.retryCount < item.maxRetry) {
          this.requestQueue.unshift(item);
          await new Promise(r => setTimeout(r, this.config.retryDelay));
        } else {
          console.error(`[NetworkManager] 队列请求失败: ${item.url}`, err);
        }
      }
    }

    this.isProcessingQueue = false;
    this.saveRequestQueue();
  }

  /**
   * 监听网络变化
   */
  onNetworkChange(callback: (info: NetworkInfo) => void): () => void {
    this.listeners.push(callback);
    return () => {
      const index = this.listeners.indexOf(callback);
      if (index > -1) this.listeners.splice(index, 1);
    };
  }

  /**
   * 通知监听器
   */
  private notifyListeners(): void {
    this.listeners.forEach(cb => cb(this.networkInfo));
  }

  /**
   * 获取配置
   */
  getConfig(): NetworkConfig {
    return { ...this.config };
  }

  /**
   * 更新配置
   */
  updateConfig(config: Partial<NetworkConfig>): void {
    this.config = { ...this.config, ...config };
  }

  /* -------------------------------------------------------------------------- */
  /*  缓存管理                                                                    */
  /* -------------------------------------------------------------------------- */

  private CACHE_KEY = 'network_cache';

  private getFromCache(key: string): any | null {
    try {
      const cached = safeLocalStorageGet(this.CACHE_KEY);
      if (!cached) return null;
      const data = JSON.parse(cached);
      const item = data[key];
      if (item && Date.now() - item.timestamp < item.ttl) {
        return item.data;
      }
      return null;
    } catch {
      return null;
    }
  }

  private setCache(key: string, data: any, ttl: number): void {
    try {
      const cached = safeLocalStorageGet(this.CACHE_KEY) || '{}';
      const dataMap = JSON.parse(cached);
      dataMap[key] = { data, timestamp: Date.now(), ttl };
      safeLocalStorageSet(this.CACHE_KEY, JSON.stringify(dataMap));
    } catch {}
  }

  /* -------------------------------------------------------------------------- */
  /*  队列持久化                                                                   */
  /* -------------------------------------------------------------------------- */

  private QUEUE_KEY = 'network_queue';

  private loadRequestQueue(): void {
    try {
      const saved = safeLocalStorageGet(this.QUEUE_KEY);
      if (saved) {
        this.requestQueue = JSON.parse(saved);
      }
    } catch {}
  }

  private saveRequestQueue(): void {
    try {
      safeLocalStorageSet(this.QUEUE_KEY, JSON.stringify(this.requestQueue));
    } catch {}
  }
}

export const networkManager = new NetworkManager();
export default networkManager;
