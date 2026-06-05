/**
 * 数据持久化层 - 本地存储 + IndexedDB + 同步队列
 * 支持：离线存储、数据同步、版本管理、冲突解决
 */

/* -------------------------------------------------------------------------- */
/*  类型定义                                                                    */
/* -------------------------------------------------------------------------- */

export interface StoredEntity {
  id: string;
  _version: number;
  _updatedAt: string;
  _syncedAt?: string;
  _deleted?: boolean;
}

export interface SyncQueueItem extends StoredEntity {
  entity: string;
  action: 'create' | 'update' | 'delete';
  data: any;
  retryCount: number;
  lastError?: string;
}

/* -------------------------------------------------------------------------- */
/*  LocalStorage 封装                                                          */
/* -------------------------------------------------------------------------- */

const STORAGE_PREFIX = 'hb_';

export function storageSet<T>(key: string, value: T): void {
  try {
    const data = JSON.stringify({
      value,
      _meta: {
        version: 1,
        updatedAt: new Date().toISOString(),
      },
    });
    localStorage.setItem(STORAGE_PREFIX + key, data);
  } catch (err) {
    console.error('[Storage] Set error:', err);
  }
}

export function storageGet<T>(key: string): T | null {
  try {
    const data = localStorage.getItem(STORAGE_PREFIX + key);
    if (!data) return null;
    const parsed = JSON.parse(data);
    return parsed.value;
  } catch (err) {
    console.error('[Storage] Get error:', err);
    return null;
  }
}

export function storageRemove(key: string): void {
  localStorage.removeItem(STORAGE_PREFIX + key);
}

export function storageClear(): void {
  Object.keys(localStorage)
    .filter((k) => k.startsWith(STORAGE_PREFIX))
    .forEach((k) => localStorage.removeItem(k));
}

/* -------------------------------------------------------------------------- */
/*  IndexedDB 封装                                                              */
/* -------------------------------------------------------------------------- */

const DB_NAME = 'HandBizDB';
const DB_VERSION = 1;

let db: IDBDatabase | null = null;

export async function initDB(): Promise<IDBDatabase> {
  if (db) return db;

  return new Promise((resolve, reject) => {
    const request = indexedDB.open(DB_NAME, DB_VERSION);

    request.onerror = () => reject(request.error);

    request.onsuccess = () => {
      db = request.result;
      resolve(db);
    };

    request.onupgradeneeded = (event) => {
      const database = (event.target as IDBOpenDBRequest).result;

      // 客户存储
      if (!database.objectStoreNames.contains('customers')) {
        const store = database.createObjectStore('customers', { keyPath: 'id' });
        store.createIndex('storeId', 'storeId', { unique: false });
        store.createIndex('tier', 'tier', { unique: false });
        store.createIndex('updatedAt', '_updatedAt', { unique: false });
      }

      // 线索存储
      if (!database.objectStoreNames.contains('leads')) {
        const store = database.createObjectStore('leads', { keyPath: 'id' });
        store.createIndex('storeId', 'storeId', { unique: false });
        store.createIndex('status', 'status', { unique: false });
      }

      // 任务存储
      if (!database.objectStoreNames.contains('tasks')) {
        const store = database.createObjectStore('tasks', { keyPath: 'id' });
        store.createIndex('staffId', 'staffId', { unique: false });
        store.createIndex('status', 'status', { unique: false });
      }

      // 同步队列
      if (!database.objectStoreNames.contains('syncQueue')) {
        database.createObjectStore('syncQueue', { keyPath: 'id' });
      }

      // NFC 事件
      if (!database.objectStoreNames.contains('nfcEvents')) {
        const store = database.createObjectStore('nfcEvents', { keyPath: 'id' });
        store.createIndex('timestamp', 'timestamp', { unique: false });
      }
    };
  });
}

export async function dbGet<T>(storeName: string, id: string): Promise<T | null> {
  const database = await initDB();
  return new Promise((resolve, reject) => {
    const tx = database.transaction(storeName, 'readonly');
    const store = tx.objectStore(storeName);
    const request = store.get(id);
    request.onsuccess = () => resolve(request.result || null);
    request.onerror = () => reject(request.error);
  });
}

export async function dbGetAll<T>(storeName: string): Promise<T[]> {
  const database = await initDB();
  return new Promise((resolve, reject) => {
    const tx = database.transaction(storeName, 'readonly');
    const store = tx.objectStore(storeName);
    const request = store.getAll();
    request.onsuccess = () => resolve(request.result);
    request.onerror = () => reject(request.error);
  });
}

