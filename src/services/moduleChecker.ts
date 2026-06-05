/**
 * 模块完整性检查服务
 * 验证代码完整性、质量、覆盖率
 */

import { safeLocalStorageGet, safeLocalStorageSet } from './env';

/* -------------------------------------------------------------------------- */
/*  类型定义                                                                    */
/* -------------------------------------------------------------------------- */

export type CheckStatus = 'pass' | 'fail' | 'warning' | 'skip';

export interface ModuleCheckResult {
  id: string;
  name: string;
  status: CheckStatus;
  score: number; // 0-100
  checks: CheckItem[];
  summary: string;
  timestamp: string;
}

export interface CheckItem {
  name: string;
  status: CheckStatus;
  message: string;
  details?: any;
}

export interface CoverageReport {
  total: number;
  covered: number;
  percentage: number;
  uncovered: string[];
}

export interface QualityReport {
  id: string;
  overallScore: number;
  moduleResults: ModuleCheckResult[];
  coverage: CoverageReport;
  recommendations: string[];
  timestamp: string;
}

/* -------------------------------------------------------------------------- */
/*  模块定义                                                                    */
/* -------------------------------------------------------------------------- */

const MODULE_DEFINITIONS = [
  { id: 'mapService', name: '地图服务', path: './mapService', critical: true },
  { id: 'geolocation', name: '地理定位', path: './geolocation', critical: true },
  { id: 'dataCollector', name: '数据采集', path: './dataCollector', critical: true },
  { id: 'dataCrawler', name: '数据爬虫', path: './dataCrawler', critical: true },
  { id: 'lbsRadar', name: 'LBS雷达', path: './lbsRadar', critical: true },
  { id: 'competitorMonitor', name: '竞品监控', path: './competitorMonitor', critical: false },
  { id: 'geoOptimization', name: 'GEO优化', path: './geoOptimization', critical: false },
  { id: 'imageService', name: '图片服务', path: './imageService', critical: false },
  { id: 'dataSync', name: '数据同步', path: './dataSync', critical: false },
  { id: 'ai', name: 'AI服务', path: './ai', critical: false },
  { id: 'storage', name: '数据存储', path: './storage', critical: true },
  { id: 'auth', name: '权限管理', path: './auth', critical: true },
  { id: 'api', name: '网络请求', path: './api', critical: true },
  { id: 'networkManager', name: '网络管理', path: './networkManager', critical: true },
  { id: 'resolutionAdapter', name: '分辨率适配', path: './resolutionAdapter', critical: false },
  { id: 'dynamicLoader', name: '动态加载', path: './dynamicLoader', critical: false },
];

/* -------------------------------------------------------------------------- */
/*  模块完整性检查服务                                                             */
/* -------------------------------------------------------------------------- */

class ModuleChecker {
  /**
   * 检查单个模块
   */
  async checkModule(moduleId: string): Promise<ModuleCheckResult> {
    const moduleDef = MODULE_DEFINITIONS.find(m => m.id === moduleId);
    if (!moduleDef) {
      return {
        id: moduleId,
        name: '未知模块',
        status: 'fail',
        score: 0,
        checks: [],
        summary: '模块定义不存在',
        timestamp: new Date().toISOString(),
      };
    }

    const checks: CheckItem[] = [];
    let totalScore = 0;

    // 1. 检查模块是否存在
    checks.push(await this.checkModuleExists(moduleDef));

    // 2. 检查导出是否正确
    checks.push(await this.checkExports(moduleDef));

    // 3. 检查类型定义
    checks.push(await this.checkTypes(moduleDef));

    // 4. 检查功能完整性
    checks.push(await this.checkFunctionality(moduleDef));

    // 5. 检查数据匹配
    checks.push(await this.checkDataMatch(moduleDef));

    // 计算总分
    const passedChecks = checks.filter(c => c.status === 'pass').length;
    totalScore = Math.round((passedChecks / checks.length) * 100);

    // 确定状态
    let status: CheckStatus = 'pass';
    if (totalScore < 60) status = 'fail';
    else if (totalScore < 80) status = 'warning';

    const summary = this.generateSummary(checks, totalScore);

    return {
      id: moduleId,
      name: moduleDef.name,
      status,
      score: totalScore,
      checks,
      summary,
      timestamp: new Date().toISOString(),
    };
  }

