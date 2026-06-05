/**
 * 权限隐私安全服务
 * 数据加密、隐私保护、安全审计、权限校验
 */

import { safeLocalStorageGet, safeLocalStorageSet } from './env';

/* -------------------------------------------------------------------------- */
/*  类型定义                                                                    */
/* -------------------------------------------------------------------------- */

export type PermissionLevel = 'public' | 'private' | 'confidential' | 'restricted';
export type DataCategory = 'personal' | 'financial' | 'location' | 'behavior' | 'device' | 'contact';

export interface PrivacyPolicy {
  id: string;
  category: DataCategory;
  level: PermissionLevel;
  description: string;
  consentRequired: boolean;
  retentionDays: number;
  encryption: boolean;
  anonymization: boolean;
}

export interface SecurityAudit {
  id: string;
  type: 'access' | 'modify' | 'delete' | 'export' | 'login' | 'permission_change';
  userId: string;
  resource: string;
  action: string;
  result: 'success' | 'denied' | 'failed';
  ip: string;
  userAgent: string;
  timestamp: string;
  details?: any;
}

export interface DataEncryption {
  algorithm: 'AES-256-GCM' | 'AES-128-CBC' | 'RSA-2048';
  keyId: string;
  iv?: string;
  tag?: string;
}

export interface AccessControl {
  resource: string;
  actions: string[];
  roles: string[];
  conditions?: {
    timeRange?: { start: string; end: string };
    ipRange?: string[];
    deviceTypes?: string[];
  };
}

/* -------------------------------------------------------------------------- */
/*  隐私策略配置                                                                  */
/* -------------------------------------------------------------------------- */

const PRIVACY_POLICIES: PrivacyPolicy[] = [
  {
    id: 'privacy-location',
    category: 'location',
    level: 'private',
    description: '用户位置信息，用于LBS服务和门店推荐',
    consentRequired: true,
    retentionDays: 30,
    encryption: true,
    anonymization: true,
  },
  {
    id: 'privacy-personal',
    category: 'personal',
    level: 'confidential',
    description: '个人身份信息（姓名、手机号）',
    consentRequired: true,
    retentionDays: 365,
    encryption: true,
    anonymization: false,
  },
  {
    id: 'privacy-financial',
    category: 'financial',
    level: 'restricted',
    description: '金融信息（交易记录、补贴金额）',
    consentRequired: true,
    retentionDays: 365,
    encryption: true,
    anonymization: false,
  },
  {
    id: 'privacy-behavior',
    category: 'behavior',
    level: 'private',
    description: '行为数据（搜索记录、浏览历史）',
    consentRequired: true,
    retentionDays: 90,
    encryption: true,
    anonymization: true,
  },
  {
    id: 'privacy-device',
    category: 'device',
    level: 'private',
    description: '设备信息（IMEI、型号、系统版本）',
    consentRequired: true,
    retentionDays: 180,
    encryption: true,
    anonymization: true,
  },
  {
    id: 'privacy-contact',
    category: 'contact',
    level: 'confidential',
    description: '联系人信息（通讯录、社交关系）',
    consentRequired: true,
    retentionDays: 90,
    encryption: true,
    anonymization: true,
  },
];

/* -------------------------------------------------------------------------- */
/*  访问控制配置                                                                  */
/* -------------------------------------------------------------------------- */

const ACCESS_CONTROLS: AccessControl[] = [
  {
    resource: '/api/lbs/*',
    actions: ['read', 'write'],
    roles: ['admin', 'manager', 'staff'],
  },
  {
    resource: '/api/crawler/*',
    actions: ['read', 'write', 'execute'],
    roles: ['admin', 'manager'],
  },
  {
    resource: '/api/sync/*',
    actions: ['read', 'write', 'execute'],
    roles: ['admin'],
  },
  {
    resource: '/api/geo/*',
    actions: ['read', 'write'],
    roles: ['admin', 'manager'],
  },
  {
    resource: '/api/ai/*',
    actions: ['read', 'execute'],
    roles: ['admin', 'manager', 'staff'],
  },
  {
    resource: '/api/storage/*',
    actions: ['read', 'write', 'delete'],
    roles: ['admin', 'manager', 'staff'],
    conditions: {
      timeRange: { start: '06:00', end: '23:00' },
    },
  },
];

/* -------------------------------------------------------------------------- */
/*  权限隐私安全服务                                                              */
/* -------------------------------------------------------------------------- */

class SecurityService {
  private audits: SecurityAudit[] = [];
  private encryptionKey: string | null = null;

