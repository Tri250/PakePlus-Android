/**
 * 图片服务 - 支持所有模块的图片需求
 * 
 * 功能：
 * - 海报生成（朋友圈、小红书、抖音）
 * - 头像上传
 * - 物料图片管理
 * - 图片压缩和格式转换
 * - CDN 加速
 */

import { safeLocalStorageGet, safeLocalStorageSet } from './env';

/* -------------------------------------------------------------------------- */
/*  类型定义                                                                    */
/* -------------------------------------------------------------------------- */

export type ImageCategory = 
  | 'poster'       // 活动海报
  | 'avatar'       // 用户头像
  | 'material'     // 营销物料
  | 'nfc'          // NFC 素材
  | 'product'      // 产品图片
  | 'store';       // 门店图片

export type PosterTemplate = 
  | 'wechat-moment'   // 朋友圈海报
  | 'xiaohongshu'     // 小红书图文
  | 'douyin'          // 抖音口播封面
  | 'sms';            // 短信图文

export interface ImageUploadResult {
  id: string;
  url: string;
  thumbnailUrl?: string;
  width: number;
  height: number;
  size: number;
  format: string;
  category: ImageCategory;
  createdAt: string;
}

export interface PosterConfig {
  template: PosterTemplate;
  title: string;
  subtitle?: string;
  price?: string;
  discount?: string;
  qrCodeUrl?: string;
  storeName?: string;
  storeAddress?: string;
  logoUrl?: string;
  backgroundImage?: string;
  theme: 'red' | 'blue' | 'green' | 'purple' | 'orange' | 'pink';
}

export interface GeneratedPoster {
  id: string;
  imageUrl: string;
  thumbnailUrl: string;
  template: PosterTemplate;
  width: number;
  height: number;
  createdAt: string;
}

/* -------------------------------------------------------------------------- */
/*  图片服务类                                                                  */
/* -------------------------------------------------------------------------- */

class ImageService {
  private uploadQueue: Array<() => Promise<any>> = [];
  private isProcessing = false;

