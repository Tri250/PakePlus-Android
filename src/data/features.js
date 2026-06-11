export const features = [
  {
    id: 1,
    name: '消息中心',
    icon: 'MessageSquare',
    color: 'from-blue-500 to-cyan-500',
    description: '实时接收和管理所有消息通知',
    details: '支持多种消息类型，智能分类，快速回复',
    stats: { count: 24, unread: 5 }
  },
  {
    id: 2,
    name: '联系人',
    icon: 'Users',
    color: 'from-purple-500 to-pink-500',
    description: '管理您的联系人列表',
    details: '智能分组，快速搜索，批量操作',
    stats: { count: 528, groups: 12 }
  },
  {
    id: 3,
    name: '日历',
    icon: 'Calendar',
    color: 'from-orange-500 to-red-500',
    description: '日程管理和时间安排',
    details: '智能提醒，共享日历，日程分析',
    stats: { today: 8, week: 24 }
  },
  {
    id: 4,
    name: '文件管理',
    icon: 'Folder',
    color: 'from-green-500 to-teal-500',
    description: '管理您的所有文件',
    details: '云同步，分类管理，快速分享',
    stats: { files: 1247, size: '2.4 GB' }
  },
  {
    id: 5,
    name: '设置',
    icon: 'Settings',
    color: 'from-gray-500 to-slate-500',
    description: '个性化设置和配置',
    details: '主题切换，隐私设置，账户管理',
    stats: {}
  },
  {
    id: 6,
    name: '相机',
    icon: 'Camera',
    color: 'from-indigo-500 to-purple-500',
    description: '拍摄和编辑照片',
    details: 'AI美颜，滤镜效果，专业模式',
    stats: { photos: 3240, videos: 156 }
  },
  {
    id: 7,
    name: '音乐',
    icon: 'Music',
    color: 'from-pink-500 to-rose-500',
    description: '享受您的音乐库',
    details: '智能推荐，离线缓存，歌词显示',
    stats: { songs: 1856, playlists: 24 }
  },
  {
    id: 8,
    name: '天气',
    icon: 'CloudSun',
    color: 'from-cyan-500 to-blue-500',
    description: '实时天气信息',
    details: '7天预报，空气质量，生活指数',
    stats: { temp: '26°C', condition: '晴' }
  }
];

export const appInfo = {
  name: 'MyApp',
  version: '2.0.1',
  description: '一款功能强大的综合应用',
  featuresCount: 8,
  downloads: '500万+'
};

export const navigationItems = [
  { id: 'home', name: '首页', icon: 'Home' },
  { id: 'features', name: '功能', icon: 'Grid3X3' },
  { id: 'about', name: '关于', icon: 'Info' }
];