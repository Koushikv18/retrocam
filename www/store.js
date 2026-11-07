'use strict';
// Local storage layer. Native (Capacitor): app-private files + system gallery export.
// Browser fallback: IndexedDB + download.
const Store = (() => {
  const native = !!(window.Capacitor && window.Capacitor.isNativePlatform && window.Capacitor.isNativePlatform());
  const plug = n => { try { return window.Capacitor.registerPlugin(n); } catch (e) { return (window.Capacitor.Plugins || {})[n]; } };
  let FS, Share, Gal;
  if (native) { FS = plug('Filesystem'); Share = plug('Share'); Gal = plug('GallerySaver'); }
  const D = 'DATA';
  const strip = d => d.slice(d.indexOf(',') + 1);

  // ---- IndexedDB fallback ----
  let dbp;
  const db = () => dbp || (dbp = new Promise((res, rej) => {
    const r = indexedDB.open('retrocam', 1);
    r.onupgradeneeded = () => r.result.createObjectStore('p', { keyPath: 'id' });
    r.onsuccess = () => res(r.result); r.onerror = () => rej(r.error);
  }));
  const idb = async (mode, fn) => {
    const d = await db();
    return new Promise((res, rej) => {
      const t = d.transaction('p', mode); const rq = fn(t.objectStore('p'));
      t.oncomplete = () => res(rq && rq.result); t.onerror = () => rej(t.error);
    });
  };
  const b64ToBlob = b64 => { const bin = atob(b64), a = new Uint8Array(bin.length); for (let i = 0; i < bin.length; i++) a[i] = bin.charCodeAt(i); return new Blob([a], { type: 'image/jpeg' }); };

  return {
    native,
    async save(id, fullB64, thumbB64) {
      if (native) {
        await FS.writeFile({ path: `photos/${id}.jpg`, data: fullB64, directory: D, recursive: true });
        await FS.writeFile({ path: `thumbs/${id}.jpg`, data: thumbB64, directory: D, recursive: true });
      } else {
        await idb('readwrite', s => s.put({ id, blob: b64ToBlob(fullB64), thumb: 'data:image/jpeg;base64,' + thumbB64 }));
      }
    },
    async list() {
      let ids = [];
      if (native) {
        try {
          const r = await FS.readdir({ path: 'thumbs', directory: D });
          ids = r.files.map(f => (f.name || f).replace(/\.jpg$/, ''));
        } catch (e) { ids = []; }
      } else {
        ids = (await idb('readonly', s => s.getAllKeys())) || [];
      }
      return ids.sort().reverse();
    },
    async thumbUrl(id) {
      if (native) return 'data:image/jpeg;base64,' + (await FS.readFile({ path: `thumbs/${id}.jpg`, directory: D })).data;
      const r = await idb('readonly', s => s.get(id)); return r && r.thumb;
    },
    async fullB64(id) {
      if (native) return (await FS.readFile({ path: `photos/${id}.jpg`, directory: D })).data;
      const r = await idb('readonly', s => s.get(id));
      return new Promise(res => { const f = new FileReader(); f.onload = () => res(strip(f.result)); f.readAsDataURL(r.blob); });
    },
    async fullUrl(id) { return 'data:image/jpeg;base64,' + await this.fullB64(id); },
    async remove(id) {
      if (native) {
        await FS.deleteFile({ path: `photos/${id}.jpg`, directory: D }).catch(() => {});
        await FS.deleteFile({ path: `thumbs/${id}.jpg`, directory: D }).catch(() => {});
      } else await idb('readwrite', s => s.delete(id));
    },
    async toGallery(id, fullB64) {
      const b64 = fullB64 || await this.fullB64(id);
      if (native) { await Gal.savePhoto({ data: b64, name: `RetroCam_${id}.jpg` }); return; }
      const a = document.createElement('a'); a.href = URL.createObjectURL(b64ToBlob(b64));
      a.download = `RetroCam_${id}.jpg`; document.body.appendChild(a); a.click(); a.remove();
    },
    async share(id) {
      if (native) {
        const { uri } = await FS.getUri({ path: `photos/${id}.jpg`, directory: D });
        await Share.share({ title: 'RetroCam', url: uri });
      } else {
        const b = b64ToBlob(await this.fullB64(id));
        const f = new File([b], `RetroCam_${id}.jpg`, { type: 'image/jpeg' });
        if (navigator.canShare && navigator.canShare({ files: [f] })) await navigator.share({ files: [f] });
        else throw new Error('Sharing not supported here');
      }
    },
  };
})();
