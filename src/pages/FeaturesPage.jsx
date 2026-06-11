import { motion } from 'framer-motion';
import FeatureCard from '../components/FeatureCard';
import { Sparkles, Download, ArrowRight, Zap } from 'lucide-react';
import { Ripple } from '../components/Animations';

export default function FeaturesPage({ features }) {
  return (
    <div className="min-h-screen p-8 relative overflow-hidden">
      {/* 背景装饰 */}
      <div className="absolute inset-0 overflow-hidden pointer-events-none">
        <motion.div
          className="absolute w-[600px] h-[600px] rounded-full"
          style={{
            background: 'radial-gradient(circle, rgba(147, 51, 234, 0.2) 0%, transparent 70%)',
            left: '-10%',
            top: '20%',
          }}
          animate={{
            scale: [1, 1.2, 1],
            rotate: [0, 90, 0],
          }}
          transition={{ duration: 15, repeat: Infinity, ease: 'linear' }}
        />
        <motion.div
          className="absolute w-[500px] h-[500px] rounded-full"
          style={{
            background: 'radial-gradient(circle, rgba(236, 72, 153, 0.2) 0%, transparent 70%)',
            right: '-10%',
            bottom: '20%',
          }}
          animate={{
            scale: [1.2, 1, 1.2],
            rotate: [0, -90, 0],
          }}
          transition={{ duration: 18, repeat: Infinity, ease: 'linear' }}
        />
      </div>

      {/* 标题区域 */}
      <motion.div
        initial={{ opacity: 0, y: -20 }}
        animate={{ opacity: 1, y: 0 }}
        className="text-center mb-12 relative z-10"
      >
        <motion.div
          className="inline-flex items-center gap-2 text-white/80 mb-4 px-4 py-2 rounded-full bg-white/10 backdrop-blur-sm border border-white/20"
          initial={{ opacity: 0, x: -20 }}
          animate={{ opacity: 1, x: 0 }}
        >
          <Zap size={18} className="text-yellow-400" />
          <span className="text-sm">探索所有功能</span>
        </motion.div>

        <motion.h1 
          className="text-4xl md:text-5xl font-bold text-white mb-4 font-[Roboto_Slab]"
          initial={{ opacity: 0, scale: 0.9 }}
          animate={{ opacity: 1, scale: 1 }}
          transition={{ delay: 0.1 }}
        >
          功能模块
        </motion.h1>

        <motion.p 
          className="text-white/70 max-w-2xl mx-auto"
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          transition={{ delay: 0.2 }}
        >
          我们提供丰富的功能模块，满足您的各种需求。每个模块都经过精心设计，为您带来极致体验。
        </motion.p>
      </motion.div>

      {/* 功能卡片网格 */}
      <motion.div
        initial={{ opacity: 0 }}
        animate={{ opacity: 1 }}
        transition={{ delay: 0.3 }}
        className="max-w-6xl mx-auto relative z-10"
      >
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6">
          {features.map((feature, index) => (
            <FeatureCard key={feature.id} feature={feature} index={index} />
          ))}
        </div>
      </motion.div>

      {/* CTA区域 */}
      <motion.div
        initial={{ opacity: 0, y: 30 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ delay: 0.5 }}
        className="text-center mt-16 relative z-10"
      >
        <motion.div
          className="inline-block glass-effect rounded-3xl p-8 max-w-lg"
          whileHover={{ scale: 1.02 }}
        >
          <motion.div
            className="w-16 h-16 rounded-2xl bg-gradient-to-br from-purple-500 to-pink-500 flex items-center justify-center mx-auto mb-4"
            animate={{ rotate: [0, 360] }}
            transition={{ duration: 20, repeat: Infinity, ease: 'linear' }}
          >
            <Sparkles size={32} className="text-white" />
          </motion.div>

          <h3 className="text-white font-semibold text-2xl mb-2">准备好开始了吗？</h3>
          <p className="text-white/70 mb-6">立即下载体验全部功能，开启您的精彩旅程</p>

          <div className="flex flex-col sm:flex-row gap-3 justify-center">
            <Ripple className="bg-gradient-to-r from-purple-500 to-pink-500 px-8 py-3 rounded-full text-white font-semibold flex items-center justify-center gap-2 shadow-lg">
              <Download size={18} />
              免费下载
            </Ripple>
            <Ripple className="bg-white/10 border border-white/20 px-8 py-3 rounded-full text-white font-medium flex items-center justify-center gap-2 hover:bg-white/20 transition-colors">
              了解更多
              <ArrowRight size={18} />
            </Ripple>
          </div>
        </motion.div>
      </motion.div>

      {/* 特性亮点 */}
      <motion.div
        initial={{ opacity: 0, y: 20 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ delay: 0.7 }}
        className="max-w-4xl mx-auto mt-16 relative z-10"
      >
        <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
          {[
            { title: '安全可靠', desc: '端到端加密，保护您的隐私', icon: '🔒' },
            { title: '极速响应', desc: '毫秒级响应，流畅体验', icon: '⚡' },
            { title: '持续更新', desc: '定期更新，功能不断', icon: '🚀' },
          ].map((item, index) => (
            <motion.div
              key={item.title}
              initial={{ opacity: 0, y: 20 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ delay: 0.8 + index * 0.1 }}
              className="glass-effect rounded-2xl p-6 text-center"
              whileHover={{ y: -5, scale: 1.02 }}
            >
              <motion.div
                className="text-4xl mb-3"
                animate={{ scale: [1, 1.1, 1] }}
                transition={{ duration: 2, repeat: Infinity, delay: index * 0.3 }}
              >
                {item.icon}
              </motion.div>
              <h4 className="text-white font-semibold mb-2">{item.title}</h4>
              <p className="text-white/60 text-sm">{item.desc}</p>
            </motion.div>
          ))}
        </div>
      </motion.div>
    </div>
  );
}