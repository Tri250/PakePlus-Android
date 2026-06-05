/**
 * 地图任务管理面板
 * - POI 采集任务创建/进度/导出/取消
 * - 地理围栏管理
 * - 离线地图下载
 */

import { useState } from 'react';
import {
  X, Play, Pause, Download, Trash2, RefreshCw, CheckCircle2,
  Clock, AlertCircle, Loader2, MapPin, Circle, Pentagon,
  FileJson, FileSpreadsheet, Map, HardDrive, Plus,
} from 'lucide-react';
import { useMapTasks } from '../hooks/useMapTasks';
import { useAppStore } from '../store/appStore';
import type { POITaskStatus, GeofenceStatus, OfflineCity, ExportFormat, TaskStatus, GeofenceType } from '../services/mapService';
import type { POICategory } from '../services/poiCollector';

interface Props {
  onClose: () => void;
}

const STATUS_CONFIG: Record<TaskStatus, { icon: React.ComponentType<any>; color: string; label: string }> = {
  pending: { icon: Clock, color: '#f59e0b', label: '等待中' },
  running: { icon: Loader2, color: '#3b82f6', label: '运行中' },
  completed: { icon: CheckCircle2, color: '#10b981', label: '已完成' },
  failed: { icon: AlertCircle, color: '#ef4444', label: '失败' },
  cancelled: { icon: Pause, color: '#94a3b8', label: '已取消' },
};

const CATEGORY_OPTIONS: Array<{ value: POICategory | 'all'; label: string }> = [
  { value: 'all', label: '全部类别' },
  { value: 'office', label: '写字楼' },
  { value: 'residential', label: '住宅' },
  { value: 'school', label: '学校' },
  { value: 'mall', label: '商场' },
  { value: 'restaurant', label: '餐饮' },
  { value: 'hospital', label: '医院' },
  { value: 'hotel', label: '酒店' },
  { value: 'transport', label: '交通' },
  { value: 'operator', label: '运营商' },
  { value: 'digital_shop', label: '数码店' },
];

const EXPORT_FORMATS: Array<{ value: ExportFormat; label: string; icon: React.ComponentType<any> }> = [
  { value: 'json', label: 'JSON', icon: FileJson },
  { value: 'csv', label: 'CSV', icon: FileSpreadsheet },
  { value: 'excel', label: 'Excel', icon: FileSpreadsheet },
  { value: 'geojson', label: 'GeoJSON', icon: Map },
];

const DEFAULT_CENTER = { lat: 22.5400, lng: 113.9436 };

