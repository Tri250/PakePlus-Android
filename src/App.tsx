import React, { useState, useEffect, useCallback } from 'react';
import { 
  Map, 
  ScanLine, 
  Users, 
  Target, 
  TrendingUp,
  Navigation,
  Phone,
  MessageSquare,
  Calendar,
  AlertCircle,
  CheckCircle2,
  Clock,
  MapPin,
  ArrowRight,
  ChevronLeft,
  Mic,
  Wifi,
  WifiOff,
  Battery,
  Signal
} from 'lucide-react';
import { repository, initDB, addToSyncQueue } from './services/storage';
import { geolocationService } from './services/geolocation';
import { networkManager } from './services/networkManager';
import { lbsRadarService } from './services/lbsRadar';
import { competitorMonitorService } from './services/competitorMonitor';
import { geoOptimizationEngine } from './services/geoOptimization';
import { dataCollector } from './services/dataCollector';
import { imageService } from './services/imageService';

// 模块类型
type ModuleType = 'home' | 'acquisition' | 'customer' | 'ground' | 'platform' | null;
type ViewType = 'home' | 'module' | 'detail' | 'map-view';

// 真实数据结构
interface TodayTask {
  id: string;
  type: 'call' | 'visit' | 'follow-up' | 'trade-in';
  title: string;
  customerName: string;
  customerPhone: string;
  address: string;
  priority: 'high' | 'medium' | 'low';
  deadline: string;
  completed: boolean;
  lat?: number;
  lng?: number;
}

interface SalesLead {
  id: string;
  name: string;
  type: 'poi' | 'crm' | 'prediction';
  distance: number;
  heatScore: number;
  address: string;
  phone?: string;
  lat: number;
  lng: number;
  alertLevel?: 'high' | 'medium' | 'low';
  suggestedScript?: string;
}

type NetworkStatusType = 'online' | 'offline' | 'slow' | 'unstable';

interface AppState {
  networkStatus: NetworkStatusType;
  location: { lat: number; lng: number; address: string } | null;
  todayTasks: TodayTask[];
  salesLeads: SalesLead[];
  isLoading: boolean;
  error: string | null;
}

// 高对比度主题
const themes = {
  normal: {
    bg: 'linear-gradient(180deg, #f8fafc 0%, #e2e8f0 100%)',
    card: '#ffffff',
    text: '#1e293b',
    textSecondary: '#64748b',
    primary: '#1677ff',
    success: '#52c41a',
    warning: '#faad14',
    danger: '#f5222d',
  },
  highContrast: {
    bg: '#000000',
    card: '#1a1a1a',
    text: '#ffffff',
    textSecondary: '#cccccc',
    primary: '#ffff00',
    success: '#00ff00',
    warning: '#ffaa00',
    danger: '#ff0000',
  }
};

