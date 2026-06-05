/**
 * 分辨率适配服务
 * 支持多设备、多分辨率、多像素密度
 */

/* -------------------------------------------------------------------------- */
/*  类型定义                                                                    */
/* -------------------------------------------------------------------------- */

export type DeviceType = 'phone' | 'tablet' | 'desktop' | 'tv';
export type Orientation = 'portrait' | 'landscape';
export type PixelDensity = 'ldpi' | 'mdpi' | 'hdpi' | 'xhdpi' | 'xxhdpi' | 'xxxhdpi';

export interface ScreenInfo {
  width: number;
  height: number;
  devicePixelRatio: number;
  deviceType: DeviceType;
  orientation: Orientation;
  density: PixelDensity;
  isNotch: boolean;
  isFoldable: boolean;
  safeArea: {
    top: number;
    bottom: number;
    left: number;
    right: number;
  };
}

export interface AdaptiveValue<T> {
  phone: T;
  tablet?: T;
  desktop?: T;
}

export interface Breakpoints {
  xs: number; // 0-576px
  sm: number; // 576-768px
  md: number; // 768-992px
  lg: number; // 992-1200px
  xl: number; // 1200-1400px
  xxl: number; // 1400px+
}

/* -------------------------------------------------------------------------- */
/*  设备配置                                                                    */
/* -------------------------------------------------------------------------- */

const DEVICE_BREAKPOINTS: Breakpoints = {
  xs: 576,
  sm: 768,
  md: 992,
  lg: 1200,
  xl: 1400,
  xxl: 1920,
};

const DENSITY_THRESHOLDS: Record<PixelDensity, number> = {
  ldpi: 0.75,
  mdpi: 1,
  hdpi: 1.5,
  xhdpi: 2,
  xxhdpi: 3,
  xxxhdpi: 4,
};

// 常见设备分辨率配置
const DEVICE_PRESETS: Record<string, Partial<ScreenInfo>> = {
  // iPhone
  'iphone-se': { width: 375, height: 667, devicePixelRatio: 2 },
  'iphone-12': { width: 390, height: 844, devicePixelRatio: 3, isNotch: true },
  'iphone-14-pro': { width: 393, height: 852, devicePixelRatio: 3, isNotch: true },
  'iphone-14-pro-max': { width: 430, height: 932, devicePixelRatio: 3, isNotch: true },
  
  // Android
  'android-standard': { width: 360, height: 640, devicePixelRatio: 2 },
  'android-large': { width: 412, height: 915, devicePixelRatio: 2.625 },
  'samsung-s24': { width: 412, height: 915, devicePixelRatio: 3 },
  
  // 折叠屏
  'fold-closed': { width: 376, height: 824, devicePixelRatio: 3, isFoldable: true },
  'fold-open': { width: 738, height: 824, devicePixelRatio: 3, isFoldable: true },
  
  // 平板
  'ipad-mini': { width: 744, height: 1133, devicePixelRatio: 2 },
  'ipad-pro-11': { width: 834, height: 1194, devicePixelRatio: 2 },
  'ipad-pro-12': { width: 1024, height: 1366, devicePixelRatio: 2 },
  'android-tablet': { width: 800, height: 1280, devicePixelRatio: 1.5 },
  
  // 桌面
  'desktop-1080': { width: 1920, height: 1080, devicePixelRatio: 1 },
  'desktop-1440': { width: 2560, height: 1440, devicePixelRatio: 1 },
  'desktop-4k': { width: 3840, height: 2160, devicePixelRatio: 1 },
};

/* -------------------------------------------------------------------------- */
/*  分辨率适配服务                                                               */
/* -------------------------------------------------------------------------- */

class ResolutionAdapter {
  private screenInfo: ScreenInfo;
  private listeners: Array<(info: ScreenInfo) => void> = [];

  constructor() {
    this.screenInfo = this.detectScreen();
    this.initResizeListener();
  }