export default function MapTaskPanel({ onClose }: Props) {
  const showToast = useAppStore((s) => s.showToast);
  const mapTasks = useMapTasks({ pollInterval: 2000 });
  const [tab, setTab] = useState<'tasks' | 'geofence' | 'offline'>('tasks');
  const [showCreateTask, setShowCreateTask] = useState(false);
  const [showCreateGeofence, setShowCreateGeofence] = useState(false);

  return (
    <div className="absolute inset-0 z-50 bg-white flex flex-col animate-slideInRight">
      {/* Header */}
      <div className="flex items-center justify-between px-5 pt-3 pb-2 border-b" style={{ borderColor: 'var(--border)' }}>
        <div className="flex items-center gap-2">
          <HardDrive className="w-5 h-5" style={{ color: 'var(--primary)' }} />
          <h2 className="text-lg font-bold" style={{ color: 'var(--text-primary)' }}>地图任务管理</h2>
        </div>
        <button
          onClick={onClose}
          className="w-9 h-9 rounded-full flex items-center justify-center"
          style={{ background: 'var(--surface-2)' }}
          aria-label="关闭"
        >
          <X className="w-4 h-4" />
        </button>
      </div>

      {/* Tabs */}
      <div className="flex gap-1 px-5 pt-3">
        {[
          { id: 'tasks', label: '采集任务', icon: Play, count: mapTasks.tasks.length },
          { id: 'geofence', label: '地理围栏', icon: Circle, count: mapTasks.geofences.length },
          { id: 'offline', label: '离线地图', icon: HardDrive, count: mapTasks.offlineCities.filter((c) => c.downloadStatus === 'completed').length },
        ].map((t) => {
          const Icon = t.icon;
          const active = tab === t.id;
          return (
            <button
              key={t.id}
              onClick={() => setTab(t.id as any)}
              className="flex-1 h-9 rounded-xl flex items-center justify-center gap-1.5 text-xs font-semibold transition-colors"
              style={{
                background: active ? 'var(--primary)' : 'var(--surface-2)',
                color: active ? '#fff' : 'var(--text-secondary)',
              }}
            >
              <Icon className="w-3.5 h-3.5" />
              {t.label}
              <span className="text-[10px] opacity-80">{t.count}</span>
            </button>
          );
        })}
      </div>

      {/* Content */}
      <div className="flex-1 overflow-y-auto scroll-area">
        {tab === 'tasks' && (
          <div className="px-5 py-3 space-y-3">
            {/* 创建任务按钮 */}
            <button
              onClick={() => setShowCreateTask(true)}
              className="w-full card p-3 flex items-center justify-center gap-2 text-sm font-semibold"
              style={{ background: 'var(--primary)', color: '#fff' }}
            >
              <Plus className="w-4 h-4" />
              创建 POI 采集任务
            </button>

            {/* 任务列表 */}
            {mapTasks.tasks.length === 0 ? (
              <div className="text-center py-8">
                <p className="text-xs" style={{ color: 'var(--text-muted)' }}>暂无采集任务</p>
              </div>
            ) : (
              <div className="space-y-2.5">
                {mapTasks.tasks.map((task) => (
                  <TaskCard
                    key={task.taskId}
                    task={task}
                    onExport={(format) => {
                      mapTasks.exportTask(task.taskId, format).then((r) => {
                        if (r.success) {
                          showToast(`导出成功：${r.recordCount} 条记录`, '✓');
                        } else {
                          showToast(r.error || '导出失败', '!');
                        }
                      });
                    }}
                    onCancel={() => {
                      mapTasks.cancelTask(task.taskId).then((r) => {
                        if (r.success) {
                          showToast('任务已取消', '✓');
                        }
                      });
                    }}
                    onRefresh={() => mapTasks.refreshTasks()}
                  />
                ))}
              </div>
            )}
          </div>
        )}

        {tab === 'geofence' && (
          <div className="px-5 py-3 space-y-3">
            <button
              onClick={() => setShowCreateGeofence(true)}
              className="w-full card p-3 flex items-center justify-center gap-2 text-sm font-semibold"
              style={{ background: 'var(--primary)', color: '#fff' }}
            >
              <Plus className="w-4 h-4" />
              创建地理围栏
            </button>

            {mapTasks.geofences.length === 0 ? (
              <div className="text-center py-8">
                <p className="text-xs" style={{ color: 'var(--text-muted)' }}>暂无地理围栏</p>
              </div>
            ) : (
              <div className="space-y-2.5">
                {mapTasks.geofences.map((gf) => (
                  <GeofenceCard
                    key={gf.geofenceId}
                    geofence={gf}
                    onDelete={() => {
                      mapTasks.deleteGeofence(gf.geofenceId).then((r) => {
                        if (r.success) showToast('围栏已删除', '✓');
                      });
                    }}
                  />
                ))}
              </div>
            )}
          </div>
        )}

        {tab === 'offline' && (
          <div className="px-5 py-3 space-y-3">
            <div className="card p-3">
              <div className="flex items-center justify-between mb-2">
                <h3 className="text-sm font-semibold" style={{ color: 'var(--text-primary)' }}>离线地图包</h3>
                <span className="text-xs" style={{ color: 'var(--text-muted)' }}>
                  已下载 {mapTasks.offlineCities.filter((c) => c.downloadStatus === 'completed').length} / {mapTasks.offlineCities.length}
                </span>
              </div>
            </div>

            <div className="space-y-2">
              {mapTasks.offlineCities.map((city) => (
                <OfflineCityCard
                  key={city.cityCode}
                  city={city}
                  onDownload={() => {
                    mapTasks.downloadOfflineMap(city.cityCode).then(() => {
                      showToast(`开始下载 ${city.cityName}`, '⬇️');
                    });
                  }}
                />
              ))}
            </div>
          </div>
        )}
      </div>

      {/* 创建任务弹窗 */}
      {showCreateTask && (
        <CreateTaskModal
          onClose={() => setShowCreateTask(false)}
          onCreate={(req) => {
            mapTasks.createTask(req).then((r) => {
              if (r.success) {
                showToast(`任务创建成功：预计采集 ${r.estimatedCount} 条`, '✓');
                setShowCreateTask(false);
              } else {
                showToast(r.error || '创建失败', '!');
              }
            });
          }}
        />
      )}

      {/* 创建围栏弹窗 */}
      {showCreateGeofence && (
        <CreateGeofenceModal
          onClose={() => setShowCreateGeofence(false)}
          onCreate={(req) => {
            mapTasks.createGeofence(req).then((r) => {
              if (r.success) {
                showToast(`围栏创建成功：${r.currentPOICount} 个 POI`, '✓');
                setShowCreateGeofence(false);
              } else {
                showToast(r.error || '创建失败', '!');
              }
            });
          }}
        />
      )}
    </div>
  );
}

