/**
 * 地图任务管理 Hook
 * - POI 采集任务创建/状态查询/导出/取消
 * - 地理围栏管理
 * - 离线地图下载
 */

import { useState, useEffect, useCallback, useRef } from 'react';
import { mapService, type POITaskStatus, type POITaskCreateRequest, type POITaskCreateResponse, type POITaskExportResponse, type GeofenceStatus, type GeofenceCreateRequest, type GeofenceCreateResponse, type OfflineCity, type ExportFormat } from '../services/mapService';

export interface UseMapTasksOptions {
  /** 自动轮询间隔（毫秒），默认 3000 */
  pollInterval?: number;
  /** 是否立即加载任务列表 */
  immediate?: boolean;
}

export interface MapTasksState {
  tasks: POITaskStatus[];
  geofences: GeofenceStatus[];
  offlineCities: OfflineCity[];
  loading: boolean;
  error: string | null;
}

export interface UseMapTasksReturn extends MapTasksState {
  // 任务管理
  createTask: (req: POITaskCreateRequest) => Promise<POITaskCreateResponse>;
  getTaskStatus: (taskId: string) => Promise<POITaskStatus | null>;
  exportTask: (taskId: string, format: ExportFormat) => Promise<POITaskExportResponse>;
  cancelTask: (taskId: string) => Promise<{ success: boolean; taskId: string }>;
  refreshTasks: () => Promise<void>;

  // 地理围栏
  createGeofence: (req: GeofenceCreateRequest) => Promise<GeofenceCreateResponse>;
  deleteGeofence: (geofenceId: string) => Promise<{ success: boolean }>;
  refreshGeofences: () => Promise<void>;

  // 离线地图
  downloadOfflineMap: (cityCode: string) => Promise<void>;
  refreshOfflineCities: () => Promise<void>;

  // 清理
  clearCache: () => void;
}

