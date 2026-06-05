/**
 * 权限管理系统 - RBAC + 数据权限
 * 支持：角色管理、权限校验、路由守卫、操作审计
 */
import { create } from 'zustand';
import { persist } from 'zustand/middleware';
import { safeLocalStorageGet, safeLocalStorageSet, safeLocalStorageRemove } from './env';

/* -------------------------------------------------------------------------- */
/*  类型定义                                                                    */
/* -------------------------------------------------------------------------- */

export type Role = 'admin' | 'manager' | 'staff' | 'viewer';

export type Permission =
  // 客户管理
  | 'customer:read'
  | 'customer:write'
  | 'customer:delete'
  | 'customer:export'
  // 线索管理
  | 'lead:read'
  | 'lead:write'
  | 'lead:assign'
  | 'lead:transfer'
  // LBS 雷达
  | 'lbs:scan'
  | 'lbs:heatmap'
  | 'lbs:route'
  // GEO 优化
  | 'geo:read'
  | 'geo:write'
  | 'geo:publish'
  // 营销作战
  | 'marketing:create'
  | 'marketing:publish'
  | 'marketing:analytics'
  // 数据分析
  | 'analytics:read'
  | 'analytics:export'
  // 系统设置
  | 'settings:read'
  | 'settings:write'
  // 团队管理
  | 'team:manage'
  // 数据中台
  | 'platform:read'
  | 'platform:sync'
  | 'platform:audit';

export interface User {
  id: string;
  name: string;
  email: string;
  phone: string;
  role: Role;
  storeId: string;
  storeName: string;
  avatar?: string;
  permissions: Permission[];
  dataScope: 'all' | 'store' | 'self';
  createdAt: string;
  lastLoginAt: string;
}

export interface AuditLog {
  id: string;
  userId: string;
  userName: string;
  action: string;
  resource: string;
  resourceId?: string;
  details: any;
  ip: string;
  userAgent: string;
  timestamp: string;
}

/* -------------------------------------------------------------------------- */
/*  角色权限映射                                                                */
/* -------------------------------------------------------------------------- */

const ROLE_PERMISSIONS: Record<Role, Permission[]> = {
  admin: [
    'customer:read', 'customer:write', 'customer:delete', 'customer:export',
    'lead:read', 'lead:write', 'lead:assign', 'lead:transfer',
    'lbs:scan', 'lbs:heatmap', 'lbs:route',
    'geo:read', 'geo:write', 'geo:publish',
    'marketing:create', 'marketing:publish', 'marketing:analytics',
    'analytics:read', 'analytics:export',
    'settings:read', 'settings:write',
    'team:manage',
    'platform:read', 'platform:sync', 'platform:audit',
  ],
  manager: [
    'customer:read', 'customer:write', 'customer:export',
    'lead:read', 'lead:write', 'lead:assign',
    'lbs:scan', 'lbs:heatmap', 'lbs:route',
    'geo:read', 'geo:write',
    'marketing:create', 'marketing:publish', 'marketing:analytics',
    'analytics:read', 'analytics:export',
    'settings:read',
    'platform:read',
  ],
  staff: [
    'customer:read', 'customer:write',
    'lead:read', 'lead:write',
    'lbs:scan', 'lbs:route',
    'geo:read',
    'marketing:create',
    'analytics:read',
  ],
  viewer: [
    'customer:read',
    'lead:read',
    'lbs:scan',
    'geo:read',
    'analytics:read',
  ],
};

/* -------------------------------------------------------------------------- */
/*  Zustand Store                                                               */
/* -------------------------------------------------------------------------- */

interface AuthState {
  user: User | null;
  isAuthenticated: boolean;
  isLoading: boolean;
  token: string | null;

  // Actions
  login: (email: string, password: string) => Promise<boolean>;
  logout: () => void;
  checkAuth: () => Promise<void>;
  hasPermission: (permission: Permission) => boolean;
  hasAnyPermission: (permissions: Permission[]) => boolean;
  hasAllPermissions: (permissions: Permission[]) => boolean;
  canAccessStore: (storeId: string) => boolean;
  logAction: (action: string, resource: string, resourceId?: string, details?: any) => void;
}

