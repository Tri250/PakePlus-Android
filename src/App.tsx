import React, { useState } from 'react';

// 模块类型
type ModuleType = 'acquisition' | 'customer' | 'ground' | 'platform' | null;

// 子模块配置
const moduleConfig = {
  acquisition: {
    name: '智能获客中枢',
    icon: '🎯',
    gradient: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)',
    bgGradient: 'linear-gradient(135deg, #667eea15 0%, #764ba215 100%)',
    tag: '获客',
    tagColor: '#667eea',
    subs: [
      { id: 'geo', name: 'GEO 搜索优化引擎', icon: '🔍', badge: '新增' },
      { id: 'lbs', name: 'LBS 雷达扫描', icon: '📡', badge: '重构' },
      { id: 'competitor', name: '竞品热力监控', icon: '🔥', badge: '新增' },
      { id: 'replacement', name: '换机周期预测', icon: '📱', badge: '新增' },
    ],
  },
  customer: {
    name: '客户资产库',
    icon: '👥',
    gradient: 'linear-gradient(135deg, #11998e 0%, #38ef7d 100%)',
    bgGradient: 'linear-gradient(135deg, #11998e15 0%, #38ef7d15 100%)',
    tag: '资产',
    tagColor: '#11998e',
    subs: [
      { id: 'segment', name: '品牌潜客分层模型', icon: '📊', badge: '' },
      { id: 'tradein', name: '以旧换新意向评分', icon: '💰', badge: '' },
      { id: 'timeline', name: '服务事件时间轴', icon: '📅', badge: '' },
      { id: 'wecom', name: '企业微信侧边栏', icon: '💬', badge: '' },
    ],
  },
  ground: {
    name: '地推作战系统',
    icon: '🚀',
    gradient: 'linear-gradient(135deg, #f093fb 0%, #f5576c 100%)',
    bgGradient: 'linear-gradient(135deg, #f093fb15 0%, #f5576c15 100%)',
    tag: '地推',
    tagColor: '#f5576c',
    subs: [
      { id: 'route', name: 'AI 智能路线规划', icon: '🗺️', badge: '重构' },
      { id: 'task', name: '扫街任务派发与追踪', icon: '📋', badge: '' },
      { id: 'script', name: '话术智能推荐', icon: '💡', badge: '' },
      { id: 'material', name: '品牌物料一键生成', icon: '🎨', badge: '' },
    ],
  },
  platform: {
    name: '品牌数据中台',
    icon: '📈',
    gradient: 'linear-gradient(135deg, #4facfe 0%, #00f2fe 100%)',
    bgGradient: 'linear-gradient(135deg, #4facfe15 0%, #00f2fe15 100%)',
    tag: '数据',
    tagColor: '#4facfe',
    subs: [
      { id: 'dashboard', name: '总部驾驶舱', icon: '🎛️', badge: '' },
      { id: 'heatmap', name: '竞品热力地图', icon: '🗺️', badge: '' },
      { id: 'board', name: '客户换机周期看板', icon: '📊', badge: '' },
      { id: 'api', name: '数据 API 回传', icon: '🔗', badge: '' },
    ],
  },
};

