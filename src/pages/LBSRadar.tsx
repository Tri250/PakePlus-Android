import { useState, useEffect } from 'react';
import { Radar, MapPin, Building2, Home, School, ShoppingBag, Navigation, TrendingUp, Clock, Filter, Loader2, Check, Route, AlertCircle, RefreshCw, Crosshair, Lock, Unlock } from 'lucide-react';
import { useGeolocation, calculateDistance, formatDistance } from '../hooks/useGeolocation';

const mockPOIs = [
  { id: '1', name: '中关村科技大厦A座', type: 'office', address: '海淀区中关村大街1号', lat: 39.9841, lng: 116.3073, score: 92, rate: 8.5, time: '11:30-13:30' },
  { id: '2', name: '创业大厦', type: 'office', address: '海淀区中关村大街2号', lat: 39.9825, lng: 116.3089, score: 85, rate: 7.2, time: '11:30-13:30' },
  { id: '3', name: '知春里小区', type: 'residential', address: '海淀区知春路', lat: 39.9798, lng: 116.3102, score: 78, rate: 6.5, time: '18:00-20:00' },
  { id: '4', name: '海淀黄庄购物中心', type: 'mall', address: '海淀区海淀黄庄', lat: 39.9812, lng: 116.3056, score: 88, rate: 7.8, time: '10:00-22:00' },
  { id: '5', name: '中关村第一小学', type: 'school', address: '海淀区中关村', lat: 39.9835, lng: 116.3068, score: 82, rate: 6.9, time: '07:30-08:30' },
];

