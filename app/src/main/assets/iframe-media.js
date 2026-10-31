/* Runs at document start inside every WebView frame, including supported cross-origin players.
 * Receives seek requests over postMessage; never reads stream URLs or cookies. */
(function () {
  'use strict';
  if (window.__PFTV_MEDIA_V3) return;
  window.__PFTV_MEDIA_V3 = true;

  function bestVideo() {
    const list = Array.from(document.querySelectorAll('video')).filter(v => v.readyState >= 1 || !!v.currentSrc);
    return list.find(v => !v.paused && !v.ended) || list[0] || null;
  }
  function reply(id, ok, v) {
    try {
      window.top.postMessage({
        __PFTV_MEDIA_RESULT:'pftv/v3', id:id, ok:ok,
        position:v&&Number.isFinite(v.currentTime)?v.currentTime:null,
        duration:v&&Number.isFinite(v.duration)?v.duration:null
      }, '*');
    } catch (_) {}
  }
  function handle(d) {
    const v=bestVideo();
    if(!v)return false;
    if(d.kind==='toggle') {
      if(v.paused)v.play().catch(()=>{}); else v.pause();
      reply(d.id,true,v);return true;
    }
    if(!Number.isFinite(v.duration)||v.duration<=0)return false;
    let to=d.kind==='relative'?v.currentTime+d.value:
      d.kind==='percent'?v.duration*d.value/100:d.kind==='time'?d.value:NaN;
    if(!Number.isFinite(to))return false;
    try{
      v.currentTime=Math.max(0,Math.min(v.duration-.1,to));
      reply(d.id,true,v);return true;
    }catch(_){return false;}
  }
  window.addEventListener('message', e => {
    const d=e.data;
    if (window !== window.top && e.source!==window.parent && e.source!==window.top) return;
    if (!d||d.__PFTV_MEDIA_COMMAND!=='pftv/v3'||typeof d.id!=='string') return;
    if (!['relative','percent','time','toggle'].includes(d.kind)) return;
    if (d.kind!=='toggle' && (!Number.isFinite(d.value) || Math.abs(d.value)>86400)) return;
    if(handle(d))return;
    // Nested players can be more than one iframe deep.
    for(const f of document.querySelectorAll('iframe')){
      try{f.contentWindow?.postMessage(d,'*');}catch(_){}
    }
  },false);
})();
