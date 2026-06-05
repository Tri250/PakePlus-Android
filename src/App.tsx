import { useAppStore } from './store/appStore';
import { useAndroidBack } from './hooks/useAndroidBack';
import StatusBar from './components/StatusBar';
import BottomTabBar from './components/BottomTabBar';
import OfflineBar from './components/OfflineBar';
import Toasts from './components/Toasts';
import HomePage from './pages/HomePage';
import RadarPage from './pages/RadarPage';
import CustomersPage from './pages/CustomersPage';
import TasksPage from './pages/TasksPage';
import StorePage from './pages/StorePage';
import DataPage from './pages/DataPage';
import CustomerDetail from './pages/CustomerDetail';
import TaskDetail from './pages/TaskDetail';
import NotificationsPanel from './pages/NotificationsPanel';
import RoleSwitcher from './pages/RoleSwitcher';
import AddCustomerSheet from './pages/AddCustomerSheet';
import SOSPanel from './pages/SOSPanel';
import SettingsSheet from './pages/SettingsSheet';
import AllFeaturesSheet from './pages/AllFeaturesSheet';
import { useEffect } from 'react';

export default function App() {
  const activeTab = useAppStore((s) => s.activeTab);
  const outdoorMode = useAppStore((s) => s.outdoorMode);
  const selectedCustomerId = useAppStore((s) => s.selectedCustomerId);
  const selectedTaskId = useAppStore((s) => s.selectedTaskId);
  const showRadar = useAppStore((s) => s.showRadar);

  // Android 物理返回键 + WebView 返回手势
  useAndroidBack();

  useEffect(() => {
    // Android WebView / 浏览器 body 锁定
    document.body.style.overflow = 'hidden';
    // 禁止双指缩放
    document.addEventListener('gesturestart', (e) => e.preventDefault());
    return () => {
      document.body.style.overflow = '';
      document.removeEventListener('gesturestart', (e) => e.preventDefault());
    };
  }, []);

  return (
    <div className={`app-frame ${outdoorMode ? 'outdoor' : ''}`}>
      <StatusBar />
      <OfflineBar />

      <div className="flex-1 relative overflow-hidden">
        <div className="absolute inset-0">
          {activeTab === 'home' && <HomePage />}
          {activeTab === 'customers' && <CustomersPage />}
          {activeTab === 'tasks' && <TasksPage />}
          {activeTab === 'store' && <StorePage />}
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
      <AllFeaturesSheet />
      <Toasts />
    </div>
  );
}
