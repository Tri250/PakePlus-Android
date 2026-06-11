import { useState } from 'react';
import { RotateCw, RotateCcw } from 'lucide-react';
import { motion } from 'framer-motion';

export default function DeviceFrame({ children }) {
  const [isRotated, setIsRotated] = useState(false);

  return (
    <div className="flex flex-col items-center gap-6">
      <motion.div
        className={`relative transition-transform duration-500 ${isRotated ? 'rotate-90' : ''}`}
        animate={{ rotate: isRotated ? 90 : 0 }}
        transition={{ type: 'spring', stiffness: 100 }}
      >
        <div className="device-screen w-72 h-[520px] shadow-2xl">
          <div className="w-full h-full flex flex-col">
            <div className="h-10 bg-gray-900 flex items-center justify-center">
              <div className="w-32 h-6 bg-gray-800 rounded-full flex items-center justify-center">
                <div className="w-2 h-2 bg-gray-600 rounded-full mr-1"></div>
                <div className="w-16 h-1 bg-gray-700 rounded-full"></div>
              </div>
            </div>
            <div className="flex-1 overflow-hidden">
              {children}
            </div>
            <div className="h-14 bg-gray-900 flex items-center justify-center">
              <div className="w-16 h-16 rounded-full border-2 border-gray-700 flex items-center justify-center">
                <div className="w-10 h-10 rounded-full bg-gray-700"></div>
              </div>
            </div>
          </div>
        </div>
        <div className="absolute -bottom-2 left-1/2 transform -translate-x-1/2 w-24 h-1 bg-black/30 rounded-full blur-md"></div>
      </motion.div>
      <motion.button
        onClick={() => setIsRotated(!isRotated)}
        className="glass-effect px-4 py-2 rounded-full flex items-center gap-2 text-white hover:bg-white/20 transition-all duration-300"
        whileHover={{ scale: 1.05 }}
        whileTap={{ scale: 0.95 }}
      >
        {isRotated ? <RotateCcw size={18} /> : <RotateCw size={18} />}
        <span>{isRotated ? '竖屏' : '横屏'}</span>
      </motion.button>
    </div>
  );
}