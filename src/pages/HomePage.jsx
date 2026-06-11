import { motion } from 'framer-motion';
import DeviceFrame from '../components/DeviceFrame';
import HomeScreen from '../components/HomeScreen';

export default function HomePage({ features, appInfo }) {
  return (
    <div className="flex flex-col items-center justify-center min-h-screen p-8">
      <motion.div
        initial={{ opacity: 0, y: -20 }}
        animate={{ opacity: 1, y: 0 }}
        className="text-center mb-8"
      >
        <motion.h1 
          className="text-4xl md:text-5xl font-bold text-white mb-4 font-[Roboto_Slab]"
          initial={{ opacity: 0, scale: 0.9 }}
          animate={{ opacity: 1, scale: 1 }}
          transition={{ delay: 0.1 }}
        >
          {appInfo.name}
        </motion.h1>
        <motion.p 
          className="text-white/80 text-lg"
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          transition={{ delay: 0.2 }}
        >
          {appInfo.description}
        </motion.p>
        <motion.div 
          className="flex justify-center gap-8 mt-4"
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          transition={{ delay: 0.3 }}
        >
          <div className="text-center">
            <p className="text-white text-2xl font-bold">{appInfo.featuresCount}</p>
            <p className="text-white/60 text-sm">功能模块</p>
          </div>
          <div className="text-center">
            <p className="text-white text-2xl font-bold">{appInfo.downloads}</p>
            <p className="text-white/60 text-sm">下载量</p>
          </div>
        </motion.div>
      </motion.div>

      <motion.div
        initial={{ opacity: 0, scale: 0.9 }}
        animate={{ opacity: 1, scale: 1 }}
        transition={{ delay: 0.4, type: 'spring', stiffness: 100 }}
      >
        <DeviceFrame>
          <HomeScreen features={features} />
        </DeviceFrame>
      </motion.div>

      <motion.p 
        className="text-white/50 text-sm mt-8"
        initial={{ opacity: 0 }}
        animate={{ opacity: 1 }}
        transition={{ delay: 0.6 }}
      >
        点击设备屏幕上的应用图标查看详情
      </motion.p>
    </div>
  );
}