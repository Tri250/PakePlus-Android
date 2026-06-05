/**
 * 网络请求层 - 统一 API 调用封装
 * 支持：请求拦截、响应拦截、错误处理、重试机制、缓存
 */
import { toastError } from '../components/Toast';
import { safeLocalStorageGet } from './env';

/* -------------------------------------------------------------------------- */
/*  类型定义                                                                    */
/* -------------------------------------------------------------------------- */

export interface RequestConfig extends Omit<RequestInit, 'cache'> {
  timeout?: number;
  retry?: number;
  retryDelay?: number;
  cache?: boolean;
  cacheTTL?: number;
}

export interface ApiResponse<T = any> {
  data: T;
  status: number;
  message: string;
  timestamp: number;
}

export interface ApiError {
  code: string;
  message: string;
  details?: any;
}

/* -------------------------------------------------------------------------- */
/*  缓存管理                                                                    */
/* -------------------------------------------------------------------------- */

const memoryCache = new Map<string, { data: any; expiresAt: number }>();

function getCacheKey(url: string, options?: RequestInit): string {
  const body = options?.body ? JSON.stringify(options.body) : '';
  return `${options?.method || 'GET'}:${url}:${body}`;
}

function getFromCache(key: string): any | null {
  const cached = memoryCache.get(key);
  if (cached && cached.expiresAt > Date.now()) {
    return cached.data;
  }
  memoryCache.delete(key);
  return null;
}

function setCache(key: string, data: any, ttl: number): void {
  memoryCache.set(key, { data, expiresAt: Date.now() + ttl });
}

/* -------------------------------------------------------------------------- */
/*  请求拦截器                                                                  */
/* -------------------------------------------------------------------------- */

type Interceptor = (config: RequestConfig) => RequestConfig | Promise<RequestConfig>;

const requestInterceptors: Interceptor[] = [];
const responseInterceptors: ((response: Response) => Response | Promise<Response>)[] = [];

export function addRequestInterceptor(interceptor: Interceptor): void {
  requestInterceptors.push(interceptor);
}

export function addResponseInterceptor(interceptor: (response: Response) => Response | Promise<Response>): void {
  responseInterceptors.push(interceptor);
}

// 默认请求拦截器：添加 Authorization
addRequestInterceptor((config) => {
  const token = safeLocalStorageGet('auth_token');
  if (token) {
    config.headers = {
      ...config.headers,
      Authorization: `Bearer ${token}`,
    };
  }
  return config;
});

/* -------------------------------------------------------------------------- */
/*  核心请求函数                                                                */
/* -------------------------------------------------------------------------- */

async function request<T>(
  url: string,
  config: RequestConfig = {}
): Promise<ApiResponse<T>> {
  const {
    timeout = 30000,
    retry = 2,
    retryDelay = 1000,
    cache = false,
    cacheTTL = 5 * 60 * 1000,
    ...fetchOptions
  } = config;

  // 应用请求拦截器
  let finalConfig = fetchOptions;
  for (const interceptor of requestInterceptors) {
    finalConfig = await interceptor(finalConfig);
  }

  const cacheKey = getCacheKey(url, finalConfig);

  // 检查缓存
  if (cache && finalConfig.method === 'GET' || !finalConfig.method) {
    const cached = getFromCache(cacheKey);
    if (cached) {
      return cached;
    }
  }

  // 创建 AbortController 用于超时
  const controller = new AbortController();
  const timeoutId = setTimeout(() => controller.abort(), timeout);

  let lastError: Error | null = null;
  let attempts = 0;

  while (attempts <= retry) {
    try {
      const response = await fetch(url, {
        ...finalConfig,
        signal: controller.signal,
      });

      clearTimeout(timeoutId);

      // 应用响应拦截器
      let finalResponse = response;
      for (const interceptor of responseInterceptors) {
        finalResponse = await interceptor(finalResponse);
      }

      if (!finalResponse.ok) {
        const errorData = await finalResponse.json().catch(() => ({}));
        throw new Error(errorData.message || `HTTP ${finalResponse.status}`);
      }

      const data = await finalResponse.json();
      const result: ApiResponse<T> = {
        data,
        status: finalResponse.status,
        message: 'success',
        timestamp: Date.now(),
      };

      // 写入缓存
      if (cache) {
        setCache(cacheKey, result, cacheTTL);
      }

      return result;
    } catch (err: any) {
      lastError = err;
      attempts++;

      if (attempts <= retry && !err.name?.includes('AbortError')) {
        await new Promise((r) => setTimeout(r, retryDelay * attempts));
      }
    }
  }

  clearTimeout(timeoutId);

  // 错误处理
  const errorMessage = lastError?.message || '网络请求失败';
  toastError(errorMessage);

  throw {
    code: 'NETWORK_ERROR',
    message: errorMessage,
    details: lastError,
  } as ApiError;
}

/* -------------------------------------------------------------------------- */
/*  便捷方法                                                                    */
/* -------------------------------------------------------------------------- */

export const api = {
  get: <T>(url: string, config?: RequestConfig) =>
    request<T>(url, { ...config, method: 'GET' }),

  post: <T>(url: string, data?: any, config?: RequestConfig) =>
    request<T>(url, {
      ...config,
      method: 'POST',
      headers: { 'Content-Type': 'application/json', ...config?.headers },
      body: JSON.stringify(data),
    }),

  put: <T>(url: string, data?: any, config?: RequestConfig) =>
    request<T>(url, {
      ...config,
      method: 'PUT',
      headers: { 'Content-Type': 'application/json', ...config?.headers },
      body: JSON.stringify(data),
    }),

  patch: <T>(url: string, data?: any, config?: RequestConfig) =>
    request<T>(url, {
      ...config,
      method: 'PATCH',
      headers: { 'Content-Type': 'application/json', ...config?.headers },
      body: JSON.stringify(data),
    }),

  delete: <T>(url: string, config?: RequestConfig) =>
    request<T>(url, { ...config, method: 'DELETE' }),

  upload: <T>(url: string, file: File, config?: RequestConfig) => {
    const formData = new FormData();
    formData.append('file', file);
    return request<T>(url, {
      ...config,
      method: 'POST',
      body: formData,
    });
  },
};

/* -------------------------------------------------------------------------- */
/*  API 端点定义                                                                */
/* -------------------------------------------------------------------------- */

export const API_ENDPOINTS = {
  // 高德地图
  amap: {
    placeSearch: 'https://restapi.amap.com/v3/place/text',
    placeAround: 'https://restapi.amap.com/v3/place/around',
    geocode: 'https://restapi.amap.com/v3/geocode/geo',
    regeocode: 'https://restapi.amap.com/v3/geocode/regeo',
    staticMap: 'https://restapi.amap.com/v3/staticmap',
  },
  // 腾讯位置服务
  tencent: {
    geocode: 'https://apis.map.qq.com/ws/geocoder/v1/',
    placeSearch: 'https://apis.map.qq.com/ws/place/v1/search',
    placeExplore: 'https://apis.map.qq.com/ws/place/v1/explore',
  },
  // 品牌 CRM
  crm: {
    customers: '/api/crm/customers',
    leads: '/api/crm/leads',
    services: '/api/crm/services',
    sync: '/api/crm/sync',
  },
  // 系统内部
  internal: {
    auth: '/api/auth',
    user: '/api/user',
    store: '/api/store',
    analytics: '/api/analytics',
    tasks: '/api/tasks',
    nfc: '/api/nfc',
  },
};

export default api;
