import { useState } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import Navigation from './components/Navigation';
import HomePage from './pages/HomePage';
import FeaturesPage from './pages/FeaturesPage';
import AboutPage from './pages/AboutPage';
import { ThemeProvider } from './context/ThemeContext';
import { useOnboarding, OnboardingGuide } from './components/Onboarding';
import { features, appInfo, navigationItems } from './data/features';

function AppContent() {
  const [activeTab, setActiveTab] = useState('home');
  const onboarding = useOnboarding();

  const renderPage = () => {
    switch (activeTab) {
      case 'home':
        return <HomePage features={features} appInfo={appInfo} />;
      case 'features':
        return <FeaturesPage features={features} />;
      case 'about':
        return <AboutPage appInfo={appInfo} />;
      default:
        return <HomePage features={features} appInfo={appInfo} />;
    }
  };

  return (
    <div className="min-h-screen relative">
      {/* 新手引导 */}
      <OnboardingGuide onboarding={onboarding} />

      {/* 导航栏 */}
      <motion.header
        initial={{ opacity: 0, y: -20 }}
        animate={{ opacity: 1, y: 0 }}
        className="fixed top-0 left-0 right-0 z-50 px-4 py-4"
      >
        <div className="max-w-6xl mx-auto flex justify-center">
          <Navigation
            activeTab={activeTab}
            onTabChange={setActiveTab}
            items={navigationItems}
          />
        </div>
      </motion.header>

      {/* 主内容区 */}
      <main className="pt-20">
        <AnimatePresence mode="wait">
          <motion.div
            key={activeTab}
            initial={{ opacity: 0, x: activeTab === 'home' ? 0 : 50, y: 20 }}
            animate={{ opacity: 1, x: 0, y: 0 }}
            exit={{ opacity: 0, x: activeTab === 'home' ? 0 : -50, y: -20 }}
            transition={{ 
              duration: 0.4,
              type: 'spring',
              stiffness: 300,
              damping: 30,
            }}
          >
            {renderPage()}
          </motion.div>
        </AnimatePresence>
      </main>

      {/* 页面切换进度指示器 */}
      <AnimatePresence>
        <motion.div
          className="fixed top-0 left-0 right-0 h-1 bg-gradient-to-r from-purple-500 via-pink-500 to-purple-500 z-[100]"
          initial={{ scaleX: 0, opacity: 0 }}
          animate={{ scaleX: 1, opacity: 1 }}
          exit={{ scaleX: 0, opacity: 0 }}
          transition={{ duration: 0.3 }}
          style={{ transformOrigin: 'left' }}
        />
      </AnimatePresence>
    </div>
  );
}

function App() {
  return (
    <ThemeProvider>
      <AppContent />
    </ThemeProvider>
  );
}

export default App;