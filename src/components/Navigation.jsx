import { motion } from 'framer-motion';
import * as Icons from 'lucide-react';
import { Ripple } from './Animations';

export default function Navigation({ activeTab, onTabChange, items }) {
  return (
    <motion.nav 
      className="glass-effect rounded-full px-2 py-2 flex items-center gap-2"
      initial={{ opacity: 0, y: -20 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ delay: 0.2 }}
    >
      {items.map((item, index) => {
        const Icon = Icons[item.icon];
        const isActive = activeTab === item.id;
        
        return (
          <motion.div
            key={item.id}
            initial={{ opacity: 0, x: -10 }}
            animate={{ opacity: 1, x: 0 }}
            transition={{ delay: 0.3 + index * 0.1 }}
          >
            <Ripple
              onClick={() => onTabChange(item.id)}
              className={`flex items-center gap-2 px-4 py-2 rounded-full transition-all duration-300 ${
                isActive 
                  ? 'bg-white text-purple-600 shadow-lg' 
                  : 'text-white/70 hover:text-white hover:bg-white/10'
              }`}
            >
              <motion.div
                animate={isActive ? { rotate: [0, 360] } : {}}
                transition={{ duration: 0.5, ease: 'easeInOut' }}
              >
                {Icon && <Icon size={18} />}
              </motion.div>
              <span className="text-sm font-medium">{item.name}</span>
              {isActive && (
                <motion.div
                  layoutId="activeIndicator"
                  className="absolute inset-0 rounded-full bg-white/10"
                  transition={{ type: 'spring', stiffness: 300, damping: 30 }}
                />
              )}
            </Ripple>
          </motion.div>
        );
      })}
    </motion.nav>
  );
}