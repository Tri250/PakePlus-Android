import { useState } from 'react';
import { Building2, User, Lock, Save, Plus, Trash2 } from 'lucide-react';

export default function Settings() {
  const [store, setStore] = useState({
    name: '华为体验店·中关村',
    address: '海淀区中关村大街1号',
    phone: '010-12345678',
  });

  return (
    <div className="space-y-6">
      <div className="bg-white rounded-xl border border-gray-200">
        <div className="p-5 border-b border-gray-200 flex items-center gap-2">
          <Building2 className="w-5 h-5 text-blue-600" />
          <h2 className="font-semibold text-gray-900">门店信息</h2>
        </div>
        <div className="p-5 space-y-4">
          <div>
            <label className="block text-sm text-gray-700 mb-1">门店名称</label>
            <input
              type="text"
              value={store.name}
              onChange={(e) => setStore({ ...store, name: e.target.value })}
              className="w-full px-3 py-2 border border-gray-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500"
            />
          </div>
          <div>
            <label className="block text-sm text-gray-700 mb-1">门店地址</label>
            <input
              type="text"
              value={store.address}
              onChange={(e) => setStore({ ...store, address: e.target.value })}
              className="w-full px-3 py-2 border border-gray-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500"
            />
          </div>
          <div>
            <label className="block text-sm text-gray-700 mb-1">联系电话</label>
            <input
              type="text"
              value={store.phone}
              onChange={(e) => setStore({ ...store, phone: e.target.value })}
              className="w-full px-3 py-2 border border-gray-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500"
            />
          </div>
          <button className="flex items-center gap-2 px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700">
            <Save className="w-4 h-4" />
            保存设置
          </button>
        </div>
      </div>

      <div className="bg-white rounded-xl border border-gray-200">
        <div className="p-5 border-b border-gray-200 flex items-center justify-between">
          <div className="flex items-center gap-2">
            <User className="w-5 h-5 text-blue-600" />
            <h2 className="font-semibold text-gray-900">员工管理</h2>
          </div>
          <button className="flex items-center gap-2 px-3 py-1.5 bg-blue-600 text-white text-sm rounded-lg hover:bg-blue-700">
            <Plus className="w-4 h-4" />
            添加员工
          </button>
        </div>
        <div className="divide-y divide-gray-100">
          {[
            { name: '张三', role: '店长', phone: '138****1111' },
            { name: '李四', role: '销售顾问', phone: '138****2222' },
            { name: '王五', role: '销售顾问', phone: '138****3333' },
          ].map((emp) => (
            <div key={emp.name} className="p-4 flex items-center gap-4">
              <div className="w-10 h-10 rounded-full bg-blue-100 flex items-center justify-center">
                <span className="text-blue-600 font-medium">{emp.name[0]}</span>
              </div>
              <div className="flex-1">
                <p className="font-medium text-gray-900">{emp.name}</p>
                <p className="text-sm text-gray-500">{emp.role} · {emp.phone}</p>
              </div>
              <button className="p-2 hover:bg-gray-100 rounded-lg">
                <Trash2 className="w-4 h-4 text-gray-400" />
              </button>
            </div>
          ))}
        </div>
      </div>

      <div className="bg-white rounded-xl border border-gray-200">
        <div className="p-5 border-b border-gray-200 flex items-center gap-2">
          <Lock className="w-5 h-5 text-blue-600" />
          <h2 className="font-semibold text-gray-900">安全设置</h2>
        </div>
        <div className="p-5 space-y-3">
          <div className="flex items-center justify-between">
            <span className="text-sm text-gray-700">修改密码</span>
            <button className="px-3 py-1.5 bg-gray-100 text-gray-700 text-sm rounded-lg hover:bg-gray-200">
              修改
            </button>
          </div>
          <div className="flex items-center justify-between">
            <span className="text-sm text-gray-700">双因素认证</span>
            <button className="px-3 py-1.5 bg-green-100 text-green-700 text-sm rounded-lg">
              已开启
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}
