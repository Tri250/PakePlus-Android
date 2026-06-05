/**
 * 数据回传接口服务
 * 
 * 支持回传到：
 * - 品牌 CRM（华为 CEM、小米零售通、OPPO 零售系统等）
 * - 企业微信
 * - 腾讯广告
 * - 运营商系统
 * - 品牌总部数据中台
 */

import { api, API_ENDPOINTS } from './api';
import { repository } from './storage';
import { toastSuccess, toastError } from '../components/Toast';
import { getEnv } from './env';

/* -------------------------------------------------------------------------- */
/*  类型定义                                                                    */
/* -------------------------------------------------------------------------- */

export type SyncTarget = 
  | 'huawei_cem'      // 华为 CEM
  | 'xiaomi_retail'   // 小米零售通
  | 'oppo_retail'     // OPPO 零售系统
  | 'vivo_retail'     // vivo 零售云
  | 'apple_reseller'  // Apple Reseller
  | 'wecom'           // 企业微信
  | 'tencent_ads'     // 腾讯广告
  | 'carrier'         // 运营商系统
  | 'brand_hq';       // 品牌总部

export interface SyncConfig {
  target: SyncTarget;
  endpoint: string;
  apiKey: string;
  enabled: boolean;
  syncInterval: number;  // 同步间隔（分钟）
  lastSyncAt?: string;
}

export interface SyncPayload {
  type: 'customer' | 'lead' | 'service' | 'nfc_event' | 'task' | 'analytics';
  action: 'create' | 'update' | 'delete';
  data: any;
  timestamp: string;
  storeId: string;
  staffId?: string;
}

export interface SyncResult {
  target: SyncTarget;
  success: boolean;
  syncedCount: number;
  failedCount: number;
  errors: string[];
  duration: number;
  timestamp: string;
}

export interface BrandCRMConfig {
  name: string;
  target: SyncTarget;
  apiVersion: string;
  authType: 'bearer' | 'api_key' | 'oauth2';
  endpoints: {
    customers: string;
    leads: string;
    services: string;
    sync: string;
  };
}

/* -------------------------------------------------------------------------- */
/*  品牌 CRM 配置                                                               */
/* -------------------------------------------------------------------------- */

const BRAND_CRM_CONFIGS: Record<SyncTarget, BrandCRMConfig> = {
  huawei_cem: {
    name: '华为 CEM',
    target: 'huawei_cem',
    apiVersion: 'v2',
    authType: 'bearer',
    endpoints: {
      customers: '/api/v2/customers',
      leads: '/api/v2/leads',
      services: '/api/v2/services',
      sync: '/api/v2/sync',
    },
  },
  xiaomi_retail: {
    name: '小米零售通',
    target: 'xiaomi_retail',
    apiVersion: 'v1',
    authType: 'api_key',
    endpoints: {
      customers: '/api/v1/members',
      leads: '/api/v1/leads',
      services: '/api/v1/records',
      sync: '/api/v1/sync',
    },
  },
  oppo_retail: {
    name: 'OPPO 零售系统',
    target: 'oppo_retail',
    apiVersion: 'v1',
    authType: 'bearer',
    endpoints: {
      customers: '/api/v1/customers',
      leads: '/api/v1/leads',
      services: '/api/v1/services',
      sync: '/api/v1/sync',
    },
  },
  vivo_retail: {
    name: 'vivo 零售云',
    target: 'vivo_retail',
    apiVersion: 'v1',
    authType: 'bearer',
    endpoints: {
      customers: '/api/v1/customers',
      leads: '/api/v1/leads',
      services: '/api/v1/services',
      sync: '/api/v1/sync',
    },
  },
  apple_reseller: {
    name: 'Apple Reseller',
    target: 'apple_reseller',
    apiVersion: 'v1',
    authType: 'oauth2',
    endpoints: {
      customers: '/api/v1/customers',
      leads: '/api/v1/leads',
      services: '/api/v1/services',
      sync: '/api/v1/sync',
    },
  },
  wecom: {
    name: '企业微信',
    target: 'wecom',
    apiVersion: 'v1',
    authType: 'bearer',
    endpoints: {
      customers: '/cgi-bin/externalcontact',
      leads: '/cgi-bin/externalcontact',
      services: '/cgi-bin/message',
      sync: '/cgi-bin/batch',
    },
  },
  tencent_ads: {
    name: '腾讯广告',
    target: 'tencent_ads',
    apiVersion: 'v3',
    authType: 'oauth2',
    endpoints: {
      customers: '/api/v3/user_data',
      leads: '/api/v3/leads',
      services: '/api/v3/conversions',
      sync: '/api/v3/sync',
    },
  },
  carrier: {
    name: '运营商系统',
    target: 'carrier',
    apiVersion: 'v1',
    authType: 'api_key',
    endpoints: {
      customers: '/api/v1/subscribers',
      leads: '/api/v1/leads',
      services: '/api/v1/services',
      sync: '/api/v1/sync',
    },
  },
  brand_hq: {
    name: '品牌总部',
    target: 'brand_hq',
    apiVersion: 'v1',
    authType: 'bearer',
    endpoints: {
      customers: '/api/v1/customers',
      leads: '/api/v1/leads',
      services: '/api/v1/services',
      sync: '/api/v1/sync',
    },
  },
};