export function useMapTasks(options: UseMapTasksOptions = {}): UseMapTasksReturn {
  const [state, setState] = useState<MapTasksState>({
    tasks: [],
    geofences: [],
    offlineCities: [],
    loading: false,
    error: null,
  });

  const pollRef = useRef<ReturnType<typeof setInterval> | null>(null);
  const mounted = useRef(true);

  // 刷新任务列表
  const refreshTasks = useCallback(async () => {
    try {
      const tasks = await mapService.listTasks();
      if (!mounted.current) return;
      setState((s) => ({ ...s, tasks }));
    } catch (e: any) {
      console.error('[useMapTasks] refreshTasks error', e);
    }
  }, []);

  // 刷新围栏列表
  const refreshGeofences = useCallback(async () => {
    try {
      const geofences = await mapService.listGeofences();
      if (!mounted.current) return;
      setState((s) => ({ ...s, geofences }));
    } catch (e: any) {
      console.error('[useMapTasks] refreshGeofences error', e);
    }
  }, []);

  // 刷新离线城市
  const refreshOfflineCities = useCallback(async () => {
    try {
      const offlineCities = await mapService.getOfflineCities();
      if (!mounted.current) return;
      setState((s) => ({ ...s, offlineCities }));
    } catch (e: any) {
      console.error('[useMapTasks] refreshOfflineCities error', e);
    }
  }, []);

  // 创建任务
  const createTask = useCallback(async (req: POITaskCreateRequest) => {
    setState((s) => ({ ...s, loading: true, error: null }));
    try {
      const result = await mapService.createPOITask(req);
      if (!mounted.current) return result;
      if (result.success) {
        // 立即刷新任务列表
        await refreshTasks();
      }
      setState((s) => ({ ...s, loading: false }));
      return result;
    } catch (e: any) {
      if (!mounted.current) return { success: false, taskId: '', estimatedDurationMs: 0, estimatedCount: 0, assignedProviders: [], createdAt: 0, error: e.message };
      setState((s) => ({ ...s, loading: false, error: e.message }));
      return { success: false, taskId: '', estimatedDurationMs: 0, estimatedCount: 0, assignedProviders: [], createdAt: 0, error: e.message };
    }
  }, [refreshTasks]);

  // 查询任务状态
  const getTaskStatus = useCallback(async (taskId: string) => {
    return mapService.getTaskStatus(taskId);
  }, []);

  // 导出任务
  const exportTask = useCallback(async (taskId: string, format: ExportFormat) => {
    setState((s) => ({ ...s, loading: true }));
    try {
      const result = await mapService.exportTask(taskId, format);
      if (!mounted.current) return result;
      setState((s) => ({ ...s, loading: false }));
      return result;
    } catch (e: any) {
      if (!mounted.current) return { success: false, taskId, format, downloadUrl: '', fileSize: 0, recordCount: 0, exportedAt: Date.now(), supportsResume: false, error: e.message };
      setState((s) => ({ ...s, loading: false, error: e.message }));
      return { success: false, taskId, format, downloadUrl: '', fileSize: 0, recordCount: 0, exportedAt: Date.now(), supportsResume: false, error: e.message };
    }
  }, []);

  // 取消任务
  const cancelTask = useCallback(async (taskId: string) => {
    const result = await mapService.cancelTask(taskId);
    if (result.success) {
      await refreshTasks();
    }
    return result;
  }, [refreshTasks]);

  // 创建围栏
  const createGeofence = useCallback(async (req: GeofenceCreateRequest) => {
    setState((s) => ({ ...s, loading: true }));
    try {
      const result = await mapService.createGeofence(req);
      if (!mounted.current) return result;
      if (result.success) {
        await refreshGeofences();
      }
      setState((s) => ({ ...s, loading: false }));
      return result;
    } catch (e: any) {
      if (!mounted.current) return { success: false, geofenceId: '', name: '', type: req.type, currentPOICount: 0, monitoring: false, createdAt: 0, error: e.message };
      setState((s) => ({ ...s, loading: false, error: e.message }));
      return { success: false, geofenceId: '', name: '', type: req.type, currentPOICount: 0, monitoring: false, createdAt: 0, error: e.message };
    }
  }, [refreshGeofences]);

  // 删除围栏
  const deleteGeofence = useCallback(async (geofenceId: string) => {
    const result = await mapService.deleteGeofence(geofenceId);
    if (result.success) {
      await refreshGeofences();
    }
    return result;
  }, [refreshGeofences]);

  // 下载离线地图
  const downloadOfflineMap = useCallback(async (cityCode: string) => {
    await mapService.downloadOfflineMap({ cityCode });
    await refreshOfflineCities();
  }, [refreshOfflineCities]);

  // 清理缓存
  const clearCache = useCallback(() => {
    mapService.clearCache();
    setState({ tasks: [], geofences: [], offlineCities: [], loading: false, error: null });
  }, []);

  // 初始化 + 自动轮询
  useEffect(() => {
    mounted.current = true;

    if (options.immediate !== false) {
      setState((s) => ({ ...s, loading: true }));
      Promise.all([refreshTasks(), refreshGeofences(), refreshOfflineCities()]).then(() => {
        setState((s) => ({ ...s, loading: false }));
      });
    }

    // 自动轮询（更新 running 任务状态）
    const interval = options.pollInterval ?? 3000;
    pollRef.current = setInterval(() => {
      const runningTasks = state.tasks.filter((t) => t.status === 'running' || t.status === 'pending');
      if (runningTasks.length > 0) {
        refreshTasks();
      }
    }, interval);

    return () => {
      mounted.current = false;
      if (pollRef.current) clearInterval(pollRef.current);
    };
  }, []);

  return {
    ...state,
    createTask,
    getTaskStatus,
    exportTask,
    cancelTask,
    refreshTasks,
    createGeofence,
    deleteGeofence,
    refreshGeofences,
    downloadOfflineMap,
    refreshOfflineCities,
    clearCache,
  };
}