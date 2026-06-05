/**
 * UI/UX 统一组件服务
 * 卡片样式、动画配置、主题管理、显示统一
 */

/* -------------------------------------------------------------------------- */
/*  类型定义                                                                    */
/* -------------------------------------------------------------------------- */

export type ThemeMode = 'light' | 'dark' | 'auto';
export type CardStyle = 'elevated' | 'outlined' | 'filled';
export type AnimationType = 'fade' | 'slide' | 'scale' | 'bounce' | 'flip' | 'none';
export type AnimationDuration = 'fast' | 'normal' | 'slow';

export interface ThemeConfig {
  mode: ThemeMode;
  primary: string;
  secondary: string;
  background: string;
  surface: string;
  text: {
    primary: string;
    secondary: string;
    disabled: string;
  };
  border: string;
  shadow: string;
}

export interface CardConfig {
  style: CardStyle;
  padding: number;
  borderRadius: number;
  shadow: boolean;
  hover: boolean;
  animation: AnimationType;
}

export interface AnimationConfig {
  type: AnimationType;
  duration: AnimationDuration;
  delay: number;
  easing: string;
  repeat: number;
}

export interface ComponentStyle {
  id: string;
  name: string;
  base: Record<string, any>;
  variants: Record<string, Record<string, any>>;
  states: {
    hover?: Record<string, any>;
    active?: Record<string, any>;
    disabled?: Record<string, any>;
    focus?: Record<string, any>;
  };
}

/* -------------------------------------------------------------------------- */
/*  主题配置                                                                    */
/* -------------------------------------------------------------------------- */

const THEMES: Record<ThemeMode, ThemeConfig> = {
  light: {
    mode: 'light',
    primary: '#3b82f6',
    secondary: '#10b981',
    background: '#ffffff',
    surface: '#f8fafc',
    text: {
      primary: '#1e293b',
      secondary: '#64748b',
      disabled: '#94a3b8',
    },
    border: '#e2e8f0',
    shadow: 'rgba(0, 0, 0, 0.1)',
  },
  dark: {
    mode: 'dark',
    primary: '#60a5fa',
    secondary: '#34d399',
    background: '#0f172a',
    surface: '#1e293b',
    text: {
      primary: '#f1f5f9',
      secondary: '#94a3b8',
      disabled: '#64748b',
    },
    border: '#334155',
    shadow: 'rgba(0, 0, 0, 0.3)',
  },
  auto: {
    mode: 'auto',
    primary: '#3b82f6',
    secondary: '#10b981',
    background: '#ffffff',
    surface: '#f8fafc',
    text: {
      primary: '#1e293b',
      secondary: '#64748b',
      disabled: '#94a3b8',
    },
    border: '#e2e8f0',
    shadow: 'rgba(0, 0, 0, 0.1)',
  },
};

/* -------------------------------------------------------------------------- */
/*  动画配置                                                                    */
/* -------------------------------------------------------------------------- */

const ANIMATION_PRESETS: Record<string, AnimationConfig> = {
  fadeIn: { type: 'fade', duration: 'normal', delay: 0, easing: 'ease-out', repeat: 1 },
  fadeOut: { type: 'fade', duration: 'normal', delay: 0, easing: 'ease-in', repeat: 1 },
  slideIn: { type: 'slide', duration: 'normal', delay: 0, easing: 'ease-out', repeat: 1 },
  slideOut: { type: 'slide', duration: 'normal', delay: 0, easing: 'ease-in', repeat: 1 },
  scaleIn: { type: 'scale', duration: 'fast', delay: 0, easing: 'ease-out', repeat: 1 },
  scaleOut: { type: 'scale', duration: 'fast', delay: 0, easing: 'ease-in', repeat: 1 },
  bounce: { type: 'bounce', duration: 'slow', delay: 0, easing: 'bounce', repeat: 1 },
  flip: { type: 'flip', duration: 'normal', delay: 0, easing: 'ease-in-out', repeat: 1 },
};

const DURATION_VALUES: Record<AnimationDuration, number> = {
  fast: 150,
  normal: 300,
  slow: 500,
};

/* -------------------------------------------------------------------------- */
/*  组件样式配置                                                                  */
/* -------------------------------------------------------------------------- */