export default function LBSRadar() {
  const [radius, setRadius] = useState(5000);
  const [scanning, setScanning] = useState(false);
  const [scanned, setScanned] = useState(false);
  const [currentAddress, setCurrentAddress] = useState('正在获取位置...');
  const [filteredPOIs, setFilteredPOIs] = useState<typeof mockPOIs>([]);

  const { 
    loading: locationLoading, 
    error: locationError, 
    location, 
    accuracy, 
    requestLocation, 
    hasPermission 
  } = useGeolocation();

  useEffect(() => {
    if (location) {
      setCurrentAddress(`当前位置 (${location.lat.toFixed(4)}, ${location.lng.toFixed(4)})`);
      const poisWithDistance = mockPOIs.map(poi => {
        const distance = calculateDistance(location.lat, location.lng, poi.lat, poi.lng);
        return { ...poi, distance };
      }).filter((poi): poi is typeof mockPOIs[0] & { distance: number } => 
        typeof (poi as any).distance === 'number' && (poi as any).distance <= radius
      )
        .sort((a, b) => a.distance - b.distance);
      
      setFilteredPOIs(poisWithDistance);
    }
  }, [location, radius]);

  const handleScan = () => {
    if (!location) {
      requestLocation();
      return;
    }
    setScanning(true);
    setScanned(false);
    setTimeout(() => {
      setScanning(false);
      setScanned(true);
    }, 2000);
  };

  const handleRefreshLocation = () => {
    setScanned(false);
    setFilteredPOIs([]);
    requestLocation();
  };

  const renderPermissionStatus = () => {
    if (hasPermission === null) {
      return (
        <div className="flex items-center gap-2 px-3 py-2 bg-gray-50 rounded-lg">
          <Loader2 className="w-4 h-4 text-gray-400 animate-spin" />
          <span className="text-sm text-gray-500">检查权限状态...</span>
        </div>
      );
    }
    if (hasPermission === false) {
      return (
        <div className="flex items-center gap-2 px-3 py-2 bg-red-50 rounded-lg border border-red-200">
          <Lock className="w-4 h-4 text-red-500" />
          <span className="text-sm text-red-600">定位权限未开启</span>
        </div>
      );
    }
    return (
      <div className="flex items-center gap-2 px-3 py-2 bg-green-50 rounded-lg border border-green-200">
        <Unlock className="w-4 h-4 text-green-500" />
        <span className="text-sm text-green-600">定位权限已开启</span>
      </div>
    );
  };

  return (
    <div className="space-y-6">
      {!location && (
        <div className={`rounded-xl border p-4 ${locationError ? 'bg-red-50 border-red-200' : 'bg-blue-50 border-blue-200'}`}>
          <div className="flex items-start gap-3">
            {locationError ? (
              <AlertCircle className="w-6 h-6 text-red-500 flex-shrink-0 mt-0.5" />
            ) : (
              <Crosshair className="w-6 h-6 text-blue-500 flex-shrink-0 mt-0.5 animate-pulse" />
            )}
            <div className="flex-1">
              <h3 className={`font-medium ${locationError ? 'text-red-800' : 'text-blue-800'}`}>
                {locationError ? '定位失败' : '需要获取您的位置'}
              </h3>
              <p className={`text-sm mt-1 ${locationError ? 'text-red-600' : 'text-blue-600'}`}>
                {locationError || 'LBS雷达需要获取您的位置才能扫描周边POI，请点击下方按钮授权'}
              </p>
              <div className="mt-3 flex items-center gap-3">
                <button
                  onClick={requestLocation}
                  disabled={locationLoading}
                  className={`flex items-center gap-2 px-4 py-2 rounded-lg text-white font-medium ${
                    locationError ? 'bg-red-600 hover:bg-red-700' : 'bg-blue-600 hover:bg-blue-700'
                  } disabled:opacity-50 transition-colors`}
                >
                  {locationLoading ? (
                    <>
                      <Loader2 className="w-5 h-5 animate-spin" />
                      正在定位...
                    </>
                  ) : (
                    <>
                      <Crosshair className="w-5 h-5" />
                      {locationError ? '重新获取位置' : '获取位置'}
                    </>
                  )}
                </button>
                {renderPermissionStatus()}
              </div>
            </div>
          </div>
        </div>
      )}

      <div className="bg-white rounded-xl border border-gray-200 p-4">
        <div className="flex flex-wrap items-center gap-4">
          <div className="flex items-center gap-2 px-3 py-2 bg-gray-50 rounded-lg">
            <MapPin className="w-5 h-5 text-blue-600" />
            <span className="text-sm text-gray-700">{currentAddress}</span>
            {accuracy && (
              <span className="text-xs text-gray-400">精度: {Math.round(accuracy)}m</span>
            )}
          </div>
          <button 
            onClick={handleRefreshLocation}
            disabled={locationLoading}
            className="p-2 hover:bg-gray-100 rounded-lg disabled:opacity-50"
            title="刷新位置"
          >
            <RefreshCw className={`w-5 h-5 text-gray-500 ${locationLoading ? 'animate-spin' : ''}`} />
          </button>
          <div className="flex items-center gap-2">
            <span className="text-sm text-gray-500">半径：</span>
            <div className="flex gap-1">
              {[3000, 5000, 8000, 10000].map((r) => (
                <button 
                  key={r} 
                  onClick={() => setRadius(r)} 
                  className={`px-3 py-1.5 text-sm rounded-lg transition-colors ${
                    radius === r ? 'bg-blue-600 text-white' : 'bg-gray-100 text-gray-700 hover:bg-gray-200'
                  }`}
                >
                  {r / 1000}km
                </button>
              ))}
            </div>
          </div>
          <button 
            onClick={handleScan} 
            disabled={scanning || !location} 
            className="flex items-center gap-2 px-4 py-2 bg-gradient-to-r from-blue-600 to-purple-600 text-white rounded-lg hover:from-blue-700 hover:to-purple-700 disabled:opacity-50 disabled:cursor-not-allowed transition-all"
          >
            {scanning ? (
              <>
                <Loader2 className="w-5 h-5 animate-spin" /> 
                扫描中...
              </>
            ) : (
              <>
                <Radar className="w-5 h-5" /> 
                {location ? '开始扫描' : '请先定位'}
              </>
            )}
          </button>
        </div>
        <div className="mt-4 flex flex-wrap items-center gap-2">
          <span className="text-sm text-gray-500">
            <Filter className="w-4 h-4 inline mr-1" />筛选：
          </span>
          {[
            { type: 'office', label: '写字楼', icon: Building2 }, 
            { type: 'residential', label: '小区', icon: Home }, 
            { type: 'school', label: '学校', icon: School }, 
            { type: 'mall', label: '商场', icon: ShoppingBag }
          ].map((t) => (
            <button 
              key={t.type} 
              className="flex items-center gap-1.5 px-3 py-1.5 text-sm rounded-lg bg-blue-100 text-blue-700 border border-blue-200 hover:bg-blue-200 transition-colors"
            >
              <t.icon className="w-4 h-4" />
              {t.label}
              <Check className="w-3 h-3" />
            </button>
          ))}
        </div>
      </div>

      <div className="bg-white rounded-xl border border-gray-200 overflow-hidden">
        <div className="h-80 bg-gradient-to-br from-blue-50 to-purple-50 relative flex items-center justify-center">
          {scanning && (
            <div className="absolute inset-0 flex items-center justify-center">
              <div className="w-32 h-32 rounded-full border-4 border-blue-400 animate-ping opacity-75" />
              <div className="absolute w-24 h-24 rounded-full border-4 border-purple-400 animate-ping opacity-50" style={{ animationDelay: '0.5s' }} />
            </div>
          )}
          {scanned && filteredPOIs.map((poi, i) => (
            <div 
              key={poi.id} 
              className="absolute cursor-pointer transform hover:scale-110 transition-transform"
              style={{ 
                left: `${15 + i * 18}%`, 
                top: `${25 + (i % 3) * 20}%` 
              }}
            >
              <div className={`w-10 h-10 rounded-full flex items-center justify-center shadow-lg text-white text-sm font-medium ${
                poi.score >= 85 ? 'bg-green-500' : poi.score >= 75 ? 'bg-blue-500' : 'bg-yellow-500'
              }`}>
                {poi.score}
              </div>
              <div className="absolute -bottom-6 left-1/2 transform -translate-x-1/2 whitespace-nowrap text-xs bg-white px-2 py-0.5 rounded shadow">
                {poi.name.slice(0, 6)}
              </div>
            </div>
          ))}
          <div className="relative z-10">
            <div className="w-4 h-4 bg-blue-600 rounded-full shadow-lg animate-pulse" />
            <div className="absolute -top-8 left-1/2 transform -translate-x-1/2 whitespace-nowrap text-xs bg-blue-600 text-white px-2 py-1 rounded">
              我的门店
            </div>
          </div>
          {!location && !locationLoading && (
            <div className="absolute inset-0 bg-gray-100/80 flex items-center justify-center">
              <div className="text-center">
                <Crosshair className="w-12 h-12 text-gray-400 mx-auto mb-2" />
                <p className="text-gray-500">请先获取位置</p>
              </div>
            </div>
          )}
        </div>
      </div>

      {scanned && (
        <div className="bg-white rounded-xl border border-gray-200">
          <div className="p-4 border-b border-gray-200 flex items-center justify-between">
            <div className="flex items-center gap-2">
              <h2 className="font-semibold text-gray-900">扫描结果</h2>
              <span className="px-2 py-0.5 bg-blue-100 text-blue-600 text-xs rounded-full">
                {filteredPOIs.length}个POI
              </span>
              {location && (
                <span className="px-2 py-0.5 bg-green-100 text-green-600 text-xs rounded-full">
                  半径 {formatDistance(radius)}
                </span>
              )}
            </div>
            <button className="flex items-center gap-2 px-3 py-1.5 bg-green-600 text-white text-sm rounded-lg hover:bg-green-700 transition-colors">
              <Route className="w-4 h-4" />
              生成路线
            </button>
          </div>
          
          {filteredPOIs.length > 0 ? (
            <div className="divide-y divide-gray-100">
              {filteredPOIs.map((poi) => (
                <div key={poi.id} className="p-4 hover:bg-gray-50 cursor-pointer transition-colors">
                  <div className="flex items-start gap-3">
                    <div className="w-10 h-10 rounded-lg bg-blue-100 flex items-center justify-center">
                      {poi.type === 'office' && <Building2 className="w-5 h-5 text-blue-600" />}
                      {poi.type === 'residential' && <Home className="w-5 h-5 text-blue-600" />}
                      {poi.type === 'school' && <School className="w-5 h-5 text-blue-600" />}
                      {poi.type === 'mall' && <ShoppingBag className="w-5 h-5 text-blue-600" />}
                    </div>
                    <div className="flex-1">
                      <div className="flex items-center gap-2">
                        <p className="font-medium text-gray-900">{poi.name}</p>
                        <span className={`px-2 py-0.5 text-xs rounded ${
                          poi.score >= 85 ? 'text-green-600 bg-green-50' : 
                          poi.score >= 75 ? 'text-blue-600 bg-blue-50' : 'text-yellow-600 bg-yellow-50'
                        }`}>
                          {poi.score}分
                        </span>
                      </div>
                      <p className="text-sm text-gray-500">{poi.address}</p>
                      <div className="flex items-center gap-4 mt-2 text-xs text-gray-500">
                        <span>
                          <MapPin className="w-3 h-3 inline" /> 
                          {'distance' in poi ? formatDistance((poi as any).distance) : '--'}
                        </span>
                        <span>
                          <TrendingUp className="w-3 h-3 inline" /> 
                          转化率 {poi.rate}%
                        </span>
                        <span>
                          <Clock className="w-3 h-3 inline" /> 
                          {poi.time}
                        </span>
                      </div>
                    </div>
                    <button className="p-2 hover:bg-gray-100 rounded-lg transition-colors">
                      <Navigation className="w-5 h-5 text-gray-400" />
                    </button>
                  </div>
                </div>
              ))}
            </div>
          ) : (
            <div className="p-8 text-center">
              <MapPin className="w-12 h-12 text-gray-300 mx-auto mb-2" />
              <p className="text-gray-500">当前半径内未找到POI，请尝试扩大搜索范围</p>
            </div>
          )}
        </div>
      )}
    </div>
  );
}
