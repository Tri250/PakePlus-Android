import { useState, useRef, useEffect } from 'react';
import { RotateCw, RotateCcw, Volume2, VolumeX, Sun, Moon, Power, Wifi, Battery, Signal } from 'lucide-react';
import { motion, AnimatePresence } from 'framer-motion';
import { Ripple } from './Animations';
import { useTheme } from '../context/ThemeContext';

export default function DeviceFrame({ children, onInteraction }) {
  const [isRotated, setIsRotated] = useState(false);
  const [showControls, setShowControls] = useState(false);
  const [isPowered, setIsPowered] = useState(true);
  const [currentTime, setCurrentTime] = useState(new Date());
  const [touchPoint, setTouchPoint] = useState(null);
  const { isDark, toggleTheme } = useTheme();
  const deviceRef = useRef(null);

  // 更新时间
  useEffect(() => {
    const timer = setInterval(() => setCurrentTime(new Date()), 1000);
    return () => clearInterval(timer);
  }, []);

  // 触摸反馈
  const handleTouch = (e) => {
    if (!isPowered) return;
    const rect = deviceRef.current?.getBoundingClientRect();
    if (rect) {
      setTouchPoint({
        x: e.clientX - rect.left,
        y: e.clientY - rect.top,
      });
      setTimeout(() => setTouchPoint(null), 300);
      onInteraction && onInteraction();
    }
  };

  const formatTime = (date) => {
    return date.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' });
  };

  return (
    <div className="flex flex-col items-center gap-6">
      {/* 设备框架 */}
      <motion.div
        ref={deviceRef}
        className="relative"
        animate={{ 
          rotate: isRotated ? 90 : 0,
          scale: isPowered ? 1 : 0.98,
        }}
        transition={{ type: 'spring', stiffness: 100, damping: 15 }}
        onMouseEnter={() => setShowControls(true)}
        onMouseLeave={() => setShowControls(false)}
        onMouseMove={handleTouch}
      >
        {/* 设备光晕效果 */}
        <motion.div
          className="absolute -inset-4 rounded-[3rem] opacity-0"
          animate={{ 
            opacity: showControls ? 0.3 : 0,
            scale: showControls ? 1.05 : 1,
          }}
          style={{
            background: 'radial-gradient(circle at center, rgba(147, 51, 234, 0.4) 0%, transparent 70%)',
            filter: 'blur(20px)',
          }}
        />

        {/* 主设备 */}
        <motion.div 
          className="device-screen w-72 h-[520px] shadow-2xl relative overflow-visible"
          animate={{
            boxShadow: isPowered 
              ? '0 25px 50px -12px rgba(0, 0, 0, 0.5), 0 0 0 1px rgba(255, 255, 255, 0.1)'
              : '0 10px 20px -5px rgba(0, 0, 0, 0.3)',
          }}
        >
          {/* 屏幕内容 */}
          <AnimatePresence mode="wait">
            {isPowered ? (
              <motion.div
                key="on"
                initial={{ opacity: 0 }}
                animate={{ opacity: 1 }}
                exit={{ opacity: 0 }}
                className="w-full h-full flex flex-col"
              >
                {/* 状态栏 */}
                <div className="h-10 bg-gray-900/95 flex items-center justify-between px-4 relative z-10">
                  <div className="flex items-center gap-1">
                    <Signal size={14} className="text-white/70" />
                    <Wifi size={14} className="text-white/70" />
                  </div>
                  <div className="text-white/90 text-xs font-medium">
                    {formatTime(currentTime)}
                  </div>
                  <div className="flex items-center gap-1">
                    <Battery size={16} className="text-green-400" />
                    <span className="text-white/70 text-xs">100%</span>
                  </div>
                </div>

                {/* 刘海 */}
                <div className="absolute top-0 left-1/2 -translate-x-1/2 w-32 h-7 bg-gray-900 rounded-b-2xl flex items-center justify-center gap-2 z-20">
                  <div className="w-2 h-2 bg-gray-700 rounded-full" />
                  <div className="w-12 h-3 bg-gray-800 rounded-full" />
                  <div className="w-2 h-2 bg-gray-600 rounded-full" />
                </div>

                {/* 屏幕内容区 */}
                <div className="flex-1 overflow-hidden relative">
                  <AnimatePresence>
                    {touchPoint && (
                      <motion.div
                        initial={{ scale: 0, opacity: 0.5 }}
                        animate={{ scale: 2, opacity: 0 }}
                        exit={{ opacity: 0 }}
                        transition={{ duration: 0.3 }}
                        className="absolute w-8 h-8 rounded-full bg-white/20 pointer-events-none"
                        style={{
                          left: touchPoint.x - 16,
                          top: touchPoint.y - 16,
                        }}
                      />
                    )}
                  </AnimatePresence>
                  {children}
                </div>

                {/* 导航栏 */}
                <div className="h-14 bg-gray-900/95 flex items-center justify-center gap-8 relative z-10">
                  <motion.button 
                    className="w-6 h-6 rounded-full border-2 border-white/30 hover:border-white/50 transition-colors"
                    whileHover={{ scale: 1.2 }}
                    whileTap={{ scale: 0.9 }}
                  />
                  <motion.div 
                    className="w-12 h-12 rounded-full border-2 border-white/40 flex items-center justify-center bg-white/5 hover:bg-white/10 transition-colors cursor-pointer"
                    whileHover={{ scale: 1.1 }}
                    whileTap={{ scale: 0.9, backgroundColor: 'rgba(255,255,255,0.2)' }}
                  >
                    <div className="w-5 h-5 rounded-full bg-white/60" />
                  </motion.div>
                  <motion.button 
                    className="w-6 h-6 rounded-sm border-2 border-white/30 hover:border-white/50 transition-colors"
                    whileHover={{ scale: 1.2 }}
                    whileTap={{ scale: 0.9 }}
                  />
                </div>
              </motion.div>
            ) : (
              <motion.div
                key="off"
                initial={{ opacity: 0 }}
                animate={{ opacity: 1 }}
                exit={{ opacity: 0 }}
                className="w-full h-full bg-black flex items-center justify-center"
              >
                <motion.button
                  onClick={() => setIsPowered(true)}
                  className="w-16 h-16 rounded-full bg-gray-800 flex items-center justify-center hover:bg-gray-700 transition-colors"
                  whileHover={{ scale: 1.1 }}
                  whileTap={{ scale: 0.9 }}
                >
                  <Power size={24} className="text-white/50" />
                </motion.button>
              </motion.div>
            )}
          </AnimatePresence>
        </motion.div>

        {/* 设备边框光效 */}
        <div className="absolute inset-0 rounded-3xl pointer-events-none">
          <div className="absolute inset-0 rounded-3xl border border-white/10" />
          <div className="absolute top-0 left-0 right-0 h-px bg-gradient-to-r from-transparent via-white/20 to-transparent" />
        </div>

        {/* 设备阴影 */}
        <div className="absolute -bottom-4 left-1/2 -translate-x-1/2 w-48 h-4 bg-black/40 rounded-full blur-xl" />
      </motion.div>

      {/* 控制按钮组 */}
      <motion.div
        initial={{ opacity: 0, y: 10 }}
        animate={{ opacity: 1, y: 0 }}
        className="flex items-center gap-3"
      >
        <Ripple className="glass-effect px-4 py-2 rounded-full flex items-center gap-2 text-white hover:bg-white/20 transition-all duration-300">
          {isRotated ? <RotateCcw size={18} /> : <RotateCw size={18} />}
          <span className="text-sm">{isRotated ? '竖屏' : '横屏'}</span>
        </Ripple>
        
        <Ripple
          onClick={() => setIsPowered(!isPowered)}
          className="glass-effect px-4 py-2 rounded-full flex items-center gap-2 text-white hover:bg-white/20 transition-all duration-300"
        >
          <Power size={18} className={isPowered ? 'text-green-400' : 'text-red-400'} />
          <span className="text-sm">{isPowered ? '关机' : '开机'}</span>
        </Ripple>

        <Ripple
          onClick={toggleTheme}
          className="glass-effect w-10 h-10 rounded-full flex items-center justify-center text-white hover:bg-white/20 transition-all duration-300"
        >
          {isDark ? <Sun size={18} /> : <Moon size={18} />}
        </Ripple>
      </motion.div>
    </div>
  );
}