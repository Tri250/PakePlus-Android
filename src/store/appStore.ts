import { create } from 'zustand';
import type { Role } from '../data/mockData';

export type TabKey = 'home' | 'customers' | 'tasks' | 'store' | 'data';

interface ToastMsg {
  id: number;
  text: string;
  icon?: string;
}

interface AppState {
  // 当前 Tab
  activeTab: TabKey;
  setActiveTab: (t: TabKey) => void;

  // 角色
  role: Role;
  setRole: (r: Role) => void;

  // 户外模式
  outdoorMode: boolean;
  toggleOutdoor: () => void;

  // 离线模式
  offline: boolean;
  toggleOffline: () => void;

  // 通知 drawer
  showNotifications: boolean;
  setShowNotifications: (v: boolean) => void;

  // 任务详情
  selectedTaskId: string | null;
  setSelectedTask: (id: string | null) => void;

  // 雷达全屏视图
  showRadar: boolean;
  setShowRadar: (v: boolean) => void;

  // 客户详情
  selectedCustomerId: string | null;
  setSelectedCustomer: (id: string | null) => void;

  // SOS 弹窗
  showSOS: boolean;
  setShowSOS: (v: boolean) => void;

  // Toast
  toasts: ToastMsg[];
  showToast: (text: string, icon?: string) => void;
  dismissToast: (id: number) => void;

  // 任务打卡
  taskCheckIns: Record<string, boolean>;
  toggleCheckIn: (taskId: string) => void;

  // 客户标记
  favoritedCustomers: string[];
  toggleFavorite: (id: string) => void;

  // 添加客户 sheet
  showAddCustomer: boolean;
  setShowAddCustomer: (v: boolean) => void;

  // 筛选
  customerFilter: 'all' | 'S' | 'A' | 'B' | 'C' | 'D';
  setCustomerFilter: (f: AppState['customerFilter']) => void;
  customerSearch: string;
  setCustomerSearch: (s: string) => void;

  // 角色切换 sheet
  showRoleSwitcher: boolean;
  setShowRoleSwitcher: (v: boolean) => void;

  // 设置 sheet
  showSettings: boolean;
  setShowSettings: (v: boolean) => void;

  // 全部功能 抽屉
  showAllFeatures: boolean;
  setShowAllFeatures: (v: boolean) => void;

  // 客户动态/竞品动态 子tab
  homeFeedTab: 'customer' | 'competitor';
  setHomeFeedTab: (t: 'customer' | 'competitor') => void;

  // 子功能页
  activeFeature: string | null;
  setActiveFeature: (id: string | null) => void;
}

let toastId = 0;

export const useAppStore = create<AppState>((set, get) => ({
  activeTab: 'home',
  setActiveTab: (t) => set({ activeTab: t }),

  role: 'rep',
  setRole: (r) => set({ role: r, showRoleSwitcher: false }),

  outdoorMode: false,
  toggleOutdoor: () => set((s) => ({ outdoorMode: !s.outdoorMode })),

  offline: false,
  toggleOffline: () => {
    const next = !get().offline;
    set({ offline: next });
    if (next) {
      get().showToast('已切换到离线模式，本地缓存可用', '📡');
    } else {
      get().showToast('网络已恢复，正在同步数据', '🟢');
    }
  },

  showNotifications: false,
  setShowNotifications: (v) => set({ showNotifications: v }),

  selectedTaskId: null,
  setSelectedTask: (id) => set({ selectedTaskId: id }),

  showRadar: false,
  setShowRadar: (v) => set({ showRadar: v }),

  selectedCustomerId: null,
  setSelectedCustomer: (id) => set({ selectedCustomerId: id }),

  showSOS: false,
  setShowSOS: (v) => set({ showSOS: v }),

  toasts: [],
  showToast: (text, icon) => {
    const id = ++toastId;
    set((s) => ({ toasts: [...s.toasts, { id, text, icon }] }));
    setTimeout(() => {
      set((s) => ({ toasts: s.toasts.filter((t) => t.id !== id) }));
    }, 2500);
  },
  dismissToast: (id) => set((s) => ({ toasts: s.toasts.filter((t) => t.id !== id) })),

  taskCheckIns: {},
  toggleCheckIn: (taskId) =>
    set((s) => {
      const next = { ...s.taskCheckIns, [taskId]: !s.taskCheckIns[taskId] };
      const isNowChecked = next[taskId];
      if (isNowChecked) {
        setTimeout(() => get().showToast('打卡成功，已记录当前位置和时间', '📍'), 50);
      }
      return { taskCheckIns: next };
    }),

  favoritedCustomers: ['c1'],
  toggleFavorite: (id) =>
    set((s) => {
      const has = s.favoritedCustomers.includes(id);
      if (has) {
        get().showToast('已取消收藏', '⭐');
        return { favoritedCustomers: s.favoritedCustomers.filter((x) => x !== id) };
      }
      get().showToast('已收藏该客户', '⭐');
      return { favoritedCustomers: [...s.favoritedCustomers, id] };
    }),

  showAddCustomer: false,
  setShowAddCustomer: (v) => set({ showAddCustomer: v }),

  customerFilter: 'all',
  setCustomerFilter: (f) => set({ customerFilter: f }),
  customerSearch: '',
  setCustomerSearch: (s) => set({ customerSearch: s }),

  showRoleSwitcher: false,
  setShowRoleSwitcher: (v) => set({ showRoleSwitcher: v }),

  showSettings: false,
  setShowSettings: (v) => set({ showSettings: v }),

  showAllFeatures: false,
  setShowAllFeatures: (v) => set({ showAllFeatures: v }),

  homeFeedTab: 'customer',
  setHomeFeedTab: (t) => set({ homeFeedTab: t }),

  activeFeature: null,
  setActiveFeature: (id) => set({ activeFeature: id }),
}));
