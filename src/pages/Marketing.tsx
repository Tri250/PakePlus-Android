import { useState } from 'react';
import { Calculator, Sparkles, Copy, ThumbsUp, Send } from 'lucide-react';

export default function Marketing() {
  const [oldDevice, setOldDevice] = useState('华为 P40 Pro');
  const [oldPrice, setOldPrice] = useState(2000);
  const [newDevice, setNewDevice] = useState('华为 Mate60 Pro');
  const [newPrice, setNewPrice] = useState(6999);
  const [subsidy, setSubsidy] = useState(500);

  const actualPay = Math.max(0, newPrice - oldPrice - subsidy);

  return (
    <div className="space-y-6">
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        <div className="bg-white rounded-xl border border-gray-200">
          <div className="p-5 border-b border-gray-200 flex items-center gap-2">
            <Calculator className="w-5 h-5 text-blue-600" />
            <h2 className="font-semibold text-gray-900">以旧换新计算器</h2>
          </div>
          <div className="p-5 space-y-4">
            <div>
              <label className="block text-sm text-gray-700 mb-1">旧设备型号</label>
              <input
                type="text"
                value={oldDevice}
                onChange={(e) => setOldDevice(e.target.value)}
                className="w-full px-3 py-2 border border-gray-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500"
              />
            </div>
            <div>
              <label className="block text-sm text-gray-700 mb-1">旧机回收价 (元)</label>
              <input
                type="number"
                value={oldPrice}
                onChange={(e) => setOldPrice(Number(e.target.value))}
                className="w-full px-3 py-2 border border-gray-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500"
              />
            </div>
            <div>
              <label className="block text-sm text-gray-700 mb-1">新设备型号</label>
              <input
                type="text"
                value={newDevice}
                onChange={(e) => setNewDevice(e.target.value)}
                className="w-full px-3 py-2 border border-gray-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500"
              />
            </div>
            <div>
              <label className="block text-sm text-gray-700 mb-1">新机售价 (元)</label>
              <input
                type="number"
                value={newPrice}
                onChange={(e) => setNewPrice(Number(e.target.value))}
                className="w-full px-3 py-2 border border-gray-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500"
              />
            </div>
            <div>
              <label className="block text-sm text-gray-700 mb-1">政府补贴 (元)</label>
              <input
                type="number"
                value={subsidy}
                onChange={(e) => setSubsidy(Number(e.target.value))}
                className="w-full px-3 py-2 border border-gray-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500"
              />
            </div>
            <div className="pt-4 border-t border-gray-200">
              <div className="flex items-center justify-between text-sm">
                <span className="text-gray-500">新机售价</span>
                <span className="text-gray-900">¥{newPrice.toLocaleString()}</span>
              </div>
              <div className="flex items-center justify-between text-sm mt-1">
                <span className="text-gray-500">旧机抵扣</span>
                <span className="text-green-600">-¥{oldPrice.toLocaleString()}</span>
              </div>
              <div className="flex items-center justify-between text-sm mt-1">
                <span className="text-gray-500">政府补贴</span>
                <span className="text-green-600">-¥{subsidy.toLocaleString()}</span>
              </div>
              <div className="flex items-center justify-between mt-3 pt-3 border-t border-gray-200">
                <span className="text-base font-semibold text-gray-900">实付金额</span>
                <span className="text-2xl font-bold text-blue-600">¥{actualPay.toLocaleString()}</span>
              </div>
            </div>
          </div>
        </div>

        <div className="bg-white rounded-xl border border-gray-200">
          <div className="p-5 border-b border-gray-200 flex items-center justify-between">
            <div className="flex items-center gap-2">
              <Sparkles className="w-5 h-5 text-purple-600" />
              <h2 className="font-semibold text-gray-900">AI 话术生成</h2>
            </div>
            <button className="px-3 py-1.5 bg-purple-100 text-purple-700 text-sm rounded-lg hover:bg-purple-200">
              重新生成
            </button>
          </div>
          <div className="p-5 space-y-4">
            <div className="p-4 bg-purple-50 rounded-lg">
              <p className="text-sm text-gray-700 leading-relaxed">
                王总您好！我是华为体验店的高级顾问。看到您现在使用的是 P40 Pro，已经使用 27 个月了，正是换机的黄金时期。我们最新的 Mate60 Pro 不仅支持卫星通话，搭载最新麒麟9000S芯片，而且现在以旧换新最高补贴 {subsidy} 元，您的 P40 Pro 还能抵扣 {oldPrice} 元，实际只需要支付 {actualPay.toLocaleString()} 元，性价比超高！
              </p>
              <div className="flex items-center gap-2 mt-3">
                <button className="p-1.5 hover:bg-purple-100 rounded">
                  <Copy className="w-4 h-4 text-purple-600" />
                </button>
                <button className="p-1.5 hover:bg-purple-100 rounded">
                  <ThumbsUp className="w-4 h-4 text-purple-600" />
                </button>
                <button className="p-1.5 hover:bg-purple-100 rounded">
                  <Send className="w-4 h-4 text-purple-600" />
                </button>
              </div>
            </div>
            <div className="p-4 bg-blue-50 rounded-lg">
              <p className="text-sm text-gray-700 leading-relaxed">
                {newDevice} 现在预订还有额外赠品：智能手表、碎屏险、原装壳膜。预约到店还能享受一对一专属服务，无需排队等待。您看本周什么时间方便？我帮您预留名额。
              </p>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