function TaskCard({
  task,
  onExport,
  onCancel,
  onRefresh,
}: {
  task: POITaskStatus;
  onExport: (format: ExportFormat) => void;
  onCancel: () => void;
  onRefresh: () => void;
}) {
  const [showExport, setShowExport] = useState(false);
  const status = STATUS_CONFIG[task.status];
  const Icon = status.icon;

  return (
    <div className="card p-3">
      <div className="flex items-start justify-between mb-2">
        <div className="flex-1 min-w-0">
          <p className="text-sm font-semibold truncate" style={{ color: 'var(--text-primary)' }}>
            {task.name}
          </p>
          <p className="text-[10px] mt-0.5" style={{ color: 'var(--text-muted)' }}>
            {task.taskId.slice(0, 12)}...
          </p>
        </div>
        <div className="flex items-center gap-1.5">
          <span
            className="chip flex items-center gap-0.5 text-[10px]"
            style={{ background: `${status.color}15`, color: status.color }}
          >
            <Icon className={`w-3 h-3 ${task.status === 'running' ? 'animate-spin' : ''}`} />
            {status.label}
          </span>
        </div>
      </div>

      {/* 进度条 */}
      {(task.status === 'running' || task.status === 'pending') && (
        <div className="mb-2">
          <div className="flex items-center justify-between text-[10px] mb-1">
            <span style={{ color: 'var(--text-muted)' }}>采集进度</span>
            <span style={{ color: 'var(--text-primary)' }}>{task.collectedCount} / {task.targetCount}</span>
          </div>
          <div className="h-2 rounded-full overflow-hidden" style={{ background: 'var(--surface-2)' }}>
            <div
              className="h-full transition-all duration-300"
              style={{ width: `${task.progress}%`, background: 'var(--primary)' }}
            />
          </div>
        </div>
      )}

      {/* 统计 */}
      <div className="grid grid-cols-3 gap-2 text-center text-[10px] mb-2">
        <div className="p-1.5 rounded" style={{ background: 'var(--surface-2)' }}>
          <p style={{ color: 'var(--text-muted)' }}>成功率</p>
          <p className="font-bold mt-0.5" style={{ color: '#10b981' }}>{task.successRate}%</p>
        </div>
        <div className="p-1.5 rounded" style={{ background: 'var(--surface-2)' }}>
          <p style={{ color: 'var(--text-muted)' }}>耗时</p>
          <p className="font-bold mt-0.5">{Math.round(task.elapsedMs / 1000)}s</p>
        </div>
        <div className="p-1.5 rounded" style={{ background: 'var(--surface-2)' }}>
          <p style={{ color: 'var(--text-muted)' }}>数据源</p>
          <p className="font-bold mt-0.5">{task.currentProvider}</p>
        </div>
      </div>

      {/* 操作按钮 */}
      <div className="flex items-center gap-2">
        {task.status === 'completed' && (
          <>
            <button
              onClick={() => setShowExport(!showExport)}
              className="flex-1 h-8 rounded-lg flex items-center justify-center gap-1 text-xs font-semibold"
              style={{ background: 'var(--primary)', color: '#fff' }}
            >
              <Download className="w-3.5 h-3.5" />
              导出
            </button>
            {showExport && (
              <div className="flex gap-1">
                {EXPORT_FORMATS.map((f) => {
                  const FIcon = f.icon;
                  return (
                    <button
                      key={f.value}
                      onClick={() => onExport(f.value)}
                      className="h-8 px-2 rounded-lg flex items-center gap-1 text-[10px] font-semibold"
                      style={{ background: 'var(--surface-2)', color: 'var(--text-primary)' }}
                    >
                      <FIcon className="w-3 h-3" />
                      {f.label}
                    </button>
                  );
                })}
              </div>
            )}
          </>
        )}
        {(task.status === 'running' || task.status === 'pending') && (
          <button
            onClick={onCancel}
            className="flex-1 h-8 rounded-lg flex items-center justify-center gap-1 text-xs font-semibold"
            style={{ background: 'rgba(239,68,68,0.10)', color: '#dc2626' }}
          >
            <Pause className="w-3.5 h-3.5" />
            取消
          </button>
        )}
        {task.status === 'failed' && (
          <button
            onClick={onRefresh}
            className="flex-1 h-8 rounded-lg flex items-center justify-center gap-1 text-xs font-semibold"
            style={{ background: 'var(--surface-2)', color: 'var(--text-primary)' }}
          >
            <RefreshCw className="w-3.5 h-3.5" />
            刷新
          </button>
        )}
      </div>
    </div>
  );
}