const COMPONENT_STYLES: ComponentStyle[] = [
  {
    id: 'card',
    name: '卡片',
    base: {
      padding: 16,
      borderRadius: 12,
      backgroundColor: 'var(--surface)',
    },
    variants: {
      elevated: { shadow: '0 4px 6px -1px var(--shadow)', border: 'none' },
      outlined: { shadow: 'none', border: '1px solid var(--border)' },
      filled: { shadow: 'none', border: 'none', backgroundColor: 'var(--surface)' },
    },
    states: {
      hover: { transform: 'translateY(-2px)', shadow: '0 8px 12px -2px var(--shadow)' },
      active: { transform: 'scale(0.98)' },
      disabled: { opacity: 0.5, pointerEvents: 'none' },
    },
  },
  {
    id: 'button',
    name: '按钮',
    base: {
      padding: '8px 16px',
      borderRadius: 8,
      fontWeight: 500,
      cursor: 'pointer',
    },
    variants: {
      primary: { backgroundColor: 'var(--primary)', color: '#ffffff' },
      secondary: { backgroundColor: 'var(--secondary)', color: '#ffffff' },
      outline: { backgroundColor: 'transparent', border: '1px solid var(--primary)', color: 'var(--primary)' },
      ghost: { backgroundColor: 'transparent', color: 'var(--primary)' },
    },
    states: {
      hover: { opacity: 0.9 },
      active: { transform: 'scale(0.95)' },
      disabled: { opacity: 0.5, cursor: 'not-allowed' },
      focus: { outline: '2px solid var(--primary)', outlineOffset: 2 },
    },
  },
  {
    id: 'input',
    name: '输入框',
    base: {
      padding: '8px 12px',
      borderRadius: 8,
      border: '1px solid var(--border)',
      backgroundColor: 'var(--background)',
    },
    variants: {
      default: {},
      filled: { backgroundColor: 'var(--surface)', border: 'none' },
      underline: { borderRadius: 0, border: 'none', borderBottom: '1px solid var(--border)' },
    },
    states: {
      hover: { borderColor: 'var(--primary)' },
      focus: { borderColor: 'var(--primary)', outline: 'none' },
      disabled: { opacity: 0.5, cursor: 'not-allowed' },
    },
  },
  {
    id: 'chip',
    name: '标签',
    base: {
      padding: '4px 8px',
      borderRadius: 16,
      fontSize: 12,
      fontWeight: 500,
    },
    variants: {
      default: { backgroundColor: 'var(--surface)', color: 'var(--text-secondary)' },
      primary: { backgroundColor: 'var(--primary)', color: '#ffffff' },
      success: { backgroundColor: '#dcfce7', color: '#166534' },
      warning: { backgroundColor: '#fef3c7', color: '#92400e' },
      error: { backgroundColor: '#fee2e2', color: '#991b1b' },
    },
    states: {
      hover: { opacity: 0.9 },
      active: { transform: 'scale(0.95)' },
    },
  },
];

/* -------------------------------------------------------------------------- */
/*  UI/UX 统一服务                                                              */
/* -------------------------------------------------------------------------- */

class UIUXService {
  private currentTheme: ThemeMode = 'light';
  private listeners: Array<(theme: ThemeConfig) => void> = [];

  constructor() {
    // 初始化主题
    this.initTheme();
  }

  /**
   * 初始化主题
   */
  private initTheme(): void {
    // 检查系统主题偏好
    if (typeof window !== 'undefined' && window.matchMedia) {
      const prefersDark = window.matchMedia('(prefers-color-scheme: dark)').matches;
      this.currentTheme = prefersDark ? 'dark' : 'light';

      // 监听系统主题变化
      window.matchMedia('(prefers-color-scheme: dark)').addEventListener('change', (e) => {
        if (this.currentTheme === 'auto') {
          this.setTheme(e.matches ? 'dark' : 'light');
        }
      });
    }
  }

  /**
   * 获取当前主题
   */
  getTheme(): ThemeConfig {
    return THEMES[this.currentTheme];
  }

  /**
   * 获取主题模式
   */
  getThemeMode(): ThemeMode {
    return this.currentTheme;
  }

  /**
   * 设置主题
   */
  setTheme(mode: ThemeMode): void {
    this.currentTheme = mode;
    this.notifyListeners();
    console.log(`[UIUX] 主题切换: ${mode}`);
  }

  /**
   * 获取所有主题
   */
  getAllThemes(): Record<ThemeMode, ThemeConfig> {
    return THEMES;
  }

  /**
   * 获取卡片样式
   */
  getCardStyle(style: CardStyle = 'elevated'): CardConfig {
    const configs: Record<CardStyle, CardConfig> = {
      elevated: {
        style: 'elevated',
        padding: 16,
        borderRadius: 12,
        shadow: true,
        hover: true,
        animation: 'fade',
      },
      outlined: {
        style: 'outlined',
        padding: 16,
        borderRadius: 12,
        shadow: false,
        hover: true,
        animation: 'fade',
      },
      filled: {
        style: 'filled',
        padding: 16,
        borderRadius: 12,
        shadow: false,
        hover: false,
        animation: 'none',
      },
    };

    return configs[style];
  }

  /**
   * 获取动画配置
   */
  getAnimation(preset: string): AnimationConfig {
    return ANIMATION_PRESETS[preset] || ANIMATION_PRESETS.fadeIn;
  }

  /**
   * 获取动画时长（毫秒）
   */
  getAnimationDuration(duration: AnimationDuration): number {
    return DURATION_VALUES[duration];
  }

