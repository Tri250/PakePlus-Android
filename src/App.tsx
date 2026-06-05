import { useAppStore } from './store/appStore';
import StatusBar from './components/StatusBar';
import BottomTabBar from './components/BottomTabBar';
import OfflineBar from './components/OfflineBar';
import Toasts from './components/Toasts';
import HomePage from './pages/HomePage';
import RadarPage from './pages/RadarPage';
import CustomersPage from './pages/CustomersPage';
import TasksPage from './pages/TasksPage';
import DataPage from './pages/DataPage';
import CustomerDetail from './pages/CustomerDetail';
import TaskDetail from './pages/TaskDetail';
import NotificationsPanel from './pages/NotificationsPanel';
import RoleSwitcher from './pages/RoleSwitcher';
import AddCustomerSheet from './pages/AddCustomerSheet';
import SOSPanel from './pages/SOSPanel';
import SettingsSheet from './pages/SettingsSheet';
import { useEffect } from 'react';

export default function App() {
  const activeTab = useAppStore((s) => s.activeTab);
  const outdoorMode = useAppStore((s) => s.outdoorMode);
  const selectedCustomerId = useAppStore((s) => s.selectedCustomerId);
  const selectedTaskId = useAppStore((s) => s.selectedTaskId);
  const showRadar = useAppStore((s) => s.showRadar);

  useEffect(() => {
    document.body.style.overflow = 'hidden';
    return () => {
      document.body.style.overflow = '';
    };
  }, []);

  return (
    <div className={`app-frame ${outdoorMode ? 'outdoor' : ''}`}>
      <StatusBar />
      <OfflineBar />

      <div className="flex-1 relative overflow-hidden">
        {/* Tab 页面映射:
            home (获客) → HomePage 首页仪表盘
            customers (客户) → CustomersPage 客户资产列表
            tasks (地推) → TasksPage 任务列表
            data (数据) → DataPage 数据中台
            RadarPage 获客雷达作为可从 HomePage 打开的全屏视图（通过 showRadar 控制）
        */}
        <div className="absolute inset-0">
          {activeTab === 'home' && <HomePage />}
          {activeTab === 'customers' && <CustomersPage />}
          {activeTab === 'tasks' && <TasksPage />}
          {activeTab === 'data' && <DataPage />}
        </div>
      </div>

      <BottomTabBar />

      {/* 全局浮层 */}
      {showRadar && <RadarPage />}
      {selectedCustomerId && <CustomerDetail />}
      {selectedTaskId && <TaskDetail />}
      <NotificationsPanel />
      <RoleSwitcher />
      <AddCustomerSheet />
      <SOSPanel />
      <SettingsSheet />
      <Toasts />
    </div>
  );
}