function GeofenceCard({
  geofence,
  onDelete,
}: {
  geofence: GeofenceStatus;
  onDelete: () => void;
}) {
  return (
    <div className="card p-3">
      <div className="flex items-start justify-between mb-2">
        <div className="flex-1 min-w-0">
          <p className="text-sm font-semibold truncate" style={{ color: 'var(--text-primary)' }}>
            {geofence.name}
          </p>
          <p className="text-[10px] mt-0.5" style={{ color: 'var(--text-muted)' }}>
            {geofence.type === 'circle' ? '圆形围栏' : '多边形围栏'}
          </p>
        </div>
        <span
          className="chip text-[10px]"
          style={{
            background: geofence.monitoring ? 'rgba(16,185,129,0.10)' : 'rgba(245,158,11,0.10)',
            color: geofence.monitoring ? '#10b981' : '#f59e0b',
          }}
        >
          {geofence.monitoring ? '监控中' : '已暂停'}
        </span>
      </div>

      <div className="flex items-center justify-between text-xs mb-2">
        <span style={{ color: 'var(--text-muted)' }}>围栏内 POI</span>
        <span className="font-bold" style={{ color: 'var(--primary)' }}>{geofence.poiCount} 个</span>
      </div>

      {geofence.lastTrigger && (
        <div className="p-2 rounded-lg text-[10px]" style={{ background: 'var(--surface-2)' }}>
          <span style={{ color: 'var(--text-muted)' }}>最近触发：</span>
          <span style={{ color: 'var(--text-primary)' }}>
            {geofence.lastTrigger.poiName} {geofence.lastTrigger.type === 'enter' ? '进入' : geofence.lastTrigger.type === 'exit' ? '离开' : '停留'}
          </span>
        </div>
      )}

      <button
        onClick={onDelete}
        className="w-full h-8 rounded-lg flex items-center justify-center gap-1 text-xs font-semibold mt-2"
        style={{ background: 'rgba(239,68,68,0.10)', color: '#dc2626' }}
      >
        <Trash2 className="w-3.5 h-3.5" />
        删除围栏
      </button>
    </div>
  );
}

function OfflineCityCard({
  city,
  onDownload,
}: {
  city: OfflineCity;
  onDownload: () => void;
}) {
  const statusColor =
    city.downloadStatus === 'completed' ? '#10b981' :
    city.downloadStatus === 'downloading' ? '#3b82f6' :
    city.downloadStatus === 'failed' ? '#ef4444' : '#94a3b8';

  return (
    <div className="card p-3 flex items-center justify-between">
      <div className="flex-1 min-w-0">
        <p className="text-sm font-semibold" style={{ color: 'var(--text-primary)' }}>
          {city.cityName}
        </p>
        <p className="text-[10px] mt-0.5" style={{ color: 'var(--text-muted)' }}>
          {city.province} · {city.dataSizeMB} MB · {city.version}
        </p>
        {city.downloadStatus === 'downloading' && city.downloadProgress && (
          <div className="mt-1.5 h-1.5 rounded-full overflow-hidden" style={{ background: 'var(--surface-2)' }}>
            <div className="h-full" style={{ width: `${city.downloadProgress}%`, background: '#3b82f6' }} />
          </div>
        )}
      </div>
      <div className="flex items-center gap-2">
        <span className="chip text-[10px]" style={{ background: `${statusColor}15`, color: statusColor }}>
          {city.downloadStatus === 'completed' ? '已下载' :
           city.downloadStatus === 'downloading' ? `${city.downloadProgress}%` :
           city.downloadStatus === 'failed' ? '失败' : '未下载'}
        </span>
        {city.downloadStatus !== 'completed' && city.downloadStatus !== 'downloading' && (
          <button
            onClick={onDownload}
            className="h-8 px-3 rounded-lg flex items-center gap-1 text-xs font-semibold"
            style={{ background: 'var(--primary)', color: '#fff' }}
          >
            <Download className="w-3.5 h-3.5" />
            下载
          </button>
        )}
      </div>
    </div>
  );
}

