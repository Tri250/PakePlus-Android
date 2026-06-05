import { useEffect, useState, useRef, useCallback } from 'react';
import { dataService, subscribe, getRealtimeStatus, type DataResult, type RealtimeStatus } from '../services/dataService';
import { hapticClick } from './useAndroidBack';

/* -------------------------------------------------------------------------- */
/*  useData - 一次性获取数据，支持 loading/error                                 */
/* -------------------------------------------------------------------------- */

interface UseDataOptions<T> {
  fetcher: () => Promise<DataResult<T>>;
  deps?: any[];
  enabled?: boolean;
  onSuccess?: (data: T) => void;
  onError?: (err: Error) => void;
}

interface UseDataState<T> {
  data: T | null;
  loading: boolean;
  error: Error | null;
  source: DataResult<T>['source'] | null;
  isLive: boolean;
  fetchedAt: number;
  staleIn: number;
}

export function useData<T>({ fetcher, deps = [], enabled = true, onSuccess, onError }: UseDataOptions<T>) {
  const [state, setState] = useState<UseDataState<T>>({
    data: null,
    loading: true,
    error: null,
    source: null,
    isLive: false,
    fetchedAt: 0,
    staleIn: 0,
  });
  const mounted = useRef(true);
  const lastFetcher = useRef(fetcher);
  lastFetcher.current = fetcher;

  const load = useCallback(async () => {
    if (!enabled) return;
    setState((s) => ({ ...s, loading: true, error: null }));
    try {
      const res = await lastFetcher.current();
      if (!mounted.current) return;
      setState({
        data: res.data,
        loading: false,
        error: null,
        source: res.source,
        isLive: res.isLive,
        fetchedAt: res.fetchedAt,
        staleIn: res.staleIn,
      });
      onSuccess?.(res.data);
    } catch (e: any) {
      if (!mounted.current) return;
      setState((s) => ({
        ...s,
        loading: false,
        error: e,
      }));
      onError?.(e);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, deps);

  useEffect(() => {
    mounted.current = true;
    load();
    return () => {
      mounted.current = false;
    };
  }, [load]);

  const refresh = useCallback(async () => {
    await load();
  }, [load]);

  return { ...state, refresh, setState };
}

/* -------------------------------------------------------------------------- */
/*  useRealtimeData - 订阅实时数据流，自动刷新                                    */
/* -------------------------------------------------------------------------- */

interface UseRealtimeDataOptions<T> {
  channel: string;
  fetcher: () => Promise<DataResult<T>>;
  interval?: number;          // 轮询间隔 (ms)
  enabled?: boolean;
}

export function useRealtimeData<T>({
  channel,
  fetcher,
  interval = 30_000,
  enabled = true,
}: UseRealtimeDataOptions<T>) {
  const [state, setState] = useState<UseDataState<T>>({
    data: null,
    loading: true,
    error: null,
    source: null,
    isLive: false,
    fetchedAt: 0,
    staleIn: 0,
  });
  const [tick, setTick] = useState(0);
  const mounted = useRef(true);

  // 订阅推送
  useEffect(() => {
    if (!enabled) return;
    const unsub = subscribe<T>(channel, (data) => {
      if (mounted.current) {
        setState((s) => ({
          ...s,
          data,
          isLive: true,
          fetchedAt: Date.now(),
          source: 'api',
        }));
        hapticClick();
      }
    });
    return unsub;
  }, [channel, enabled]);

  // 轮询刷新
  useEffect(() => {
    if (!enabled) return;
    mounted.current = true;
    let timer: ReturnType<typeof setInterval> | null = null;

    const load = async () => {
      try {
        const res = await fetcher();
        if (!mounted.current) return;
        setState({
          data: res.data,
          loading: false,
          error: null,
          source: res.source,
          isLive: res.isLive,
          fetchedAt: res.fetchedAt,
          staleIn: res.staleIn,
        });
      } catch (e: any) {
        if (!mounted.current) return;
        setState((s) => ({ ...s, loading: false, error: e }));
      }
    };

    load();
    timer = setInterval(() => {
      setTick((t) => t + 1);
      load();
    }, interval);

    return () => {
      mounted.current = false;
      if (timer) clearInterval(timer);
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [channel, interval, tick, enabled]);

  const refresh = useCallback(async () => {
    try {
      const res = await fetcher();
      setState({
        data: res.data,
        loading: false,
        error: null,
        source: res.source,
        isLive: res.isLive,
        fetchedAt: res.fetchedAt,
        staleIn: res.staleIn,
      });
    } catch (e: any) {
      setState((s) => ({ ...s, loading: false, error: e }));
    }
  }, [fetcher]);

  return { ...state, refresh, setState };
}

/* -------------------------------------------------------------------------- */
/*  useRealtimeStatus - 实时状态                                                */
/* -------------------------------------------------------------------------- */

export function useRealtimeStatus(): RealtimeStatus {
  const [status, setStatus] = useState<RealtimeStatus>(getRealtimeStatus());
  useEffect(() => {
    const t = setInterval(() => setStatus(getRealtimeStatus()), 1000);
    return () => clearInterval(t);
  }, []);
  return status;
}

/* -------------------------------------------------------------------------- */
/*  便捷 hooks - 直接绑定到 dataService                                          */
/* -------------------------------------------------------------------------- */

export const useCustomers = (opts?: Parameters<typeof dataService.getCustomers>[0]) =>
  useRealtimeData({
    channel: 'customers',
    fetcher: () => dataService.getCustomers(opts),
    interval: 60_000,
  });

export const useTasks = () =>
  useRealtimeData({
    channel: 'tasks',
    fetcher: () => dataService.getTasks(),
    interval: 30_000,
  });

export const useLeads = () =>
  useRealtimeData({
    channel: 'leads',
    fetcher: () => dataService.getLeads(),
    interval: 20_000,
  });

export const useMetrics = () =>
  useRealtimeData({
    channel: 'metrics',
    fetcher: () => dataService.getMetrics(),
    interval: 15_000,
  });

export const useFeed = () =>
  useRealtimeData({
    channel: 'feed',
    fetcher: () => dataService.getFeed(),
    interval: 10_000,
  });

export const useCompetitorEvents = () =>
  useRealtimeData({
    channel: 'competitor',
    fetcher: () => dataService.getCompetitorEvents(),
    interval: 45_000,
  });

export const useTrend = () =>
  useRealtimeData({
    channel: 'trend',
    fetcher: () => dataService.getTrend(),
    interval: 60_000,
  });

export const useCompetitors = () =>
  useRealtimeData({
    channel: 'competitors',
    fetcher: () => dataService.getCompetitors(),
    interval: 60_000,
  });

export const useAchievements = () =>
  useRealtimeData({
    channel: 'achievements',
    fetcher: () => dataService.getAchievements(),
    interval: 120_000,
  });

export const useAlerts = () =>
  useRealtimeData({
    channel: 'alerts',
    fetcher: () => dataService.getAlerts(),
    interval: 30_000,
  });

export const useTeamMembers = () =>
  useRealtimeData({
    channel: 'team',
    fetcher: () => dataService.getTeamMembers(),
    interval: 60_000,
  });

export const useNotifications = () =>
  useRealtimeData({
    channel: 'notifications',
    fetcher: () => dataService.getNotifications(),
    interval: 20_000,
  });
