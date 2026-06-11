import { motion } from 'framer-motion';
import FeatureCard from '../components/FeatureCard';
import { Sparkles } from 'lucide-react';

export default function FeaturesPage({ features }) {
  return (
    <div className="min-h-screen p-8">
      <motion.div
        initial={{ opacity: 0, y: -20 }}
        animate={{ opacity: 1, y: 0 }}
        className="text-center mb-12"
      >
        <motion.div
          className="inline-flex items-center gap-2 text-white/80 mb-4"
          initial={{ opacity: 0, x: -20 }}
          animate={{ opacity: 1, x: 0 }}
        >
          <Sparkles size={20} />
          <span className="text-sm">探索所有功能</span>
        </motion.div>
        <h1 className="text-4xl md:text-5xl font-bold text-white mb-4 font-[Roboto_Slab]">
          功能模块
        </h1>
        <p className="text-white/70 max-w-2xl mx-auto">
          我们提供丰富的功能模块，满足您的各种需求。点击卡片了解更多详情。
        </p>
      </motion.div>

      <motion.div
        initial={{ opacity: 0 }}
        animate={{ opacity: 1 }}
        className="max-w-6xl mx-auto"
      >
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6">
          {features.map((feature, index) => (
            <FeatureCard key={feature.id} feature={feature} index={index} />
          ))}
        </div>
      </motion.div>

      <motion.div
        initial={{ opacity: 0, y: 30 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ delay: 0.5 }}
        className="text-center mt-12"
      >
        <div className="glass-effect rounded-2xl p-8 max-w-md mx-auto">
          <h3 className="text-white font-semibold text-xl mb-2">准备好开始了吗？</h3>
          <p className="text-white/70 mb-4">立即下载体验全部功能</p>
          <motion.button
            className="bg-white text-purple-600 px-6 py-3 rounded-full font-semibold hover:bg-white/90 transition-all duration-300 shadow-lg"
            whileHover={{ scale: 1.05 }}
            whileTap={{ scale: 0.95 }}
          >
            免费下载
          </motion.button>
        </div>
      </motion.div>
    </div>
  );
}