function CreateTaskModal({
  onClose,
  onCreate,
}: {
  onClose: () => void;
  onCreate: (req: any) => void;
}) {
  const [name, setName] = useState('深圳南山科技园 POI 采集');
  const [radius, setRadius] = useState(1000);
  const [keywords, setKeywords] = useState('写字楼|商场|餐厅');
  const [category, setCategory] = useState<POICategory | 'all'>('all');
  const [maxCount, setMaxCount] = useState(100);

  return (
    <div className="absolute inset-0 z-60 bg-white flex flex-col animate-slideInRight">
      <div className="flex items-center justify-between px-5 pt-3 pb-2 border-b" style={{ borderColor: 'var(--border)' }}>
        <h2 className="text-lg font-bold" style={{ color: 'var(--text-primary)' }}>创建采集任务</h2>
        <button onClick={onClose} className="w-9 h-9 rounded-full flex items-center justify-center" style={{ background: 'var(--surface-2)' }}>
          <X className="w-4 h-4" />
        </button>
      </div>

      <div className="flex-1 overflow-y-auto scroll-area px-5 py-3 space-y-3">
        <div>
          <label className="text-xs font-semibold mb-1" style={{ color: 'var(--text-primary)' }}>任务名称</label>
          <input
            type="text"
            value={name}
            onChange={(e) => setName(e.target.value)}
            className="w-full h-11 px-3 rounded-xl text-sm"
            style={{ background: 'var(--surface-2)', color: 'var(--text-primary)', border: '1px solid var(--border)' }}
          />
        </div>

        <div>
          <label className="text-xs font-semibold mb-1" style={{ color: 'var(--text-primary)' }}>搜索半径（米）</label>
          <input
            type="number"
            value={radius}
            onChange={(e) => setRadius(Number(e.target.value))}
            className="w-full h-11 px-3 rounded-xl text-sm"
            style={{ background: 'var(--surface-2)', color: 'var(--text-primary)', border: '1px solid var(--border)' }}
          />
        </div>

        <div>
          <label className="text-xs font-semibold mb-1" style={{ color: 'var(--text-primary)' }}>搜索关键字（多个以 | 分隔）</label>
          <input
            type="text"
            value={keywords}
            onChange={(e) => setKeywords(e.target.value)}
            className="w-full h-11 px-3 rounded-xl text-sm"
            style={{ background: 'var(--surface-2)', color: 'var(--text-primary)', border: '1px solid var(--border)' }}
          />
        </div>

        <div>
          <label className="text-xs font-semibold mb-1" style={{ color: 'var(--text-primary)' }}>POI 类别</label>
          <select
            value={category}
            onChange={(e) => setCategory(e.target.value as any)}
            className="w-full h-11 px-3 rounded-xl text-sm"
            style={{ background: 'var(--surface-2)', color: 'var(--text-primary)', border: '1px solid var(--border)' }}
          >
            {CATEGORY_OPTIONS.map((c) => (
              <option key={c.value} value={c.value}>{c.label}</option>
            ))}
          </select>
        </div>

        <div>
          <label className="text-xs font-semibold mb-1" style={{ color: 'var(--text-primary)' }}>最大采集数量</label>
          <input
            type="number"
            value={maxCount}
            onChange={(e) => setMaxCount(Number(e.target.value))}
            className="w-full h-11 px-3 rounded-xl text-sm"
            style={{ background: 'var(--surface-2)', color: 'var(--text-primary)', border: '1px solid var(--border)' }}
          />
        </div>
      </div>

      <div className="px-5 py-3 border-t" style={{ borderColor: 'var(--border)' }}>
        <button
          onClick={() => {
            onCreate({
              name,
              center: DEFAULT_CENTER,
              radius,
              keywords: keywords.split('|').filter(Boolean),
              categories: category === 'all' ? undefined : [category],
              maxCount,
            });
          }}
          className="w-full h-11 rounded-xl flex items-center justify-center gap-2 text-sm font-semibold"
          style={{ background: 'var(--primary)', color: '#fff' }}
        >
          <Play className="w-4 h-4" />
          开始采集
        </button>
      </div>
    </div>
  );
}

