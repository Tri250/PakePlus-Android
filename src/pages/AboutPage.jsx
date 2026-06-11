import { motion } from 'framer-motion';
import { Info, Github, Twitter, Mail, Award, Users, Globe } from 'lucide-react';

export default function AboutPage({ appInfo }) {
  const stats = [
    { icon: Users, value: '500万+', label: '活跃用户' },
    { icon: Globe, value: '150+', label: '覆盖国家' },
    { icon: Award, value: '50+', label: '行业奖项' },
  ];

  const team = [
    { name: '张明', role: '创始人 & CEO', avatar: 'ZM' },
    { name: '李华', role: '技术总监', avatar: 'LH' },
    { name: '王芳', role: '设计总监', avatar: 'WF' },
    { name: '陈伟', role: '产品经理', avatar: 'CW' },
  ];

  return (
    <div className="min-h-screen p-8">
      <motion.div
        initial={{ opacity: 0, y: -20 }}
        animate={{ opacity: 1, y: 0 }}
        className="text-center mb-12"
      >
        <motion.div
          className="inline-flex items-center justify-center w-16 h-16 rounded-full bg-white/10 mb-4"
          initial={{ scale: 0 }}
          animate={{ scale: 1 }}
          transition={{ type: 'spring', stiffness: 200 }}
        >
          <Info size={32} className="text-white" />
        </motion.div>
        <h1 className="text-4xl md:text-5xl font-bold text-white mb-4 font-[Roboto_Slab]">
          关于 {appInfo.name}
        </h1>
        <p className="text-white/70 max-w-2xl mx-auto text-lg">
          {appInfo.description}，我们致力于为用户提供最优质的移动体验。
        </p>
      </motion.div>

      <motion.div
        initial={{ opacity: 0 }}
        animate={{ opacity: 1 }}
        transition={{ delay: 0.2 }}
        className="max-w-4xl mx-auto mb-12"
      >
        <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
          {stats.map((stat, index) => {
            const Icon = stat.icon;
            return (
              <motion.div
                key={stat.label}
                initial={{ opacity: 0, y: 20 }}
                animate={{ opacity: 1, y: 0 }}
                transition={{ delay: 0.3 + index * 0.1 }}
                className="glass-effect rounded-2xl p-6 text-center"
              >
                <Icon size={32} className="text-white/80 mx-auto mb-4" />
                <p className="text-white text-3xl font-bold mb-1">{stat.value}</p>
                <p className="text-white/60 text-sm">{stat.label}</p>
              </motion.div>
            );
          })}
        </div>
      </motion.div>

      <motion.div
        initial={{ opacity: 0 }}
        animate={{ opacity: 1 }}
        transition={{ delay: 0.4 }}
        className="max-w-4xl mx-auto mb-12"
      >
        <h2 className="text-2xl font-bold text-white mb-6 text-center font-[Roboto_Slab]">
          核心团队
        </h2>
        <div className="grid grid-cols-2 md:grid-cols-4 gap-6">
          {team.map((member, index) => (
            <motion.div
              key={member.name}
              initial={{ opacity: 0, scale: 0.8 }}
              animate={{ opacity: 1, scale: 1 }}
              transition={{ delay: 0.5 + index * 0.1 }}
              className="glass-effect rounded-2xl p-6 text-center"
            >
              <div className="w-16 h-16 rounded-full bg-gradient-to-br from-purple-500 to-pink-500 flex items-center justify-center mx-auto mb-4">
                <span className="text-white font-bold text-lg">{member.avatar}</span>
              </div>
              <p className="text-white font-semibold mb-1">{member.name}</p>
              <p className="text-white/60 text-sm">{member.role}</p>
            </motion.div>
          ))}
        </div>
      </motion.div>

      <motion.div
        initial={{ opacity: 0, y: 20 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ delay: 0.7 }}
        className="max-w-4xl mx-auto"
      >
        <div className="glass-effect rounded-2xl p-8 text-center">
          <h3 className="text-white font-semibold text-xl mb-6">联系我们</h3>
          <div className="flex justify-center gap-6">
            <motion.a
              href="#"
              className="w-12 h-12 rounded-full bg-white/10 flex items-center justify-center text-white hover:bg-white/20 transition-all duration-300"
              whileHover={{ scale: 1.1, y: -2 }}
            >
              <Github size={24} />
            </motion.a>
            <motion.a
              href="#"
              className="w-12 h-12 rounded-full bg-white/10 flex items-center justify-center text-white hover:bg-white/20 transition-all duration-300"
              whileHover={{ scale: 1.1, y: -2 }}
            >
              <Twitter size={24} />
            </motion.a>
            <motion.a
              href="#"
              className="w-12 h-12 rounded-full bg-white/10 flex items-center justify-center text-white hover:bg-white/20 transition-all duration-300"
              whileHover={{ scale: 1.1, y: -2 }}
            >
              <Mail size={24} />
            </motion.a>
          </div>
          <p className="text-white/60 mt-6">
            版本 {appInfo.version}
          </p>
        </div>
      </motion.div>
    </div>
  );
}