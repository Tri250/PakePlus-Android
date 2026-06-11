import { motion } from 'framer-motion';
import { Info, Github, Twitter, Mail, Award, Users, Globe, Heart, Star, Zap } from 'lucide-react';
import { Ripple } from '../components/Animations';

export default function AboutPage({ appInfo }) {
  const stats = [
    { icon: Users, value: '500万+', label: '活跃用户', color: 'from-blue-500 to-cyan-500' },
    { icon: Globe, value: '150+', label: '覆盖国家', color: 'from-green-500 to-emerald-500' },
    { icon: Award, value: '50+', label: '行业奖项', color: 'from-orange-500 to-yellow-500' },
  ];

  const team = [
    { name: '张明', role: '创始人 & CEO', avatar: 'ZM', color: 'from-purple-500 to-pink-500' },
    { name: '李华', role: '技术总监', avatar: 'LH', color: 'from-blue-500 to-cyan-500' },
    { name: '王芳', role: '设计总监', avatar: 'WF', color: 'from-orange-500 to-red-500' },
    { name: '陈伟', role: '产品经理', avatar: 'CW', color: 'from-green-500 to-teal-500' },
  ];

  const values = [
    { title: '创新驱动', desc: '持续探索新技术，引领行业发展', icon: Zap },
    { title: '用户至上', desc: '倾听用户声音，打造极致体验', icon: Heart },
    { title: '追求卓越', desc: '精益求精，每个细节都值得打磨', icon: Star },
  ];

  return (
    <div className="min-h-screen p-8 relative overflow-hidden">
      {/* 背景装饰 */}
      <div className="absolute inset-0 overflow-hidden pointer-events-none">
        <motion.div
          className="absolute w-[800px] h-[800px] rounded-full opacity-20"
          style={{
            background: 'radial-gradient(circle, rgba(147, 51, 234, 0.4) 0%, transparent 70%)',
            left: '50%',
            top: '0%',
            transform: 'translateX(-50%)',
          }}
          animate={{
            scale: [1, 1.1, 1],
          }}
          transition={{ duration: 10, repeat: Infinity, ease: 'easeInOut' }}
        />
      </div>

      {/* 标题区域 */}
      <motion.div
        initial={{ opacity: 0, y: -20 }}
        animate={{ opacity: 1, y: 0 }}
        className="text-center mb-12 relative z-10"
      >
        <motion.div
          className="inline-flex items-center justify-center w-20 h-20 rounded-2xl bg-gradient-to-br from-purple-500 to-pink-500 mb-6 shadow-lg"
          initial={{ scale: 0, rotate: -180 }}
          animate={{ scale: 1, rotate: 0 }}
          transition={{ type: 'spring', stiffness: 200 }}
        >
          <Info size={40} className="text-white" />
        </motion.div>

        <motion.h1 
          className="text-4xl md:text-5xl font-bold text-white mb-4 font-[Roboto_Slab]"
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          transition={{ delay: 0.1 }}
        >
          关于 {appInfo.name}
        </motion.h1>

        <motion.p 
          className="text-white/70 max-w-2xl mx-auto text-lg"
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          transition={{ delay: 0.2 }}
        >
          {appInfo.description}，我们致力于为用户提供最优质的移动体验。
        </motion.p>
      </motion.div>

      {/* 统计数据 */}
      <motion.div
        initial={{ opacity: 0 }}
        animate={{ opacity: 1 }}
        transition={{ delay: 0.3 }}
        className="max-w-4xl mx-auto mb-16 relative z-10"
      >
        <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
          {stats.map((stat, index) => {
            const Icon = stat.icon;
            return (
              <motion.div
                key={stat.label}
                initial={{ opacity: 0, y: 20 }}
                animate={{ opacity: 1, y: 0 }}
                transition={{ delay: 0.4 + index * 0.1 }}
                className="glass-effect rounded-2xl p-6 text-center group"
                whileHover={{ y: -5, scale: 1.02 }}
              >
                <motion.div
                  className={`w-14 h-14 rounded-xl bg-gradient-to-br ${stat.color} flex items-center justify-center mx-auto mb-4`}
                  whileHover={{ rotate: 360 }}
                  transition={{ duration: 0.5 }}
                >
                  <Icon size={28} className="text-white" />
                </motion.div>
                <motion.p 
                  className="text-white text-3xl font-bold mb-1"
                  initial={{ opacity: 0 }}
                  animate={{ opacity: 1 }}
                  transition={{ delay: 0.5 + index * 0.1 }}
                >
                  {stat.value}
                </motion.p>
                <p className="text-white/60 text-sm">{stat.label}</p>
              </motion.div>
            );
          })}
        </div>
      </motion.div>

      {/* 核心价值 */}
      <motion.div
        initial={{ opacity: 0 }}
        animate={{ opacity: 1 }}
        transition={{ delay: 0.5 }}
        className="max-w-4xl mx-auto mb-16 relative z-10"
      >
        <motion.h2 
          className="text-2xl font-bold text-white mb-8 text-center font-[Roboto_Slab]"
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          transition={{ delay: 0.6 }}
        >
          核心价值
        </motion.h2>
        <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
          {values.map((value, index) => {
            const Icon = value.icon;
            return (
              <motion.div
                key={value.title}
                initial={{ opacity: 0, scale: 0.8 }}
                animate={{ opacity: 1, scale: 1 }}
                transition={{ delay: 0.7 + index * 0.1 }}
                className="glass-effect rounded-2xl p-6 text-center"
                whileHover={{ y: -5 }}
              >
                <motion.div
                  className="w-12 h-12 rounded-full bg-white/10 flex items-center justify-center mx-auto mb-4"
                  animate={{ scale: [1, 1.1, 1] }}
                  transition={{ duration: 2, repeat: Infinity, delay: index * 0.3 }}
                >
                  <Icon size={24} className="text-purple-300" />
                </motion.div>
                <h3 className="text-white font-semibold mb-2">{value.title}</h3>
                <p className="text-white/60 text-sm">{value.desc}</p>
              </motion.div>
            );
          })}
        </div>
      </motion.div>

      {/* 核心团队 */}
      <motion.div
        initial={{ opacity: 0 }}
        animate={{ opacity: 1 }}
        transition={{ delay: 0.7 }}
        className="max-w-4xl mx-auto mb-16 relative z-10"
      >
        <motion.h2 
          className="text-2xl font-bold text-white mb-8 text-center font-[Roboto_Slab]"
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          transition={{ delay: 0.8 }}
        >
          核心团队
        </motion.h2>
        <div className="grid grid-cols-2 md:grid-cols-4 gap-6">
          {team.map((member, index) => (
            <motion.div
              key={member.name}
              initial={{ opacity: 0, scale: 0.8 }}
              animate={{ opacity: 1, scale: 1 }}
              transition={{ delay: 0.9 + index * 0.1 }}
              className="glass-effect rounded-2xl p-6 text-center group"
              whileHover={{ y: -5, scale: 1.02 }}
            >
              <motion.div
                className={`w-16 h-16 rounded-full bg-gradient-to-br ${member.color} flex items-center justify-center mx-auto mb-4 shadow-lg`}
                whileHover={{ rotate: 360 }}
                transition={{ duration: 0.5 }}
              >
                <span className="text-white font-bold text-lg">{member.avatar}</span>
              </motion.div>
              <p className="text-white font-semibold mb-1">{member.name}</p>
              <p className="text-white/60 text-sm">{member.role}</p>
            </motion.div>
          ))}
        </div>
      </motion.div>

      {/* 联系我们 */}
      <motion.div
        initial={{ opacity: 0, y: 20 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ delay: 1 }}
        className="max-w-4xl mx-auto relative z-10"
      >
        <motion.div
          className="glass-effect rounded-3xl p-8 text-center"
          whileHover={{ scale: 1.01 }}
        >
          <h3 className="text-white font-semibold text-xl mb-6">联系我们</h3>
          <div className="flex justify-center gap-4 mb-6">
            {[
              { icon: Github, label: 'GitHub' },
              { icon: Twitter, label: 'Twitter' },
              { icon: Mail, label: 'Email' },
            ].map((item) => (
              <motion.a
                key={item.label}
                href="#"
                className="w-12 h-12 rounded-full bg-white/10 flex items-center justify-center text-white hover:bg-white/20 transition-all duration-300"
                whileHover={{ scale: 1.1, y: -2 }}
                whileTap={{ scale: 0.95 }}
              >
                <item.icon size={24} />
              </motion.a>
            ))}
          </div>
          <p className="text-white/60">
            版本 {appInfo.version}
          </p>
        </motion.div>
      </motion.div>
    </div>
  );
}