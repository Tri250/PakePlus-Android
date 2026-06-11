import { useState } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import Navigation from './components/Navigation';
import HomePage from './pages/HomePage';
import FeaturesPage from './pages/FeaturesPage';
import AboutPage from './pages/AboutPage';
import { features, appInfo, navigationItems } from './data/features';

function App() {
  const [activeTab, setActiveTab] = useState('home');

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
    <div className="min-h-screen">
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

      <main className="pt-20">
        <AnimatePresence mode="wait">
          <motion.div
            key={activeTab}
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0, y: -20 }}
            transition={{ duration: 0.3 }}
          >
            {renderPage()}
          </motion.div>
        </AnimatePresence>
      </main>
    </div>
  );
}

export default App;