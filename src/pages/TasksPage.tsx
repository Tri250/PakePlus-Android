import { CheckCircle2, Clock, MapPin, MoreHorizontal, Play, Camera, Route, Trophy } from 'lucide-react';
import { useAppStore } from '../store/appStore';
import { tasks, achievements } from '../data/mockData';
import { useMemo } from 'react';

export default function TasksPage() {
  const setSelectedTask = useAppStore((s) => s.setSelectedTask);
  const taskCheckIns = useAppStore((s) => s.taskCheckIns);
  const toggleCheckIn = useAppStore((s) => s.toggleCheckIn);
  const showToast = useAppStore((s) => s.showToast);

  const stats = useMemo(() => {
    const done = tasks.filter((t) => t.status === 'done').length + Object.values(taskCheckIns).filter(Boolean).length;
    const total = tasks.length;
    return { done, total, remaining: total - done };
  }, [taskCheckIns]);

  const todoTask = tasks.find((t) => t.status === 'doing') || tasks.find((t) => t.status === 'todo');

  return (
    <div className="flex flex-col h-full">
      <div className="scroll-area">
        {/* 页面标题 */}
        <div className="px-5 pt-1 pb-3 flex items-center justify-between animate-fadeIn">
          <h1 className="text-[28px] font-bold tracking-tight" style={{ color: 'var(--text-primary)' }}>
            地推任务
          </h1>
          <span
            className="chip"
            style={{ background: 'var(--surface-2)', color: 'var(--text-secondary)' }}
          >
            今天 · 6月5日
          </span>
        </div>

        {/* 今日进度卡 */}
        <div className="px-5 mb-3 animate-slideUp" style={{ animationDelay: '60ms' }}>
          <div className="card p-4">
            <div className="flex items-end justify-between mb-2">
              <div>
                <p className="text-sm font-semibold" style={{ color: 'var(--text-primary)' }}>
                  今日进度
                </p>
                <p className="text-[11px] mt-0.5" style={{ color: 'var(--text-muted)' }}>
                  剩余 {stats.remaining} 个任务待完成
                </p>
              </div>
              <div
                className="text-[28px] font-bold leading-none"
                style={{ color: 'var(--primary)' }}
              >
                {stats.done}
                <span className="text-base font-normal mx-0.5" style={{ color: 'var(--text-muted)' }}>
                  /
                </span>
                <span className="text-base font-normal" style={{ color: 'var(--text-muted)' }}>
                  {stats.total}
                </span>
              </div>
            </div>
            <div className="progress">
              <div
                className="progress-bar"
                style={{
                  width: `${(stats.done / stats.total) * 100}%`,
                  background: 'linear-gradient(90deg, #3b82f6 0%, #60a5fa 100%)',
                }}
              />
            </div>
            {/* 成就条 */}
            <div
              className="mt-3 flex items-center gap-2 p-2.5 rounded-xl"
              style={{ background: 'var(--surface-2)' }}
            >
              <Trophy className="w-4 h-4" style={{ color: '#f59e0b' }} />
              <p className="text-xs font-semibold flex-1" style={{ color: 'var(--text-primary)' }}>
                再完成 1 个任务即可解锁「单日王者」成就
              </p>
              <span className="text-[10px] font-bold" style={{ color: '#f59e0b' }}>
                1/2
              </span>
            </div>
          </div>
        </div>

        {/* 任务列表 */}
        <div className="px-5 space-y-2.5 pb-6">
          {tasks.map((t, i) => (
            <div
              key={t.id}
              className="card p-4 animate-slideInRight cursor-pointer"
              style={{ animationDelay: `${120 + i * 60}ms` }}
              onClick={() => setSelectedTask(t.id)}
            >
              <div className="flex items-start gap-3">
                <div
                  className="w-2.5 h-2.5 rounded-full mt-1.5 flex-shrink-0"
                  style={{
                    background: t.statusColor,
                    boxShadow: t.status === 'doing' ? `0 0 0 4px ${t.statusColor}30` : 'none',
                  }}
                />
                <div className="flex-1 min-w-0">
                  <div className="flex items-center justify-between gap-2">
                    <p
                      className="text-[15px] font-semibold truncate"
                      style={{
                        color: 'var(--text-primary)',
                        textDecoration: t.status === 'done' ? 'line-through' : 'none',
                        opacity: t.status === 'done' ? 0.5 : 1,
                      }}
                    >
                      {t.title}
                    </p>
                    <span
                      className="chip flex-shrink-0"
                      style={{
                        background: `${t.statusColor}15`,
                        color: t.statusColor,
                      }}
                    >
                      {t.statusLabel}
                    </span>
                  </div>
                  <p className="text-xs mt-1" style={{ color: 'var(--text-secondary)' }}>
                    {t.progressText}
                  </p>
                  <div className="flex items-center gap-1.5 mt-1">
                    <MapPin className="w-3 h-3" style={{ color: 'var(--text-muted)' }} />
                    <span className="text-[11px]" style={{ color: 'var(--text-muted)' }}>
                      距离 {t.distance}
                    </span>
                  </div>
                  {t.status === 'doing' && (
                    <div className="progress mt-2.5">
                      <div
                        className="progress-bar"
                        style={{
                          width: `${t.progress}%`,
                          background: t.statusColor,
                        }}
                      />
                    </div>
                  )}
                  {t.status === 'doing' && (
                    <div className="flex items-center gap-2 mt-3">
                      <button
                        className="flex-1 h-9 rounded-lg flex items-center justify-center gap-1 text-xs font-semibold"
                        style={{ background: 'var(--primary)', color: '#fff' }}
                        onClick={(e) => {
                          e.stopPropagation();
                          toggleCheckIn(t.id);
                        }}
                      >
                        {taskCheckIns[t.id] ? (
                          <>
                            <CheckCircle2 className="w-3.5 h-3.5" />
                            已打卡
                          </>
                        ) : (
                          <>
                            <Camera className="w-3.5 h-3.5" />
                            拍照打卡
                          </>
                        )}
                      </button>
                      <button
                        className="flex-1 h-9 rounded-lg flex items-center justify-center gap-1 text-xs font-semibold"
                        style={{ background: 'var(--surface-2)', color: 'var(--text-primary)' }}
                        onClick={(e) => e.stopPropagation()}
                      >
                        <Route className="w-3.5 h-3.5" />
                        路线
                      </button>
                    </div>
                  )}
                </div>
              </div>
            </div>
          ))}

          {/* 智能建议卡 */}
          {todoTask && (
            <div
              className="card p-3.5 flex items-center gap-3 animate-fadeScale"
              style={{
                background: 'linear-gradient(135deg, #eff6ff 0%, #dbeafe 100%)',
                border: '1px solid #bfdbfe',
                animationDelay: '400ms',
              }}
            >
              <div
                className="w-9 h-9 rounded-full flex items-center justify-center flex-shrink-0"
                style={{ background: '#fff' }}
              >
                <Route className="w-4 h-4" style={{ color: '#3b82f6' }} />
              </div>
              <p className="text-[13px] font-semibold flex-1" style={{ color: '#1e40af' }}>
                建议先完成「{todoTask.title}」再去福田，节省通勤时间
              </p>
              <button
                className="btn-primary text-xs"
                style={{ padding: '6px 12px' }}
                onClick={() => showToast('正在规划最优路线...', '🗺️')}
              >
                查看路线
              </button>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
