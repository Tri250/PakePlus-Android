import { useState } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import * as Icons from 'lucide-react';

export default function HomeScreen({ features, onFeatureClick }) {
  const [selectedFeature, setSelectedFeature] = useState(null);

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
    <div className="h-full bg-gradient-to-br from-gray-900 via-gray-800 to-gray-900 p-4 overflow-hidden">
      <div className="flex items-center justify-between mb-6">
        <div>
          <h1 className="text-white text-lg font-semibold">MyApp</h1>
          <p className="text-gray-400 text-sm">欢迎回来</p>
        </div>
        <div className="flex items-center gap-3">
          <div className="w-10 h-10 rounded-full bg-gradient-to-r from-purple-500 to-pink-500 flex items-center justify-center">
            <Icons.User size={20} className="text-white" />
          </div>
        </div>
      </div>

      <div className="mb-6">
        <div className="bg-gradient-to-r from-purple-600 to-pink-600 rounded-2xl p-4">
          <h2 className="text-white font-semibold mb-2">今日概览</h2>
          <div className="flex justify-between text-white/80 text-sm">
            <span>5 条未读消息</span>
            <span>8 个待办事项</span>
            <span>26°C 晴朗</span>
          </div>
        </div>
      </div>

      <AnimatePresence mode="wait">
        {selectedFeature ? (
          <motion.div
            initial={{ opacity: 0, x: 50 }}
            animate={{ opacity: 1, x: 0 }}
            exit={{ opacity: 0, x: -50 }}
            className="h-[calc(100%-180px)]"
          >
            <div className="bg-gray-800/80 backdrop-blur rounded-2xl p-4 h-full flex flex-col">
              <div className="flex items-center gap-3 mb-4">
                <button
                  onClick={closeDetail}
                  className="w-8 h-8 rounded-full bg-gray-700 flex items-center justify-center"
                >
                  <Icons.ArrowLeft size={18} className="text-white" />
                </button>
                <h3 className="text-white font-semibold text-lg">{selectedFeature.name}</h3>
              </div>
              <div className={`w-16 h-16 rounded-2xl bg-gradient-to-br ${selectedFeature.color} flex items-center justify-center mb-4`}>
                {getIcon(selectedFeature.icon)}
              </div>
              <p className="text-gray-300 mb-4">{selectedFeature.description}</p>
              <p className="text-gray-400 text-sm mb-4">{selectedFeature.details}</p>
              {Object.keys(selectedFeature.stats).length > 0 && (
                <div className="grid grid-cols-2 gap-3 mt-auto">
                  {Object.entries(selectedFeature.stats).map(([key, value]) => (
                    <div key={key} className="bg-gray-700/50 rounded-xl p-3 text-center">
                      <p className="text-white font-bold text-lg">{value}</p>
                      <p className="text-gray-400 text-xs capitalize">{key}</p>
                    </div>
                  ))}
                </div>
              )}
            </div>
          </motion.div>
        ) : (
          <motion.div
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            className="grid grid-cols-4 gap-3"
          >
            {features.map((feature, index) => (
              <motion.div
                key={feature.id}
                initial={{ opacity: 0, y: 20 }}
                animate={{ opacity: 1, y: 0 }}
                transition={{ delay: index * 0.05 }}
                onClick={() => handleFeatureClick(feature)}
                className="flex flex-col items-center cursor-pointer group"
              >
                <div className={`app-icon bg-gradient-to-br ${feature.color} text-white shadow-lg group-hover:shadow-purple-500/30`}>
                  {getIcon(feature.icon)}
                </div>
                <span className="text-white text-xs text-center truncate w-full">{feature.name}</span>
              </motion.div>
            ))}
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  );
}