const App: React.FC = () => {
  const [currentView, setCurrentView] = useState<ViewType>('home');
  const [activeModule, setActiveModule] = useState<ModuleType>(null);
  const [highContrastMode, setHighContrastMode] = useState(false);
  const [isListening, setIsListening] = useState(false);
  const [state, setState] = useState<AppState>({
    networkStatus: 'online',
    location: null,
    todayTasks: [],
    salesLeads: [],
    isLoading: false,
    error: null,
  });

  const theme = highContrastMode ? themes.highContrast : themes.normal;

  // 初始化 - 真实数据加载
  useEffect(() => {
    const init = async () => {
      try {
        // 初始化数据库
        await initDB();
        
        // 获取网络状态
        const netStatus = await networkManager.getStatus();
        setState(prev => ({ ...prev, networkStatus: netStatus.status }));

        // 获取真实位置
        const location = await geolocationService.getCurrentLocation();
        setState(prev => ({ 
          ...prev, 
          location: {
            lat: location.lat,
            lng: location.lng,
            address: location.address || '定位中...'
          }
        }));

        // 加载今日真实任务
        await loadTodayTasks();

        // 加载真实线索
        await loadSalesLeads(location.lat, location.lng);

      } catch (err: any) {
        setState(prev => ({ ...prev, error: err.message }));
      }
    };

    init();

    // 网络状态监听
    const unsubscribe = networkManager.subscribe((status) => {
      setState(prev => ({ ...prev, networkStatus: status.status }));
    });

    return () => unsubscribe();
  }, []);

  // 加载今日任务 - 真实数据
  const loadTodayTasks = async () => {
    try {
      // 从本地存储获取任务
      const tasks = await repository.task.getAll();
      
      // 如果没有任务，创建示例任务（仅首次）
      if (tasks.length === 0) {
        const sampleTasks: TodayTask[] = [
          {
            id: 'task-1',
            type: 'call',
            title: '联系换机意向客户',
            customerName: '张先生',
            customerPhone: '138****1234',
            address: '朝阳区建国路88号',
            priority: 'high',
            deadline: '14:00',
            completed: false,
            lat: 39.9087,
            lng: 116.4667,
          },
          {
            id: 'task-2',
            type: 'visit',
            title: '扫街拜访-国贸商圈',
            customerName: '商圈潜客',
            customerPhone: '',
            address: '国贸CBD区域',
            priority: 'medium',
            deadline: '16:00',
            completed: false,
            lat: 39.9054,
            lng: 116.4551,
          },
          {
            id: 'task-3',
            type: 'follow-up',
            title: '跟进昨日到店客户',
            customerName: '李女士',
            customerPhone: '139****5678',
            address: '海淀区中关村',
            priority: 'high',
            deadline: '11:00',
            completed: true,
            lat: 39.9845,
            lng: 116.3150,
          },
        ];

        for (const task of sampleTasks) {
          await repository.task.save(task);
        }
        setState(prev => ({ ...prev, todayTasks: sampleTasks }));
      } else {
        setState(prev => ({ ...prev, todayTasks: tasks }));
      }
    } catch (err) {
      console.error('加载任务失败:', err);
    }
  };

  // 加载销售线索 - 真实扫描
  const loadSalesLeads = async (lat: number, lng: number) => {
    try {
      setState(prev => ({ ...prev, isLoading: true }));
      
      // 执行真实LBS扫描
      const scanResult = await lbsRadarService.scan('STORE-001', {
        lat,
        lng,
        radius: 5,
      });

      // 转换线索数据
      const leads: SalesLead[] = scanResult.salesLeads.map((lead: any) => ({
        id: lead.id,
        name: lead.name,
        type: lead.type,
        distance: lead.distance,
        heatScore: lead.heatScore,
        address: lead.address || '未知地址',
        phone: lead.phone,
        lat: lead.lat,
        lng: lead.lng,
        alertLevel: lead.alertLevel,
        suggestedScript: lead.suggestedScript,
      }));

      // 保存到本地
      for (const lead of leads) {
        await repository.lead.save(lead);
      }

      setState(prev => ({ ...prev, salesLeads: leads, isLoading: false }));
    } catch (err) {
      setState(prev => ({ ...prev, isLoading: false, error: '扫描失败' }));
    }
  };

  // 语音指令处理
  const handleVoiceCommand = useCallback(() => {
    if (!('webkitSpeechRecognition' in window)) {
      alert('您的设备不支持语音识别');
      return;
    }

    const recognition = new (window as any).webkitSpeechRecognition();
    recognition.lang = 'zh-CN';
    recognition.continuous = false;
    recognition.interimResults = false;

    recognition.onstart = () => setIsListening(true);
    recognition.onend = () => setIsListening(false);

    recognition.onresult = (event: any) => {
      const command = event.results[0][0].transcript;
      processVoiceCommand(command);
    };

    recognition.start();
  }, []);

  // 处理语音指令
  const processVoiceCommand = (command: string) => {
    if (command.includes('扫描') || command.includes('雷达')) {
      if (state.location) {
        loadSalesLeads(state.location.lat, state.location.lng);
      }
    } else if (command.includes('客户') || command.includes('线索')) {
      setActiveModule('customer');
      setCurrentView('module');
    } else if (command.includes('地图')) {
      setCurrentView('map-view');
    }
  };

  // 完成任务
  const completeTask = async (taskId: string) => {
    const task = state.todayTasks.find(t => t.id === taskId);
    if (task) {
      const updated = { ...task, completed: true };
      await repository.task.save(updated);
      
      // 添加到同步队列
      await addToSyncQueue({
        entity: 'task',
        action: 'update',
        data: updated,
      });

      setState(prev => ({
        ...prev,
        todayTasks: prev.todayTasks.map(t => t.id === taskId ? updated : t)
      }));
    }
  };

  // 一键外呼
  const makeCall = (phone: string) => {
    if (phone) {
      window.location.href = `tel:${phone.replace(/[^0-9]/g, '')}`;
    }
  };

  // 一键导航
  const openNavigation = (lat: number, lng: number, address: string) => {
    const url = `https://map.baidu.com/search/${encodeURIComponent(address)}`;
    window.open(url, '_blank');
  };

  // 渲染状态栏
  const renderStatusBar = () => (
    <div style={{
      display: 'flex',
      justifyContent: 'space-between',
      alignItems: 'center',
      padding: '8px 16px',
      background: theme.card,
      borderBottom: `1px solid ${highContrastMode ? '#333' : '#e2e8f0'}`,
    }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
        {state.networkStatus === 'online' ? (
          <Wifi size={18} color={theme.success} />
        ) : (
          <WifiOff size={18} color={theme.danger} />
        )}
        <span style={{ fontSize: '12px', color: theme.textSecondary }}>
          {state.networkStatus === 'online' ? '在线' : '离线'}
        </span>
      </div>
      <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
        <button
          onClick={() => setHighContrastMode(!highContrastMode)}
          style={{
            padding: '4px 8px',
            fontSize: '11px',
            background: highContrastMode ? theme.primary : 'transparent',
            color: highContrastMode ? '#000' : theme.text,
            border: `1px solid ${theme.textSecondary}`,
            borderRadius: '4px',
            cursor: 'pointer',
          }}
        >
          {highContrastMode ? '标准模式' : '高对比度'}
        </button>
        <span style={{ fontSize: '12px', color: theme.textSecondary }}>
          {new Date().toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })}
        </span>
      </div>
    </div>
  );

  // 渲染智能首页工作台
  const renderHome = () => (
    <div style={{
      minHeight: '100vh',
      background: theme.bg,
      paddingBottom: '80px',
    }}>
      {renderStatusBar()}
      
      {/* 头部信息 */}
      <header style={{
        padding: '20px 16px',
        background: theme.card,
        marginBottom: '12px',
      }}>
        <div style={{
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          marginBottom: '16px',
        }}>
          <div>
            <h1 style={{
              fontSize: '24px',
              fontWeight: 700,
              color: theme.text,
              margin: '0 0 4px 0',
            }}>
              今日工作台
            </h1>
            <p style={{ fontSize: '14px', color: theme.textSecondary, margin: 0 }}>
              {state.location?.address || '定位中...'}
            </p>
          </div>
          <button
            onClick={handleVoiceCommand}
            style={{
              width: '56px',
              height: '56px',
              borderRadius: '50%',
              background: isListening ? theme.danger : theme.primary,
              border: 'none',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              cursor: 'pointer',
              boxShadow: '0 4px 12px rgba(0,0,0,0.15)',
            }}
          >
            <Mic size={28} color={highContrastMode ? '#000' : '#fff'} />
          </button>
        </div>

        {/* 快捷操作 */}
        <div style={{
          display: 'grid',
          gridTemplateColumns: 'repeat(4, 1fr)',
          gap: '12px',
        }}>
          {[
            { icon: ScanLine, label: '扫码获客', color: theme.primary, action: () => {} },
            { icon: Phone, label: '快速外呼', color: theme.success, action: () => {} },
            { icon: MapPin, label: '附近线索', color: theme.warning, action: () => setCurrentView('map-view') },
            { icon: Target, label: '今日任务', color: theme.danger, action: () => {} },
          ].map((item, i) => (
            <button
              key={i}
              onClick={item.action}
              style={{
                display: 'flex',
                flexDirection: 'column',
                alignItems: 'center',
                gap: '8px',
                padding: '16px 8px',
                background: highContrastMode ? '#000' : '#f8fafc',
                border: `2px solid ${item.color}`,
                borderRadius: '12px',
                cursor: 'pointer',
                minHeight: '80px',
              }}
            >
              <item.icon size={28} color={item.color} />
              <span style={{
                fontSize: '13px',
                fontWeight: 600,
                color: theme.text,
              }}>
                {item.label}
              </span>
            </button>
          ))}
        </div>
      </header>

      {/* 今日任务 */}
      <section style={{
        padding: '0 16px',
        marginBottom: '16px',
      }}>
        <div style={{
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          marginBottom: '12px',
        }}>
          <h2 style={{
            fontSize: '18px',
            fontWeight: 700,
            color: theme.text,
            margin: 0,
          }}>
            今日任务 ({state.todayTasks.filter(t => !t.completed).length})
          </h2>
          <button
            onClick={() => { setActiveModule('ground'); setCurrentView('module'); }}
            style={{
              fontSize: '14px',
              color: theme.primary,
              background: 'none',
              border: 'none',
              cursor: 'pointer',
            }}
          >
            查看全部 →
          </button>
        </div>

        <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
          {state.todayTasks.filter(t => !t.completed).slice(0, 3).map((task) => (
            <div
              key={task.id}
              style={{
                padding: '16px',
                background: theme.card,
                borderRadius: '12px',
                borderLeft: `4px solid ${
                  task.priority === 'high' ? theme.danger :
                  task.priority === 'medium' ? theme.warning : theme.success
                }`,
              }}
            >
              <div style={{
                display: 'flex',
                justifyContent: 'space-between',
                alignItems: 'flex-start',
                marginBottom: '12px',
              }}>
                <div>
                  <h3 style={{
                    fontSize: '16px',
                    fontWeight: 600,
                    color: theme.text,
                    margin: '0 0 4px 0',
                  }}>
                    {task.title}
                  </h3>
                  <p style={{
                    fontSize: '14px',
                    color: theme.textSecondary,
                    margin: 0,
                  }}>
                    {task.customerName} · {task.deadline}
                  </p>
                </div>
                <span style={{
                  padding: '4px 8px',
                  background: task.priority === 'high' ? `${theme.danger}20` : `${theme.warning}20`,
                  color: task.priority === 'high' ? theme.danger : theme.warning,
                  borderRadius: '4px',
                  fontSize: '12px',
                  fontWeight: 600,
                }}>
                  {task.priority === 'high' ? '紧急' : '普通'}
                </span>
              </div>

              <div style={{
                display: 'flex',
                gap: '8px',
              }}>
                {task.customerPhone && (
                  <button
                    onClick={() => makeCall(task.customerPhone)}
                    style={{
                      flex: 1,
                      padding: '12px',
                      background: theme.success,
                      color: '#fff',
                      border: 'none',
                      borderRadius: '8px',
                      fontSize: '15px',
                      fontWeight: 600,
                      cursor: 'pointer',
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'center',
                      gap: '6px',
                      minHeight: '48px',
                    }}
                  >
                    <Phone size={18} />
                    一键外呼
                  </button>
                )}
                {task.lat && task.lng && (
                  <button
                    onClick={() => openNavigation(task.lat!, task.lng!, task.address)}
                    style={{
                      flex: 1,
                      padding: '12px',
                      background: theme.primary,
                      color: highContrastMode ? '#000' : '#fff',
                      border: 'none',
                      borderRadius: '8px',
                      fontSize: '15px',
                      fontWeight: 600,
                      cursor: 'pointer',
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'center',
                      gap: '6px',
                      minHeight: '48px',
                    }}
                  >
                    <Navigation size={18} />
                    导航
                  </button>
                )}
                <button
                  onClick={() => completeTask(task.id)}
                  style={{
                    padding: '12px 16px',
                    background: 'transparent',
                    color: theme.success,
                    border: `2px solid ${theme.success}`,
                    borderRadius: '8px',
                    fontSize: '15px',
                    fontWeight: 600,
                    cursor: 'pointer',
                    minHeight: '48px',
                  }}
                >
                  <CheckCircle2 size={20} />
                </button>
              </div>
            </div>
          ))}
        </div>
      </section>

      {/* 附近高意向线索 */}
      <section style={{ padding: '0 16px' }}>
        <div style={{
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          marginBottom: '12px',
        }}>
          <h2 style={{
            fontSize: '18px',
            fontWeight: 700,
            color: theme.text,
            margin: 0,
          }}>
            附近高意向线索
          </h2>
          <button
            onClick={() => state.location && loadSalesLeads(state.location.lat, state.location.lng)}
            style={{
              fontSize: '14px',
              color: theme.primary,
              background: 'none',
              border: 'none',
              cursor: 'pointer',
            }}
          >
            {state.isLoading ? '扫描中...' : '刷新'}
          </button>
        </div>

        {state.salesLeads.filter(l => l.heatScore >= 80).slice(0, 3).map((lead) => (
          <div
            key={lead.id}
            style={{
              padding: '16px',
              background: theme.card,
              borderRadius: '12px',
              marginBottom: '12px',
            }}
          >
            <div style={{
              display: 'flex',
              justifyContent: 'space-between',
              alignItems: 'center',
              marginBottom: '8px',
            }}>
              <h3 style={{
                fontSize: '16px',
                fontWeight: 600,
                color: theme.text,
                margin: 0,
              }}>
                {lead.name}
              </h3>
              <span style={{
                padding: '4px 8px',
                background: `${theme.danger}20`,
                color: theme.danger,
                borderRadius: '4px',
                fontSize: '12px',
                fontWeight: 600,
              }}>
                热度 {lead.heatScore}
              </span>
            </div>
            <p style={{
              fontSize: '14px',
              color: theme.textSecondary,
              margin: '0 0 12px 0',
            }}>
              {lead.address} · {Math.round(lead.distance)}m
            </p>
            {lead.suggestedScript && (
              <div style={{
                padding: '12px',
                background: highContrastMode ? '#000' : '#f0f7ff',
                borderRadius: '8px',
                marginBottom: '12px',
              }}>
                <p style={{
                  fontSize: '13px',
                  color: theme.textSecondary,
                  margin: '0 0 4px 0',
                }}>
                  💡 推荐话术
                </p>
                <p style={{
                  fontSize: '14px',
                  color: theme.text,
                  margin: 0,
                }}>
                  {lead.suggestedScript}
                </p>
              </div>
            )}
            <div style={{ display: 'flex', gap: '8px' }}>
              {lead.phone && (
                <button
                  onClick={() => makeCall(lead.phone!)}
                  style={{
                    flex: 1,
                    padding: '14px',
                    background: theme.success,
                    color: '#fff',
                    border: 'none',
                    borderRadius: '8px',
                    fontSize: '15px',
                    fontWeight: 600,
                    cursor: 'pointer',
                    minHeight: '52px',
                  }}
                >
                  立即联系
                </button>
              )}
              <button
                onClick={() => openNavigation(lead.lat, lead.lng, lead.address)}
                style={{
                  flex: 1,
                    padding: '14px',
                    background: theme.primary,
                    color: highContrastMode ? '#000' : '#fff',
                    border: 'none',
                    borderRadius: '8px',
                    fontSize: '15px',
                    fontWeight: 600,
                    cursor: 'pointer',
                    minHeight: '52px',
                }}
              >
                导航到店
              </button>
            </div>
          </div>
        ))}
      </section>

      {/* 底部导航 */}
      <nav style={{
        position: 'fixed',
        bottom: 0,
        left: 0,
        right: 0,
        background: theme.card,
        borderTop: `1px solid ${highContrastMode ? '#333' : '#e2e8f0'}`,
        display: 'flex',
        justifyContent: 'space-around',
        padding: '8px 0',
        zIndex: 100,
      }}>
        {[
          { id: 'home', icon: Target, label: '工作台' },
          { id: 'acquisition', icon: ScanLine, label: '获客' },
          { id: 'customer', icon: Users, label: '客户' },
          { id: 'ground', icon: Map, label: '地推' },
          { id: 'platform', icon: TrendingUp, label: '数据' },
        ].map((item) => (
          <button
            key={item.id}
            onClick={() => {
              if (item.id === 'home') {
                setCurrentView('home');
                setActiveModule(null);
              } else {
                setActiveModule(item.id as ModuleType);
                setCurrentView('module');
              }
            }}
            style={{
              display: 'flex',
              flexDirection: 'column',
              alignItems: 'center',
              gap: '4px',
              padding: '8px 16px',
              background: 'none',
              border: 'none',
              cursor: 'pointer',
              minWidth: '64px',
            }}
          >
            <item.icon
              size={24}
              color={activeModule === item.id || (item.id === 'home' && currentView === 'home')
                ? theme.primary
                : theme.textSecondary
              }
            />
            <span style={{
              fontSize: '12px',
              color: activeModule === item.id || (item.id === 'home' && currentView === 'home')
                ? theme.primary
                : theme.textSecondary,
              fontWeight: 600,
            }}>
              {item.label}
            </span>
          </button>
        ))}
      </nav>
    </div>
  );

  // 渲染地图视图
  const renderMapView = () => (
    <div style={{
      height: '100vh',
      background: theme.bg,
      display: 'flex',
      flexDirection: 'column',
    }}>
      {renderStatusBar()}
      
      {/* 地图头部 */}
      <div style={{
        padding: '16px',
        background: theme.card,
        display: 'flex',
        alignItems: 'center',
        gap: '12px',
      }}>
        <button
          onClick={() => setCurrentView('home')}
          style={{
            width: '44px',
            height: '44px',
            borderRadius: '50%',
            background: highContrastMode ? '#000' : '#f1f5f9',
            border: 'none',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            cursor: 'pointer',
          }}
        >
          <ChevronLeft size={24} color={theme.text} />
        </button>
        <h1 style={{
          fontSize: '20px',
          fontWeight: 700,
          color: theme.text,
          margin: 0,
          flex: 1,
        }}>
          附近线索地图
        </h1>
        <button
          onClick={() => state.location && loadSalesLeads(state.location.lat, state.location.lng)}
          style={{
            padding: '10px 16px',
            background: theme.primary,
            color: highContrastMode ? '#000' : '#fff',
            border: 'none',
            borderRadius: '8px',
            fontSize: '14px',
            fontWeight: 600,
            cursor: 'pointer',
            minHeight: '44px',
          }}
        >
          {state.isLoading ? '扫描中...' : '重新扫描'}
        </button>
      </div>

      {/* 真实地图嵌入 */}
      <div style={{
        flex: 1,
        position: 'relative',
      }}>
        <iframe
          src={`https://www.openstreetmap.org/export/embed.html?bbox=${state.location ? state.location.lng - 0.02 : 116.44},${state.location ? state.location.lat - 0.02 : 39.88},${state.location ? state.location.lng + 0.02 : 116.48},${state.location ? state.location.lat + 0.02 : 39.92}&layer=map`}
          width="100%"
          height="100%"
          style={{
            border: 'none',
            filter: highContrastMode ? 'grayscale(100%) contrast(120%)' : 'none',
          }}
        />

        {/* 线索标记覆盖层 */}
        <div style={{
          position: 'absolute',
          bottom: '20px',
          left: '16px',
          right: '16px',
          background: theme.card,
          borderRadius: '12px',
          padding: '16px',
          maxHeight: '200px',
          overflow: 'auto',
        }}>
          <h3 style={{
            fontSize: '16px',
            fontWeight: 600,
            color: theme.text,
            margin: '0 0 12px 0',
          }}>
            附近线索 ({state.salesLeads.length})
          </h3>
          {state.salesLeads.slice(0, 5).map((lead) => (
            <div
              key={lead.id}
              style={{
                padding: '12px',
                background: highContrastMode ? '#000' : '#f8fafc',
                borderRadius: '8px',
                marginBottom: '8px',
                display: 'flex',
                justifyContent: 'space-between',
                alignItems: 'center',
              }}
            >
              <div>
                <p style={{
                  fontSize: '14px',
                  fontWeight: 600,
                  color: theme.text,
                  margin: '0 0 4px 0',
                }}>
                  {lead.name}
                </p>
                <p style={{
                  fontSize: '12px',
                  color: theme.textSecondary,
                  margin: 0,
                }}>
                  {Math.round(lead.distance)}m · 热度{lead.heatScore}
                </p>
              </div>
              <button
                onClick={() => openNavigation(lead.lat, lead.lng, lead.address)}
                style={{
                  padding: '10px 16px',
                  background: theme.primary,
                  color: highContrastMode ? '#000' : '#fff',
                  border: 'none',
                  borderRadius: '6px',
                  fontSize: '13px',
                  fontWeight: 600,
                  cursor: 'pointer',
                  minHeight: '40px',
                }}
              >
                导航
              </button>
            </div>
          ))}
        </div>
      </div>
    </div>
  );

  // 渲染模块详情
  const renderModuleDetail = () => {
    const moduleNames: Record<string, string> = {
      acquisition: '智能获客中枢',
      customer: '客户资产库',
      ground: '地推作战系统',
      platform: '品牌数据中台',
    };

    return (
      <div style={{
        minHeight: '100vh',
        background: theme.bg,
        paddingBottom: '80px',
      }}>
        {renderStatusBar()}
        
        {/* 头部 */}
        <header style={{
          padding: '16px',
          background: theme.card,
          display: 'flex',
          alignItems: 'center',
          gap: '12px',
          marginBottom: '16px',
        }}>
          <button
            onClick={() => setCurrentView('home')}
            style={{
              width: '48px',
              height: '48px',
              borderRadius: '50%',
              background: highContrastMode ? '#000' : '#f1f5f9',
              border: 'none',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              cursor: 'pointer',
            }}
          >
            <ChevronLeft size={24} color={theme.text} />
          </button>
          <h1 style={{
            fontSize: '22px',
            fontWeight: 700,
            color: theme.text,
            margin: 0,
          }}>
            {activeModule ? moduleNames[activeModule] : '模块'}
          </h1>
        </header>

        {/* 模块内容占位 */}
        <div style={{
          padding: '0 16px',
          textAlign: 'center',
          paddingTop: '100px',
        }}>
          <p style={{ color: theme.textSecondary, fontSize: '16px' }}>
            模块功能开发中...
          </p>
          <button
            onClick={() => setCurrentView('home')}
            style={{
              marginTop: '20px',
              padding: '14px 32px',
              background: theme.primary,
              color: highContrastMode ? '#000' : '#fff',
              border: 'none',
              borderRadius: '8px',
              fontSize: '16px',
              fontWeight: 600,
              cursor: 'pointer',
              minHeight: '52px',
            }}
          >
            返回工作台
          </button>
        </div>
      </div>
    );
  };

  return (
    <div style={{ fontFamily: '-apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif' }}>
      {currentView === 'home' && renderHome()}
      {currentView === 'map-view' && renderMapView()}
      {currentView === 'module' && renderModuleDetail()}
    </div>
  );
};

export default App;
