import { motion } from 'framer-motion';
import * as Icons from 'lucide-react';

export default function FeatureCard({ feature, index }) {
  const Icon = Icons[feature.icon];

  return (
    <motion.div
      initial={{ opacity: 0, y: 30 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ delay: index * 0.1 }}
      className="feature-card"
      whileHover={{ scale: 1.03, y: -5 }}
    >
      <div className={`w-14 h-14 rounded-2xl bg-gradient-to-br ${feature.color} flex items-center justify-center mb-4`}>
        {Icon && <Icon size={28} className="text-white" />}
      </div>
      <h3 className="text-white font-semibold text-lg mb-2">{feature.name}</h3>
      <p className="text-white/70 text-sm mb-3">{feature.description}</p>
      <p className="text-white/50 text-xs">{feature.details}</p>
      {Object.keys(feature.stats).length > 0 && (
        <div className="flex flex-wrap gap-2 mt-4">
          {Object.entries(feature.stats).map(([key, value]) => (
            <span key={key} className="bg-white/10 rounded-full px-3 py-1 text-white/80 text-xs">
              {value}
            </span>
          ))}
        </div>
      )}
    </motion.div>
  );
}