/* -------------------------------------------------------------------------- */
/*  数据回传服务类                                                              */
/* -------------------------------------------------------------------------- */

class DataSyncService {
  private configs: Map<SyncTarget, SyncConfig> = new Map();
  private syncQueue: SyncPayload[] = [];
  private isSyncing = false;

  constructor() {
    // 初始化默认配置
    this.loadConfigs();
  }

  /**
   * 加载配置
   */
  private loadConfigs(): void {
    try {
      const saved = typeof localStorage !== 'undefined' 
        ? localStorage.getItem('sync_configs') 
        : null;
      if (saved) {
        const configs = JSON.parse(saved);
        for (const config of configs) {
          this.configs.set(config.target, config);
        }
      }
    } catch (e) {
      // 忽略存储错误
    }
  }

  /**
   * 保存配置
   */
  private saveConfigs(): void {
    try {
      if (typeof localStorage !== 'undefined') {
        const configs = Array.from(this.configs.values());
        localStorage.setItem('sync_configs', JSON.stringify(configs));
      }
    } catch (e) {
      // 忽略存储错误
    }
  }

  /**
   * 设置同步配置
   */
  setConfig(config: SyncConfig): void {
    this.configs.set(config.target, config);
    this.saveConfigs();
  }

  /**
   * 获取配置
   */
  getConfig(target: SyncTarget): SyncConfig | undefined {
    return this.configs.get(target);
  }

  /**
   * 获取所有配置
   */
  getAllConfigs(): SyncConfig[] {
    return Array.from(this.configs.values());
  }

  /**
   * 添加到同步队列
   */
  addToQueue(payload: SyncPayload): void {
    this.syncQueue.push(payload);
    // 自动触发同步
    this.triggerSync();
  }

  /**
   * 触发同步
   */
  private async triggerSync(): Promise<void> {
    if (this.isSyncing || this.syncQueue.length === 0) return;

    this.isSyncing = true;
    const batch = this.syncQueue.splice(0, 50); // 每批最多 50 条

    try {
      const enabledConfigs = Array.from(this.configs.values()).filter(c => c.enabled);
      
      for (const config of enabledConfigs) {
        await this.syncBatch(config, batch);
      }
    } finally {
      this.isSyncing = false;
      
      // 如果还有待同步数据，继续
      if (this.syncQueue.length > 0) {
        setTimeout(() => this.triggerSync(), 1000);
      }
    }
  }

  /**
   * 同步一批数据
   */
  private async syncBatch(config: SyncConfig, batch: SyncPayload[]): Promise<SyncResult> {
    const startTime = Date.now();
    const errors: string[] = [];
    let syncedCount = 0;
    let failedCount = 0;

    const crmConfig = BRAND_CRM_CONFIGS[config.target];

    for (const payload of batch) {
      try {
        // 根据类型选择端点
        let endpoint = crmConfig.endpoints.sync;
        if (payload.type === 'customer') endpoint = crmConfig.endpoints.customers;
        else if (payload.type === 'lead') endpoint = crmConfig.endpoints.leads;
        else if (payload.type === 'service') endpoint = crmConfig.endpoints.services;

        // 模拟 API 调用（实际应调用真实 API）
        await this.callAPI(config, endpoint, payload);
        syncedCount++;
      } catch (err: any) {
        failedCount++;
        errors.push(`${payload.type}:${payload.data.id} - ${err.message}`);
      }
    }

    // 更新最后同步时间
    config.lastSyncAt = new Date().toISOString();
    this.saveConfigs();

    return {
      target: config.target,
      success: failedCount === 0,
      syncedCount,
      failedCount,
      errors,
      duration: Date.now() - startTime,
      timestamp: new Date().toISOString(),
    };
  }

  /**
   * 调用 API
   */
  private async callAPI(
    config: SyncConfig,
    endpoint: string,
    payload: SyncPayload
  ): Promise<any> {
    const crmConfig = BRAND_CRM_CONFIGS[config.target];

    // 模拟网络延迟
    await new Promise(r => setTimeout(r, 100 + Math.random() * 200));

    // 构建请求
    const headers: Record<string, string> = {
      'Content-Type': 'application/json',
    };

    if (crmConfig.authType === 'bearer') {
      headers['Authorization'] = `Bearer ${config.apiKey}`;
    } else if (crmConfig.authType === 'api_key') {
      headers['X-Api-Key'] = config.apiKey;
    }

    // 实际项目中应该调用真实 API
    // const response = await fetch(`${config.endpoint}${endpoint}`, {
    //   method: 'POST',
    //   headers,
    //   body: JSON.stringify(payload),
    // });

    console.log(`[DataSync] ${config.target} -> ${endpoint}`, payload);
    return { success: true };
  }

