import { TrendingUp, TrendingDown, Users, Target, DollarSign, Award, Activity } from 'lucide-react';

export default function Analytics() {
  const stats = [
    { title: '总线索数', value: '1,234', change: 15.2, icon: Users, color: 'blue' },
    { title: '转化率', value: '68%', change: 5.3, icon: Target, color: 'green' },
    { title: '客单价', value: '¥3,200', change: 8.1, icon: DollarSign, color: 'purple' },
    { title: '员工之星', value: '12', change: 0, icon: Award, color: 'yellow' },
  ];

  const funnel = [
    { name: '线索', value: 1234, percent: 100 },
    { name: '到店', value: 856, percent: 69.3 },
    { name: '成交', value: 423, percent: 34.2 },
    { name: '复购', value: 156, percent: 12.6 },
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
                <p className="text-xs mt-1 text-green-600 flex items-center gap-1">
                  <TrendingUp className="w-3 h-3" /> +{stat.change}%
                </p>
              </div>
              <div className={`w-12 h-12 rounded-lg bg-${stat.color}-100 flex items-center justify-center`}>
                <stat.icon className={`w-6 h-6 text-${stat.color}-600`} />
              </div>
            </div>
          </div>
        ))}
      </div>

      <div className="bg-white rounded-xl border border-gray-200 p-5">
        <h2 className="font-semibold text-gray-900 mb-4">转化漏斗</h2>
        <div className="space-y-3">
          {funnel.map((step) => (
            <div key={step.name}>
              <div className="flex items-center justify-between text-sm mb-1">
                <span className="text-gray-700">{step.name}</span>
                <span className="text-gray-900 font-medium">{step.value}</span>
              </div>
              <div className="h-8 bg-gray-100 rounded-lg overflow-hidden">
                <div
                  className="h-full bg-gradient-to-r from-blue-500 to-purple-500 rounded-lg flex items-center justify-end px-3"
                  style={{ width: `${step.percent}%` }}
                >
                  <span className="text-xs text-white font-medium">{step.percent}%</span>
                </div>
              </div>
            </div>
          ))}
        </div>
      </div>

      <div className="bg-white rounded-xl border border-gray-200 p-5">
        <h2 className="font-semibold text-gray-900 mb-4">员工绩效</h2>
        <div className="space-y-3">
          {[
            { name: '张三', sales: 38, revenue: 156000, rank: 1 },
            { name: '李四', sales: 32, revenue: 128000, rank: 2 },
            { name: '王五', sales: 28, revenue: 112000, rank: 3 },
          ].map((emp) => (
            <div key={emp.name} className="flex items-center gap-4 p-3 bg-gray-50 rounded-lg">
              <div className={`w-8 h-8 rounded-full flex items-center justify-center text-white font-bold ${
                emp.rank === 1 ? 'bg-yellow-500' : emp.rank === 2 ? 'bg-gray-400' : 'bg-amber-600'
              }`}>
                {emp.rank}
              </div>
              <div className="flex-1">
                <p className="font-medium text-gray-900">{emp.name}</p>
                <p className="text-sm text-gray-500">成交 {emp.sales} 单</p>
              </div>
              <p className="font-bold text-gray-900">¥{emp.revenue.toLocaleString()}</p>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}