  constructor() {
    // 加载审计日志
    this.loadAudits();
    // 初始化加密密钥
    this.initEncryptionKey();
  }

  /**
   * 初始化加密密钥
   */
  private initEncryptionKey(): void {
    try {
      const savedKey = safeLocalStorageGet('encryption_key');
      if (savedKey) {
        this.encryptionKey = savedKey;
      } else {
        // 生成新密钥（实际应使用安全的密钥生成）
        this.encryptionKey = this.generateKey();
        safeLocalStorageSet('encryption_key', this.encryptionKey);
      }
    } catch {
      this.encryptionKey = this.generateKey();
    }
  }

  /**
   * 生成加密密钥
   */
  private generateKey(): string {
    // 模拟密钥生成（实际应使用 Web Crypto API）
    const chars = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789';
    let key = '';
    for (let i = 0; i < 32; i++) {
      key += chars.charAt(Math.floor(Math.random() * chars.length));
    }
    return key;
  }

  /**
   * 数据加密
   */
  encrypt(data: string, algorithm: DataEncryption['algorithm'] = 'AES-256-GCM'): { encrypted: string; encryption: DataEncryption } {
    // 模拟加密（实际应使用 Web Crypto API）
    const keyId = `key-${Date.now()}`;
    // 使用 Buffer 处理 Base64 编码（支持中文）
    const encrypted = typeof Buffer !== 'undefined' 
      ? Buffer.from(data, 'utf-8').toString('base64')
      : btoa(unescape(encodeURIComponent(data)));
    
    return {
      encrypted,
      encryption: {
        algorithm,
        keyId,
      },
    };
  }

  /**
   * 数据解密
   */
  decrypt(encrypted: string, encryption: DataEncryption): string {
    // 模拟解密
    try {
      return typeof Buffer !== 'undefined'
        ? Buffer.from(encrypted, 'base64').toString('utf-8')
        : decodeURIComponent(escape(atob(encrypted)));
    } catch {
      throw new Error('解密失败');
    }
  }

  /**
   * 数据脱敏
   */
  anonymize(data: any, category: DataCategory): any {
    const policy = PRIVACY_POLICIES.find(p => p.category === category);
    if (!policy || !policy.anonymization) return data;

    // 创建深拷贝
    const result = JSON.parse(JSON.stringify(data));

    // 根据类别进行脱敏
    switch (category) {
      case 'personal':
        if (result.name) result.name = this.maskName(result.name);
        if (result.phone) result.phone = this.maskPhone(result.phone);
        if (result.idCard) result.idCard = this.maskIdCard(result.idCard);
        break;
      case 'location':
        // 位置模糊化：保留小数点后2位
        if (result.lat) result.lat = Math.round(result.lat * 100) / 100;
        if (result.lng) result.lng = Math.round(result.lng * 100) / 100;
        break;
      case 'financial':
        if (result.amount) result.amount = '***';
        break;
      case 'device':
        if (result.imei) result.imei = result.imei.slice(0, 6) + '****' + result.imei.slice(-2);
        break;
    }

    return result;
  }

  /**
   * 姓名脱敏
   */
  private maskName(name: string): string {
    if (name.length <= 2) return name[0] + '*';
    return name[0] + '*'.repeat(name.length - 2) + name[name.length - 1];
  }

  /**
   * 手机号脱敏
   */
  private maskPhone(phone: string): string {
    return phone.slice(0, 3) + '****' + phone.slice(-4);
  }

  /**
   * 身份证脱敏
   */
  private maskIdCard(idCard: string): string {
    return idCard.slice(0, 6) + '********' + idCard.slice(-4);
  }

  /**
   * 检查访问权限
   */
  checkAccess(
    resource: string,
    action: string,
    role: string,
    context?: { ip?: string; time?: Date; deviceType?: string }
  ): boolean {
    // 查找匹配的访问控制规则
    for (const ac of ACCESS_CONTROLS) {
      if (this.matchResource(resource, ac.resource)) {
        // 检查角色
        if (!ac.roles.includes(role)) continue;
        
        // 检查动作
        if (!ac.actions.includes(action)) continue;
        
        // 检查条件
        if (ac.conditions) {
          if (ac.conditions.timeRange) {
            const now = context?.time || new Date();
            const currentTime = `${now.getHours().toString().padStart(2, '0')}:${now.getMinutes().toString().padStart(2, '0')}`;
            if (currentTime < ac.conditions.timeRange.start || currentTime > ac.conditions.timeRange.end) {
              continue;
            }
          }
          
          if (ac.conditions.ipRange && context?.ip) {
            if (!ac.conditions.ipRange.includes(context.ip)) continue;
          }
          
          if (ac.conditions.deviceTypes && context?.deviceType) {
            if (!ac.conditions.deviceTypes.includes(context.deviceType)) continue;
          }
        }
        
        return true;
      }
    }

    return false;
  }