  /**
   * 检查所有模块
   */
  async checkAllModules(): Promise<QualityReport> {
    const results: ModuleCheckResult[] = [];
    const uncovered: string[] = [];

    for (const moduleDef of MODULE_DEFINITIONS) {
      const result = await this.checkModule(moduleDef.id);
      results.push(result);
      
      if (result.score < 80) {
        uncovered.push(moduleDef.id);
      }
    }

    // 计算覆盖率
    const covered = results.filter(r => r.score >= 80).length;
    const coverage: CoverageReport = {
      total: results.length,
      covered,
      percentage: Math.round((covered / results.length) * 100),
      uncovered,
    };

    // 计算总体分数
    const overallScore = Math.round(
      results.reduce((sum, r) => sum + r.score, 0) / results.length
    );

    // 生成建议
    const recommendations = this.generateRecommendations(results, coverage);

    return {
      id: `QUALITY-${Date.now()}`,
      overallScore,
      moduleResults: results,
      coverage,
      recommendations,
      timestamp: new Date().toISOString(),
    };
  }

  /**
   * 检查模块是否存在
   */
  private async checkModuleExists(moduleDef: typeof MODULE_DEFINITIONS[0]): Promise<CheckItem> {
    try {
      // 尝试导入模块
      const module = await import(moduleDef.path);
      
      if (module) {
        return {
          name: '模块存在',
          status: 'pass',
          message: '模块文件存在且可导入',
        };
      }
      
      return {
        name: '模块存在',
        status: 'fail',
        message: '模块导入失败',
      };
    } catch (err: any) {
      return {
        name: '模块存在',
        status: 'fail',
        message: `导入错误: ${err.message}`,
      };
    }
  }

  /**
   * 检查导出是否正确
   */
  private async checkExports(moduleDef: typeof MODULE_DEFINITIONS[0]): Promise<CheckItem> {
    try {
      const module = await import(moduleDef.path);
      const exports = Object.keys(module);
      
      if (exports.length === 0) {
        return {
          name: '导出检查',
          status: 'fail',
          message: '模块没有导出任何内容',
        };
      }
      
      // 检查是否有默认导出或服务实例
      const hasDefault = 'default' in module;
      const hasService = exports.some(e => e.toLowerCase().includes('service'));
      
      if (hasDefault || hasService) {
        return {
          name: '导出检查',
          status: 'pass',
          message: `导出 ${exports.length} 项，包含服务实例`,
          details: exports.slice(0, 5),
        };
      }
      
      return {
        name: '导出检查',
        status: 'warning',
        message: `导出 ${exports.length} 项，建议添加服务实例`,
        details: exports,
      };
    } catch (err: any) {
      return {
        name: '导出检查',
        status: 'fail',
        message: err.message,
      };
    }
  }

  /**
   * 检查类型定义
   */
  private async checkTypes(moduleDef: typeof MODULE_DEFINITIONS[0]): Promise<CheckItem> {
    try {
      const module = await import(moduleDef.path);
      
      // 检查是否有类型导出（运行时无法直接检查，使用启发式方法）
      const exports = Object.keys(module);
      const typeLikeExports = exports.filter(e => 
        e[0] === e[0].toUpperCase() && // 大写开头
        typeof module[e] !== 'function' &&
        typeof module[e] !== 'object'
      );
      
      if (typeLikeExports.length > 0 || exports.length > 2) {
        return {
          name: '类型定义',
          status: 'pass',
          message: '模块包含类型定义',
        };
      }
      
      return {
        name: '类型定义',
        status: 'warning',
        message: '建议添加更多类型定义',
      };
    } catch (err: any) {
      return {
        name: '类型定义',
        status: 'fail',
        message: err.message,
      };
    }
  }

  /**
   * 检查功能完整性
   */
  private async checkFunctionality(moduleDef: typeof MODULE_DEFINITIONS[0]): Promise<CheckItem> {
    try {
      const module = await import(moduleDef.path);
      
      // 查找服务实例
      const serviceKey = Object.keys(module).find(k => 
        k.toLowerCase().includes('service') || k === 'default'
      );
      
      if (!serviceKey) {
        return {
          name: '功能完整性',
          status: 'warning',
          message: '未找到服务实例',
        };
      }
      
      const service = module[serviceKey];
      
      // 检查方法数量
      const methods = Object.getOwnPropertyNames(Object.getPrototypeOf(service))
        .filter(m => m !== 'constructor' && typeof service[m] === 'function');
      
      if (methods.length >= 3) {
        return {
          name: '功能完整性',
          status: 'pass',
          message: `服务包含 ${methods.length} 个方法`,
          details: methods.slice(0, 5),
        };
      }
      
      return {
        name: '功能完整性',
        status: 'warning',
        message: `服务方法较少 (${methods.length})`,
        details: methods,
      };
    } catch (err: any) {
      return {
        name: '功能完整性',
        status: 'fail',
        message: err.message,
      };
    }
  }

