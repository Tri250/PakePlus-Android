/// <reference types="vite/client" />

interface ImportMetaEnv {
  VITE_MAPBOX_KEY?: string;
  VITE_HERE_KEY?: string;
  VITE_LOCATIONIQ_KEY?: string;
  VITE_MAPTILER_KEY?: string;
  VITE_AMAP_KEY?: string;
  VITE_TENCENT_KEY?: string;
  VITE_CRM_API_URL?: string;
  VITE_CRM_SYNC_KEY?: string;
  VITE_DEEPSEEK_KEY?: string;
  VITE_HUNYUAN_KEY?: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
