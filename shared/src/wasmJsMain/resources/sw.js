// Service worker: makes the web app work offline after the first visit.
// On install it caches every file of the build, listed in precache.json (written by the
// wasmJsBrowserDistribution task; the dev server has no list, so there it caches as files are
// fetched). Requests go to the network first, so a new build is picked up when online, and fall
// back to the cache when offline. Without this, files the app or Data Capture load on first use
// (form icons and text) fail to download once the connection is gone.
// ponytail: files of older builds stay in the cache; bump CACHE to clear them if that matters.
const CACHE = "anc-v1";

self.addEventListener("install", (event) => {
  event.waitUntil(
    (async () => {
      const cache = await caches.open(CACHE);
      try {
        const files = await (await fetch("precache.json", { cache: "no-store" })).json();
        await cache.addAll(files);
      } catch (e) {
        // No precache.json (dev server): files are cached as they are fetched.
      }
      await self.skipWaiting();
    })(),
  );
});

self.addEventListener("activate", (event) => {
  event.waitUntil(
    (async () => {
      for (const key of await caches.keys()) if (key !== CACHE) await caches.delete(key);
      await self.clients.claim();
    })(),
  );
});

self.addEventListener("fetch", (event) => {
  const request = event.request;
  if (request.method !== "GET" || new URL(request.url).origin !== self.location.origin) return;
  event.respondWith(
    (async () => {
      const cache = await caches.open(CACHE);
      try {
        const response = await fetch(request);
        if (response.ok) cache.put(request, response.clone());
        return response;
      } catch (e) {
        const cached = await cache.match(request, { ignoreSearch: true });
        if (cached) return cached;
        throw e;
      }
    })(),
  );
});