  /**
   * 手动触发全量同步
   */
  async syncAll(target?: SyncTarget): Promise<SyncResult[]> {
    const results: SyncResult[] = [];
    const configs = target 
      ? [this.configs.get(target)].filter(Boolean) as SyncConfig[]
      : Array.from(this.configs.values()).filter(c => c.enabled);

    for (const config of configs) {
      try {
        // 从本地数据库读取所有数据
        const customers = await repository.customer.getAll();
        const leads = await repository.lead.getAll();
        const tasks = await repository.task.getAll();

        const allData: SyncPayload[] = [
          ...customers.map(c => ({
            type: 'customer' as const,
            action: 'update' as const,
            data: c,
            timestamp: new Date().toISOString(),
            storeId: c.storeId || 'ST001',
          })),
          ...leads.map(l => ({
            type: 'lead' as const,
            action: 'update' as const,
            data: l,
            timestamp: new Date().toISOString(),
            storeId: l.storeId || 'ST001',
          })),
          ...tasks.map(t => ({
            type: 'task' as const,
            action: 'update' as const,
            data: t,
            timestamp: new Date().toISOString(),
            storeId: t.storeId || 'ST001',
          })),
        ];

        const result = await this.syncBatch(config, allData);
        results.push(result);

        if (result.success) {
          toastSuccess(`${BRAND_CRM_CONFIGS[config.target].name} 同步成功: ${result.syncedCount} 条`);
        } else {
          toastError(`${BRAND_CRM_CONFIGS[config.target].name} 同步失败: ${result.failedCount} 条`);
        }
      } catch (err: any) {
        results.push({
          target: config.target,
          success: false,
          syncedCount: 0,
          failedCount: 0,
          errors: [err.message],
          duration: 0,
          timestamp: new Date().toISOString(),
        });
      }
    }

    return results;
  }

  /**
   * 获取同步状态
   */
  getSyncStatus(): Record<SyncTarget, { enabled: boolean; lastSyncAt?: string; queueSize: number }> {
    const status: Record<string, any> = {};
    
    for (const [target, config] of this.configs) {
      status[target] = {
        enabled: config.enabled,
        lastSyncAt: config.lastSyncAt,
        queueSize: this.syncQueue.length,
      };
    }
    
    return status;
  }

  /**
   * 测试连接
   */
  async testConnection(target: SyncTarget): Promise<{ success: boolean; latency: number; message: string }> {
    const config = this.configs.get(target);
    if (!config) {
      return { success: false, latency: 0, message: '未配置' };
    }

    const startTime = Date.now();
    try {
      // 模拟测试连接
      await new Promise(r => setTimeout(r, 200 + Math.random() * 300));
      const latency = Date.now() - startTime;
      
      return { 
        success: true, 
        latency, 
        message: `连接成功，延迟 ${latency}ms` 
      };
    } catch (err: any) {
      return { 
        success: false, 
        latency: Date.now() - startTime, 
        message: err.message 
      };
    }
  }
}

export const dataSyncService = new DataSyncService();
export default dataSyncService;

/* -------------------------------------------------------------------------- */
/*  预设同步配置                                                                */
/* -------------------------------------------------------------------------- */

export const DEFAULT_SYNC_CONFIGS: SyncConfig[] = [
  {
    target: 'huawei_cem',
    endpoint: 'https://crm.huawei.com',
    apiKey: '',
    enabled: false,
    syncInterval: 30,
  },
  {
    target: 'xiaomi_retail',
    endpoint: 'https://retail.mi.com',
    apiKey: '',
    enabled: false,
    syncInterval: 30,
  },
  {
    target: 'oppo_retail',
    endpoint: 'https://retail.oppo.com',
    apiKey: '',
    enabled: false,
    syncInterval: 30,
  },
  {
    target: 'vivo_retail',
    endpoint: 'https://retail.vivo.com',
    apiKey: '',
    enabled: false,
    syncInterval: 30,
  },
  {
    target: 'wecom',
    endpoint: 'https://qyapi.weixin.qq.com',
    apiKey: '',
    enabled: false,
    syncInterval: 10,
  },
  {
    target: 'brand_hq',
    endpoint: getEnv('VITE_CRM_API_URL'),
    apiKey: getEnv('VITE_CRM_SYNC_KEY'),
    enabled: true,
    syncInterval: 15,
  },
];
