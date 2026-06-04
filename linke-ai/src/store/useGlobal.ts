/**
 * 全局状态:Zustand 单 store
 */
import { create } from 'zustand';
import type { Store, User, RadiusKm } from '@/lib/types';
import { api, setToken, getToken } from '@/lib/api';

export interface RealtimePosition {
  lng: number;
  lat: number;
  accuracy?: number;
  source?: 'browser' | 'manual' | 'ip';
  province?: string;
  city?: string;
  district?: string;
  address?: string;
  nearestPoi?: string;
  capturedAt?: number;
}

interface GlobalState {
  user: User | null;
  token: string | null;
  stores: Store[];
  currentStoreId: string | null;
  radius: RadiusKm;
  isAuthed: boolean;
  loading: boolean;
  // 实时定位(浏览器 GPS)
  realtimePosition: RealtimePosition | null;
  locating: boolean;
  // actions
  bootstrap: () => Promise<void>;
  login: (phone: string, code: string) => Promise<void>;
  logout: () => void;
  setCurrentStore: (id: string) => void;
  setRadius: (km: RadiusKm) => void;
  setRealtimePosition: (pos: RealtimePosition | null) => void;
  setLocating: (v: boolean) => void;
}

export const useGlobal = create<GlobalState>((set, get) => ({
  user: null,
  token: getToken(),
  stores: [],
  currentStoreId: null,
  radius: 3,
  isAuthed: false,
  loading: true,
  realtimePosition: null,
  locating: false,

  bootstrap: async () => {
    set({ loading: true });
    const token = getToken();
    if (!token) {
      set({ loading: false, isAuthed: false });
      return;
    }
    try {
      const me = await api.get<{ user: User }>('/auth/me');
      const stores = await api.get<{ stores: Store[] }>('/stores');
      const rememberedStore = localStorage.getItem('linke_store');
      const currentStoreId = rememberedStore && stores.stores.some((s) => s.id === rememberedStore)
        ? rememberedStore
        : stores.stores[0]?.id || null;
      set({
        user: me.user,
        isAuthed: true,
        stores: stores.stores,
        currentStoreId,
      });
    } catch {
      setToken(null);
      set({ user: null, token: null, isAuthed: false });
    } finally {
      set({ loading: false });
    }
  },

  login: async (phone, code) => {
    const r = await api.post<{ token: string; user: User }>('/auth/login', { phone, code });
    setToken(r.token);
    set({ token: r.token, user: r.user, isAuthed: true });
    await get().bootstrap();
  },

  logout: () => {
    setToken(null);
    set({ user: null, token: null, isAuthed: false, stores: [], currentStoreId: null });
  },

  setCurrentStore: (id) => {
    localStorage.setItem('linke_store', id);
    set({ currentStoreId: id });
  },

  setRadius: (km) => set({ radius: km }),
  setRealtimePosition: (pos) => set({ realtimePosition: pos }),
  setLocating: (v) => set({ locating: v }),
}));
