import { useState } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import {
  Settings as SettingsIcon,
  User,
  Building2,
  Bell,
  Shield,
  Palette,
  Globe,
  Database,
  Key,
  LogOut,
  Save,
  RefreshCw,
  CheckCircle2,
  AlertCircle,
  Moon,
  Sun,
  ChevronRight,
  MapPin,
  Smartphone,
  Mail,
  Clock,
  Trash2,
  Download,
  Upload,
} from 'lucide-react';
import { toastSuccess, toastError } from '../components/Toast';
import { ConfirmDialog, useConfirmDialog } from '../components/ConfirmDialog';

/* -------------------------------------------------------------------------- */
/*  系统设置 V2.0 - 完整配置管理                                                 */
/* -------------------------------------------------------------------------- */

type Tab = 'profile' | 'store' | 'map' | 'notification' | 'security' | 'data';

interface StoreConfig {
  name: string;
  city: string;
  address: string;
  lat: number;
  lng: number;
  radius: number;
  phone: string;
  openHours: string;
}

interface MapConfig {
  provider: 'amap' | 'tencent' | 'nominatim';
  amapKey: string;
  tencentKey: string;
  cacheEnabled: boolean;
  cacheTTL: number;
}

interface NotificationConfig {
  email: boolean;
  sms: boolean;
  wechat: boolean;
  push: boolean;
  quietHours: boolean;
  quietStart: string;
  quietEnd: string;
}

const DEFAULT_STORE: StoreConfig = {
  name: '国贸旗舰店',
  city: '北京',
  address: '北京市朝阳区建国门外大街 2 号',
  lat: 39.9087,
  lng: 116.473168,
  radius: 3,
  phone: '010-88888888',
  openHours: '09:00-21:00',
};

const DEFAULT_MAP: MapConfig = {
  provider: 'amap',
  amapKey: '',
  tencentKey: '',
  cacheEnabled: true,
  cacheTTL: 24,
};

const DEFAULT_NOTIFICATION: NotificationConfig = {
  email: true,
  sms: true,
  wechat: true,
  push: false,
  quietHours: false,
  quietStart: '22:00',
  quietEnd: '08:00',
};