  /**
   * 匹配资源路径
   */
  private matchResource(resource: string, pattern: string): boolean {
    if (pattern.includes('*')) {
      const regex = new RegExp('^' + pattern.replace(/\*/g, '.*') + '$');
      return regex.test(resource);
    }
    return resource === pattern;
  }

  /**
   * 记录安全审计
   */
  audit(audit: Omit<SecurityAudit, 'id' | 'timestamp'>): SecurityAudit {
    const record: SecurityAudit = {
      ...audit,
      id: `AUDIT-${Date.now()}-${Math.random().toString(36).slice(2, 6)}`,
      timestamp: new Date().toISOString(),
    };

    this.audits.push(record);
    this.saveAudits();

    console.log(`[Security] 审计记录: ${audit.type} - ${audit.resource} - ${audit.result}`);
    return record;
  }

  /**
   * 获取审计日志
   */
  getAudits(options?: {
    userId?: string;
    type?: SecurityAudit['type'];
    startTime?: string;
    endTime?: string;
    limit?: number;
  }): SecurityAudit[] {
    let results = [...this.audits];

    if (options?.userId) {
      results = results.filter(a => a.userId === options.userId);
    }

    if (options?.type) {
      results = results.filter(a => a.type === options.type);
    }

    if (options?.startTime) {
      results = results.filter(a => a.timestamp >= options.startTime!);
    }

    if (options?.endTime) {
      results = results.filter(a => a.timestamp <= options.endTime!);
    }

    results.sort((a, b) => b.timestamp.localeCompare(a.timestamp));

    if (options?.limit) {
      results = results.slice(0, options.limit);
    }

    return results;
  }

  /**
   * 获取隐私策略
   */
  getPrivacyPolicies(): PrivacyPolicy[] {
    return PRIVACY_POLICIES;
  }

  /**
   * 获取数据类别策略
   */
  getPrivacyPolicy(category: DataCategory): PrivacyPolicy | null {
    return PRIVACY_POLICIES.find(p => p.category === category) || null;
  }

  /**
   * 检查数据是否需要用户同意
   */
  requiresConsent(category: DataCategory): boolean {
    const policy = PRIVACY_POLICIES.find(p => p.category === category);
    return policy?.consentRequired ?? true;
  }

  /**
   * 检查数据是否过期
   */
  isDataExpired(category: DataCategory, timestamp: string): boolean {
    const policy = PRIVACY_POLICIES.find(p => p.category === category);
    if (!policy) return false;

    const dataTime = new Date(timestamp).getTime();
    const expiryTime = dataTime + policy.retentionDays * 24 * 60 * 60 * 1000;

    return Date.now() > expiryTime;
  }

  /**
   * 获取安全统计
   */
  getSecurityStats(): {
    totalAudits: number;
    byType: Record<SecurityAudit['type'], number>;
    byResult: Record<SecurityAudit['result'], number>;
    recentFailures: number;
  } {
    const byType: Record<SecurityAudit['type'], number> = {
      access: 0, modify: 0, delete: 0, export: 0, login: 0, permission_change: 0,
    };
    const byResult: Record<SecurityAudit['result'], number> = {
      success: 0, denied: 0, failed: 0,
    };

    this.audits.forEach(a => {
      byType[a.type]++;
      byResult[a.result]++;
    });

    // 最近24小时失败次数
    const oneDayAgo = Date.now() - 24 * 60 * 60 * 1000;
    const recentFailures = this.audits.filter(
      a => new Date(a.timestamp).getTime() > oneDayAgo && a.result !== 'success'
    ).length;

    return {
      totalAudits: this.audits.length,
      byType,
      byResult,
      recentFailures,
    };
  }

  /* -------------------------------------------------------------------------- */
  /*  数据持久化                                                                   */
  /* -------------------------------------------------------------------------- */

  private AUDIT_KEY = 'security_audits';

  private loadAudits(): void {
    try {
      const saved = safeLocalStorageGet(this.AUDIT_KEY);
      if (saved) {
        this.audits = JSON.parse(saved);
      }
    } catch {}
  }

  private saveAudits(): void {
    try {
      // 只保留最近1000条
      const toSave = this.audits.slice(-1000);
      safeLocalStorageSet(this.AUDIT_KEY, JSON.stringify(toSave));
    } catch {}
  }
}

export const securityService = new SecurityService();
export default securityService;
