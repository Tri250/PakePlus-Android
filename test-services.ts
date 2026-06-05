/**
 * 接口验证测试脚本
 * 验证所有服务数据回传接口是否正常
 */

async function runTests() {
  console.log('='.repeat(60));
  console.log('掌上商客 V2.0 - 接口验证测试');
  console.log('测试时间:', new Date().toLocaleString('zh-CN'));
  console.log('='.repeat(60));

  try {
    // 动态导入服务
    const services = await import('./src/services/index.js');
    
    const { 
      quickHealthCheck, 
      runAllTests,
      MAP_PROVIDERS,
      POSTER_TEMPLATES,
      DEFAULT_SYNC_CONFIGS,
      PRESET_SCANS
    } = services;

    // 1. 显示配置信息
    console.log('\n📋 配置信息汇总:');
    console.log('─'.repeat(40));
    
    console.log('\n🗺️ 地图服务提供商 (2026免费资源):');
    Object.entries(MAP_PROVIDERS).forEach(([key, config]: [string, any]) => {
      const hasKey = config.apiKey ? '✅ 已配置' : '⚠️ 未配置(使用免费额度)';
      console.log(`  - ${key}: ${hasKey} (优先级: ${config.priority})`);
    });

    console.log('\n📊 数据采集预设 (3-5-8-10km):');
    Object.entries(PRESET_SCANS).forEach(([key, config]: [string, any]) => {
      console.log(`  - ${key}: ${config.radius}km, ${config.categories.length}类目, 最多${config.maxResults}条`);
    });

    console.log('\n🖼️ 海报模板:');
    Object.entries(POSTER_TEMPLATES).forEach(([key, template]: [string, any]) => {
      console.log(`  - ${template.title} (${template.template})`);
    });

    console.log('\n🔄 数据同步目标 (品牌CRM):');
    DEFAULT_SYNC_CONFIGS.forEach((config: any) => {
      console.log(`  - ${config.name}: ${config.enabled ? '✅ 启用' : '❌ 禁用'}`);
    });

    // 2. 快速健康检查
    console.log('\n\n🔍 执行快速健康检查...');
    console.log('─'.repeat(40));
    
    const healthCheck = await quickHealthCheck();
    
    console.log('\n健康检查结果:');
    const healthEntries = Object.entries(healthCheck) as [string, boolean][];
    const healthyCount = healthEntries.filter(([, v]) => v).length;
    healthEntries.forEach(([service, result]) => {
      const status = result ? '✅ 正常' : '❌ 异常';
      console.log(`  ${service}: ${status}`);
    });
    console.log(`\n健康率: ${healthyCount}/${healthEntries.length} (${((healthyCount/healthEntries.length)*100).toFixed(1)}%)`);

    // 3. 完整测试报告
    console.log('\n\n📊 完整测试报告:');
    console.log('─'.repeat(40));
    
    const fullReport = await runAllTests();
    
    console.log(`\n总测试数: ${fullReport.totalTests}`);
    console.log(`通过: ${fullReport.passed} ✅`);
    console.log(`失败: ${fullReport.failed} ❌`);
    console.log(`警告: ${fullReport.warnings} ⚠️`);
    console.log(`\n摘要: ${fullReport.summary}`);

    // 4. 分类统计
    console.log('\n\n📈 分类统计:');
    console.log('─'.repeat(40));
    
    const categories: Record<string, { passed: number; failed: number; warnings: number }> = {};
    fullReport.results.forEach((r: any) => {
      const category = r.category;
      if (!categories[category]) {
        categories[category] = { passed: 0, failed: 0, warnings: 0 };
      }
      if (r.status === 'pass') categories[category].passed++;
      else if (r.status === 'warn') categories[category].warnings++;
      else categories[category].failed++;
    });

    Object.entries(categories).forEach(([cat, stats]) => {
      const total = stats.passed + stats.failed + stats.warnings;
      console.log(`  ${cat}: ${stats.passed}/${total} 通过, ${stats.warnings} 警告`);
    });

    // 5. 失败详情
    const failedTests = fullReport.results.filter((r: any) => r.status === 'fail');
    if (failedTests.length > 0) {
      console.log('\n\n❌ 失败测试详情:');
      console.log('─'.repeat(40));
      failedTests.forEach((r: any) => {
        console.log(`\n  ${r.name}:`);
        console.log(`    消息: ${r.message}`);
      });
    }

    // 6. 总结
    console.log('\n\n' + '='.repeat(60));
    console.log('测试完成!');
    console.log('='.repeat(60));
    
    const passRate = (fullReport.passed / fullReport.totalTests) * 100;
    if (fullReport.failed === 0) {
      console.log('✅ 所有接口验证通过，数据回传正常！');
    } else if (passRate >= 80) {
      console.log(`⚠️ 大部分接口正常 (${passRate.toFixed(1)}%)，部分接口需要配置API Key。`);
    } else {
      console.log(`❌ 多个接口测试失败，请检查配置。`);
    }

    process.exit(fullReport.failed > 0 ? 1 : 0);

  } catch (error) {
    console.error('\n❌ 测试执行出错:', error);
    process.exit(1);
  }
}

runTests();