  /**
   * 检测屏幕信息
   */
  private detectScreen(): ScreenInfo {
    // 默认值
    let width = 375;
    let height = 667;
    let dpr = 2;

    // 浏览器环境
    if (typeof window !== 'undefined') {
      width = window.innerWidth;
      height = window.innerHeight;
      dpr = window.devicePixelRatio || 1;
    }

    // 判断设备类型
    const deviceType = this.getDeviceType(width, height);
    
    // 判断像素密度
    const density = this.getPixelDensity(dpr);
    
    // 判断方向
    const orientation: Orientation = width > height ? 'landscape' : 'portrait';
    
    // 判断刘海屏
    const isNotch = this.checkNotch();
    
    // 判断折叠屏
    const isFoldable = this.checkFoldable(width, height);
    
    // 安全区域
    const safeArea = this.getSafeArea();

    return {
      width,
      height,
      devicePixelRatio: dpr,
      deviceType,
      orientation,
      density,
      isNotch,
      isFoldable,
      safeArea,
    };
  }

  /**
   * 初始化尺寸监听
   */
  private initResizeListener(): void {
    if (typeof window === 'undefined') return;

    window.addEventListener('resize', () => {
      this.screenInfo = this.detectScreen();
      this.notifyListeners();
    });

    // 监听方向变化
    if ('orientation' in screen) {
      screen.orientation.addEventListener('change', () => {
        this.screenInfo = this.detectScreen();
        this.notifyListeners();
      });
    }
  }

  /**
   * 获取设备类型
   */
  private getDeviceType(width: number, height: number): DeviceType {
    const minDimension = Math.min(width, height);
    const maxDimension = Math.max(width, height);

    if (maxDimension >= 1200) return 'desktop';
    if (minDimension >= 600) return 'tablet';
    if (maxDimension >= 800) return 'tv';
    return 'phone';
  }

  /**
   * 获取像素密度
   */
  private getPixelDensity(dpr: number): PixelDensity {
    if (dpr >= 3.5) return 'xxxhdpi';
    if (dpr >= 2.5) return 'xxhdpi';
    if (dpr >= 1.5) return 'xhdpi';
    if (dpr >= 1.3) return 'hdpi';
    if (dpr >= 0.9) return 'mdpi';
    return 'ldpi';
  }

  /**
   * 检查刘海屏
   */
  private checkNotch(): boolean {
    if (typeof window === 'undefined') return false;
    
    // iOS 安全区域检测
    const isIPhoneX = window.screen.height >= 812;
    return isIPhoneX;
  }

  /**
   * 检查折叠屏
   */
  private checkFoldable(width: number, height: number): boolean {
    // 折叠屏展开时宽高比接近 1:1
    const ratio = Math.max(width, height) / Math.min(width, height);
    return ratio < 1.5 && Math.min(width, height) > 600;
  }

  /**
   * 获取安全区域
   */
  private getSafeArea(): ScreenInfo['safeArea'] {
    if (typeof window === 'undefined') {
      return { top: 0, bottom: 0, left: 0, right: 0 };
    }

    // 尝试获取 CSS 环境变量
    const getEnv = (varName: string): number => {
      const value = getComputedStyle(document.documentElement).getPropertyValue(varName);
      return parseFloat(value) || 0;
    };

    return {
      top: getEnv('env(safe-area-inset-top)') || (this.checkNotch() ? 44 : 20),
      bottom: getEnv('env(safe-area-inset-bottom)') || (this.checkNotch() ? 34 : 0),
      left: getEnv('env(safe-area-inset-left)'),
      right: getEnv('env(safe-area-inset-right)'),
    };
  }

  /**
   * 获取屏幕信息
   */
  getScreenInfo(): ScreenInfo {
    return { ...this.screenInfo };
  }

  /**
   * 获取当前断点
   */
  getBreakpoint(): keyof Breakpoints {
    const { width } = this.screenInfo;
    
    if (width < DEVICE_BREAKPOINTS.xs) return 'xs';
    if (width < DEVICE_BREAKPOINTS.sm) return 'sm';
    if (width < DEVICE_BREAKPOINTS.md) return 'md';
    if (width < DEVICE_BREAKPOINTS.lg) return 'lg';
    if (width < DEVICE_BREAKPOINTS.xl) return 'xl';
    return 'xxl';
  }

