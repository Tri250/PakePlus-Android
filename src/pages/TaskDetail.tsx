import { useAppStore } from '../store/appStore';
import { tasks } from '../data/mockData';
import { ChevronLeft, MapPin, Camera, Route, Phone, Navigation, Clock, CheckCircle2, X } from 'lucide-react';

export default function TaskDetail() {
  const id = useAppStore((s) => s.selectedTaskId);
  const setSelected = useAppStore((s) => s.setSelectedTask);
  const taskCheckIns = useAppStore((s) => s.taskCheckIns);
  const toggleCheckIn = useAppStore((s) => s.toggleCheckIn);
  const showToast = useAppStore((s) => s.showToast);

  const task = tasks.find((t) => t.id === id);
  if (!task) return null;
  const isCheckedIn = !!taskCheckIns[task.id];

  return (
    <div className="absolute inset-0 z-50 bg-white animate-slideInRight flex flex-col">
      <div className="flex items-center justify-between p-4 flex-shrink-0">
        <button
          onClick={() => setSelected(null)}
          className="w-9 h-9 rounded-full flex items-center justify-center"
          style={{ background: 'var(--surface-2)' }}
          aria-label="返回"
        >
          <ChevronLeft className="w-5 h-5" />
        </button>
        <h1 className="text-base font-semibold">任务详情</h1>
        <button
          className="w-9 h-9 rounded-full flex items-center justify-center"
          style={{ background: 'var(--surface-2)' }}
        >
          <X className="w-5 h-5" />
        </button>
      </div>

      <div className="scroll-area flex-1">
        <div className="px-5 pb-6 animate-fadeIn">
          {/* 标题 */}
          <div className="mb-4">
            <div className="flex items-center gap-2 mb-2">
              <div
                className="w-3 h-3 rounded-full"
                style={{ background: task.statusColor }}
              />
              <span
                className="chip"
                style={{
                  background: `${task.statusColor}15`,
                  color: task.statusColor,
                }}
              >
                {task.statusLabel}
              </span>
            </div>
            <h2 className="text-2xl font-bold" style={{ color: 'var(--text-primary)' }}>
              {task.title}
            </h2>
            <div className="flex items-center gap-1.5 mt-2">
              <MapPin className="w-3.5 h-3.5" style={{ color: 'var(--text-muted)' }} />
              <span className="text-sm" style={{ color: 'var(--text-secondary)' }}>
                距离 {task.distance}
              </span>
            </div>
          </div>

          {/* 进度卡 */}
          {task.status === 'doing' && (
            <div
              className="card p-4 mb-4 animate-slideUp"
              style={{
                background: 'linear-gradient(135deg, #eff6ff 0%, #dbeafe 100%)',
              }}
            >
              <div className="flex items-end justify-between mb-2">
                <p className="text-sm font-semibold" style={{ color: '#1e40af' }}>
                  任务进度
                </p>
                <span className="text-xl font-bold" style={{ color: '#3b82f6' }}>
                  {task.progress}%
                </span>
              </div>
              <div className="progress" style={{ background: 'rgba(255,255,255,0.6)' }}>
                <div
                  className="progress-bar"
                  style={{ width: `${task.progress}%` }}
                />
              </div>
              <p className="text-xs mt-2" style={{ color: '#1e40af' }}>
                {task.progressText}
              </p>
            </div>
          )}

          {/* 详情信息 */}
          <div
            className="card p-4 mb-4 space-y-3 animate-slideUp"
            style={{ animationDelay: '60ms' }}
          >
            <DetailRow icon={<Clock className="w-4 h-4" />} label="截止时间" value="今天 18:00" />
            <DetailRow icon={<MapPin className="w-4 h-4" />} label="覆盖范围" value={`${task.distance} 半径`} />
            <DetailRow icon={<Route className="w-4 h-4" />} label="推荐路径" value="起点：当前位置 → 终点：任务中心" />
            <DetailRow
              icon={<CheckCircle2 className="w-4 h-4" />}
              label="已完成"
              value={isCheckedIn ? '1 次打卡' : '暂未打卡'}
            />
          </div>

          {/* 操作列表 */}
          <h3 className="text-sm font-semibold mb-2 mt-4">任务操作</h3>
          <div className="space-y-2.5">
            <ActionRow
              icon={<Camera />}
              title={isCheckedIn ? '已拍照打卡' : '拍照打卡'}
              desc={isCheckedIn ? '已记录位置和时间' : '支持自动打位置水印'}
              color="#3b82f6"
              onClick={() => toggleCheckIn(task.id)}
              done={isCheckedIn}
            />
            <ActionRow
              icon={<Route />}
              title="查看路线"
              desc="最优路径规划"
              color="#8b5cf6"
              onClick={() => showToast('正在打开高德地图...', '🗺️')}
            />
            <ActionRow
              icon={<Phone />}
              title="联系店长"
              desc="一键呼叫区域经理"
              color="#10b981"
              onClick={() => showToast('正在呼叫店长...', '📞')}
            />
            <ActionRow
              icon={<Navigation />}
              title="导航到现场"
              desc="支持百度/高德/腾讯地图"
              color="#f59e0b"
              onClick={() => showToast('已唤起地图应用', '🧭')}
            />
          </div>

          {/* 任务清单 */}
          {task.poiCount && task.poiCount > 0 && (
            <div className="mt-4">
              <h3 className="text-sm font-semibold mb-2">任务清单 ({task.doneCount || 0}/{task.poiCount})</h3>
              <div className="card p-3.5 space-y-2">
                {Array.from({ length: Math.min(task.poiCount, 5) }).map((_, i) => {
                  const done = i < (task.doneCount || 0);
                  return (
                    <div key={i} className="flex items-center gap-2.5">
                      <div
                        className="w-5 h-5 rounded-full flex items-center justify-center flex-shrink-0"
                        style={{
                          background: done ? '#10b981' : 'transparent',
                          border: done ? 'none' : '1.5px solid var(--border)',
                        }}
                      >
                        {done && <CheckCircle2 className="w-3.5 h-3.5 text-white" strokeWidth={3} />}
                      </div>
                      <span
                        className="text-sm"
                        style={{
                          color: done ? 'var(--text-muted)' : 'var(--text-primary)',
                          textDecoration: done ? 'line-through' : 'none',
                        }}
                      >
                        POI {i + 1} - {['华强北电子大厦', '赛格广场', '通天地通信市场', '明通数码城', '远望数码城'][i] || 'POI 地点'}
                      </span>
                    </div>
                  );
                })}
              </div>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}

function DetailRow({ icon, label, value }: { icon: React.ReactNode; label: string; value: string }) {
  return (
    <div className="flex items-center gap-2.5">
      <div
        className="w-8 h-8 rounded-full flex items-center justify-center flex-shrink-0"
        style={{ background: 'var(--surface-2)', color: 'var(--text-secondary)' }}
      >
        {icon}
      </div>
      <div className="flex-1">
        <p className="text-[11px]" style={{ color: 'var(--text-muted)' }}>
          {label}
        </p>
        <p className="text-sm font-medium" style={{ color: 'var(--text-primary)' }}>
          {value}
        </p>
      </div>
    </div>
  );
}

function ActionRow({
  icon,
  title,
  desc,
  color,
  onClick,
  done,
}: {
  icon: React.ReactNode;
  title: string;
  desc: string;
  color: string;
  onClick: () => void;
  done?: boolean;
}) {
  return (
    <button
      onClick={onClick}
      className="w-full card p-3.5 flex items-center gap-3 text-left"
      style={done ? { background: 'rgba(16,185,129,0.08)' } : {}}
    >
      <div
        className="w-10 h-10 rounded-full flex items-center justify-center flex-shrink-0"
        style={{ background: `${color}15`, color }}
      >
        {icon}
      </div>
      <div className="flex-1 min-w-0">
        <p className="text-sm font-semibold" style={{ color: 'var(--text-primary)' }}>
          {title}
        </p>
        <p className="text-[11px] mt-0.5" style={{ color: 'var(--text-muted)' }}>
          {desc}
        </p>
      </div>
      {done && <CheckCircle2 className="w-5 h-5" style={{ color: '#10b981' }} strokeWidth={2.5} />}
    </button>
  );
}
