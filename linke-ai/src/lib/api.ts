/**
 * 通用 fetch 封装:统一 baseURL、Authorization、错误处理
 */
const BASE = '/api';

let tokenGetter: () => string | null = () => null;

export const setTokenGetter = (fn: () => string | null) => {
  tokenGetter = fn;
};

export const setToken = (token: string | null) => {
  if (token) localStorage.setItem('linke_token', token);
  else localStorage.removeItem('linke_token');
};

export const getToken = (): string | null => localStorage.getItem('linke_token');

setTokenGetter(getToken);

export class ApiError extends Error {
  status: number;
  data: unknown;
  constructor(message: string, status: number, data: unknown) {
    super(message);
    this.status = status;
    this.data = data;
  }
}

const request = async <T>(path: string, init: RequestInit = {}): Promise<T> => {
  const headers = new Headers(init.headers || {});
  headers.set('Content-Type', 'application/json');
  const token = tokenGetter();
  if (token) headers.set('Authorization', `Bearer ${token}`);
  const res = await fetch(`${BASE}${path}`, { ...init, headers });
  const text = await res.text();
  let json: unknown = null;
  try {
    json = text ? JSON.parse(text) : null;
  } catch {
    json = { raw: text };
  }
  if (!res.ok) {
    const msg = (json as { error?: string })?.error || `请求失败 (${res.status})`;
    throw new ApiError(msg, res.status, json);
  }
  return (json as { data?: T }).data ?? (json as T);
};

export const api = {
  get: <T>(p: string) => request<T>(p),
  post: <T>(p: string, body?: unknown) =>
    request<T>(p, { method: 'POST', body: body ? JSON.stringify(body) : undefined }),
  patch: <T>(p: string, body?: unknown) =>
    request<T>(p, { method: 'PATCH', body: body ? JSON.stringify(body) : undefined }),
  del: <T>(p: string) => request<T>(p, { method: 'DELETE' }),
};
