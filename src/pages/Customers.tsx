import { useState } from 'react';
import { Search, Filter, Plus, Phone, MessageSquare, RefreshCw, Star, X } from 'lucide-react';

const mockCustomers = [
  { id: '1', name: '王先生', phone: '138****8888', segment: 'S', tags: ['即将换机', '旗舰机用户'], device: '华为 P40 Pro', months: 27 },
  { id: '2', name: '李女士', phone: '139****6666', segment: 'A', tags: ['价格敏感', '性价比'], device: '小米 13', months: 18 },
  { id: '3', name: '张先生', phone: '137****5555', segment: 'B', tags: ['拍照需求'], device: 'OPPO Find X6', months: 12 },
  { id: '4', name: '赵女士', phone: '136****4444', segment: 'C', tags: ['观望中'], device: 'vivo X90', months: 8 },
  { id: '5', name: '刘先生', phone: '135****3333', segment: 'D', tags: ['低活跃'], device: '荣耀 70', months: 6 },
];

export default function Customers() {
  const [search, setSearch] = useState('');
  const [filter, setFilter] = useState('');

  const filtered = mockCustomers.filter((c) => {
    if (search && !c.name.includes(search) && !c.phone.includes(search)) return false;
    if (filter && c.segment !== filter) return false;
    return true;
  });

  const segmentColors: Record<string, string> = {
    S: 'bg-purple-100 text-purple-700',
    A: 'bg-blue-100 text-blue-700',
    B: 'bg-green-100 text-green-700',
    C: 'bg-yellow-100 text-yellow-700',
    D: 'bg-gray-100 text-gray-700',
  };

  return (
    <div className="space-y-6">
      <div className="bg-white rounded-xl border border-gray-200 p-4">
        <div className="flex flex-wrap items-center gap-4">
          <div className="flex-1 min-w-[200px] relative">
            <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-gray-400" />
            <input
              type="text"
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              placeholder="搜索客户姓名/手机号"
              className="w-full pl-10 pr-4 py-2 border border-gray-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500"
            />
          </div>
          <select
            value={filter}
            onChange={(e) => setFilter(e.target.value)}
            className="px-3 py-2 border border-gray-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500"
          >
            <option value="">全部分层</option>
            <option value="S">S级</option>
            <option value="A">A级</option>
            <option value="B">B级</option>
            <option value="C">C级</option>
            <option value="D">D级</option>
          </select>
          <button className="flex items-center gap-2 px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700">
            <Plus className="w-4 h-4" />
            新增客户
          </button>
        </div>
      </div>

      <div className="bg-white rounded-xl border border-gray-200">
        <div className="p-4 border-b border-gray-200 flex items-center justify-between">
          <h2 className="font-semibold text-gray-900">客户列表</h2>
          <span className="text-sm text-gray-500">共 {filtered.length} 条</span>
        </div>
        <div className="divide-y divide-gray-100">
          {filtered.map((c) => (
            <div key={c.id} className="p-4 hover:bg-gray-50 flex items-center gap-4">
              <div className="w-10 h-10 rounded-full bg-blue-100 flex items-center justify-center">
                <span className="text-blue-600 font-medium">{c.name[0]}</span>
              </div>
              <div className="flex-1">
                <div className="flex items-center gap-2 mb-1">
                  <span className="font-medium text-gray-900">{c.name}</span>
                  <span className="text-sm text-gray-500">{c.phone}</span>
                  <span className={`px-2 py-0.5 text-xs rounded ${segmentColors[c.segment]}`}>
                    {c.segment}级
                  </span>
                </div>
                <div className="flex items-center gap-3 text-sm text-gray-500">
                  <span>使用 {c.device}</span>
                  <span>·</span>
                  <span>{c.months}个月</span>
                  {c.tags.map((t) => (
                    <span key={t} className="px-2 py-0.5 bg-gray-100 text-gray-600 text-xs rounded">
                      {t}
                    </span>
                  ))}
                </div>
              </div>
              <div className="flex items-center gap-2">
                <button className="p-2 hover:bg-gray-100 rounded-lg" title="电话">
                  <Phone className="w-4 h-4 text-gray-400" />
                </button>
                <button className="p-2 hover:bg-gray-100 rounded-lg" title="消息">
                  <MessageSquare className="w-4 h-4 text-gray-400" />
                </button>
                <button className="p-2 hover:bg-gray-100 rounded-lg" title="详情">
                  <Star className="w-4 h-4 text-gray-400" />
                </button>
              </div>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}