const App: React.FC = () => {
  const [activeModule, setActiveModule] = useState<ModuleType>(null);

  // 渲染主页
  const renderHome = () => (
    <div style={{
      minHeight: '100vh',
      background: 'linear-gradient(180deg, #f8fafc 0%, #e2e8f0 100%)',
      padding: '24px',
    }}>
      {/* 顶部标题 */}
      <header style={{
        textAlign: 'center',
        marginBottom: '32px',
      }}>
        <div style={{
          display: 'inline-flex',
          alignItems: 'center',
          gap: '12px',
          marginBottom: '8px',
        }}>
          <span style={{ fontSize: '36px' }}>📱</span>
          <div>
            <h1 style={{
              fontSize: '28px',
              fontWeight: 700,
              background: 'linear-gradient(135deg, #667eea 0%, #764ba2 50%, #11998e 100%)',
              WebkitBackgroundClip: 'text',
              WebkitTextFillColor: 'transparent',
              margin: 0,
            }}>
              掌上商客 V2.0
            </h1>
            <p style={{ color: '#64748b', fontSize: '14px', margin: '4px 0 0 0' }}>
              HandBiz Radar · 智能获客中枢
            </p>
          </div>
        </div>
        {/* 统计标签 */}
        <div style={{
          display: 'flex',
          justifyContent: 'center',
          gap: '12px',
          marginTop: '16px',
        }}>
          {[
            { label: '4 核心模块', color: '#667eea' },
            { label: '16 功能入口', color: '#11998e' },
            { label: '12 数据源', color: '#f5576c' },
          ].map((item, i) => (
            <span key={i} style={{
              padding: '6px 14px',
              background: `${item.color}15`,
              color: item.color,
              borderRadius: '20px',
              fontSize: '13px',
              fontWeight: 500,
            }}>
              {item.label}
            </span>
          ))}
        </div>
      </header>

      {/* 模块卡片网格 */}
      <div style={{
        display: 'grid',
        gridTemplateColumns: 'repeat(auto-fit, minmax(320px, 1fr))',
        gap: '20px',
        maxWidth: '900px',
        margin: '0 auto',
      }}>
        {Object.entries(moduleConfig).map(([key, module]) => (
          <div
            key={key}
            onClick={() => setActiveModule(key as ModuleType)}
            style={{
              background: '#fff',
              borderRadius: '20px',
              padding: '24px',
              cursor: 'pointer',
              boxShadow: '0 4px 20px rgba(0,0,0,0.08)',
              transition: 'all 0.3s ease',
              position: 'relative',
              overflow: 'hidden',
            }}
            onMouseEnter={(e) => {
              e.currentTarget.style.transform = 'translateY(-4px)';
              e.currentTarget.style.boxShadow = '0 12px 40px rgba(0,0,0,0.12)';
            }}
            onMouseLeave={(e) => {
              e.currentTarget.style.transform = 'translateY(0)';
              e.currentTarget.style.boxShadow = '0 4px 20px rgba(0,0,0,0.08)';
            }}
          >
            {/* 背景装饰 */}
            <div style={{
              position: 'absolute',
              top: '-20px',
              right: '-20px',
              width: '120px',
              height: '120px',
              background: module.bgGradient,
              borderRadius: '50%',
              opacity: 0.6,
            }} />

            {/* 头部 */}
            <div style={{
              display: 'flex',
              alignItems: 'center',
              gap: '16px',
              marginBottom: '20px',
              position: 'relative',
            }}>
              {/* 图标 */}
              <div style={{
                width: '56px',
                height: '56px',
                borderRadius: '16px',
                background: module.gradient,
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                fontSize: '28px',
                boxShadow: `0 8px 20px ${module.tagColor}40`,
              }}>
                {module.icon}
              </div>
              {/* 标题 */}
              <div style={{ flex: 1 }}>
                <div style={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: '8px',
                }}>
                  <h2 style={{
                    fontSize: '18px',
                    fontWeight: 700,
                    color: '#1e293b',
                    margin: 0,
                  }}>
                    {module.name}
                  </h2>
                  <span style={{
                    padding: '2px 8px',
                    background: `${module.tagColor}20`,
                    color: module.tagColor,
                    borderRadius: '6px',
                    fontSize: '11px',
                    fontWeight: 600,
                  }}>
                    {module.tag}
                  </span>
                </div>
                <p style={{
                  fontSize: '13px',
                  color: '#94a3b8',
                  margin: '4px 0 0 0',
                }}>
                  {module.subs.length} 个功能模块
                </p>
              </div>
              {/* 箭头 */}
              <div style={{
                width: '32px',
                height: '32px',
                borderRadius: '8px',
                background: '#f1f5f9',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                color: '#94a3b8',
              }}>
                →
              </div>
            </div>

            {/* 子模块列表 */}
            <div style={{
              display: 'grid',
              gridTemplateColumns: 'repeat(2, 1fr)',
              gap: '10px',
              position: 'relative',
            }}>
              {module.subs.map((sub) => (
                <div key={sub.id} style={{
                  padding: '12px',
                  background: '#f8fafc',
                  borderRadius: '12px',
                  display: 'flex',
                  alignItems: 'center',
                  gap: '10px',
                  transition: 'background 0.2s',
                }}>
                  <span style={{ fontSize: '18px' }}>{sub.icon}</span>
                  <div style={{ flex: 1, minWidth: 0 }}>
                    <div style={{
                      fontSize: '13px',
                      fontWeight: 500,
                      color: '#475569',
                      whiteSpace: 'nowrap',
                      overflow: 'hidden',
                      textOverflow: 'ellipsis',
                    }}>
                      {sub.name}
                    </div>
                  </div>
                  {sub.badge && (
                    <span style={{
                      padding: '2px 6px',
                      background: sub.badge === '新增' ? '#10b98120' : '#f59e0b20',
                      color: sub.badge === '新增' ? '#10b981' : '#f59e0b',
                      borderRadius: '4px',
                      fontSize: '10px',
                      fontWeight: 600,
                    }}>
                      {sub.badge}
                    </span>
                  )}
                </div>
              ))}
            </div>
          </div>
        ))}
      </div>

      {/* 底部 */}
      <footer style={{
        marginTop: '40px',
        textAlign: 'center',
        color: '#94a3b8',
        fontSize: '13px',
      }}>
        <p>© 2026 掌上商客 V2.0 · 支持2026免费地图服务 · 全国5级行政区划覆盖</p>
      </footer>
    </div>
  );

  // 渲染模块详情
  const renderModuleDetail = () => {
    const module = moduleConfig[activeModule!];
    if (!module) return null;

    return (
      <div style={{
        minHeight: '100vh',
        background: 'linear-gradient(180deg, #f8fafc 0%, #e2e8f0 100%)',
      }}>
        {/* 顶部导航 */}
        <header style={{
          background: module.gradient,
          padding: '20px 24px',
          color: '#fff',
          boxShadow: '0 4px 20px rgba(0,0,0,0.15)',
        }}>
          <div style={{
            maxWidth: '900px',
            margin: '0 auto',
            display: 'flex',
            alignItems: 'center',
            gap: '16px',
          }}>
            <button
              onClick={() => setActiveModule(null)}
              style={{
                width: '40px',
                height: '40px',
                borderRadius: '10px',
                background: 'rgba(255,255,255,0.2)',
                border: 'none',
                cursor: 'pointer',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                color: '#fff',
                fontSize: '18px',
              }}
            >
              ←
            </button>
            <span style={{ fontSize: '32px' }}>{module.icon}</span>
            <div>
              <h1 style={{ fontSize: '22px', fontWeight: 700, margin: 0 }}>
                {module.name}
              </h1>
              <p style={{ fontSize: '14px', opacity: 0.9, margin: '4px 0 0 0' }}>
                {module.subs.length} 个功能模块可用
              </p>
            </div>
          </div>
        </header>

        {/* 子模块网格 */}
        <div style={{
          padding: '24px',
          maxWidth: '900px',
          margin: '0 auto',
        }}>
          <div style={{
            display: 'grid',
            gridTemplateColumns: 'repeat(auto-fit, minmax(280px, 1fr))',
            gap: '16px',
          }}>
            {module.subs.map((sub, index) => (
              <div key={sub.id} style={{
                background: '#fff',
                borderRadius: '16px',
                padding: '20px',
                boxShadow: '0 2px 12px rgba(0,0,0,0.06)',
                display: 'flex',
                alignItems: 'center',
                gap: '16px',
                cursor: 'pointer',
                transition: 'all 0.2s',
              }}
              onMouseEnter={(e) => {
                e.currentTarget.style.boxShadow = '0 8px 24px rgba(0,0,0,0.1)';
                e.currentTarget.style.transform = 'translateY(-2px)';
              }}
              onMouseLeave={(e) => {
                e.currentTarget.style.boxShadow = '0 2px 12px rgba(0,0,0,0.06)';
                e.currentTarget.style.transform = 'translateY(0)';
              }}
              >
                <div style={{
                  width: '48px',
                  height: '48px',
                  borderRadius: '12px',
                  background: module.bgGradient,
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  fontSize: '24px',
                }}>
                  {sub.icon}
                </div>
                <div style={{ flex: 1 }}>
                  <div style={{
                    display: 'flex',
                    alignItems: 'center',
                    gap: '8px',
                  }}>
                    <h3 style={{
                      fontSize: '15px',
                      fontWeight: 600,
                      color: '#1e293b',
                      margin: 0,
                    }}>
                      {sub.name}
                    </h3>
                    {sub.badge && (
                      <span style={{
                        padding: '2px 8px',
                        background: sub.badge === '新增' ? '#10b98120' : '#f59e0b20',
                        color: sub.badge === '新增' ? '#10b981' : '#f59e0b',
                        borderRadius: '6px',
                        fontSize: '10px',
                        fontWeight: 600,
                      }}>
                        {sub.badge}
                      </span>
                    )}
                  </div>
                  <p style={{
                    fontSize: '12px',
                    color: '#94a3b8',
                    margin: '4px 0 0 0',
                  }}>
                    点击进入功能模块
                  </p>
                </div>
                <div style={{
                  width: '28px',
                  height: '28px',
                  borderRadius: '6px',
                  background: '#f1f5f9',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  color: '#94a3b8',
                  fontSize: '14px',
                }}>
                  →
                </div>
              </div>
            ))}
          </div>

          {/* 功能说明 */}
          <div style={{
            marginTop: '24px',
            padding: '20px',
            background: '#fff',
            borderRadius: '16px',
            boxShadow: '0 2px 12px rgba(0,0,0,0.06)',
          }}>
            <h3 style={{
              fontSize: '16px',
              fontWeight: 600,
              color: '#1e293b',
              margin: '0 0 12px 0',
            }}>
              📋 模块功能说明
            </h3>
            <div style={{
              display: 'grid',
              gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))',
              gap: '12px',
            }}>
              {module.subs.map((sub) => (
                <div key={sub.id} style={{
                  padding: '12px',
                  background: '#f8fafc',
                  borderRadius: '10px',
                  fontSize: '13px',
                  color: '#64748b',
                }}>
                  <span style={{ marginRight: '8px' }}>{sub.icon}</span>
                  {sub.name}
                </div>
              ))}
            </div>
          </div>
        </div>
      </div>
    );
  };

  return activeModule ? renderModuleDetail() : renderHome();
};

export default App;
