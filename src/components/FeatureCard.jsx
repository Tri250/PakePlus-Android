import { useState } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import * as Icons from 'lucide-react';
import { Ripple } from './Animations';

export default function FeatureCard({ feature, index }) {
  const Icon = Icons[feature.icon];
  const [isExpanded, setIsExpanded] = useState(false);
  const [isHovered, setIsHovered] = useState(false);

  return (
    <motion.div
      initial={{ opacity: 0, y: 30, scale: 0.9 }}
      animate={{ opacity: 1, y: 0, scale: 1 }}
      transition={{ 
        delay: index * 0.1,
        type: 'spring',
        stiffness: 300,
        damping: 20,
      }}
      onHoverStart={() => setIsHovered(true)}
      onHoverEnd={() => setIsHovered(false)}
      className="relative"
    >
      <motion.div
        className="feature-card relative overflow-hidden"
        animate={{
          scale: isHovered ? 1.03 : 1,
          y: isHovered ? -5 : 0,
        }}
        transition={{ type: 'spring', stiffness: 300, damping: 20 }}
      >
        {/* 背景光效 */}
        <motion.div
          className="absolute inset-0 opacity-0"
          animate={{ opacity: isHovered ? 0.1 : 0 }}
          style={{
            background: `linear-gradient(135deg, ${feature.color.includes('purple') ? 'rgba(147, 51, 234, 0.5)' : 'rgba(236, 72, 153, 0.5)'} 0%, transparent 100%)`,
          }}
        />

        {/* 图标 */}
        <motion.div
          className={`w-14 h-14 rounded-2xl bg-gradient-to-br ${feature.color} flex items-center justify-center mb-4 relative`}
          animate={{
            rotate: isHovered ? [0, -5, 5, 0] : 0,
            scale: isHovered ? 1.1 : 1,
          }}
          transition={{ duration: 0.3 }}
        >
          {Icon && <Icon size={28} className="text-white" />}
          
          {/* 光晕效果 */}
          <motion.div
            className="absolute inset-0 rounded-2xl"
            animate={{
              boxShadow: isHovered 
                ? '0 0 30px rgba(147, 51, 234, 0.5)' 
                : '0 0 0px rgba(147, 51, 234, 0)',
            }}
          />
        </motion.div>

        {/* 标题和描述 */}
        <motion.h3 
          className="text-white font-semibold text-lg mb-2"
          animate={{ x: isHovered ? 5 : 0 }}
        >
          {feature.name}
        </motion.h3>
        <p className="text-white/70 text-sm mb-3">{feature.description}</p>
        <p className="text-white/50 text-xs">{feature.details}</p>

        {/* 统计标签 */}
        {Object.keys(feature.stats).length > 0 && (
          <motion.div 
            className="flex flex-wrap gap-2 mt-4"
            initial={{ opacity: 0, y: 10 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ delay: 0.2 + index * 0.05 }}
          >
            {Object.entries(feature.stats).map(([key, value]) => (
              <motion.span
                key={key}
                className="bg-white/10 rounded-full px-3 py-1 text-white/80 text-xs"
                whileHover={{ 
                  scale: 1.1, 
                  backgroundColor: 'rgba(255,255,255,0.2)' 
                }}
              >
                {value}
              </motion.span>
            ))}
          </motion.div>
        )}

        {/* 悬停时的操作按钮 */}
        <AnimatePresence>
          {isHovered && (
            <motion.div
              initial={{ opacity: 0, y: 10 }}
              animate={{ opacity: 1, y: 0 }}
              exit={{ opacity: 0, y: 10 }}
              className="mt-4"
            >
              <Ripple className="w-full bg-gradient-to-r from-purple-500 to-pink-500 rounded-xl py-2 text-center text-white text-sm font-medium">
                查看详情
              </Ripple>
            </motion.div>
          )}
        </AnimatePresence>
      </motion.div>

      {/* 卡片边框光效 */}
      <motion.div
        className="absolute inset-0 rounded-2xl pointer-events-none"
        animate={{
          boxShadow: isHovered 
            ? '0 0 0 2px rgba(147, 51, 234, 0.3), 0 20px 40px -10px rgba(0, 0, 0, 0.3)' 
            : '0 0 0 0px rgba(147, 51, 234, 0)',
        }}
      />
    </motion.div>
  );
}