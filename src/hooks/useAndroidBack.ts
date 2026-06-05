import { useEffect } from 'react';
import { useAppStore } from '../store/appStore';

/**
 * Android 物理返回键 / WebView 返回手势处理
 * 优先级: 详情 > 弹层 > 全屏视图 > 默认
 */
export function useAndroidBack() {
  const showRadar = useAppStore((s) => s.showRadar);
  const setShowRadar = useAppStore((s) => s.setShowRadar);
  const selectedCustomerId = useAppStore((s) => s.selectedCustomerId);
  const setSelectedCustomer = useAppStore((s) => s.setSelectedCustomer);
  const selectedTaskId = useAppStore((s) => s.selectedTaskId);
  const setSelectedTask = useAppStore((s) => s.setSelectedTask);
  const showNotifications = useAppStore((s) => s.showNotifications);
  const setShowNotifications = useAppStore((s) => s.setShowNotifications);
  const showRoleSwitcher = useAppStore((s) => s.showRoleSwitcher);
  const setShowRoleSwitcher = useAppStore((s) => s.setShowRoleSwitcher);
  const showAddCustomer = useAppStore((s) => s.showAddCustomer);
  const setShowAddCustomer = useAppStore((s) => s.setShowAddCustomer);
  const showSOS = useAppStore((s) => s.showSOS);
  const setShowSOS = useAppStore((s) => s.setShowSOS);
  const showSettings = useAppStore((s) => s.showSettings);
  const setShowSettings = useAppStore((s) => s.setShowSettings);
  const showAllFeatures = useAppStore((s) => s.showAllFeatures);
  const setShowAllFeatures = useAppStore((s) => s.setShowAllFeatures);

  useEffect(() => {
    // PakePlus / WebView 暴露的 back 事件
    const onBack = (e?: Event) => {
      e?.preventDefault?.();

      // 优先级: 详情 → 弹层 → 全屏
      if (selectedCustomerId) {
        setSelectedCustomer(null);
        return;
      }
      if (selectedTaskId) {
        setSelectedTask(null);
        return;
      }
      if (showRadar) {
        setShowRadar(false);
        return;
      }
      if (showNotifications) {
        setShowNotifications(false);
        return;
      }
      if (showRoleSwitcher) {
        setShowRoleSwitcher(false);
        return;
      }
      if (showAddCustomer) {
        setShowAddCustomer(false);
        return;
      }
      if (showSOS) {
        setShowSOS(false);
        return;
      }
      if (showSettings) {
        setShowSettings(false);
        return;
      }
      if (showAllFeatures) {
        setShowAllFeatures(false);
        return;
      }
      // 默认: 退出应用
      // PakePlus 桥接的退出事件
      const w = window as unknown as { PakePlus?: { exit?: () => void } };
      if (w.PakePlus?.exit) {
        w.PakePlus.exit();
      } else {
        history.back();
      }
    };

    // 监听浏览器/PakePlus 的 popstate 与自定义事件
    window.addEventListener('popstate', onBack);
    document.addEventListener('pakeplus-back', onBack as EventListener);
    return () => {
      window.removeEventListener('popstate', onBack);
      document.removeEventListener('pakeplus-back', onBack as EventListener);
    };
  }, [
    showRadar, setShowRadar,
    selectedCustomerId, setSelectedCustomer,
    selectedTaskId, setSelectedTask,
    showNotifications, setShowNotifications,
    showRoleSwitcher, setShowRoleSwitcher,
    showAddCustomer, setShowAddCustomer,
    showSOS, setShowSOS,
    showSettings, setShowSettings,
    showAllFeatures, setShowAllFeatures,
  ]);
}

/**
 * 触觉反馈 (Android 振动)
 */
export function haptic(style: 'light' | 'medium' | 'heavy' = 'light') {
  // 优先使用 Android WebView 桥接
  const w = window as unknown as {
    PakePlus?: { vibrate?: (ms: number) => void };
    navigator?: { vibrate?: (pattern: number | number[]) => boolean };
  };

  if (w.PakePlus?.vibrate) {
    const ms = style === 'light' ? 10 : style === 'medium' ? 20 : 40;
    w.PakePlus.vibrate(ms);
    return;
  }
  if (typeof w.navigator?.vibrate === 'function') {
    w.navigator.vibrate(style === 'light' ? 10 : style === 'medium' ? 20 : 40);
  }
}

/**
 * 截图反馈 (轻震动)
 */
export function hapticClick() {
  haptic('light');
}

/**
 * 成功反馈
 */
export function hapticSuccess() {
  haptic('medium');
}

/**
 * 警告反馈
 */
export function hapticWarning() {
  haptic('heavy');
}