export async function dbPut<T extends StoredEntity>(storeName: string, data: T): Promise<void> {
  const database = await initDB();
  const enrichedData = {
    ...data,
    _version: (data._version || 0) + 1,
    _updatedAt: new Date().toISOString(),
  };

  return new Promise((resolve, reject) => {
    const tx = database.transaction(storeName, 'readwrite');
    const store = tx.objectStore(storeName);
    const request = store.put(enrichedData);
    request.onsuccess = () => resolve();
    request.onerror = () => reject(request.error);
  });
}

export async function dbDelete(storeName: string, id: string): Promise<void> {
  const database = await initDB();
  return new Promise((resolve, reject) => {
    const tx = database.transaction(storeName, 'readwrite');
    const store = tx.objectStore(storeName);
    const request = store.delete(id);
    request.onsuccess = () => resolve();
    request.onerror = () => reject(request.error);
  });
}

export async function dbQueryByIndex<T>(
  storeName: string,
  indexName: string,
  value: IDBValidKey
): Promise<T[]> {
  const database = await initDB();
  return new Promise((resolve, reject) => {
    const tx = database.transaction(storeName, 'readonly');
    const store = tx.objectStore(storeName);
    const index = store.index(indexName);
    const request = index.getAll(value);
    request.onsuccess = () => resolve(request.result);
    request.onerror = () => reject(request.error);
  });
}

/* -------------------------------------------------------------------------- */
/*  同步队列管理                                                                */
/* -------------------------------------------------------------------------- */

export async function addToSyncQueue(item: Omit<SyncQueueItem, 'id' | '_version' | '_updatedAt' | 'retryCount'>): Promise<void> {
  const queueItem: SyncQueueItem = {
    ...item,
    id: `SYNC-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
    _version: 1,
    _updatedAt: new Date().toISOString(),
    retryCount: 0,
  };

  await dbPut('syncQueue', queueItem);
}

export async function getSyncQueue(): Promise<SyncQueueItem[]> {
  return dbGetAll<SyncQueueItem>('syncQueue');
}

export async function processSyncQueue(processor: (item: SyncQueueItem) => Promise<boolean>): Promise<void> {
  const queue = await getSyncQueue();

  for (const item of queue) {
    try {
      const success = await processor(item);
      if (success) {
        await dbDelete('syncQueue', item.id);
      } else {
        item.retryCount++;
        item.lastError = 'Processing failed';
        if (item.retryCount < 5) {
          await dbPut('syncQueue', item);
        } else {
          // 超过重试次数，移除并记录
          await dbDelete('syncQueue', item.id);
          console.error('[SyncQueue] Max retries exceeded:', item);
        }
      }
    } catch (err: any) {
      item.retryCount++;
      item.lastError = err.message;
      await dbPut('syncQueue', item);
    }
  }
}

/* -------------------------------------------------------------------------- */
/*  数据仓库                                                                    */
/* -------------------------------------------------------------------------- */

export const repository = {
  customer: {
    get: (id: string) => dbGet<any>('customers', id),
    getAll: () => dbGetAll<any>('customers'),
    getByStore: (storeId: string) => dbQueryByIndex<any>('customers', 'storeId', storeId),
    save: (data: any) => dbPut('customers', data),
    delete: (id: string) => dbDelete('customers', id),
  },

  lead: {
    get: (id: string) => dbGet<any>('leads', id),
    getAll: () => dbGetAll<any>('leads'),
    save: (data: any) => dbPut('leads', data),
    delete: (id: string) => dbDelete('leads', id),
  },

  task: {
    get: (id: string) => dbGet<any>('tasks', id),
    getAll: () => dbGetAll<any>('tasks'),
    getByStaff: (staffId: string) => dbQueryByIndex<any>('tasks', 'staffId', staffId),
    save: (data: any) => dbPut('tasks', data),
    delete: (id: string) => dbDelete('tasks', id),
  },

  nfcEvent: {
    save: (data: any) => dbPut('nfcEvents', data),
    getAll: () => dbGetAll<any>('nfcEvents'),
  },
};

/* -------------------------------------------------------------------------- */
/*  初始化                                                                      */
/* -------------------------------------------------------------------------- */

// 自动初始化
if (typeof window !== 'undefined') {
  initDB().catch(console.error);
}
