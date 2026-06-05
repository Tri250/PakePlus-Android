/**
 * POI 实时数据 Hook
 * - 支持自定义中心点
 * - 支持自定义距离环
 * - 支持关键字/类别过滤
 * - 30s 自动轮询，可手动 force 刷新
 */

import { useEffect, useRef, useState, useCallback } from 'react';
import { dataService } from '../services/dataService';
import type { POICollectResult, CustomerLead, DistanceRing } from '../services/poiCollector';

export interface UsePOIOptions {
  center: { lat: number; lng: number };
  rings?: number[];
  keyword?: string;
  /** 自动轮询间隔（毫秒），默认 30s */
  interval?: number;
  /** 是否立即拉取 */
  immediate?: boolean;
}

export interface POIState {
  data: POICollectResult | null;
  leads: CustomerLead[];
  rings: DistanceRing[];
  loading: boolean;
  error: string | null;
  fetchedAt: number;
  source: 'api' | 'crawler' | 'cache' | 'mock' | 'synthetic' | null;
}

export interface UsePOIReturn extends POIState {
  refresh: (force?: boolean) => Promise<void>;
  setRings: (rings: number[]) => void;
  setKeyword: (kw: string) => void;
  // 筛选
  highValueLeads: CustomerLead[];
  byRing: Record<number, CustomerLead[]>;
}

const DEFAULT_RINGS = [200, 500, 1000, 3000, 5000];

export function usePOI(opts: UsePOIOptions): UsePOIReturn {
  const [state, setState] = useState<POIState>({
    data: null,
    leads: [],
    rings: [],
    loading: false,
    error: null,
    fetchedAt: 0,
    source: null,
  });

  const [rings, setRings] = useState<number[]>(opts.rings || DEFAULT_RINGS);
  const [keyword, setKeyword] = useState<string>(opts.keyword || '');

  const pollRef = useRef<ReturnType<typeof setInterval> | null>(null);
  const mounted = useRef(true);

  const refresh = useCallback(
    async (force = false) => {
      setState((s) => ({ ...s, loading: true, error: null }));
      try {
        const result = await dataService.getLeadsFromPOI({
          center: opts.center,
          rings,
          keyword,
          force,
        });

        if (!mounted.current) return;

        const source: POIState['source'] = result.stats.byProvider.amap
          ? 'api'
          : result.stats.byProvider.baidu || result.stats.byProvider.tencent
          ? 'crawler'
          : 'synthetic';

        setState({
          data: result,
          leads: result.leads,
          rings: result.rings,
          loading: false,
          error: null,
          fetchedAt: result.fetchedAt,
          source,
        });
      } catch (e: any) {
        if (!mounted.current) return;
        setState((s) => ({
          ...s,
          loading: false,
          error: e.message || '采集失败',
        }));
      }
    },
    [opts.center.lat, opts.center.lng, rings, keyword],
  );

  useEffect(() => {
    mounted.current = true;
    if (opts.immediate !== false) refresh(false);
    const interval = opts.interval ?? 30_000;
    pollRef.current = setInterval(() => refresh(false), interval);
    return () => {
      mounted.current = false;
      if (pollRef.current) clearInterval(pollRef.current);
    };
  }, [opts.center.lat, opts.center.lng, rings.join(','), keyword, opts.interval]);

  // 衍生数据
  const highValueLeads = state.leads.filter((l) => l.intentScore >= 75 && l.kind === 'poi');
  const byRing = state.leads.reduce<Record<number, CustomerLead[]>>((acc, l) => {
    (acc[l.ringMeters] = acc[l.ringMeters] || []).push(l);
    return acc;
  }, {});

  return {
    ...state,
    refresh,
    setRings,
    setKeyword,
    highValueLeads,
    byRing,
  };
}