export default function Settings() {
  const [tab, setTab] = useState<Tab>('profile');
  const [saving, setSaving] = useState(false);
  const [darkMode, setDarkMode] = useState(false);
  const [store, setStore] = useState<StoreConfig>(DEFAULT_STORE);
  const [mapConfig, setMapConfig] = useState<MapConfig>(DEFAULT_MAP);
  const [notification, setNotification] = useState<NotificationConfig>(DEFAULT_NOTIFICATION);
  const confirm = useConfirmDialog();

  const handleSave = async () => {
    setSaving(true);
    await new Promise((r) => setTimeout(r, 1200));
    setSaving(false);
    toastSuccess('设置已保存');
  };

  const handleClearCache = async () => {
    confirm.confirm({
      title: '清除缓存',
      message: '确定要清除所有地图数据缓存吗？这不会影响您的其他设置。',
      variant: 'warning',
      onConfirm: async () => {
        await new Promise((r) => setTimeout(r, 800));
        toastSuccess('缓存已清除');
      },
    });
  };

  const handleLogout = () => {
    confirm.confirm({
      title: '退出登录',
      message: '确定要退出当前账号吗？退出后需要重新登录。',
      variant: 'danger',
      onConfirm: async () => {
        toastSuccess('已退出登录');
        // 实际退出逻辑
      },
    });
  };

  return (
    <div className="space-y-6">
      {/* 顶部 */}
      <div className="bg-gradient-to-r from-slate-800 via-slate-700 to-slate-800 rounded-2xl p-5 text-white">
        <div className="flex flex-wrap items-center justify-between gap-4">
          <div>
            <div className="flex items-center gap-2 mb-1">
              <SettingsIcon className="w-5 h-5" />
              <h1 className="text-xl font-bold">系统设置 V2.0</h1>
            </div>
            <p className="text-sm text-slate-300">
              门店配置 / 地图服务 / 通知设置 / 安全与隐私 / 数据管理
            </p>
          </div>
          <div className="flex items-center gap-2">
            <button
              onClick={handleSave}
              disabled={saving}
              className="px-4 py-2 bg-emerald-600 text-white rounded-lg text-sm font-medium flex items-center gap-2 disabled:opacity-50"
            >
              {saving ? <RefreshCw className="w-4 h-4 animate-spin" /> : <Save className="w-4 h-4" />}
              {saving ? '保存中...' : '保存设置'}
            </button>
          </div>
        </div>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-4 gap-4">
        {/* 侧边导航 */}
        <div className="bg-white rounded-xl border border-gray-200 p-2">
          {[
            { key: 'profile', label: '个人资料', icon: User },
            { key: 'store', label: '门店配置', icon: Building2 },
            { key: 'map', label: '地图服务', icon: MapPin },
            { key: 'notification', label: '通知设置', icon: Bell },
            { key: 'security', label: '安全与隐私', icon: Shield },
            { key: 'data', label: '数据管理', icon: Database },
          ].map((t) => {
            const Icon = t.icon;
            const active = tab === t.key;
            return (
              <button
                key={t.key}
                onClick={() => setTab(t.key as Tab)}
                className={`w-full flex items-center gap-3 px-3 py-2.5 rounded-lg text-left transition-colors ${
                  active ? 'bg-slate-100 text-slate-900' : 'text-gray-600 hover:bg-gray-50'
                }`}
              >
                <Icon className="w-4 h-4" />
                <span className="text-sm font-medium">{t.label}</span>
                {active && <ChevronRight className="w-4 h-4 ml-auto" />}
              </button>
            );
          })}
        </div>

        {/* 内容区 */}
        <div className="lg:col-span-3">
          <AnimatePresence mode="wait">
            {tab === 'profile' && (
              <motion.div
                key="profile"
                initial={{ opacity: 0, y: 20 }}
                animate={{ opacity: 1, y: 0 }}
                exit={{ opacity: 0, y: -20 }}
                className="bg-white rounded-xl border border-gray-200 p-6 space-y-4"
              >
                <h3 className="font-semibold text-gray-900 mb-4">个人资料</h3>
                <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                  <Field label="姓名" defaultValue="李店长" icon={User} />
                  <Field label="手机号" defaultValue="138****8888" icon={Smartphone} />
                  <Field label="邮箱" defaultValue="liming@handbiz.com" icon={Mail} />
                  <Field label="角色" defaultValue="店长" disabled />
                </div>
                <div className="pt-4 border-t border-gray-200">
                  <div className="flex items-center justify-between">
                    <div>
                      <div className="text-sm font-medium text-gray-900">深色模式</div>
                      <div className="text-xs text-gray-500">切换界面主题</div>
                    </div>
                    <button
                      onClick={() => setDarkMode(!darkMode)}
                      className={`w-12 h-6 rounded-full transition-colors ${
                        darkMode ? 'bg-slate-800' : 'bg-gray-200'
                      }`}
                    >
                      <motion.div
                        className="w-5 h-5 bg-white rounded-full shadow"
                        animate={{ x: darkMode ? 24 : 2 }}
                        transition={{ type: 'spring', stiffness: 500, damping: 30 }}
                      />
                    </button>
                  </div>
                </div>
              </motion.div>
            )}

            {tab === 'store' && (
              <motion.div
                key="store"
                initial={{ opacity: 0, y: 20 }}
                animate={{ opacity: 1, y: 0 }}
                exit={{ opacity: 0, y: -20 }}
                className="bg-white rounded-xl border border-gray-200 p-6 space-y-4"
              >
                <h3 className="font-semibold text-gray-900 mb-4">门店配置</h3>
                <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                  <Field label="门店名称" value={store.name} onChange={(v) => setStore({ ...store, name: v })} />
                  <Field label="所在城市" value={store.city} onChange={(v) => setStore({ ...store, city: v })} />
                  <div className="md:col-span-2">
                    <Field label="详细地址" value={store.address} onChange={(v) => setStore({ ...store, address: v })} />
                  </div>
                  <Field label="联系电话" value={store.phone} onChange={(v) => setStore({ ...store, phone: v })} />
                  <Field label="营业时间" value={store.openHours} onChange={(v) => setStore({ ...store, openHours: v })} />
                  <Field label="LBS 扫描半径 (km)" type="number" value={String(store.radius)} onChange={(v) => setStore({ ...store, radius: Number(v) })} />
                  <div className="flex items-center gap-2 text-sm text-gray-600">
                    <MapPin className="w-4 h-4" />
                    坐标：{store.lat.toFixed(6)}, {store.lng.toFixed(6)}
                  </div>
                </div>
              </motion.div>
            )}

            {tab === 'map' && (
              <motion.div
                key="map"
                initial={{ opacity: 0, y: 20 }}
                animate={{ opacity: 1, y: 0 }}
                exit={{ opacity: 0, y: -20 }}
                className="bg-white rounded-xl border border-gray-200 p-6 space-y-4"
              >
                <h3 className="font-semibold text-gray-900 mb-4">地图服务配置</h3>
                <div className="space-y-4">
                  <div>
                    <label className="text-xs text-gray-500 block mb-2">主服务提供商</label>
                    <div className="grid grid-cols-3 gap-2">
                      {[
                        { value: 'amap', label: '高德地图', recommended: true },
                        { value: 'tencent', label: '腾讯位置服务' },
                        { value: 'nominatim', label: 'Nominatim (免费兜底)' },
                      ].map((p) => (
                        <button
                          key={p.value}
                          onClick={() => setMapConfig({ ...mapConfig, provider: p.value as any })}
                          className={`p-3 rounded-lg border text-left ${
                            mapConfig.provider === p.value
                              ? 'border-blue-500 bg-blue-50'
                              : 'border-gray-200 hover:border-gray-300'
                          }`}
                        >
                          <div className="text-sm font-medium text-gray-900">{p.label}</div>
                          {p.recommended && (
                            <div className="text-xs text-blue-600">推荐</div>
                          )}
                        </button>
                      ))}
                    </div>
                  </div>
                  <Field
                    label="高德 Web API Key"
                    value={mapConfig.amapKey}
                    onChange={(v) => setMapConfig({ ...mapConfig, amapKey: v })}
                    placeholder="请输入高德 API Key"
                  />
                  <Field
                    label="腾讯位置服务 Key"
                    value={mapConfig.tencentKey}
                    onChange={(v) => setMapConfig({ ...mapConfig, tencentKey: v })}
                    placeholder="请输入腾讯 Key"
                  />
                  <div className="flex items-center justify-between p-3 bg-gray-50 rounded-lg">
                    <div>
                      <div className="text-sm font-medium text-gray-900">启用缓存</div>
                      <div className="text-xs text-gray-500">缓存 POI 数据 {mapConfig.cacheTTL} 小时</div>
                    </div>
                    <button
                      onClick={() => setMapConfig({ ...mapConfig, cacheEnabled: !mapConfig.cacheEnabled })}
                      className={`w-10 h-5 rounded-full ${mapConfig.cacheEnabled ? 'bg-emerald-500' : 'bg-gray-300'}`}
                    >
                      <div className={`w-4 h-4 bg-white rounded-full shadow transition-transform ${mapConfig.cacheEnabled ? 'translate-x-5' : 'translate-x-0.5'}`} />
                    </button>
                  </div>
                  <button
                    onClick={handleClearCache}
                    className="px-3 py-2 text-sm text-amber-700 bg-amber-50 border border-amber-200 rounded-lg flex items-center gap-2"
                  >
                    <Trash2 className="w-4 h-4" /> 清除缓存
                  </button>
                </div>
              </motion.div>
            )}

            {tab === 'notification' && (
              <motion.div
                key="notification"
                initial={{ opacity: 0, y: 20 }}
                animate={{ opacity: 1, y: 0 }}
                exit={{ opacity: 0, y: -20 }}
                className="bg-white rounded-xl border border-gray-200 p-6 space-y-4"
              >
                <h3 className="font-semibold text-gray-900 mb-4">通知设置</h3>
                <div className="space-y-3">
                  {[
                    { key: 'email', label: '邮件通知', desc: '接收日报、周报等汇总通知' },
                    { key: 'sms', label: '短信通知', desc: '重要操作提醒（如任务分配）' },
                    { key: 'wechat', label: '企业微信', desc: '实时消息推送到企微' },
                    { key: 'push', label: 'App 推送', desc: '移动端推送通知' },
                  ].map((n) => (
                    <div key={n.key} className="flex items-center justify-between p-3 bg-gray-50 rounded-lg">
                      <div>
                        <div className="text-sm font-medium text-gray-900">{n.label}</div>
                        <div className="text-xs text-gray-500">{n.desc}</div>
                      </div>
                      <button
                        onClick={() => setNotification({ ...notification, [n.key]: !notification[n.key as keyof NotificationConfig] })}
                        className={`w-10 h-5 rounded-full ${notification[n.key as keyof NotificationConfig] ? 'bg-emerald-500' : 'bg-gray-300'}`}
                      >
                        <div className={`w-4 h-4 bg-white rounded-full shadow transition-transform ${notification[n.key as keyof NotificationConfig] ? 'translate-x-5' : 'translate-x-0.5'}`} />
                      </button>
                    </div>
                  ))}
                  <div className="flex items-center justify-between p-3 bg-gray-50 rounded-lg">
                    <div>
                      <div className="text-sm font-medium text-gray-900">免打扰时段</div>
                      <div className="text-xs text-gray-500">{notification.quietStart} - {notification.quietEnd}</div>
                    </div>
                    <button
                      onClick={() => setNotification({ ...notification, quietHours: !notification.quietHours })}
                      className={`w-10 h-5 rounded-full ${notification.quietHours ? 'bg-slate-700' : 'bg-gray-300'}`}
                    >
                      <div className={`w-4 h-4 bg-white rounded-full shadow transition-transform ${notification.quietHours ? 'translate-x-5' : 'translate-x-0.5'}`} />
                    </button>
                  </div>
                </div>
              </motion.div>
            )}

            {tab === 'security' && (
              <motion.div
                key="security"
                initial={{ opacity: 0, y: 20 }}
                animate={{ opacity: 1, y: 0 }}
                exit={{ opacity: 0, y: -20 }}
                className="bg-white rounded-xl border border-gray-200 p-6 space-y-4"
              >
                <h3 className="font-semibold text-gray-900 mb-4">安全与隐私</h3>
                <div className="space-y-3">
                  <div className="p-4 bg-emerald-50 border border-emerald-200 rounded-lg flex items-start gap-3">
                    <CheckCircle2 className="w-5 h-5 text-emerald-600 mt-0.5" />
                    <div>
                      <div className="text-sm font-medium text-emerald-900">隐私协议已签署</div>
                      <div className="text-xs text-emerald-700">客户数据采集已获得授权，操作日志保留 180 天</div>
                    </div>
                  </div>
                  <button className="w-full p-3 text-left border border-gray-200 rounded-lg hover:bg-gray-50 flex items-center justify-between">
                    <div className="flex items-center gap-2">
                      <Key className="w-4 h-4 text-gray-500" />
                      <span className="text-sm text-gray-900">修改密码</span>
                    </div>
                    <ChevronRight className="w-4 h-4 text-gray-400" />
                  </button>
                  <button className="w-full p-3 text-left border border-gray-200 rounded-lg hover:bg-gray-50 flex items-center justify-between">
                    <div className="flex items-center gap-2">
                      <Smartphone className="w-4 h-4 text-gray-500" />
                      <span className="text-sm text-gray-900">两步验证</span>
                    </div>
                    <span className="text-xs text-gray-500">未开启</span>
                  </button>
                  <button className="w-full p-3 text-left border border-gray-200 rounded-lg hover:bg-gray-50 flex items-center justify-between">
                    <div className="flex items-center gap-2">
                      <Clock className="w-4 h-4 text-gray-500" />
                      <span className="text-sm text-gray-900">登录历史</span>
                    </div>
                    <ChevronRight className="w-4 h-4 text-gray-400" />
                  </button>
                </div>
              </motion.div>
            )}

            {tab === 'data' && (
              <motion.div
                key="data"
                initial={{ opacity: 0, y: 20 }}
                animate={{ opacity: 1, y: 0 }}
                exit={{ opacity: 0, y: -20 }}
                className="bg-white rounded-xl border border-gray-200 p-6 space-y-4"
              >
                <h3 className="font-semibold text-gray-900 mb-4">数据管理</h3>
                <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                  <button className="p-4 border border-gray-200 rounded-lg hover:bg-gray-50 text-left">
                    <div className="flex items-center gap-2 mb-2">
                      <Download className="w-5 h-5 text-blue-600" />
                      <span className="font-medium text-gray-900">导出数据</span>
                    </div>
                    <p className="text-xs text-gray-500">导出客户、线索、订单等数据为 Excel</p>
                  </button>
                  <button className="p-4 border border-gray-200 rounded-lg hover:bg-gray-50 text-left">
                    <div className="flex items-center gap-2 mb-2">
                      <Upload className="w-5 h-5 text-emerald-600" />
                      <span className="font-medium text-gray-900">导入数据</span>
                    </div>
                    <p className="text-xs text-gray-500">从 Excel 批量导入客户数据</p>
                  </button>
                  <button className="p-4 border border-gray-200 rounded-lg hover:bg-gray-50 text-left">
                    <div className="flex items-center gap-2 mb-2">
                      <Database className="w-5 h-5 text-violet-600" />
                      <span className="font-medium text-gray-900">数据同步</span>
                    </div>
                    <p className="text-xs text-gray-500">同步品牌 CRM 数据（华为 CEM/小米零售通）</p>
                  </button>
                  <button className="p-4 border border-red-200 rounded-lg hover:bg-red-50 text-left">
                    <div className="flex items-center gap-2 mb-2">
                      <Trash2 className="w-5 h-5 text-red-600" />
                      <span className="font-medium text-red-900">清除数据</span>
                    </div>
                    <p className="text-xs text-red-600">清除本地缓存数据（不影响云端）</p>
                  </button>
                </div>
                <div className="pt-4 border-t border-gray-200">
                  <button
                    onClick={handleLogout}
                    className="w-full px-4 py-2.5 bg-red-600 text-white rounded-lg flex items-center justify-center gap-2"
                  >
                    <LogOut className="w-4 h-4" /> 退出登录
                  </button>
                </div>
              </motion.div>
            )}
          </AnimatePresence>
        </div>
      </div>

      {/* 确认弹窗 */}
      <ConfirmDialog
        isOpen={confirm.isOpen}
        onClose={confirm.close}
        onConfirm={confirm.onConfirm}
        title={confirm.title}
        message={confirm.message}
        variant={confirm.variant}
      />
    </div>
  );
}

function Field({
  label,
  value,
  defaultValue,
  onChange,
  placeholder,
  disabled,
  type = 'text',
  icon,
}: {
  label: string;
  value?: string;
  defaultValue?: string;
  onChange?: (v: string) => void;
  placeholder?: string;
  disabled?: boolean;
  type?: string;
  icon?: any;
}) {
  const Icon = icon;
  return (
    <div>
      <label className="text-xs text-gray-500 block mb-1">{label}</label>
      <div className="relative">
        {Icon && (
          <Icon className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-gray-400" />
        )}
        <input
          type={type}
          value={value}
          defaultValue={defaultValue}
          onChange={(e) => onChange?.(e.target.value)}
          placeholder={placeholder}
          disabled={disabled}
          className={`w-full px-3 py-2 border border-gray-200 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 ${
            Icon ? 'pl-10' : ''
          } ${disabled ? 'bg-gray-50 text-gray-500' : ''}`}
        />
      </div>
    </div>
  );
}
