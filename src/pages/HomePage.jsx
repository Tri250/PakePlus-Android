import { useState, useEffect } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import DeviceFrame from '../components/DeviceFrame';
import HomeScreen from '../components/HomeScreen';
import { BounceIn, SlideIn } from '../components/Animations';
import { Sparkles, Download, Star, ChevronDown } from 'lucide-react';

export default function HomePage({ features, appInfo }) {
  const [mousePosition, setMousePosition] = useState({ x: 0, y: 0 });
  const [showScrollIndicator, setShowScrollIndicator] = useState(true);

  useEffect(() => {
    const handleMouseMove = (e) => {
      setMousePosition({
        x: (e.clientX / window.innerWidth - 0.5) * 20,
        y: (e.clientY / window.innerHeight - 0.5) * 20,
      });
    };
    window.addEventListener('mousemove', handleMouseMove);
    return () => window.removeEventListener('mousemove', handleMouseMove);
  }, []);

  return (
    <div className="flex flex-col items-center justify-center min-h-screen p-8 relative overflow-hidden">
      {/* 动态背景 */}
      <div className="absolute inset-0 overflow-hidden pointer-events-none">
        {/* 浮动光球 */}
        <motion.div
          className="absolute w-96 h-96 rounded-full"
          style={{
            background: 'radial-gradient(circle, rgba(147, 51, 234, 0.3) 0%, transparent 70%)',
            left: '20%',
            top: '30%',
          }}
          animate={{
            x: mousePosition.x * 2,
            y: mousePosition.y * 2,
            scale: [1, 1.2, 1],
          }}
          transition={{
            scale: { duration: 4, repeat: Infinity, ease: 'easeInOut' },
            x: { type: 'spring', stiffness: 50 },
            y: { type: 'spring', stiffness: 50 },
          }}
        />
        <motion.div
          className="absolute w-80 h-80 rounded-full"
          style={{
            background: 'radial-gradient(circle, rgba(236, 72, 153, 0.3) 0%, transparent 70%)',
            right: '20%',
            bottom: '30%',
          }}
          animate={{
            x: mousePosition.x * -1.5,
            y: mousePosition.y * -1.5,
            scale: [1.2, 1, 1.2],
          }}
          transition={{
            scale: { duration: 5, repeat: Infinity, ease: 'easeInOut' },
            x: { type: 'spring', stiffness: 50 },
            y: { type: 'spring', stiffness: 50 },
          }}
        />
        
        {/* 网格背景 */}
        <div 
          className="absolute inset-0 opacity-10"
          style={{
            backgroundImage: `
              linear-gradient(rgba(255,255,255,0.1) 1px, transparent 1px),
              linear-gradient(90deg, rgba(255,255,255,0.1) 1px, transparent 1px)
            `,
            backgroundSize: '50px 50px',
          }}
        />
      </div>

      {/* 标题区域 */}
      <motion.div
        initial={{ opacity: 0, y: -20 }}
        animate={{ opacity: 1, y: 0 }}
        className="text-center mb-8 relative z-10"
      >
        <motion.div
          className="inline-flex items-center gap-2 px-4 py-2 rounded-full bg-white/10 backdrop-blur-sm border border-white/20 mb-4"
          initial={{ opacity: 0, scale: 0.8 }}
          animate={{ opacity: 1, scale: 1 }}
          transition={{ delay: 0.1 }}
        >
          <Sparkles size={16} className="text-yellow-400" />
          <span className="text-white/80 text-sm">全新体验</span>
        </motion.div>

        <motion.h1 
          className="text-5xl md:text-6xl font-bold text-white mb-4 font-[Roboto_Slab] tracking-tight"
          initial={{ opacity: 0, scale: 0.9 }}
          animate={{ opacity: 1, scale: 1 }}
          transition={{ delay: 0.2 }}
        >
          <motion.span
            animate={{ 
              backgroundPosition: ['0% center', '100% center', '0% center'],
            }}
            transition={{ duration: 5, repeat: Infinity, ease: 'linear' }}
            className="bg-gradient-to-r from-white via-purple-200 to-white bg-clip-text text-transparent bg-[length:200%_auto]"
          >
            {appInfo.name}
          </motion.span>
        </motion.h1>
        
        <motion.p 
          className="text-white/70 text-lg max-w-md mx-auto mb-6"
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          transition={{ delay: 0.3 }}
        >
          {appInfo.description}
        </motion.p>

        {/* 统计数据 */}
        <motion.div 
          className="flex justify-center gap-8"
          initial={{ opacity: 0, y: 10 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ delay: 0.4 }}
        >
          {[
            { value: appInfo.featuresCount, label: '功能模块', icon: Star },
            { value: appInfo.downloads, label: '下载量', icon: Download },
          ].map((stat, index) => {
            const Icon = stat.icon;
            return (
              <motion.div
                key={stat.label}
                className="text-center"
                initial={{ opacity: 0, scale: 0.8 }}
                animate={{ opacity: 1, scale: 1 }}
                transition={{ delay: 0.5 + index * 0.1 }}
                whileHover={{ scale: 1.1, y: -5 }}
              >
                <div className="flex items-center justify-center gap-2 mb-1">
                  <Icon size={16} className="text-purple-300" />
                  <p className="text-white text-2xl font-bold">{stat.value}</p>
                </div>
                <p className="text-white/50 text-sm">{stat.label}</p>
              </motion.div>
            );
          })}
        </motion.div>
      </motion.div>

      {/* 设备展示 */}
      <motion.div
        initial={{ opacity: 0, scale: 0.9, y: 20 }}
        animate={{ opacity: 1, scale: 1, y: 0 }}
        transition={{ delay: 0.6, type: 'spring', stiffness: 100 }}
        className="relative z-10"
      >
        {/* 设备光效背景 */}
        <motion.div
          className="absolute -inset-20 rounded-full opacity-30 blur-3xl"
          style={{
            background: 'radial-gradient(circle at center, rgba(147, 51, 234, 0.5) 0%, rgba(236, 72, 153, 0.3) 50%, transparent 70%)',
          }}
          animate={{
            scale: [1, 1.1, 1],
            rotate: [0, 180, 360],
          }}
          transition={{
            duration: 20,
            repeat: Infinity,
            ease: 'linear',
          }}
        />
        
        <DeviceFrame>
          <HomeScreen features={features} />
        </DeviceFrame>
      </motion.div>

      {/* 提示文字 */}
      <motion.div
        initial={{ opacity: 0 }}
        animate={{ opacity: 1 }}
        transition={{ delay: 1 }}
        className="mt-8 text-center relative z-10"
      >
        <motion.p 
          className="text-white/50 text-sm mb-2"
          animate={{ opacity: [0.5, 1, 0.5] }}
          transition={{ duration: 2, repeat: Infinity }}
        >
          点击设备屏幕上的应用图标查看详情
        </motion.p>
        
        {/* 交互提示动画 */}
        <motion.div
          className="flex items-center justify-center gap-2 text-white/40 text-xs"
          animate={{ y: [0, 5, 0] }}
          transition={{ duration: 1.5, repeat: Infinity }}
        >
          <ChevronDown size={14} />
          <span>滚动探索更多</span>
        </motion.div>
      </motion.div>
    </div>
  );
}