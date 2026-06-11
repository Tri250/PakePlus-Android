import { motion } from 'framer-motion';
import * as Icons from 'lucide-react';

export default function Navigation({ activeTab, onTabChange, items }) {
  return (
    <nav className="glass-effect rounded-full px-2 py-2 flex items-center gap-2">
      {items.map((item) => {
        const Icon = Icons[item.icon];
        const isActive = activeTab === item.id;
        
        return (
          <motion.button
            key={item.id}
            onClick={() => onTabChange(item.id)}
            className={`flex items-center gap-2 px-4 py-2 rounded-full transition-all duration-300 ${
              isActive 
                ? 'bg-white text-purple-600' 
                : 'text-white/70 hover:text-white hover:bg-white/10'
            }`}
            whileHover={{ scale: 1.05 }}
            whileTap={{ scale: 0.95 }}
          >
            {Icon && <Icon size={18} />}
            <span className="text-sm font-medium">{item.name}</span>
          </motion.button>
        );
      })}
    </nav>
  );
}