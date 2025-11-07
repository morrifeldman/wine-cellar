const VERSION_URL = '/version.json';
const CACHE_PREFIX = 'wine-cellar-assets-';
const CORE_ASSETS = ['/js/main.js'];

let activeVersion = null;
let activeCacheName = null;

const log = (...args) => {
  if (self && self.console) {
    console.log('[wine-cellar sw]', ...args);
  }
};

const extractVersion = (payload) => {
  if (!payload || typeof payload !== 'object') {
    return null;
  }
  return payload.commit || payload.version || payload.date || null;
};

const cacheNameFor = (version) => `${CACHE_PREFIX}${version || 'runtime'}`;

const ensureActiveCache = async () => {
  if (activeCacheName) {
    return activeCacheName;
  }
  const keys = await caches.keys();
  const existing = keys.find((key) => key.startsWith(CACHE_PREFIX));
  if (existing) {
    activeCacheName = existing;
    activeVersion = existing.substring(CACHE_PREFIX.length) || null;
    return activeCacheName;
  }
  activeCacheName = cacheNameFor(activeVersion);
  await caches.open(activeCacheName);
  return activeCacheName;
};

const fetchAndStore = async (cache, request) => {
  try {
    const response = await fetch(request, { cache: 'no-store' });
    if (response && response.ok) {
      await cache.put(request, response.clone());
    }
    return response;
  } catch (error) {
    log('Failed to fetch asset', request.url, error);
    throw error;
  }
};

const cacheCoreAssets = async () => {
  const cacheKey = await ensureActiveCache();
  const cache = await caches.open(cacheKey);
  await Promise.all(
    CORE_ASSETS.map(async (path) => {
      const request = new Request(path, { cache: 'no-store' });
      try {
        await fetchAndStore(cache, request);
      } catch (error) {
        // Ignore fetch failures during install so the SW still activates.
      }
    })
  );
};

const cleanupOldCaches = async () => {
  const keys = await caches.keys();
  await Promise.all(
    keys
      .filter((key) => key.startsWith(CACHE_PREFIX) && key !== activeCacheName)
      .map((key) => caches.delete(key))
  );
};

const broadcastUpdate = async (newVersion) => {
  const clients = await self.clients.matchAll({ type: 'window', includeUncontrolled: true });
  clients.forEach((client) =>
    client.postMessage({ type: 'version-update', version: newVersion })
  );
};

const applyVersion = async (nextVersion, { notifyClients }) => {
  if (!nextVersion) {
    await ensureActiveCache();
    return false;
  }
  await ensureActiveCache();
  if (nextVersion === activeVersion) {
    return false;
  }
  activeVersion = nextVersion;
  activeCacheName = cacheNameFor(activeVersion);
  await caches.open(activeCacheName);
  await cacheCoreAssets();
  await cleanupOldCaches();
  if (notifyClients) {
    await broadcastUpdate(activeVersion);
  }
  return true;
};

const readVersionFromNetwork = async () => {
  try {
    const response = await fetch(VERSION_URL, { cache: 'no-store' });
    if (!response || !response.ok) {
      return null;
    }
    const payload = await response.clone().json().catch(() => null);
    return extractVersion(payload);
  } catch (error) {
    log('Unable to read version.json', error);
    return null;
  }
};

const handleVersionRequest = async (request) => {
  try {
    const response = await fetch(request, { cache: 'no-store' });
    const payload = await response.clone().json().catch(() => null);
    const version = extractVersion(payload);
    const changed = await applyVersion(version, { notifyClients: true });
    if (changed) {
      log('Activated new app version', version);
    }
    return response;
  } catch (error) {
    const cacheKey = await ensureActiveCache();
    const cache = await caches.open(cacheKey);
    const cached = await cache.match(request);
    if (cached) {
      return cached;
    }
    throw error;
  }
};

self.addEventListener('install', (event) => {
  event.waitUntil(
    (async () => {
      const version = await readVersionFromNetwork();
      await applyVersion(version, { notifyClients: false });
      await cacheCoreAssets();
      self.skipWaiting();
    })()
  );
});

self.addEventListener('activate', (event) => {
  event.waitUntil(
    (async () => {
      if (!activeVersion) {
        const version = await readVersionFromNetwork();
        await applyVersion(version, { notifyClients: false });
      }
      await cleanupOldCaches();
      await self.clients.claim();
    })()
  );
});

const serveJsAsset = async (event, request) => {
  const cacheKey = await ensureActiveCache();
  const cache = await caches.open(cacheKey);
  const cached = await cache.match(request);

  if (cached) {
    event.waitUntil(
      (async () => {
        try {
          await fetchAndStore(cache, request);
        } catch (error) {
          // Ignore update failures; we still return the cached asset.
        }
      })()
    );
    return cached;
  }

  try {
    const response = await fetchAndStore(cache, request);
    return response;
  } catch (error) {
    throw error;
  }
};

self.addEventListener('fetch', (event) => {
  const { request } = event;
  if (request.method !== 'GET') {
    return;
  }

  const url = new URL(request.url);
  if (url.origin !== self.location.origin) {
    return;
  }

  if (url.pathname === VERSION_URL) {
    event.respondWith(handleVersionRequest(request));
    return;
  }

  if (url.pathname.startsWith('/js/')) {
    event.respondWith(serveJsAsset(event, request));
  }
});