export const useAuthStore = create<AuthState>()(
  persist(
    (set, get) => ({
      user: null,
      isAuthenticated: false,
      isLoading: false,
      token: null,

      login: async (email: string, password: string) => {
        set({ isLoading: true });

        // 模拟登录（实际应调用 API）
        await new Promise((r) => setTimeout(r, 800));

        // Mock 用户数据
        const mockUser: User = {
          id: 'U001',
          name: '李店长',
          email: email,
          phone: '138****8888',
          role: 'manager',
          storeId: 'ST001',
          storeName: '国贸旗舰店',
          permissions: ROLE_PERMISSIONS.manager,
          dataScope: 'store',
          createdAt: '2025-01-15',
          lastLoginAt: new Date().toISOString(),
        };

        set({
          user: mockUser,
          isAuthenticated: true,
          isLoading: false,
          token: 'mock_token_' + Date.now(),
        });

        // 存储到 localStorage
        safeLocalStorageSet('auth_token', 'mock_token_' + Date.now());

        return true;
      },

      logout: () => {
        safeLocalStorageRemove('auth_token');
        set({
          user: null,
          isAuthenticated: false,
          token: null,
        });
      },

      checkAuth: async () => {
        const token = safeLocalStorageGet('auth_token');
        if (!token) {
          set({ isAuthenticated: false, user: null });
          return;
        }

        // 模拟验证 token
        set({ isLoading: true });
        await new Promise((r) => setTimeout(r, 300));

        const mockUser: User = {
          id: 'U001',
          name: '李店长',
          email: 'liming@handbiz.com',
          phone: '138****8888',
          role: 'manager',
          storeId: 'ST001',
          storeName: '国贸旗舰店',
          permissions: ROLE_PERMISSIONS.manager,
          dataScope: 'store',
          createdAt: '2025-01-15',
          lastLoginAt: new Date().toISOString(),
        };

        set({
          user: mockUser,
          isAuthenticated: true,
          isLoading: false,
          token,
        });
      },

      hasPermission: (permission: Permission) => {
        const { user } = get();
        if (!user) return false;
        return user.permissions.includes(permission);
      },

      hasAnyPermission: (permissions: Permission[]) => {
        const { user } = get();
        if (!user) return false;
        return permissions.some((p) => user.permissions.includes(p));
      },

      hasAllPermissions: (permissions: Permission[]) => {
        const { user } = get();
        if (!user) return false;
        return permissions.every((p) => user.permissions.includes(p));
      },

      canAccessStore: (storeId: string) => {
        const { user } = get();
        if (!user) return false;
        if (user.dataScope === 'all') return true;
        if (user.dataScope === 'store') return user.storeId === storeId;
        return false;
      },

      logAction: (action: string, resource: string, resourceId?: string, details?: any) => {
        const { user } = get();
        if (!user) return;

        const log: AuditLog = {
          id: `LOG-${Date.now()}`,
          userId: user.id,
          userName: user.name,
          action,
          resource,
          resourceId,
          details,
          ip: '127.0.0.1',
          userAgent: typeof navigator !== 'undefined' ? navigator.userAgent : 'Node.js',
          timestamp: new Date().toISOString(),
        };

        // 存储到本地（实际应发送到服务器）
        const logsStr = safeLocalStorageGet('audit_logs') || '[]';
        const logs = JSON.parse(logsStr);
        logs.push(log);
        // 保留最近 1000 条
        if (logs.length > 1000) logs.shift();
        safeLocalStorageSet('audit_logs', JSON.stringify(logs));
      },
    }),
    {
      name: 'auth-storage',
      partialize: (state) => ({
        user: state.user,
        isAuthenticated: state.isAuthenticated,
        token: state.token,
      }),
    }
  )
);

/* -------------------------------------------------------------------------- */
/*  权限守卫组件                                                                */
/* -------------------------------------------------------------------------- */

export function requirePermission(permission: Permission): boolean {
  const { hasPermission } = useAuthStore.getState();
  return hasPermission(permission);
}

export function requireAnyPermission(permissions: Permission[]): boolean {
  const { hasAnyPermission } = useAuthStore.getState();
  return hasAnyPermission(permissions);
}

/* -------------------------------------------------------------------------- */
/*  工具函数                                                                    */
/* -------------------------------------------------------------------------- */

export function getRoleName(role: Role): string {
  const names: Record<Role, string> = {
    admin: '系统管理员',
    manager: '店长',
    staff: '店员',
    viewer: '只读用户',
  };
  return names[role];
}

export function getRoleColor(role: Role): string {
  const colors: Record<Role, string> = {
    admin: 'bg-red-100 text-red-700',
    manager: 'bg-blue-100 text-blue-700',
    staff: 'bg-emerald-100 text-emerald-700',
    viewer: 'bg-gray-100 text-gray-700',
  };
  return colors[role];
}

export function getAuditLogs(limit = 100): AuditLog[] {
  const logsStr = safeLocalStorageGet('audit_logs') || '[]';
  const logs = JSON.parse(logsStr);
  return logs.slice(-limit).reverse();
}

export function clearAuditLogs(): void {
  safeLocalStorageRemove('audit_logs');
}
