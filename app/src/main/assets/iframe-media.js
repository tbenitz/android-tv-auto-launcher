/* PirateFlix TV v2.3 - optional controls within the host's own embedded player.
 * Installs in a WebView frame at document start. No network interception, no DRM bypass.
 */
(() => {
  'use strict';
  if (window.__PFTV_FRAME_V4) return;
  window.__PFTV_FRAME_V4 = true;
  let sourceNodes = [];
  let clickQueue = Promise.resolve();
  const liveVideo = () => {
    const vs = Array.from(document.querySelectorAll('video')).filter(v=>v.readyState>=1 || !!v.currentSrc);
    return vs.find(v=>!v.paused && !v.ended) || vs[0] || null;
  };
  function reply(d, ok, method, extra) {
    try { window.top.postMessage(Object.assign({__PFTV_MEDIA_RESULT:'pftv/v4',id:d.id,
      ok:!!ok,method:method||'',position:liveVideo()?.currentTime??null,
      duration:liveVideo()?.duration??null},extra||{}),'*'); } catch (_) {}
  }
  function emitStream() {
    if (window===window.top) return;
    const v=liveVideo();if(!v||v.mediaKeys)return;
    const s=v.currentSrc||v.src||v.querySelector('source[src]')?.src||'';
    if(!/^https?:\/\//i.test(s) || !/\.(?:m3u8|mpd|mp4|m4v|webm|mkv)(?:[?#]|$)/i.test(s))return;
    window.top.postMessage({__PFTV_MEDIA_EVENT:'pftv/v4',event:'stream',url:s,origin:location.origin},'*');
  }
  document.addEventListener('playing',emitStream,true);
  document.addEventListener('loadedmetadata',emitStream,true);
  function nameOf(el){
    return [el.getAttribute('aria-label'),el.getAttribute('title'),el.getAttribute('data-tooltip'),
      el.getAttribute('data-testid'),el.textContent].filter(Boolean).join(' ').replace(/\s+/g,' ').trim().slice(0,170);
  }
  function playerButtons(dir) {
    const list=Array.from(document.querySelectorAll('button,[role=button],[onclick]'));
    const rx=dir>0?/(?:forward|skip\s*(?:forward|ahead)|seek\s*forward|next\s*(?:10|15|30)|fast.?forward|right.?10|forward.?10|skip-forward|skip-forward-10|lucide-skip-forward)/i:
      /(?:rewind|backward|skip\s*(?:back|backward)|seek\s*back|previous\s*(?:10|15|30)|replay.?10|left.?10|backward.?10|skip-back|lucide-rotate-ccw)/i;
    return list.filter(b => {
      if(b.closest('[aria-hidden=true]')||b.disabled)return false;
      const s=[nameOf(b),b.outerHTML.slice(0,440)].join(' ');
      if(/advert|sponsor|download|next episode|previous episode/i.test(s))return false;
      return rx.test(s);
    });
  }
  function maybeClickSeek(seconds,id) {
    const direction=seconds>=0?1:-1;
    const matches=playerButtons(direction);
    if(!matches.length)return false;
    const btn=matches[0];
    // 10-second player controls: cap batch size and queue to avoid click floods.
    const clicks=Math.min(24,Math.max(1,Math.round(Math.abs(seconds)/10)));
    clickQueue=clickQueue.then(()=>new Promise(resolve=>{
      let n=0;
      const step=()=>{
        if(!btn.isConnected||n++>=clicks){resolve();return;}
        try{btn.click();}catch(_){}
        setTimeout(step,28);
      };step();
    })).then(()=>{
      reply({id},true,'button-clicks',{clicks:clicks,approxSeconds:direction*clicks*10});
    });
    return true;
  }
  function sourceCandidates(){
    const rx=/\b(?:server|source|mirror|backup|stream\s*\d+|player\s*\d+|vidsrc|vidcloud|filemoon|upcloud|streamtape|streamwish)\b/i;
    return Array.from(document.querySelectorAll('button,[role=option],[role=tab],[role=button],a[href],li'))
      .filter(el => !el.disabled && !el.closest('[aria-hidden=true]') &&
        nameOf(el).length<95 && rx.test(nameOf(el)))
      .slice(0,35);
  }
  function handle(d) {
    if(d.kind==='sources'){
      sourceNodes=sourceCandidates();
      reply(d,true,'sources',{sources:sourceNodes.map((el,i)=>({id:i,label:nameOf(el)}))});
      return true;
    }
    if(d.kind==='selectSource'){
      const i=Number(d.value),el=sourceNodes[i];
      if(!el||!el.isConnected)return false;
      try{el.click();reply(d,true,'source-selected');return true;}catch(_){return false;}
    }
    if(d.kind==='play'){
      const v=liveVideo();if(v){v.play().catch(()=>{});reply(d,true,'html5');return true;}
    }
    if(d.kind==='pause'){
      const v=liveVideo();if(v){v.pause();reply(d,true,'html5');return true;}
    }
    const v=liveVideo();
    if(d.kind==='toggle'&&v){
      try{v.paused?v.play().catch(()=>{}):v.pause();reply(d,true,'html5');return true;}catch(_){}
    }
    if(['relative','percent','time'].includes(d.kind) && v && Number.isFinite(v.duration) && v.duration>0 && !v.mediaKeys){
      const value=Number(d.value);
      const to=d.kind==='relative'?v.currentTime+value:d.kind==='percent'?v.duration*value/100:value;
      try{v.currentTime=Math.max(0,Math.min(v.duration-.1,to));reply(d,true,'html5');return true;}catch(_){}
    }
    if(d.kind==='relative'&&maybeClickSeek(Number(d.value),d.id))return true;
    return false;
  }
  window.addEventListener('message',e=>{
    const d=e.data;
    if(window!==window.top&&e.source!==window.parent&&e.source!==window.top)return;
    if(!d||d.__PFTV_MEDIA_COMMAND!=='pftv/v4'||typeof d.id!=='string')return;
    if(!['relative','percent','time','toggle','pause','play','sources','selectSource'].includes(d.kind))return;
    if(['relative','percent','time'].includes(d.kind)&&(!Number.isFinite(Number(d.value))||Math.abs(Number(d.value))>86400))return;
    if(handle(d))return;
    // Bridge commands through multiple iframe nesting levels.
    if(d.kind==='selectSource')return;
    for(const child of document.querySelectorAll('iframe')){
      try{child.contentWindow?.postMessage(d,'*');}catch(_){}
    }
    if(d.kind==='relative'||d.kind==='toggle') reply(d,false,'unsupported');
  },false);
})();
