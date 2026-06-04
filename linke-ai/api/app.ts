/**
 * 邻客 AI BFF 入口
 */
import express, {
  type Request,
  type Response,
  type NextFunction,
} from 'express';
import cors from 'cors';
import path from 'path';
import dotenv from 'dotenv';
import { fileURLToPath } from 'url';

import authRoutes from './routes/auth.js';
import storeRoutes from './routes/stores.js';
import aiRoutes from './routes/ai.js';
import campaignRoutes from './routes/campaigns.js';
import leadRoutes from './routes/leads.js';
import dashboardRoutes from './routes/dashboard.js';
import touchRoutes from './routes/touch.js';
import geoRoutes from './routes/geo.js';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

// load env
dotenv.config();

const app: express.Application = express();

app.use(cors());
app.use(express.json({ limit: '10mb' }));
app.use(express.urlencoded({ extended: true, limit: '10mb' }));

/**
 * API Routes
 */
app.use('/api/auth', authRoutes);
app.use('/api/stores', storeRoutes);
app.use('/api/ai', aiRoutes);
app.use('/api/campaigns', campaignRoutes);
app.use('/api/leads', leadRoutes);
app.use('/api/dashboard', dashboardRoutes);
app.use('/api/touch', touchRoutes);
app.use('/api/geo', geoRoutes);

/**
 * health
 */
app.use(
  '/api/health',
  (_req: Request, res: Response, _next: NextFunction): void => {
    res.status(200).json({
      success: true,
      message: 'ok',
      app: '邻客 AI BFF',
      time: new Date().toISOString(),
    });
  },
);

/**
 * error handler middleware
 */
app.use((error: Error, _req: Request, res: Response, _next: NextFunction) => {
  console.error('[BFF ERROR]', error);
  res.status(500).json({
    success: false,
    error: error.message || 'Server internal error',
  });
});

/**
 * 404 handler
 */
app.use((_req: Request, res: Response) => {
  res.status(404).json({
    success: false,
    error: 'API not found',
  });
});

export default app;
