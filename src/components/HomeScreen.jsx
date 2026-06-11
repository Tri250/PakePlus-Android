import { useState, useRef } from 'react';
import { motion, AnimatePresence, useMotionValue, useSpring, useTransform } from 'framer-motion';
import * as Icons from 'lucide-react';
import { Ripple, SlideIn } from './Animations';

export default function HomeScreen({ features, onFeatureClick }) {
  const [selectedFeature, setSelectedFeature] = useState(null);
  const [hoveredFeature, setHoveredFeature] = useState(null);
  const containerRef = useRef(null);

  const getIcon = (iconName) => {
    const Icon = Icons[iconName];
    return Icon ? <Icon size={28} /> : null;
  };

  const handleFeatureClick = (feature) => {
    setSelectedFeature(feature);
    onFeatureClick && onFeatureClick(feature);
  };

  const closeDetail = () => {
    setSelectedFeature(null);
  };

  return (
    <div 
      ref={containerRef}
      className="h-full bg-gradient-to-br from-gray-900 via-gray-800 to-gray-900 p-4 overflow-hidden relative"
    >
      {/* 背景装饰 */}
      <div className="absolute inset-0 overflow-hidden pointer-events-none">
        <div className="absolute -top-20 -right-20 w-40 h-40 bg-purple-500/20 rounded-full blur-3xl" />
        <div className="absolute -bottom-20 -left-20 w-40 h-40 bg-pink-500/20 rounded-full blur-3xl" />
      </div>

      {/* 头部 */}
      <motion.div 
        className="flex items-center justify-between mb-6 relative z-10"
        initial={{ opacity: 0, y: -10 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ delay: 0.1 }}
      >
        <div>
          <motion.h1 
            className="text-white text-lg font-semibold"
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            transition={{ delay: 0.2 }}
          >
            MyApp
          </motion.h1>
          <motion.p 
            className="text-gray-400 text-sm"
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            transition={{ delay: 0.3 }}
          >
            欢迎回来
          </motion.p>
        </div>
        <motion.div 
          className="flex items-center gap-3"
          initial={{ opacity: 0, scale: 0.8 }}
          animate={{ opacity: 1, scale: 1 }}
          transition={{ delay: 0.4 }}
        >
          <motion.div 
            className="w-10 h-10 rounded-full bg-gradient-to-r from-purple-500 to-pink-500 flex items-center justify-center cursor-pointer"
            whileHover={{ scale: 1.1, rotate: 10 }}
            whileTap={{ scale: 0.9 }}
          >
            <Icons.User size={20} className="text-white" />
          </motion.div>
        </motion.div>
      </motion.div>

      {/* 今日概览卡片 */}
      <motion.div
        initial={{ opacity: 0, y: 20 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ delay: 0.2 }}
        className="mb-6 relative z-10"
      >
        <Ripple className="bg-gradient-to-r from-purple-600 via-pink-600 to-purple-600 rounded-2xl p-4 relative overflow-hidden">
          {/* 卡片动画背景 */}
          <motion.div
            className="absolute inset-0 bg-gradient-to-r from-transparent via-white/10 to-transparent"
            animate={{
              x: ['-100%', '100%'],
            }}
            transition={{
              duration: 3,
              repeat: Infinity,
              ease: 'linear',
            }}
          />
          <h2 className="text-white font-semibold mb-2 relative z-10">今日概览</h2>
          <div className="flex justify-between text-white/80 text-sm relative z-10">
            <motion.span
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              transition={{ delay: 0.4 }}
            >
              5 条未读消息
            </motion.span>
            <motion.span
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              transition={{ delay: 0.5 }}
            >
              8 个待办事项
            </motion.span>
            <motion.span
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              transition={{ delay: 0.6 }}
            >
              26°C 晴朗
            </motion.span>
          </div>
        </Ripple>
      </motion.div>

      {/* 功能模块区域 */}
      <AnimatePresence mode="wait">
        {selectedFeature ? (
          // 详情页面
          <motion.div
            key="detail"
            initial={{ opacity: 0, x: 50, scale: 0.95 }}
            animate={{ opacity: 1, x: 0, scale: 1 }}
            exit={{ opacity: 0, x: -50, scale: 0.95 }}
            transition={{ type: 'spring', stiffness: 300, damping: 30 }}
            className="h-[calc(100%-180px)] relative z-10"
          >
            <motion.div 
              className="bg-gray-800/80 backdrop-blur-xl rounded-2xl p-4 h-full flex flex-col border border-white/10"
              layoutId={`card-${selectedFeature.id}`}
            >
              {/* 返回按钮和标题 */}
              <div className="flex items-center gap-3 mb-4">
                <motion.button
                  onClick={closeDetail}
                  className="w-8 h-8 rounded-full bg-gray-700 flex items-center justify-center hover:bg-gray-600 transition-colors"
                  whileHover={{ scale: 1.1, rotate: -10 }}
                  whileTap={{ scale: 0.9 }}
                >
                  <Icons.ArrowLeft size={18} className="text-white" />
                </motion.button>
                <motion.h3 
                  className="text-white font-semibold text-lg"
                  initial={{ opacity: 0, x: -10 }}
                  animate={{ opacity: 1, x: 0 }}
                  transition={{ delay: 0.1 }}
                >
                  {selectedFeature.name}
                </motion.h3>
              </div>

              {/* 图标 */}
              <motion.div
                initial={{ scale: 0, rotate: -180 }}
                animate={{ scale: 1, rotate: 0 }}
                transition={{ type: 'spring', stiffness: 260, damping: 20, delay: 0.1 }}
                className={`w-16 h-16 rounded-2xl bg-gradient-to-br ${selectedFeature.color} flex items-center justify-center mb-4 shadow-lg`}
                style={{ boxShadow: '0 10px 30px -10px rgba(147, 51, 234, 0.5)' }}
              >
                {getIcon(selectedFeature.icon)}
              </motion.div>

              {/* 描述 */}
              <motion.p 
                className="text-gray-300 mb-4"
                initial={{ opacity: 0 }}
                animate={{ opacity: 1 }}
                transition={{ delay: 0.2 }}
              >
                {selectedFeature.description}
              </motion.p>
              <motion.p 
                className="text-gray-400 text-sm mb-4"
                initial={{ opacity: 0 }}
                animate={{ opacity: 1 }}
                transition={{ delay: 0.3 }}
              >
                {selectedFeature.details}
              </motion.p>

              {/* 统计数据 */}
              {Object.keys(selectedFeature.stats).length > 0 && (
                <motion.div 
                  className="grid grid-cols-2 gap-3 mt-auto"
                  initial={{ opacity: 0, y: 20 }}
                  animate={{ opacity: 1, y: 0 }}
                  transition={{ delay: 0.4 }}
                >
                  {Object.entries(selectedFeature.stats).map(([key, value], index) => (
                    <motion.div
                      key={key}
                      initial={{ opacity: 0, scale: 0.8 }}
                      animate={{ opacity: 1, scale: 1 }}
                      transition={{ delay: 0.5 + index * 0.1 }}
                      className="bg-gray-700/50 rounded-xl p-3 text-center hover:bg-gray-700/70 transition-colors"
                    >
                      <p className="text-white font-bold text-lg">{value}</p>
                      <p className="text-gray-400 text-xs capitalize">{key}</p>
                    </motion.div>
                  ))}
                </motion.div>
              )}

              {/* 操作按钮 */}
              <motion.div
                initial={{ opacity: 0, y: 10 }}
                animate={{ opacity: 1, y: 0 }}
                transition={{ delay: 0.6 }}
                className="mt-4 flex gap-2"
              >
                <Ripple className="flex-1 bg-gradient-to-r from-purple-500 to-pink-500 rounded-xl py-3 text-center text-white font-medium">
                  打开应用
                </Ripple>
              </motion.div>
            </motion.div>
          </motion.div>
        ) : (
          // 应用图标网格
          <motion.div
            key="grid"
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            className="grid grid-cols-4 gap-3 relative z-10"
          >
            {features.map((feature, index) => (
              <motion.div
                key={feature.id}
                initial={{ opacity: 0, y: 20, scale: 0.8 }}
                animate={{ opacity: 1, y: 0, scale: 1 }}
                transition={{ 
                  delay: index * 0.05,
                  type: 'spring',
                  stiffness: 300,
                  damping: 20,
                }}
                onClick={() => handleFeatureClick(feature)}
                onHoverStart={() => setHoveredFeature(feature.id)}
                onHoverEnd={() => setHoveredFeature(null)}
                className="flex flex-col items-center cursor-pointer group"
              >
                {/* 应用图标 */}
                <motion.div
                  className={`app-icon bg-gradient-to-br ${feature.color} text-white relative overflow-hidden`}
                  whileHover={{ 
                    scale: 1.15, 
                    y: -5,
                    boxShadow: '0 15px 30px -10px rgba(147, 51, 234, 0.5)',
                  }}
                  whileTap={{ scale: 0.95 }}
                  layoutId={`icon-${feature.id}`}
                >
                  {/* 悬停光效 */}
                  <AnimatePresence>
                    {hoveredFeature === feature.id && (
                      <motion.div
                        initial={{ opacity: 0 }}
                        animate={{ opacity: 1 }}
                        exit={{ opacity: 0 }}
                        className="absolute inset-0 bg-white/20"
                      />
                    )}
                  </AnimatePresence>
                  
                  {/* 图标 */}
                  <motion.div
                    animate={hoveredFeature === feature.id ? { rotate: [0, -10, 10, 0] } : {}}
                    transition={{ duration: 0.3 }}
                  >
                    {getIcon(feature.icon)}
                  </motion.div>

                  {/* 通知徽章 */}
                  {feature.stats?.unread && (
                    <motion.div
                      initial={{ scale: 0 }}
                      animate={{ scale: 1 }}
                      className="absolute -top-1 -right-1 w-5 h-5 bg-red-500 rounded-full flex items-center justify-center text-white text-xs font-bold"
                    >
                      {feature.stats.unread}
                    </motion.div>
                  )}
                </motion.div>
                
                {/* 应用名称 */}
                <motion.span 
                  className="text-white text-xs text-center truncate w-full mt-1"
                  animate={{
                    color: hoveredFeature === feature.id ? '#ffffff' : 'rgba(255,255,255,0.9)',
                  }}
                >
                  {feature.name}
                </motion.span>
              </motion.div>
            ))}
          </motion.div>
        )}
      </AnimatePresence>

      {/* 底部搜索栏 */}
      <motion.div
        initial={{ opacity: 0, y: 20 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ delay: 0.5 }}
        className="absolute bottom-2 left-4 right-4 z-10"
      >
        <Ripple className="bg-gray-800/60 backdrop-blur-sm rounded-full py-2 px-4 flex items-center gap-2 text-gray-400">
          <Icons.Search size={16} />
          <span className="text-sm">搜索应用...</span>
        </Ripple>
      </motion.div>
    </div>
  );
}