import { useState, useEffect } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { X, ChevronRight, Sparkles } from 'lucide-react';

const guideSteps = [
  {
    title: '欢迎体验 MyApp',
    description: '这是一个功能丰富的Android应用展示，让我们来探索它的功能吧！',
    icon: Sparkles,
  },
  {
    title: '交互式设备模拟',
    description: '点击屏幕上的应用图标，查看详细功能介绍。支持横竖屏切换。',
    target: 'device',
  },
  {
    title: '探索功能模块',
    description: '切换到"功能"页面，查看所有功能模块的详细信息。',
    target: 'nav-features',
  },
];

export function useOnboarding() {
  const [showGuide, setShowGuide] = useState(() => {
    if (typeof window !== 'undefined') {
      return !localStorage.getItem('onboarding_completed');
    }
    return false;
  });
  const [currentStep, setCurrentStep] = useState(0);

  useEffect(() => {
    if (!showGuide) {
      localStorage.setItem('onboarding_completed', 'true');
    }
  }, [showGuide]);

  const nextStep = () => {
    if (currentStep < guideSteps.length - 1) {
      setCurrentStep(currentStep + 1);
    } else {
      setShowGuide(false);
    }
  };

  const skipGuide = () => {
    setShowGuide(false);
    localStorage.setItem('onboarding_completed', 'true');
  };

  return {
    showGuide,
    currentStep,
    totalSteps: guideSteps.length,
    step: guideSteps[currentStep],
    nextStep,
    skipGuide,
    progress: ((currentStep + 1) / guideSteps.length) * 100,
  };
}

export function OnboardingGuide({ onboarding }) {
  const { showGuide, step, currentStep, totalSteps, nextStep, skipGuide, progress } = onboarding;

  if (!showGuide) return null;

  const Icon = step?.icon;

  return (
    <AnimatePresence>
      <motion.div
        initial={{ opacity: 0 }}
        animate={{ opacity: 1 }}
        exit={{ opacity: 0 }}
        className="fixed inset-0 z-[100] flex items-center justify-center bg-black/60 backdrop-blur-sm"
      >
        <motion.div
          initial={{ scale: 0.9, y: 20 }}
          animate={{ scale: 1, y: 0 }}
          exit={{ scale: 0.9, y: 20 }}
          className="relative max-w-md mx-4 bg-gradient-to-br from-purple-900/90 to-pink-900/90 backdrop-blur-xl rounded-3xl p-8 shadow-2xl border border-white/20"
        >
          {/* 进度条 */}
          <div className="absolute top-0 left-0 right-0 h-1 bg-white/10 rounded-t-3xl overflow-hidden">
            <motion.div
              className="h-full bg-gradient-to-r from-purple-400 to-pink-400"
              initial={{ width: 0 }}
              animate={{ width: `${progress}%` }}
              transition={{ duration: 0.3 }}
            />
          </div>

          {/* 关闭按钮 */}
          <button
            onClick={skipGuide}
            className="absolute top-4 right-4 w-8 h-8 rounded-full bg-white/10 flex items-center justify-center hover:bg-white/20 transition-colors"
          >
            <X size={16} className="text-white" />
          </button>

          {/* 内容 */}
          <div className="text-center">
            {Icon && (
              <motion.div
                initial={{ scale: 0 }}
                animate={{ scale: 1 }}
                transition={{ type: 'spring', delay: 0.1 }}
                className="w-16 h-16 mx-auto mb-6 rounded-2xl bg-gradient-to-br from-purple-500 to-pink-500 flex items-center justify-center"
              >
                <Icon size={32} className="text-white" />
              </motion.div>
            )}

            <motion.h3
              initial={{ opacity: 0, y: 10 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ delay: 0.2 }}
              className="text-2xl font-bold text-white mb-3"
            >
              {step?.title}
            </motion.h3>

            <motion.p
              initial={{ opacity: 0, y: 10 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ delay: 0.3 }}
              className="text-white/70 mb-8"
            >
              {step?.description}
            </motion.p>

            {/* 步骤指示器 */}
            <div className="flex justify-center gap-2 mb-6">
              {Array.from({ length: totalSteps }).map((_, i) => (
                <motion.div
                  key={i}
                  className={`w-2 h-2 rounded-full transition-all duration-300 ${
                    i === currentStep
                      ? 'bg-white w-6'
                      : i < currentStep
                      ? 'bg-white/60'
                      : 'bg-white/30'
                  }`}
                />
              ))}
            </div>

            {/* 按钮 */}
            <div className="flex gap-3 justify-center">
              <motion.button
                onClick={skipGuide}
                className="px-6 py-3 rounded-full text-white/70 hover:text-white hover:bg-white/10 transition-all"
                whileHover={{ scale: 1.05 }}
                whileTap={{ scale: 0.95 }}
              >
                跳过
              </motion.button>
              <motion.button
                onClick={nextStep}
                className="px-6 py-3 rounded-full bg-white text-purple-600 font-semibold flex items-center gap-2 hover:bg-white/90 transition-all shadow-lg"
                whileHover={{ scale: 1.05 }}
                whileTap={{ scale: 0.95 }}
              >
                {currentStep === totalSteps - 1 ? '开始体验' : '下一步'}
                <ChevronRight size={18} />
              </motion.button>
            </div>
          </div>
        </motion.div>
      </motion.div>
    </AnimatePresence>
  );
}

// Toast通知组件
export function Toast({ message, type = 'info', onClose }) {
  const types = {
    info: 'from-blue-500 to-cyan-500',
    success: 'from-green-500 to-emerald-500',
    warning: 'from-orange-500 to-yellow-500',
    error: 'from-red-500 to-pink-500',
  };

  return (
    <motion.div
      initial={{ opacity: 0, y: 50, scale: 0.9 }}
      animate={{ opacity: 1, y: 0, scale: 1 }}
      exit={{ opacity: 0, y: 50, scale: 0.9 }}
      className={`fixed bottom-8 left-1/2 -translate-x-1/2 z-[200] px-6 py-4 rounded-2xl bg-gradient-to-r ${types[type]} text-white shadow-2xl flex items-center gap-3`}
    >
      <span>{message}</span>
      <button onClick={onClose} className="hover:bg-white/20 rounded-full p-1 transition-colors">
        <X size={16} />
      </button>
    </motion.div>
  );
}