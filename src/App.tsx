import React, { useState, useEffect } from 'react';
import {
  mapService,
  lbsRadarService,
  competitorMonitorService,
  geoOptimizationEngine,
  dataCrawlerService,
  networkManager,
  resolutionAdapter,
  uiuxService,
  securityService,
  apiRouterService,
} from './services';

// 标签页类型
type TabType = 'dashboard' | 'lbs' | 'competitor' | 'geo' | 'crawler' | 'settings';

const App: React.FC = () => {
  const [activeTab, setActiveTab] = useState<TabType>('dashboard');
  const [loading, setLoading] = useState(false);
  const [data, setData] = useState<any>(null);
  const [theme, setTheme] = useState(uiuxService.getThemeMode());

  // 主题切换
  useEffect(() => {
    uiuxService.setTheme(theme);
    uiuxService.applyTheme();
  }, [theme]);

  // 渲染标签页
  const renderTabs = () => (
    <nav className="flex gap-2 p-4 bg-white border-b">
      {[
        { id: 'dashboard', label: '📊 仪表盘', icon: '📊' },
        { id: 'lbs', label: '📍 LBS雷达', icon: '📍' },
        { id: 'competitor', label: '🎯 竞品监控', icon: '🎯' },
        { id: 'geo', label: '🔍 GEO优化', icon: '🔍' },
        { id: 'crawler', label: '🕷️ 数据爬虫', icon: '🕷️' },
        { id: 'settings', label: '⚙️ 设置', icon: '⚙️' },
      ].map((tab) => (
        <button
          key={tab.id}
          onClick={() => setActiveTab(tab.id as TabType)}
          className={`px-4 py-2 rounded-lg font-medium transition-all ${
            activeTab === tab.id
              ? 'bg-blue-500 text-white'
              : 'bg-gray-100 text-gray-700 hover:bg-gray-200'
          }`}
        >
          {tab.label}
        </button>
      ))}
    </nav>
  );

  // 仪表盘页面
  const renderDashboard = () => {
    const networkInfo = networkManager.getNetworkInfo();
    const screenInfo = resolutionAdapter.getScreenInfo();
    const routeStats = apiRouterService.getStats();
    const securityStats = securityService.getSecurityStats();

    return (
      <div className="p-6 space-y-6 animate-fadeIn">
        <h2 className="text-2xl font-bold">掌上商客 V2.0 - 智能获客中枢</h2>
        
        {/* 核心指标 */}
        <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
          <div className="card">
            <div className="text-3xl font-bold text-blue-500">{routeStats.totalRoutes}</div>
            <div className="text-gray-500">API 路由</div>
          </div>
          <div className="card">
            <div className="text-3xl font-bold text-green-500">{securityStats.totalAudits}</div>
            <div className="text-gray-500">安全审计</div>
          </div>
          <div className="card">
            <div className="text-3xl font-bold text-purple-500">12</div>
            <div className="text-gray-500">数据源</div>
          </div>
          <div className="card">
            <div className="text-3xl font-bold text-orange-500">51</div>
            <div className="text-gray-500">测试项</div>
          </div>
        </div>

        {/* 系统状态 */}
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          <div className="card">
            <h3 className="font-bold mb-3">🌐 网络状态</h3>
            <div className="space-y-2">
              <div className="flex justify-between">
                <span className="text-gray-500">状态</span>
                <span className={`font-medium ${networkInfo.status === 'online' ? 'text-green-500' : 'text-red-500'}`}>
                  {networkInfo.status}
                </span>
              </div>
              <div className="flex justify-between">
                <span className="text-gray-500">类型</span>
                <span>{networkInfo.type}</span>
              </div>
              <div className="flex justify-between">
                <span className="text-gray-500">下行速度</span>
                <span>{networkInfo.downlink} Mbps</span>
              </div>
            </div>
          </div>

          <div className="card">
            <h3 className="font-bold mb-3">📱 设备信息</h3>
            <div className="space-y-2">
              <div className="flex justify-between">
                <span className="text-gray-500">分辨率</span>
                <span>{screenInfo.width} x {screenInfo.height}</span>
              </div>
              <div className="flex justify-between">
                <span className="text-gray-500">设备类型</span>
                <span>{screenInfo.deviceType}</span>
              </div>
              <div className="flex justify-between">
                <span className="text-gray-500">像素密度</span>
                <span>{screenInfo.density}</span>
              </div>
            </div>
          </div>
        </div>

        {/* 功能模块 */}
        <div className="card">
          <h3 className="font-bold mb-3">📦 功能模块</h3>
          <div className="grid grid-cols-2 md:grid-cols-4 gap-3">
            {[
              { name: '地图服务', status: '✅', count: 5 },
              { name: 'LBS雷达', status: '✅', count: 4 },
              { name: '竞品监控', status: '✅', count: 7 },
              { name: 'GEO优化', status: '✅', count: 19 },
              { name: '数据爬虫', status: '✅', count: 12 },
              { name: 'AI服务', status: '✅', count: 5 },
              { name: '安全服务', status: '✅', count: 6 },
              { name: 'UI/UX', status: '✅', count: 4 },
            ].map((mod) => (
              <div key={mod.name} className="flex items-center justify-between p-2 bg-gray-50 rounded-lg">
                <span>{mod.name}</span>
                <span className="chip chip-success">{mod.status} {mod.count}</span>
              </div>
            ))}
          </div>
        </div>
      </div>
    );
  };

  // LBS雷达页面
  const renderLBSRadar = () => (
    <div className="p-6 space-y-6 animate-fadeIn">
      <h2 className="text-2xl font-bold">📍 LBS 雷达扫描</h2>
      <p className="text-gray-500">四层数据融合：地图POI + 品牌CRM + 换机预测 + 国补计算</p>

      <div className="card">
        <h3 className="font-bold mb-3">扫描参数</h3>
        <div className="grid grid-cols-2 md:grid-cols-4 gap-4 mb-4">
          <div>
            <label className="block text-sm text-gray-500 mb-1">中心纬度</label>
            <input type="number" className="w-full p-2 border rounded-lg" defaultValue={39.9087} step="0.0001" />
          </div>
          <div>
            <label className="block text-sm text-gray-500 mb-1">中心经度</label>
            <input type="number" className="w-full p-2 border rounded-lg" defaultValue={116.4667} step="0.0001" />
          </div>
          <div>
            <label className="block text-sm text-gray-500 mb-1">半径 (km)</label>
            <select className="w-full p-2 border rounded-lg">
              <option value="3">3 km</option>
              <option value="5" selected>5 km</option>
              <option value="8">8 km</option>
              <option value="10">10 km</option>
            </select>
          </div>
          <div className="flex items-end">
            <button
              className="btn btn-primary w-full"
              onClick={async () => {
                setLoading(true);
                try {
                  const result = await lbsRadarService.scan('STORE-001', {
                    lat: 39.9087,
                    lng: 116.4667,
                    radius: 5,
                  });
                  setData(result);
                } finally {
                  setLoading(false);
                }
              }}
            >
              {loading ? '扫描中...' : '开始扫描'}
            </button>
          </div>
        </div>
      </div>

      {data && (
        <div className="space-y-4">
          <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
            <div className="card text-center">
              <div className="text-2xl font-bold text-blue-500">{data.salesLeads?.length || 0}</div>
              <div className="text-gray-500">销售线索</div>
            </div>
            <div className="card text-center">
              <div className="text-2xl font-bold text-green-500">{data.layer1POIs?.length || 0}</div>
              <div className="text-gray-500">POI数据</div>
            </div>
            <div className="card text-center">
              <div className="text-2xl font-bold text-purple-500">{data.layer3Predictions?.length || 0}</div>
              <div className="text-gray-500">换机预测</div>
            </div>
            <div className="card text-center">
              <div className="text-2xl font-bold text-orange-500">{data.layer4TradeInQuotes?.length || 0}</div>
              <div className="text-gray-500">以旧换新</div>
            </div>
          </div>

          <div className="card">
            <h3 className="font-bold mb-3">销售线索列表</h3>
            <div className="space-y-2 max-h-64 overflow-auto">
              {data.salesLeads?.slice(0, 10).map((lead: any, i: number) => (
                <div key={i} className="flex justify-between items-center p-2 bg-gray-50 rounded-lg">
                  <div>
                    <span className="font-medium">{lead.name}</span>
                    <span className="text-gray-500 text-sm ml-2">{lead.distance}m</span>
                  </div>
                  <div className="flex gap-2">
                    <span className="chip chip-primary">{lead.type}</span>
                    <span className="chip chip-success">热度 {lead.heatScore}</span>
                  </div>
                </div>
              ))}
            </div>
          </div>
        </div>
      )}
    </div>
  );

  // 竞品监控页面
  const renderCompetitor = () => (
    <div className="p-6 space-y-6 animate-fadeIn">
      <h2 className="text-2xl font-bold">🎯 竞品热力监控</h2>
      <p className="text-gray-500">实时监控门店周边竞品变化，生成截流建议</p>

      <div className="card">
        <h3 className="font-bold mb-3">竞品品牌</h3>
        <div className="flex flex-wrap gap-2">
          {Object.entries(competitorMonitorService.getCompetitorConfigs()).map(([key, config]) => (
            <span key={key} className="chip chip-primary" style={{ backgroundColor: config.color + '20', color: config.color }}>
              {config.name}
            </span>
          ))}
        </div>
      </div>

      <div className="card">
        <button
          className="btn btn-primary"
          onClick={async () => {
            setLoading(true);
            try {
              const result = await competitorMonitorService.scanCompetitors('STORE-001', 39.9087, 116.4667, 5000);
              setData(result);
            } finally {
              setLoading(false);
            }
          }}
        >
          {loading ? '扫描中...' : '扫描竞品'}
        </button>
      </div>

      {data && (
        <div className="space-y-4">
          <div className="card">
            <h3 className="font-bold mb-2">扫描结果</h3>
            <p className="text-gray-600">{data.summary}</p>
          </div>

          <div className="grid grid-cols-3 gap-4">
            <div className="card text-center">
              <div className="text-2xl font-bold text-blue-500">{data.totalCompetitors}</div>
              <div className="text-gray-500">竞品总数</div>
            </div>
            <div className="card text-center">
              <div className="text-2xl font-bold text-green-500">{data.newStores?.length || 0}</div>
              <div className="text-gray-500">新开门店</div>
            </div>
            <div className="card text-center">
              <div className="text-2xl font-bold text-red-500">{data.closedStores?.length || 0}</div>
              <div className="text-gray-500">关闭门店</div>
            </div>
          </div>
        </div>
      )}
    </div>
  );

  // GEO优化页面
  const renderGEO = () => {
    const keywords = geoOptimizationEngine.getKeywords();
    const regions = geoOptimizationEngine.getChinaRegions(3);

    return (
      <div className="p-6 space-y-6 animate-fadeIn">
        <h2 className="text-2xl font-bold">🔍 GEO 搜索优化</h2>
        <p className="text-gray-500">针对AI搜索引擎优化，提升门店在线可见性</p>

        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          <div className="card">
            <h3 className="font-bold mb-3">品牌关键词矩阵</h3>
            <div className="space-y-2 max-h-64 overflow-auto">
              {keywords.slice(0, 10).map((kw) => (
                <div key={kw.id} className="flex justify-between items-center p-2 bg-gray-50 rounded-lg">
                  <span>{kw.keyword}</span>
                  <span className="chip chip-primary">{kw.category}</span>
                </div>
              ))}
            </div>
          </div>

          <div className="card">
            <h3 className="font-bold mb-3">行政区划覆盖</h3>
            <div className="grid grid-cols-3 gap-2 text-center">
              <div className="p-2 bg-blue-50 rounded-lg">
                <div className="text-xl font-bold text-blue-500">{regions.provinces.length}</div>
                <div className="text-xs text-gray-500">省级</div>
              </div>
              <div className="p-2 bg-green-50 rounded-lg">
                <div className="text-xl font-bold text-green-500">{regions.cities.length}</div>
                <div className="text-xs text-gray-500">城市</div>
              </div>
              <div className="p-2 bg-purple-50 rounded-lg">
                <div className="text-xl font-bold text-purple-500">{regions.districts.length}</div>
                <div className="text-xs text-gray-500">区县</div>
              </div>
            </div>
          </div>
        </div>

        <div className="card">
          <h3 className="font-bold mb-3">AI搜索平台</h3>
          <div className="flex flex-wrap gap-2">
            {['豆包', 'ChatGPT', '微信AI', '腾讯元宝', 'DeepSeek', 'Kimi'].map((platform) => (
              <span key={platform} className="chip chip-success">{platform}</span>
            ))}
          </div>
        </div>
      </div>
    );
  };

  // 数据爬虫页面
  const renderCrawler = () => {
    const configs = dataCrawlerService.getAllConfigs();
    const dimensions = dataCrawlerService.getDataDimensions();

    return (
      <div className="p-6 space-y-6 animate-fadeIn">
        <h2 className="text-2xl font-bold">🕷️ 数据爬虫采集</h2>
        <p className="text-gray-500">多源数据采集，支持12个数据源</p>

        <div className="card">
          <h3 className="font-bold mb-3">数据源配置</h3>
          <div className="grid grid-cols-2 md:grid-cols-3 gap-2">
            {configs.map((config) => (
              <div key={config.source} className="p-2 bg-gray-50 rounded-lg flex justify-between items-center">
                <span>{config.name}</span>
                <span className={`chip ${config.enabled ? 'chip-success' : 'chip-error'}`}>
                  {config.enabled ? '启用' : '禁用'}
                </span>
              </div>
            ))}
          </div>
        </div>

        <div className="card">
          <h3 className="font-bold mb-3">2026 数据维度</h3>
          <div className="grid grid-cols-2 md:grid-cols-5 gap-2">
            {Object.entries(dimensions).map(([key, dim]: [string, any]) => (
              <div key={key} className="p-3 bg-gray-50 rounded-lg text-center">
                <div className="font-bold capitalize">{key}</div>
                <div className="text-xs text-gray-500">{dim.sources.length} 源</div>
              </div>
            ))}
          </div>
        </div>

        <div className="card">
          <h3 className="font-bold mb-3">快速操作</h3>
          <div className="flex flex-wrap gap-2">
            <button
              className="btn btn-primary"
              onClick={async () => {
                setLoading(true);
                try {
                  const result = await dataCrawlerService.crawlPOI({
                    query: '华为体验店',
                    lat: 39.9087,
                    lng: 116.4667,
                    radius: 5000,
                  });
                  setData(result);
                } finally {
                  setLoading(false);
                }
              }}
            >
              采集 POI
            </button>
            <button
              className="btn btn-secondary"
              onClick={async () => {
                setLoading(true);
                try {
                  const result = await dataCrawlerService.crawlProduct({ brand: '华为' });
                  setData(result);
                } finally {
                  setLoading(false);
                }
              }}
            >
              采集产品
            </button>
            <button
              className="btn btn-outline"
              onClick={async () => {
                setLoading(true);
                try {
                  const result = await dataCrawlerService.crawlSubsidy({});
                  setData(result);
                } finally {
                  setLoading(false);
                }
              }}
            >
              采集补贴
            </button>
          </div>
        </div>

        {data && (
          <div className="card">
            <h3 className="font-bold mb-2">采集结果</h3>
            <p>成功采集 {data.count} 条数据，耗时 {data.duration}ms</p>
          </div>
        )}
      </div>
    );
  };

  // 设置页面
  const renderSettings = () => (
    <div className="p-6 space-y-6 animate-fadeIn">
      <h2 className="text-2xl font-bold">⚙️ 系统设置</h2>

      <div className="card">
        <h3 className="font-bold mb-3">🎨 主题设置</h3>
        <div className="flex gap-2">
          {(['light', 'dark', 'auto'] as const).map((t) => (
            <button
              key={t}
              onClick={() => setTheme(t)}
              className={`btn ${theme === t ? 'btn-primary' : 'btn-outline'}`}
            >
              {t === 'light' ? '☀️ 亮色' : t === 'dark' ? '🌙 暗色' : '🔄 自动'}
            </button>
          ))}
        </div>
      </div>

      <div className="card">
        <h3 className="font-bold mb-3">🔒 隐私策略</h3>
        <div className="space-y-2">
          {securityService.getPrivacyPolicies().map((policy) => (
            <div key={policy.id} className="flex justify-between items-center p-2 bg-gray-50 rounded-lg">
              <div>
                <span className="font-medium">{policy.category}</span>
                <span className="text-gray-500 text-sm ml-2">{policy.description}</span>
              </div>
              <div className="flex gap-1">
                {policy.encryption && <span className="chip chip-primary">加密</span>}
                {policy.anonymization && <span className="chip chip-success">脱敏</span>}
              </div>
            </div>
          ))}
        </div>
      </div>

      <div className="card">
        <h3 className="font-bold mb-3">📊 系统信息</h3>
        <div className="space-y-2">
          <div className="flex justify-between">
            <span className="text-gray-500">版本</span>
            <span>V2.0.0</span>
          </div>
          <div className="flex justify-between">
            <span className="text-gray-500">服务数量</span>
            <span>23 个</span>
          </div>
          <div className="flex justify-between">
            <span className="text-gray-500">测试覆盖</span>
            <span>51 项 (98% 通过)</span>
          </div>
        </div>
      </div>
    </div>
  );

  // 渲染内容
  const renderContent = () => {
    switch (activeTab) {
      case 'dashboard':
        return renderDashboard();
      case 'lbs':
        return renderLBSRadar();
      case 'competitor':
        return renderCompetitor();
      case 'geo':
        return renderGEO();
      case 'crawler':
        return renderCrawler();
      case 'settings':
        return renderSettings();
      default:
        return renderDashboard();
    }
  };

  return (
    <div className="min-h-screen bg-gray-50">
      {/* 顶部栏 */}
      <header className="bg-white shadow-sm">
        <div className="max-w-7xl mx-auto px-4 py-3 flex justify-between items-center">
          <div className="flex items-center gap-2">
            <span className="text-2xl">📱</span>
            <h1 className="text-xl font-bold">掌上商客 V2.0</h1>
          </div>
          <div className="flex items-center gap-4">
            <span className="text-sm text-gray-500">HandBiz Radar</span>
            <span className="chip chip-success">在线</span>
          </div>
        </div>
      </header>

      {/* 标签栏 */}
      {renderTabs()}

      {/* 内容区 */}
      <main className="max-w-7xl mx-auto">
        {renderContent()}
      </main>

      {/* 底部栏 */}
      <footer className="bg-white border-t mt-8">
        <div className="max-w-7xl mx-auto px-4 py-4 text-center text-gray-500 text-sm">
          © 2026 掌上商客 V2.0 - 智能获客中枢 | 支持 12 数据源 | 51 项测试
        </div>
      </footer>
    </div>
  );
};

export default App;
