import { Link } from 'react-router-dom';
import { TrendingUp, Users, Target, RefreshCw, DollarSign, Clock, AlertCircle, ChevronRight, Building2 } from 'lucide-react';

export default function Dashboard() {
  const stats = [
    { title: '今日客户', value: '128', change: 12.5, icon: Users, color: 'blue' },
    { title: '本月营收', value: '¥326,800', change: 18.2, icon: DollarSign, color: 'green' },
    { title: '线索转化', value: '68%', change: 5.3, icon: Target, color: 'purple' },
    { title: '待跟进', value: '42', change: -3.1, icon: Clock, color: 'orange' },
  ];

  const tasks = [
    { id: 1, title: '王先生 - 华为Mate60 Pro 跟进', time: '10:30', priority: 'high' },
    { id: 2, title: '李女士 - iPhone 15 Plus 报价', time: '14:00', priority: 'medium' },
    { id: 3, title: '小米14 系列潜客回访', time: '15:30', priority: 'medium' },
  ];

  return (
    <div className="space-y-6">
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
        {stats.map((stat) => (
          <div key={stat.title} className="bg-white rounded-xl border border-gray-200 p-5">
            <div className="flex items-center justify-between">
              <div>
                <p className="text-sm text-gray-500">{stat.title}</p>
                <p className="text-2xl font-bold text-gray-900 mt-1">{stat.value}</p>
                <p className={`text-xs mt-1 ${stat.change > 0 ? 'text-green-600' : 'text-red-600'}`}>
                  {stat.change > 0 ? '+' : ''}{stat.change}% 较上周
                </p>
              </div>
              <div className={`w-12 h-12 rounded-lg bg-${stat.color}-100 flex items-center justify-center`}>
                <stat.icon className={`w-6 h-6 text-${stat.color}-600`} />
              </div>
            </div>
          </div>
        ))}
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        <div className="lg:col-span-2 bg-white rounded-xl border border-gray-200">
          <div className="p-5 border-b border-gray-200 flex items-center justify-between">
            <h2 className="font-semibold text-gray-900">今日任务</h2>
            <Link to="/customers" className="text-sm text-blue-600 hover:text-blue-700">查看全部</Link>
          </div>
          <div className="divide-y divide-gray-100">
            {tasks.map((task) => (
              <div key={task.id} className="p-4 hover:bg-gray-50 flex items-center gap-4">
                <div className={`w-2 h-2 rounded-full ${
                  task.priority === 'high' ? 'bg-red-500' : 'bg-yellow-500'
                }`} />
                <div className="flex-1">
                  <p className="text-sm text-gray-900">{task.title}</p>
                  <p className="text-xs text-gray-500 mt-1">{task.time}</p>
                </div>
                <ChevronRight className="w-4 h-4 text-gray-400" />
              </div>
            ))}
          </div>
        </div>

        <div className="bg-white rounded-xl border border-gray-200">
          <div className="p-5 border-b border-gray-200">
            <h2 className="font-semibold text-gray-900">AI 智能建议</h2>
          </div>
          <div className="p-4 space-y-3">
            <div className="p-3 bg-blue-50 rounded-lg">
              <div className="flex items-center gap-2 mb-1">
                <TrendingUp className="w-4 h-4 text-blue-600" />
                <span className="text-sm font-medium text-blue-900">高转化机会</span>
              </div>
              <p className="text-xs text-blue-700">3位S级客户近期换机概率高，建议优先触达</p>
            </div>
            <div className="p-3 bg-orange-50 rounded-lg">
              <div className="flex items-center gap-2 mb-1">
                <AlertCircle className="w-4 h-4 text-orange-600" />
                <span className="text-sm font-medium text-orange-900">客户关怀</span>
              </div>
              <p className="text-xs text-orange-700">5位客户超过30天未联系，建议回访</p>
            </div>
            <div className="p-3 bg-green-50 rounded-lg">
              <div className="flex items-center gap-2 mb-1">
                <Building2 className="w-4 h-4 text-green-600" />
                <span className="text-sm font-medium text-green-900">门店动态</span>
              </div>
              <p className="text-xs text-green-700">华为体验店周环比上升12%，表现优秀</p>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