function CreateGeofenceModal({
  onClose,
  onCreate,
}: {
  onClose: () => void;
  onCreate: (req: any) => void;
}) {
  const [name, setName] = useState('科技园围栏');
  const [type, setType] = useState<GeofenceType>('circle');
  const [radius, setRadius] = useState(500);

  return (
    <div className="absolute inset-0 z-60 bg-white flex flex-col animate-slideInRight">
      <div className="flex items-center justify-between px-5 pt-3 pb-2 border-b" style={{ borderColor: 'var(--border)' }}>
        <h2 className="text-lg font-bold" style={{ color: 'var(--text-primary)' }}>创建地理围栏</h2>
        <button onClick={onClose} className="w-9 h-9 rounded-full flex items-center justify-center" style={{ background: 'var(--surface-2)' }}>
          <X className="w-4 h-4" />
        </button>
      </div>

      <div className="flex-1 overflow-y-auto scroll-area px-5 py-3 space-y-3">
        <div>
          <label className="text-xs font-semibold mb-1" style={{ color: 'var(--text-primary)' }}>围栏名称</label>
          <input
            type="text"
            value={name}
            onChange={(e) => setName(e.target.value)}
            className="w-full h-11 px-3 rounded-xl text-sm"
            style={{ background: 'var(--surface-2)', color: 'var(--text-primary)', border: '1px solid var(--border)' }}
          />
        </div>

        <div>
          <label className="text-xs font-semibold mb-1" style={{ color: 'var(--text-primary)' }}>围栏类型</label>
          <div className="flex gap-2">
            <button
              onClick={() => setType('circle')}
              className="flex-1 h-11 rounded-xl flex items-center justify-center gap-2 text-sm font-semibold"
              style={{
                background: type === 'circle' ? 'var(--primary)' : 'var(--surface-2)',
                color: type === 'circle' ? '#fff' : 'var(--text-primary)',
              }}
            >
              <Circle className="w-4 h-4" />
              圆形
            </button>
            <button
              onClick={() => setType('polygon')}
              className="flex-1 h-11 rounded-xl flex items-center justify-center gap-2 text-sm font-semibold"
              style={{
                background: type === 'polygon' ? 'var(--primary)' : 'var(--surface-2)',
                color: type === 'polygon' ? '#fff' : 'var(--text-primary)',
              }}
            >
              <Pentagon className="w-4 h-4" />
              多边形
            </button>
          </div>
        </div>

        {type === 'circle' && (
          <div>
            <label className="text-xs font-semibold mb-1" style={{ color: 'var(--text-primary)' }}>半径（米）</label>
            <input
              type="number"
              value={radius}
              onChange={(e) => setRadius(Number(e.target.value))}
              className="w-full h-11 px-3 rounded-xl text-sm"
              style={{ background: 'var(--surface-2)', color: 'var(--text-primary)', border: '1px solid var(--border)' }}
            />
          </div>
        )}
      </div>

      <div className="px-5 py-3 border-t" style={{ borderColor: 'var(--border)' }}>
        <button
          onClick={() => {
            onCreate({
              name,
              type,
              circle: type === 'circle' ? { center: DEFAULT_CENTER, radius } : undefined,
              triggerType: 'all',
            });
          }}
          className="w-full h-11 rounded-xl flex items-center justify-center gap-2 text-sm font-semibold"
          style={{ background: 'var(--primary)', color: '#fff' }}
        >
          <MapPin className="w-4 h-4" />
          创建围栏
        </button>
      </div>
    </div>
  );
}