  /**
   * 检查数据匹配
   */
  private async checkDataMatch(moduleDef: typeof MODULE_DEFINITIONS[0]): Promise<CheckItem> {
    // 模拟数据匹配检查
    // 实际应检查模块输出数据格式是否符合规范
    
    const dataFormats: Record<string, string[]> = {
      mapService: ['GeocodingResult', 'POIResult'],
      dataCollector: ['ScanResult', 'ScanReport'],
      lbsRadar: ['SalesLead', 'LBSRadarScanResult'],
      competitorMonitor: ['CompetitorStore', 'CompetitorMonitorReport'],
      geoOptimization: ['StoreDescription', 'GEORankingReport'],
    };
    
    const expectedFormats = dataFormats[moduleDef.id];
    
    if (!expectedFormats) {
      return {
        name: '数据匹配',
        status: 'skip',
        message: '无需检查数据格式',
      };
    }
    
    try {
      const module = await import(moduleDef.path);
      const exports = Object.keys(module);
      
      const hasFormats = expectedFormats.some(f => exports.includes(f));
      
      if (hasFormats) {
        return {
          name: '数据匹配',
          status: 'pass',
          message: `数据格式匹配: ${expectedFormats.join(', ')}`,
        };
      }
      
      return {
        name: '数据匹配',
        status: 'warning',
        message: `建议添加数据格式: ${expectedFormats.join(', ')}`,
      };
    } catch (err: any) {
      return {
        name: '数据匹配',
        status: 'fail',
        message: err.message,
      };
    }
  }

  /**
   * 生成摘要
   */
  private generateSummary(checks: CheckItem[], score: number): string {
    const passed = checks.filter(c => c.status === 'pass').length;
    const failed = checks.filter(c => c.status === 'fail').length;
    const warnings = checks.filter(c => c.status === 'warning').length;
    
    return `得分 ${score}分，通过 ${passed}/${checks.length}，失败 ${failed}，警告 ${warnings}`;
  }

  /**
   * 生成建议
   */
  private generateRecommendations(results: ModuleCheckResult[], coverage: CoverageReport): string[] {
    const recommendations: string[] = [];
    
    // 低分模块建议
    const lowScoreModules = results.filter(r => r.score < 80);
    if (lowScoreModules.length > 0) {
      recommendations.push(
        `⚠️ ${lowScoreModules.length} 个模块得分低于80分，建议优先优化: ${lowScoreModules.map(m => m.name).join('、')}`
      );
    }
    
    // 失败检查建议
    const failedChecks = results.flatMap(r => r.checks.filter(c => c.status === 'fail'));
    if (failedChecks.length > 0) {
      recommendations.push(
        `❌ 发现 ${failedChecks.length} 项失败检查，需要修复`
      );
    }
    
    // 覆盖率建议
    if (coverage.percentage < 90) {
      recommendations.push(
        `📊 模块覆盖率 ${coverage.percentage}%，建议提升至90%以上`
      );
    }
    
    // 关键模块检查
    const criticalModules = MODULE_DEFINITIONS.filter(m => m.critical);
    const criticalResults = results.filter(r => 
      criticalModules.some(m => m.id === r.id)
    );
    const criticalFailed = criticalResults.filter(r => r.status === 'fail');
    if (criticalFailed.length > 0) {
      recommendations.push(
        `🔴 ${criticalFailed.length} 个关键模块检查失败，必须修复: ${criticalFailed.map(m => m.name).join('、')}`
      );
    }
    
    if (recommendations.length === 0) {
      recommendations.push('✅ 所有模块检查通过，质量良好');
    }
    
    return recommendations;
  }

  /**
   * 获取模块定义列表
   */
  getModuleDefinitions() {
    return MODULE_DEFINITIONS;
  }

  /**
   * 快速健康检查
   */
  async quickHealthCheck(): Promise<{ healthy: boolean; issues: string[] }> {
    const issues: string[] = [];
    
    // 检查关键模块
    const criticalModules = MODULE_DEFINITIONS.filter(m => m.critical);
    
    for (const moduleDef of criticalModules) {
      try {
        await import(moduleDef.path);
      } catch {
        issues.push(`关键模块 ${moduleDef.name} 无法加载`);
      }
    }
    
    return {
      healthy: issues.length === 0,
      issues,
    };
  }
}

export const moduleChecker = new ModuleChecker();
export default moduleChecker;