  /**
   * 自适应值
   */
  adaptive<T>(values: AdaptiveValue<T>): T {
    const { deviceType } = this.screenInfo;
    
    if (deviceType === 'desktop' && values.desktop) return values.desktop;
    if (deviceType === 'tablet' && values.tablet) return values.tablet;
    return values.phone;
  }

  /**
   * 计算缩放尺寸
   */
  scale(baseSize: number, options: { min?: number; max?: number } = {}): number {
    const { width, devicePixelRatio } = this.screenInfo;
    const baseWidth = 375; // iPhone 6/7/8 基准宽度
    
    let scaled = baseSize * (width / baseWidth);
    
    // 考虑像素密度
    if (devicePixelRatio > 2) {
      scaled *= 0.9; // 高密度设备适当缩小
    }
    
    // 应用限制
    if (options.min !== undefined) scaled = Math.max(scaled, options.min);
    if (options.max !== undefined) scaled = Math.min(scaled, options.max);
    
    return Math.round(scaled);
  }

  /**
   * 计算字体大小
   */
  fontSize(baseSize: number): number {
    const { density } = this.screenInfo;
    
    const densityScale: Record<PixelDensity, number> = {
      ldpi: 0.85,
      mdpi: 1,
      hdpi: 1.1,
      xhdpi: 1.15,
      xxhdpi: 1.2,
      xxxhdpi: 1.25,
    };
    
    return Math.round(baseSize * densityScale[density]);
  }

  /**
   * 获取图片尺寸
   */
  getImageSize(originalWidth: number, originalHeight: number): { width: number; height: number } {
    const { width: screenWidth, height: screenHeight } = this.screenInfo;
    
    // 最大不超过屏幕的 90%
    const maxWidth = screenWidth * 0.9;
    const maxHeight = screenHeight * 0.9;
    
    let width = originalWidth;
    let height = originalHeight;
    
    // 等比缩放
    if (width > maxWidth) {
      height = height * (maxWidth / width);
      width = maxWidth;
    }
    
    if (height > maxHeight) {
      width = width * (maxHeight / height);
      height = maxHeight;
    }
    
    return { width: Math.round(width), height: Math.round(height) };
  }

  /**
   * 是否匹配断点
   */
  isBreakpoint(breakpoint: keyof Breakpoints): boolean {
    return this.getBreakpoint() === breakpoint;
  }

  /**
   * 是否移动设备
   */
  isMobile(): boolean {
    return this.screenInfo.deviceType === 'phone';
  }

  /**
   * 是否平板
   */
  isTablet(): boolean {
    return this.screenInfo.deviceType === 'tablet';
  }

  /**
   * 是否桌面
   */
  isDesktop(): boolean {
    return this.screenInfo.deviceType === 'desktop';
  }

  /**
   * 获取设备预设
   */
  getDevicePresets(): Record<string, Partial<ScreenInfo>> {
    return { ...DEVICE_PRESETS };
  }

  /**
   * 模拟设备
   */
  simulateDevice(preset: string): void {
    const device = DEVICE_PRESETS[preset];
    if (!device) {
      console.warn(`[ResolutionAdapter] 未知设备预设: ${preset}`);
      return;
    }

    this.screenInfo = {
      ...this.screenInfo,
      ...device,
      deviceType: device.deviceType || this.getDeviceType(device.width || 375, device.height || 667),
      orientation: (device.width || 0) > (device.height || 0) ? 'landscape' : 'portrait',
      density: device.devicePixelRatio ? this.getPixelDensity(device.devicePixelRatio) : 'mdpi',
    };

    this.notifyListeners();
    console.log(`[ResolutionAdapter] 模拟设备: ${preset}`, this.screenInfo);
  }

  /**
   * 监听屏幕变化
   */
  onScreenChange(callback: (info: ScreenInfo) => void): () => void {
    this.listeners.push(callback);
    return () => {
      const index = this.listeners.indexOf(callback);
      if (index > -1) this.listeners.splice(index, 1);
    };
  }

  /**
   * 通知监听器
   */
  private notifyListeners(): void {
    this.listeners.forEach(cb => cb(this.screenInfo));
  }
}

export const resolutionAdapter = new ResolutionAdapter();
export default resolutionAdapter;