  /**
   * 上传图片
   */
  async upload(
    file: File,
    category: ImageCategory,
    options?: { compress?: boolean; maxWidth?: number; maxHeight?: number }
  ): Promise<ImageUploadResult> {
    const { compress = true, maxWidth = 1920, maxHeight = 1080 } = options || {};

    // 压缩图片
    let processedFile = file;
    if (compress && file.type.startsWith('image/')) {
      processedFile = await this.compressImage(file, maxWidth, maxHeight);
    }

    // 模拟上传（实际应调用 OSS API）
    const id = `IMG-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;
    
    // 创建本地预览 URL
    const url = URL.createObjectURL(processedFile);
    
    return {
      id,
      url,
      thumbnailUrl: url,
      width: maxWidth,
      height: maxHeight,
      size: processedFile.size,
      format: file.type.split('/')[1] || 'jpg',
      category,
      createdAt: new Date().toISOString(),
    };
  }

  /**
   * 压缩图片
   */
  private async compressImage(
    file: File,
    maxWidth: number,
    maxHeight: number
  ): Promise<File> {
    return new Promise((resolve) => {
      const reader = new FileReader();
      reader.onload = (e) => {
        const img = new Image();
        img.onload = () => {
          const canvas = document.createElement('canvas');
          let { width, height } = img;

          // 计算缩放比例
          if (width > maxWidth) {
            height = (height * maxWidth) / width;
            width = maxWidth;
          }
          if (height > maxHeight) {
            width = (width * maxHeight) / height;
            height = maxHeight;
          }

          canvas.width = width;
          canvas.height = height;

          const ctx = canvas.getContext('2d');
          ctx?.drawImage(img, 0, 0, width, height);

          canvas.toBlob(
            (blob) => {
              if (blob) {
                resolve(new File([blob], file.name, { type: 'image/jpeg' }));
              } else {
                resolve(file);
              }
            },
            'image/jpeg',
            0.8
          );
        };
        img.src = e.target?.result as string;
      };
      reader.readAsDataURL(file);
    });
  }

  /**
   * 生成海报
   */
  async generatePoster(config: PosterConfig): Promise<GeneratedPoster> {
    const id = `POSTER-${Date.now()}`;
    
    // 根据模板生成不同尺寸
    const sizes: Record<PosterTemplate, { width: number; height: number }> = {
      'wechat-moment': { width: 1080, height: 1920 },
      'xiaohongshu': { width: 1080, height: 1440 },
      'douyin': { width: 1080, height: 1920 },
      'sms': { width: 750, height: 1334 },
    };

    const size = sizes[config.template];

    // 检查是否在浏览器环境
    const isBrowser = typeof document !== 'undefined' && typeof HTMLCanvasElement !== 'undefined';
    
    if (isBrowser) {
      // 浏览器环境：使用 Canvas 生成海报
      return this.generatePosterWithCanvas(config, id, size);
    } else {
      // Node.js 环境：生成模拟数据 URL
      return this.generatePosterMock(config, id, size);
    }
  }

  /**
   * 使用 Canvas 生成海报（浏览器环境）
   */
  private async generatePosterWithCanvas(
    config: PosterConfig,
    id: string,
    size: { width: number; height: number }
  ): Promise<GeneratedPoster> {
    const canvas = document.createElement('canvas');
    canvas.width = size.width;
    canvas.height = size.height;
    const ctx = canvas.getContext('2d');

    if (ctx) {
      // 绘制背景
      const themeColors: Record<string, string> = {
        red: '#ef4444',
        blue: '#3b82f6',
        green: '#10b981',
        purple: '#8b5cf6',
        orange: '#f59e0b',
        pink: '#ec4899',
      };
      
      const gradient = ctx.createLinearGradient(0, 0, 0, size.height);
      gradient.addColorStop(0, themeColors[config.theme]);
      gradient.addColorStop(1, this.adjustColor(themeColors[config.theme], -30));
      ctx.fillStyle = gradient;
      ctx.fillRect(0, 0, size.width, size.height);

      // 绘制标题
      ctx.fillStyle = '#ffffff';
      ctx.font = 'bold 48px sans-serif';
      ctx.textAlign = 'center';
      this.wrapText(ctx, config.title, size.width / 2, 200, size.width - 100, 60);

      // 绘制副标题
      if (config.subtitle) {
        ctx.font = '32px sans-serif';
        this.wrapText(ctx, config.subtitle, size.width / 2, 400, size.width - 100, 45);
      }

      // 绘制价格
      if (config.price) {
        ctx.font = 'bold 72px sans-serif';
        ctx.fillText(config.price, size.width / 2, size.height / 2);
      }

      // 绘制折扣
      if (config.discount) {
        ctx.font = '36px sans-serif';
        ctx.fillText(config.discount, size.width / 2, size.height / 2 + 80);
      }

      // 绘制门店信息
      if (config.storeName) {
        ctx.font = '28px sans-serif';
        ctx.fillText(config.storeName, size.width / 2, size.height - 200);
      }
      if (config.storeAddress) {
        ctx.font = '20px sans-serif';
        ctx.fillText(config.storeAddress, size.width / 2, size.height - 150);
      }

      // 绘制二维码占位
      ctx.fillStyle = '#ffffff';
      ctx.fillRect(size.width / 2 - 75, size.height - 400, 150, 150);
      ctx.strokeStyle = '#cccccc';
      ctx.strokeRect(size.width / 2 - 75, size.height - 400, 150, 150);
      ctx.fillStyle = '#999999';
      ctx.font = '16px sans-serif';
      ctx.fillText('扫码查看', size.width / 2, size.height - 320);
    }

    // 转换为 Blob
    const blob = await new Promise<Blob>((resolve) => {
      canvas.toBlob((b) => resolve(b!), 'image/jpeg', 0.9);
    });

    const imageUrl = URL.createObjectURL(blob);

    return {
      id,
      imageUrl,
      thumbnailUrl: imageUrl,
      template: config.template,
      width: size.width,
      height: size.height,
      createdAt: new Date().toISOString(),
    };
  }

  /**
   * 生成模拟海报（Node.js 环境）
   */
  private async generatePosterMock(
    config: PosterConfig,
    id: string,
    size: { width: number; height: number }
  ): Promise<GeneratedPoster> {
    // 生成 SVG 格式的模拟海报
    const themeColors: Record<string, string> = {
      red: '#ef4444',
      blue: '#3b82f6',
      green: '#10b981',
      purple: '#8b5cf6',
      orange: '#f59e0b',
      pink: '#ec4899',
    };

    const bgColor = themeColors[config.theme] || '#3b82f6';
    
    // 生成 SVG 内容
    const svgContent = `
      <svg xmlns="http://www.w3.org/2000/svg" width="${size.width}" height="${size.height}">
        <defs>
          <linearGradient id="bg" x1="0%" y1="0%" x2="0%" y2="100%">
            <stop offset="0%" style="stop-color:${bgColor}"/>
            <stop offset="100%" style="stop-color:${this.adjustColor(bgColor, -30)}"/>
          </linearGradient>
        </defs>
        <rect width="100%" height="100%" fill="url(#bg)"/>
        <text x="50%" y="200" text-anchor="middle" fill="white" font-size="48" font-weight="bold">${config.title}</text>
        ${config.subtitle ? `<text x="50%" y="400" text-anchor="middle" fill="white" font-size="32">${config.subtitle}</text>` : ''}
        ${config.price ? `<text x="50%" y="${size.height / 2}" text-anchor="middle" fill="white" font-size="72" font-weight="bold">${config.price}</text>` : ''}
        ${config.discount ? `<text x="50%" y="${size.height / 2 + 80}" text-anchor="middle" fill="white" font-size="36">${config.discount}</text>` : ''}
        ${config.storeName ? `<text x="50%" y="${size.height - 200}" text-anchor="middle" fill="white" font-size="28">${config.storeName}</text>` : ''}
        ${config.storeAddress ? `<text x="50%" y="${size.height - 150}" text-anchor="middle" fill="white" font-size="20">${config.storeAddress}</text>` : ''}
        <rect x="${size.width / 2 - 75}" y="${size.height - 400}" width="150" height="150" fill="white" stroke="#ccc"/>
        <text x="50%" y="${size.height - 320}" text-anchor="middle" fill="#999" font-size="16">扫码查看</text>
      </svg>
    `;

    // 转换为 data URL
    const imageUrl = `data:image/svg+xml;base64,${Buffer.from(svgContent).toString('base64')}`;

    // 缓存结果
    const result: GeneratedPoster = {
      id,
      imageUrl,
      thumbnailUrl: imageUrl,
      template: config.template,
      width: size.width,
      height: size.height,
      createdAt: new Date().toISOString(),
    };

    // 保存到缓存
    try {
      const cacheKey = `poster_${id}`;
      safeLocalStorageSet(cacheKey, JSON.stringify(result));
    } catch {}

    console.log(`[ImageService] 生成模拟海报: ${id}, 尺寸: ${size.width}x${size.height}`);

    return result;
  }

  /**
   * 文字换行
   */
  private wrapText(
    ctx: CanvasRenderingContext2D,
    text: string,
    x: number,
    y: number,
    maxWidth: number,
    lineHeight: number
  ): void {
    const chars = text.split('');
    let line = '';
    let currentY = y;

    for (const char of chars) {
      const testLine = line + char;
      const metrics = ctx.measureText(testLine);
      if (metrics.width > maxWidth && line.length > 0) {
        ctx.fillText(line, x, currentY);
        line = char;
        currentY += lineHeight;
      } else {
        line = testLine;
      }
    }
    ctx.fillText(line, x, currentY);
  }

  /**
   * 调整颜色亮度
   */
  private adjustColor(color: string, amount: number): string {
    const hex = color.replace('#', '');
    const r = Math.max(0, Math.min(255, parseInt(hex.slice(0, 2), 16) + amount));
    const g = Math.max(0, Math.min(255, parseInt(hex.slice(2, 4), 16) + amount));
    const b = Math.max(0, Math.min(255, parseInt(hex.slice(4, 6), 16) + amount));
    return `#${r.toString(16).padStart(2, '0')}${g.toString(16).padStart(2, '0')}${b.toString(16).padStart(2, '0')}`;
  }

