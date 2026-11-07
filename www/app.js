'use strict';
(function () {
  const $ = s => document.querySelector(s);
  const video = document.createElement('video');
  video.setAttribute('playsinline', ''); video.muted = true; video.autoplay = true;

  // ---------- persisted settings ----------
  let S = {}; try { S = JSON.parse(localStorage.getItem('rc') || '{}'); } catch (e) {}
  const persist = () => { try { localStorage.setItem('rc', JSON.stringify(S)); } catch (e) {} };

  let cur = PRESETS.find(p => p.id === S.preset) || PRESETS[0];
  let facing = S.facing || 'environment', stampOn = S.stamp !== false, gridOn = !!S.grid, timerSec = S.timer || 0;
  let zoom = 1, stream = null, track = null, imageCapture = null;
  let paused = false, token = 0, busy = false, newFrame = true, torchOn = false, countdown = null;
  const t0 = performance.now();
  const frame = $('#frame'), canvas = $('#gl'), vf = $('#viewfinder');

  function toast(m, ms) {
    const t = $('#toast'); t.textContent = m; t.classList.add('show');
    clearTimeout(toast.h); toast.h = setTimeout(() => t.classList.remove('show'), ms || 1900);
  }
  window.addEventListener('error', e => toast('Error: ' + e.message, 3500));
  window.addEventListener('unhandledrejection', e => toast('Error: ' + ((e.reason && e.reason.message) || e.reason), 3500));
  const vibrate = ms => { try { navigator.vibrate && navigator.vibrate(ms); } catch (e) {} };
  const withTimeout = (p, ms) => Promise.race([p, new Promise((_, r) => setTimeout(() => r(new Error('timeout')), ms))]);

  // ---------- renderer ----------
  let pre, cap = null;
  try { pre = new Renderer(canvas); }
  catch (e) { $('#permMsg').textContent = 'This device cannot run the camera effects: ' + e.message; $('#permBtn').style.display = 'none'; $('#perm').hidden = false; return; }

  function layout() {
    let w = vf.clientWidth, h = vf.clientHeight; if (!w || !h) return;
    const ar = 1 / cur.ratio;
    if (w / h > ar) w = Math.floor(h * ar); else h = Math.floor(w / ar);
    frame.style.width = w + 'px'; frame.style.height = h + 'px';
    const sc = Math.min(window.devicePixelRatio || 1, 2) * 0.7;
    canvas.width = Math.max(2, Math.round(w * sc)); canvas.height = Math.max(2, Math.round(h * sc));
  }
  if (window.ResizeObserver) new ResizeObserver(layout).observe(vf); else window.addEventListener('resize', layout);

  const hasRVFC = 'requestVideoFrameCallback' in HTMLVideoElement.prototype;
  if (hasRVFC) { const cb = () => { newFrame = true; video.requestVideoFrameCallback(cb); }; video.requestVideoFrameCallback(cb); }

  function loop() {
    requestAnimationFrame(loop);
    if (paused) return;
    if (!(video.readyState >= 2 && video.videoWidth > 0)) { pre.clear(); return; }
    pre.render(video, video.videoWidth, video.videoHeight, {
      preset: cur, mirror: facing === 'user', zoom, time: ((performance.now() - t0) / 1000) % 100,
      upload: hasRVFC ? newFrame : true
    });
    newFrame = false;
  }

  // ---------- camera ----------
  function stopStream() {
    if (stream) stream.getTracks().forEach(t => t.stop());
    stream = null; track = null; imageCapture = null; video.srcObject = null;
  }
  function showPerm(msg) { $('#permMsg').textContent = msg; $('#perm').hidden = false; }

  async function startCamera() {
    const my = ++token; stopStream();
    const tries = [
      { audio: false, video: { facingMode: { ideal: facing }, width: { ideal: 1920 }, height: { ideal: 1440 } } },
      { audio: false, video: { facingMode: { ideal: facing } } },
      { audio: false, video: true }
    ];
    let s = null, err = null;
    for (const c of tries) {
      try { s = await navigator.mediaDevices.getUserMedia(c); break; }
      catch (e) { err = e; if (e.name === 'NotAllowedError' || e.name === 'SecurityError') break; }
    }
    if (my !== token) { if (s) s.getTracks().forEach(t => t.stop()); return; }
    if (!s) {
      const denied = err && (err.name === 'NotAllowedError' || err.name === 'SecurityError');
      showPerm(denied ? 'Camera permission is off. Allow it in Android Settings > Apps > RetroCam > Permissions, then tap Try again.'
                      : 'Could not open the camera' + (err ? ': ' + err.message : '.'));
      return;
    }
    stream = s; video.srcObject = s;
    try { await video.play(); } catch (e) {}
    track = s.getVideoTracks()[0];
    track.addEventListener('ended', () => { if (!paused && my === token) setTimeout(startCamera, 400); });
    let caps = {}; try { caps = track.getCapabilities ? track.getCapabilities() : {}; } catch (e) {}
    $('#btnTorch').style.display = caps.torch ? '' : 'none';
    torchOn = false; $('#btnTorch').classList.remove('on');
    try { imageCapture = ('ImageCapture' in window) ? new ImageCapture(track) : null; } catch (e) { imageCapture = null; }
    $('#perm').hidden = true; newFrame = true;
  }
  let wake = null;
  async function keepAwake() { try { if (navigator.wakeLock) wake = await navigator.wakeLock.request('screen'); } catch (e) {} }
  function pause() { if (paused) return; paused = true; token++; stopStream(); try { wake && wake.release(); } catch (e) {} wake = null; }
  function resume() { if (!paused) return; paused = false; startCamera(); keepAwake(); }
  document.addEventListener('visibilitychange', () => document.hidden ? pause() : resume());

  // ---------- capture ----------
  async function grabStill() {
    if (imageCapture) {
      try {
        const blob = await withTimeout(imageCapture.takePhoto(), 4500);
        const bmp = await createImageBitmap(blob, { imageOrientation: 'from-image' });
        const portraitView = video.videoHeight >= video.videoWidth;
        const rot = (portraitView && bmp.width > bmp.height) ? (facing === 'user' ? -1 : 1) : 0;
        const lim = Math.min(pre.maxTex, 4096), sc = Math.min(1, lim / Math.max(bmp.width, bmp.height));
        const dw = Math.round(bmp.width * sc), dh = Math.round(bmp.height * sc);
        const c = document.createElement('canvas'); c.width = rot ? dh : dw; c.height = rot ? dw : dh;
        const x = c.getContext('2d'); x.imageSmoothingQuality = 'high';
        if (rot) { x.translate(c.width / 2, c.height / 2); x.rotate(rot * Math.PI / 2); x.drawImage(bmp, -dw / 2, -dh / 2, dw, dh); }
        else x.drawImage(bmp, 0, 0, dw, dh);
        bmp.close();
        return { src: c, w: c.width, h: c.height };
      } catch (e) { /* fall back to video frame */ }
    }
    return { src: video, w: video.videoWidth, h: video.videoHeight };
  }
  const canvasToBlob = (c, q) => new Promise((res, rej) => c.toBlob(b => b ? res(b) : rej(new Error('Encode failed')), 'image/jpeg', q));
  const blobToB64 = b => new Promise((res, rej) => { const f = new FileReader(); f.onload = () => res(f.result.slice(f.result.indexOf(',') + 1)); f.onerror = () => rej(f.error); f.readAsDataURL(b); });
  const pad = n => String(n).padStart(2, '0');
  const stampText = () => { const d = new Date(); return `'${pad(d.getFullYear() % 100)}  ${pad(d.getMonth() + 1)}  ${pad(d.getDate())}`; };

  async function shoot() {
    if (busy) return; busy = true; $('#shutter').disabled = true;
    try {
      if (!(video.readyState >= 2 && video.videoWidth > 0)) throw new Error('Camera not ready');
      $('#flash').classList.remove('go'); void $('#flash').offsetWidth; $('#flash').classList.add('go'); vibrate(18);
      const still = await grabStill();
      if (!cap) cap = new Renderer(document.createElement('canvas'), { keep: true });
      const ar = 1 / cur.ratio, va = still.w / still.h;
      const effH = va > ar ? still.h : still.w / ar;
      const outH = Math.max(2, Math.min(3072, Math.round(effH))), outW = Math.max(2, Math.round(outH * ar));
      cap.canvas.width = outW; cap.canvas.height = outH;
      cap.render(still.src, still.w, still.h, { preset: cur, mirror: facing === 'user', zoom, time: Math.random() * 50, upload: true });
      const out = document.createElement('canvas'); out.width = outW; out.height = outH;
      const ctx = out.getContext('2d'); ctx.drawImage(cap.canvas, 0, 0);
      cap.canvas.width = cap.canvas.height = 1;
      if (stampOn && cur.stamp) {
        ctx.font = `bold ${Math.round(outH * 0.03)}px monospace`; ctx.fillStyle = cur.stampColor;
        ctx.shadowColor = cur.stampColor; ctx.shadowBlur = outH * 0.006; ctx.textAlign = 'right'; ctx.textBaseline = 'alphabetic';
        ctx.fillText(stampText(), outW - outH * 0.035, outH - outH * 0.035);
      }
      let fin = out;
      if (cur.frame === 'polaroid') {
        const b = Math.round(outW * 0.06), bb = Math.round(outW * 0.22);
        fin = document.createElement('canvas'); fin.width = outW + b * 2; fin.height = outH + b + bb;
        const fx = fin.getContext('2d'); fx.fillStyle = '#f3efe6'; fx.fillRect(0, 0, fin.width, fin.height); fx.drawImage(out, b, b);
      }
      const blob = await canvasToBlob(fin, 0.93);
      const th = document.createElement('canvas'); th.width = 360; th.height = Math.round(360 * fin.height / fin.width);
      th.getContext('2d').drawImage(fin, 0, 0, th.width, th.height);
      const thumbB64 = (th.toDataURL('image/jpeg', 0.8)).split(',')[1];
      const b64 = await blobToB64(blob);
      const id = String(Date.now());
      await Store.save(id, b64, thumbB64);
      $('#thumb').style.backgroundImage = `url(data:image/jpeg;base64,${thumbB64})`;
      try { await Store.toGallery(id, b64); toast(Store.native ? 'Saved to Photos > RetroCam' : 'Saved'); }
      catch (e) { toast('Saved in app. Gallery export failed: ' + (e.message || e), 3500); }
    } catch (e) { toast('Capture failed: ' + (e.message || e), 3000); }
    finally { busy = false; $('#shutter').disabled = false; }
  }
  function onShutter() {
    if (countdown) { clearInterval(countdown); countdown = null; $('#count').hidden = true; return; }
    if (!timerSec) return shoot();
    let n = timerSec; const el = $('#count'); el.textContent = n; el.hidden = false;
    countdown = setInterval(() => {
      n--; if (n <= 0) { clearInterval(countdown); countdown = null; el.hidden = true; shoot(); } else { el.textContent = n; vibrate(8); }
    }, 1000);
  }

  // ---------- UI ----------
  function selectPreset(p, scroll) {
    cur = p; S.preset = p.id; persist(); $('#presetName').textContent = p.name;
    document.querySelectorAll('.preset').forEach(el => {
      const on = el.dataset.id === p.id; el.classList.toggle('sel', on);
      if (on && scroll) el.scrollIntoView({ inline: 'center', block: 'nearest', behavior: 'smooth' });
    });
    $('#btnDate').classList.toggle('on', stampOn && p.stamp); layout();
  }
  PRESETS.forEach(p => { const b = document.createElement('button'); b.className = 'preset'; b.dataset.id = p.id; b.textContent = p.name; b.onclick = () => selectPreset(p, true); $('#presets').appendChild(b); });
  $('#btnDate').onclick = () => { stampOn = !stampOn; S.stamp = stampOn; persist(); $('#btnDate').classList.toggle('on', stampOn && cur.stamp); if (!cur.stamp) toast('This look has no date stamp'); };
  $('#btnGrid').onclick = () => { gridOn = !gridOn; S.grid = gridOn; persist(); $('#gridlines').hidden = !gridOn; $('#btnGrid').classList.toggle('on', gridOn); };
  $('#btnTimer').onclick = () => { timerSec = timerSec === 0 ? 3 : timerSec === 3 ? 10 : 0; S.timer = timerSec; persist(); $('#btnTimer').textContent = timerSec ? `TIMER ${timerSec}S` : 'TIMER OFF'; $('#btnTimer').classList.toggle('on', !!timerSec); };
  $('#btnFlip').onclick = () => { facing = facing === 'user' ? 'environment' : 'user'; S.facing = facing; persist(); zoom = 1; startCamera(); };
  $('#btnTorch').onclick = async () => {
    if (!track) return;
    try { torchOn = !torchOn; await track.applyConstraints({ advanced: [{ torch: torchOn }] }); $('#btnTorch').classList.toggle('on', torchOn); }
    catch (e) { torchOn = false; $('#btnTorch').classList.remove('on'); toast('Flash not supported on this camera'); }
  };
  $('#shutter').onclick = onShutter;
  $('#permBtn').onclick = () => { $('#perm').hidden = true; startCamera(); };
  $('#gridlines').hidden = !gridOn; $('#btnGrid').classList.toggle('on', gridOn);
  $('#btnTimer').textContent = timerSec ? `TIMER ${timerSec}S` : 'TIMER OFF'; $('#btnTimer').classList.toggle('on', !!timerSec);

  // gestures: pinch = digital zoom, horizontal swipe = change look
  let g = null;
  const dist = t => Math.hypot(t[0].clientX - t[1].clientX, t[0].clientY - t[1].clientY);
  vf.addEventListener('touchstart', e => {
    if (e.touches.length === 2) g = { type: 'pinch', d: dist(e.touches), z: zoom };
    else if (e.touches.length === 1) g = { type: 'swipe', x: e.touches[0].clientX, y: e.touches[0].clientY };
  }, { passive: true });
  vf.addEventListener('touchmove', e => {
    if (g && g.type === 'pinch' && e.touches.length === 2) { zoom = Math.min(4, Math.max(1, g.z * dist(e.touches) / g.d)); }
  }, { passive: true });
  vf.addEventListener('touchend', e => {
    if (g && g.type === 'swipe' && e.changedTouches.length) {
      const dx = e.changedTouches[0].clientX - g.x, dy = e.changedTouches[0].clientY - g.y;
      if (Math.abs(dx) > 70 && Math.abs(dx) > Math.abs(dy) * 1.5) {
        const i = PRESETS.indexOf(cur), n = (i + (dx < 0 ? 1 : -1) + PRESETS.length) % PRESETS.length; selectPreset(PRESETS[n], true);
      }
    }
    if (g && g.type === 'pinch') toast(zoom.toFixed(1) + 'x', 900);
    if (e.touches.length === 0) g = null;
  }, { passive: true });

  // ---------- gallery ----------
  let curId = null, delArmed = false;
  async function openGallery() {
    const ids = await Store.list(); const grid = $('#grid'); grid.innerHTML = '';
    $('#gCount').textContent = ids.length + (ids.length === 1 ? ' PHOTO' : ' PHOTOS');
    $('#gEmpty').hidden = ids.length > 0; $('#gallery').hidden = false;
    for (const id of ids.slice(0, 300)) {
      const d = document.createElement('div'); d.onclick = () => openViewer(id); grid.appendChild(d);
      Store.thumbUrl(id).then(u => { if (u) d.style.backgroundImage = `url(${u})`; }).catch(() => {});
    }
  }
  async function openViewer(id) {
    curId = id; delArmed = false; $('#vDel').textContent = 'DELETE'; $('#vImg').removeAttribute('src');
    $('#viewer').hidden = false;
    try { $('#vImg').src = await Store.fullUrl(id); } catch (e) { toast('Could not open photo'); }
  }
  const closeViewer = () => { $('#viewer').hidden = true; curId = null; $('#vImg').removeAttribute('src'); };
  const closeGallery = () => { $('#gallery').hidden = true; refreshThumb(); };
  async function refreshThumb() {
    try { const ids = await Store.list(); if (ids[0]) { const u = await Store.thumbUrl(ids[0]); $('#thumb').style.backgroundImage = `url(${u})`; } else $('#thumb').style.backgroundImage = ''; } catch (e) {}
  }
  $('#thumb').onclick = openGallery; $('#gClose').onclick = closeGallery; $('#vBack').onclick = closeViewer;
  $('#vShare').onclick = async () => { try { await Store.share(curId); } catch (e) { if (!/cancel/i.test(e.message || '')) toast('Share failed: ' + (e.message || e)); } };
  $('#vSave').onclick = async () => { try { await Store.toGallery(curId); toast('Saved to Photos'); } catch (e) { toast('Save failed: ' + (e.message || e), 3000); } };
  $('#vDel').onclick = async () => {
    if (!delArmed) { delArmed = true; $('#vDel').textContent = 'TAP AGAIN'; setTimeout(() => { delArmed = false; $('#vDel').textContent = 'DELETE'; }, 2500); return; }
    await Store.remove(curId); closeViewer(); openGallery();
  };

  // Android back button + app lifecycle
  if (Store.native) {
    try {
      const App = window.Capacitor.registerPlugin('App');
      App.addListener('backButton', () => {
        if (!$('#viewer').hidden) closeViewer();
        else if (!$('#gallery').hidden) closeGallery();
        else App.exitApp();
      });
      App.addListener('pause', pause); App.addListener('resume', resume);
    } catch (e) {}
  }

  selectPreset(cur, true); layout(); refreshThumb(); startCamera(); keepAwake(); loop();
})();