  /**
   * 获取组件样式
   */
  getComponentStyle(id: string): ComponentStyle | null {
    return COMPONENT_STYLES.find(s => s.id === id) || null;
  }

  /**
   * 获取所有组件样式
   */
  getAllComponentStyles(): ComponentStyle[] {
    return COMPONENT_STYLES;
  }

  /**
   * 生成CSS变量
   */
  generateCSSVariables(): string {
    const theme = this.getTheme();
    const vars: string[] = [];

    vars.push(`--primary: ${theme.primary}`);
    vars.push(`--secondary: ${theme.secondary}`);
    vars.push(`--background: ${theme.background}`);
    vars.push(`--surface: ${theme.surface}`);
    vars.push(`--text-primary: ${theme.text.primary}`);
    vars.push(`--text-secondary: ${theme.text.secondary}`);
    vars.push(`--text-disabled: ${theme.text.disabled}`);
    vars.push(`--border: ${theme.border}`);
    vars.push(`--shadow: ${theme.shadow}`);

    return `:root {\n  ${vars.join(';\n  ')};\n}`;
  }

  /**
   * 生成动画CSS
   */
  generateAnimationCSS(): string {
    const animations: string[] = [];

    // Fade
    animations.push(`
@keyframes fadeIn {
  from { opacity: 0; }
  to { opacity: 1; }
}`);
    animations.push(`
@keyframes fadeOut {
  from { opacity: 1; }
  to { opacity: 0; }
}`);

    // Slide
    animations.push(`
@keyframes slideIn {
  from { transform: translateY(20px); opacity: 0; }
  to { transform: translateY(0); opacity: 1; }
}`);
    animations.push(`
@keyframes slideOut {
  from { transform: translateY(0); opacity: 1; }
  to { transform: translateY(-20px); opacity: 0; }
}`);

    // Scale
    animations.push(`
@keyframes scaleIn {
  from { transform: scale(0.9); opacity: 0; }
  to { transform: scale(1); opacity: 1; }
}`);
    animations.push(`
@keyframes scaleOut {
  from { transform: scale(1); opacity: 1; }
  to { transform: scale(0.9); opacity: 0; }
}`);

    // Bounce
    animations.push(`
@keyframes bounce {
  0%, 100% { transform: translateY(0); }
  50% { transform: translateY(-10px); }
}`);

    // Flip
    animations.push(`
@keyframes flip {
  from { transform: perspective(400px) rotateY(0); }
  to { transform: perspective(400px) rotateY(180deg); }
}`);

    return animations.join('\n');
  }

  /**
   * 应用主题到DOM
   */
  applyTheme(): void {
    if (typeof document === 'undefined') return;

    const theme = this.getTheme();
    const root = document.documentElement;

    root.style.setProperty('--primary', theme.primary);
    root.style.setProperty('--secondary', theme.secondary);
    root.style.setProperty('--background', theme.background);
    root.style.setProperty('--surface', theme.surface);
    root.style.setProperty('--text-primary', theme.text.primary);
    root.style.setProperty('--text-secondary', theme.text.secondary);
    root.style.setProperty('--text-disabled', theme.text.disabled);
    root.style.setProperty('--border', theme.border);
    root.style.setProperty('--shadow', theme.shadow);

    // 设置 color-scheme
    root.style.colorScheme = theme.mode === 'dark' ? 'dark' : 'light';
  }

  /**
   * 监听主题变化
   */
  onThemeChange(callback: (theme: ThemeConfig) => void): () => void {
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
    const theme = this.getTheme();
    this.listeners.forEach(cb => cb(theme));
  }

  /**
   * 获取响应式样式
   */
  getResponsiveStyle(breakpoint: 'xs' | 'sm' | 'md' | 'lg' | 'xl'): Record<string, any> {
    const styles: Record<string, Record<string, any>> = {
      xs: { fontSize: 12, padding: 8, gap: 8 },
      sm: { fontSize: 14, padding: 12, gap: 12 },
      md: { fontSize: 16, padding: 16, gap: 16 },
      lg: { fontSize: 18, padding: 20, gap: 20 },
      xl: { fontSize: 20, padding: 24, gap: 24 },
    };

    return styles[breakpoint];
  }

  /**
   * 获取间距
   */
  getSpacing(size: 'xs' | 'sm' | 'md' | 'lg' | 'xl' | '2xl'): number {
    const spacings: Record<string, number> = {
      xs: 4,
      sm: 8,
      md: 16,
      lg: 24,
      xl: 32,
      '2xl': 48,
    };

    return spacings[size];
  }

  /**
   * 获取圆角
   */
  getBorderRadius(size: 'none' | 'sm' | 'md' | 'lg' | 'full'): number {
    const radii: Record<string, number> = {
      none: 0,
      sm: 4,
      md: 8,
      lg: 16,
      full: 9999,
    };

    return radii[size];
  }
}

export const uiuxService = new UIUXService();
export default uiuxService;