  /**
   * 获取图片预览 URL
   */
  getPreviewUrl(imageId: string): string {
    return `/api/images/${imageId}/preview`;
  }

  /**
   * 删除图片
   */
  async delete(imageId: string): Promise<boolean> {
    // 模拟删除
    console.log(`[ImageService] 删除图片: ${imageId}`);
    return true;
  }
}

export const imageService = new ImageService();
export default imageService;

/* -------------------------------------------------------------------------- */
/*  预设海报模板                                                                */
/* -------------------------------------------------------------------------- */

export const POSTER_TEMPLATES = {
  // Mate70 上市首发
  mate70Launch: {
    template: 'wechat-moment' as PosterTemplate,
    title: 'Mate70 Pro 上市首发',
    subtitle: '旗舰影像 · 鸿蒙 4.0 · 卫星通信',
    price: '¥6,999 起',
    discount: '老用户专享 ¥1,500 补贴',
    theme: 'red' as const,
  },
  
  // 暑期学生季
  studentSeason: {
    template: 'xiaohongshu' as PosterTemplate,
    title: '暑期学生季',
    subtitle: '凭学生证立享 9 折',
    price: '送一年碎屏险',
    discount: '3 期免息分期',
    theme: 'orange' as const,
  },
  
  // 以旧换新
  tradeIn: {
    template: 'wechat-moment' as PosterTemplate,
    title: '以旧换新专场',
    subtitle: '旧机抵扣 + 国补 ¥500 + 品牌补贴',
    price: '最高省 ¥3,000',
    discount: '到店免费估价',
    theme: 'green' as const,
  },
  
  // 家庭融合套餐
  familyPlan: {
    template: 'sms' as PosterTemplate,
    title: '家庭融合套餐',
    subtitle: '全家共享流量 + 视频会员',
    price: '¥199/月',
    discount: '5 人共享',
    theme: 'blue' as const,
  },
};
