/* PirateFlix TV v1.2: remote navigation, cinema isolation and player controls.
   D-pad selection is virtual; search only receives focus on OK. */
(function () {
  'use strict';
  if (window !== window.top) return;
  if (window.PFTV && window.PFTV.version === 3) {
    window.PFTV.setZoom(window.__PFTV_ZOOM || 115);
    return;
  }

  const CSS_ID = 'pftv-remote-style';
  let selected = null, editing = false, cinemaTarget = null, watchIntentAt = 0;
  let lastUrl = location.href, zoomPercent = 115, manualCinemaExit = false;
  let oldScroll = { x: 0, y: 0 };
  let pendingSeek = null;
  let requestNo = 0;
  const hidden = [], chain = [];

  let style = document.getElementById(CSS_ID) || document.createElement('style');
  style.id = CSS_ID;
  style.textContent = `
    .pftv-active {
      outline: 4px solid #20e5cc !important; outline-offset: 3px !important;
      box-shadow: 0 0 0 7px rgba(5,12,25,.55),0 0 20px rgba(32,229,204,.75) !important;
      border-radius: 5px !important;
    }
    html.pftv-cinema html, html.pftv-cinema, html.pftv-cinema body {
      overflow: hidden !important; overscroll-behavior: none !important;
    }
    html.pftv-cinema .pftv-cinema-hide {
      visibility: hidden !important; pointer-events: none !important;
    }
    html.pftv-cinema .pftv-cinema-chain {
      transform: none !important; perspective: none !important;
      filter: none !important; contain: none !important;
      clip-path: none !important; overflow: visible !important;
      isolation: auto !important; visibility: visible !important;
    }
    html.pftv-cinema .pftv-cinema-target {
      position: fixed !important; top:0 !important;left:0 !important;
      right:0 !important; bottom:0 !important;
      width:100vw !important; height:100vh !important;
      min-width: 100vw !important; min-height:100vh !important;
      max-width:none !important; max-height:none !important;
      padding:0 !important;margin:0 !important;
      transform:none !important;filter:none !important;
      opacity:1 !important;visibility:visible !important;
      z-index:2147483600 !important;background:#000 !important;
      border:0 !important; overflow:hidden !important;
    }
    html.pftv-cinema .pftv-cinema-target video,
    html.pftv-cinema .pftv-cinema-target iframe {
      width:100% !important;height:100% !important;
      max-width:100% !important;max-height:100% !important;
      object-fit:contain !important;
    }
    #pftv-toast {
      display:none; pointer-events:none; position:fixed;left:50%;bottom:7%;
      transform:translateX(-50%);z-index:2147483647;
      background:rgba(7,14,24,.94);border-radius:12px;
      color:#fff;padding:10px 18px;font:18px sans-serif;
      max-width:90%;text-align:center;
    }
  `;
  (document.head || document.documentElement).appendChild(style);

  function toast(message) {
    let el = document.getElementById('pftv-toast');
    if (!el) {
      el = document.createElement('div');el.id='pftv-toast';
      document.documentElement.appendChild(el);
    }
    el.textContent = message;el.style.display='block';
    clearTimeout(toast.timeout);
    toast.timeout = setTimeout(()=>{el.style.display='none';},2400);
  }
  function visible(el) {
    if (!el || !el.isConnected || !(el instanceof Element)) return false;
    const r = el.getBoundingClientRect();
    if (r.width<8 || r.height<8) return false;
    for (let p=el;p && p.nodeType===1;p=p.parentElement) {
      if (p.hidden || p.inert || p.getAttribute('aria-hidden')==='true') return false;
      const s=getComputedStyle(p);
      if (s.display==='none'||s.visibility==='hidden'||s.visibility==='collapse') return false;
    }
    return true;
  }
  function editable(el) {
    return !!el && (el.matches('input,textarea,select,[contenteditable=true],[role=textbox],[role=searchbox]') || el.isContentEditable);
  }
  function clickable(el) {
    if(!el || el.disabled || el.closest('[disabled],[aria-disabled=true]'))return false;
    if(el.matches('a[href],button,input,textarea,select,summary,[contenteditable=true],[role=button],[role=tab],[role=link],[role=option],[role=checkbox],[role=combobox],[role=menuitem],video'))return true;
    if(el.hasAttribute('onclick')||(el.hasAttribute('tabindex')&&el.tabIndex>=0))return true;
    const cl = typeof el.className==='string'?el.className:'';
    if(/\bcursor-pointer\b|\bclickable\b|\bchannel-card\b/.test(cl))return true;
    return getComputedStyle(el).cursor==='pointer';
  }
  function isFavoriteButton(el) {
    const s = ((el.getAttribute('aria-label')||'')+' '+(el.getAttribute('title')||'')+' '+(el.textContent||'')).slice(0,110);
    return /\b(favo(u)?rite|watchlist|watch later|bookmark|heart|star)\b/i.test(s) ||
      (el.matches('button') && /heart|bookmark|star/i.test(String(el.innerHTML).slice(0,220)));
  }
  function isMediaCard(el) {
    if(!clickable(el))return false;
    const r=el.getBoundingClientRect();
    if(r.width<110 || r.height<65 || r.height>innerHeight*1.45) return false;
    if(el.matches('input,button,select,textarea'))return false;
    const cl=(typeof el.className==='string'?el.className:'')+' '+(el.getAttribute('role')||'');
    return /card|channel|poster|tile|cursor-pointer|grid-item/i.test(cl) &&
      !!el.querySelector('img,video,svg,[class*=poster],[class*=logo],button');
  }
  function cardAncestor(el) {
    let node=el && el.parentElement;
    for(let i=0;node && i<6;i++,node=node.parentElement) {
      if(isMediaCard(node))return node;
    }
    return null;
  }
  function candidates() {
    if(!document.body)return [];
    const selectors='a[href],button,input,textarea,select,summary,[contenteditable=true],'+
      '[role=button],[role=tab],[role=link],[role=option],[role=checkbox],'+
      '[role=combobox],[role=menuitem],[onclick],[tabindex],'+
      '[class*=cursor-pointer],[class*=clickable],[class*=channel-card],video';
    const all=document.body.querySelectorAll(selectors), result=[];
    const seen=new Set();
    for(let i=0;i<Math.min(all.length,5500);i++){
      const el=all[i];
      if(seen.has(el)||!clickable(el)||!visible(el)||el.closest('#pftv-toast'))continue;
      const r=el.getBoundingClientRect();
      if(r.width>innerWidth*1.5||r.height>innerHeight*1.6)continue;
      const parentCard=cardAncestor(el);
      // A TV channel card must remain selectable even if it contains a favorite button.
      if(parentCard && (isFavoriteButton(el) || (el.matches('button,[role=button]') &&
          el.getBoundingClientRect().width < parentCard.getBoundingClientRect().width * .5))) continue;
      // Avoid nested wrappers competing with the main channel/movie card.
      if(parentCard && !el.matches('input,textarea,select,a[href],button,[role=tab]'))continue;
      // Suppress non-card giant click wrappers, but preserve actual card targets.
      if(!isMediaCard(el)&&el.querySelector('input,textarea,select,button,a[href]') &&
         !el.matches('button,a[href]') && r.height>innerHeight*.55)continue;
      seen.add(el);result.push(el);
      if(result.length>=3000)break;
    }
    return result;
  }
  function rect(el){
    const r=el.getBoundingClientRect();
    return {x:(r.left+r.right)/2,y:(r.top+r.bottom)/2,
      l:r.left,r:r.right,t:r.top,b:r.bottom};
  }
  function setSelected(el){
    if(!el||!el.isConnected)return false;
    if(selected&&selected!==el)selected.classList.remove('pftv-active');
    selected=el;el.classList.add('pftv-active');
    // Only focusing a text input on an explicit activation opens Android keyboard.
    if(!editable(el)&&el.tabIndex>=0){
      try{el.focus({preventScroll:true});}catch(_){}
    }
    try{el.scrollIntoView({block:'nearest',inline:'nearest',behavior:'smooth'});}catch(_){}
    return true;
  }
  function scrollContainer(el,axis) {
    for(let p=el;p&&p!==document.body;p=p.parentElement) {
      const s=getComputedStyle(p);
      if(axis==='y'&&/(auto|scroll)/.test(s.overflowY)&&p.scrollHeight>p.clientHeight+15)return p;
      if(axis==='x'&&/(auto|scroll)/.test(s.overflowX)&&p.scrollWidth>p.clientWidth+15)return p;
    }
    return document.scrollingElement||document.documentElement;
  }
  function move(direction) {
    if(cinemaTarget){ toast('Left/Right: seek • OK: play/pause • Hold OK: jump/source'); return true; }
    if(editing) {
      if(direction==='UP'||direction==='DOWN'){
        if(document.activeElement?.blur)document.activeElement.blur();
        editing=false;
      } else return true;
    }
    const list=candidates();
    if(!list.length){
      window.scrollBy({top:direction==='DOWN'?innerHeight*.66:direction==='UP'?-innerHeight*.66:0,
        left:direction==='RIGHT'?innerWidth*.5:direction==='LEFT'?-innerWidth*.5:0,behavior:'smooth'});
      return true;
    }
    if(!selected||!selected.isConnected||!visible(selected)||!list.includes(selected)){
      const visibleList=list.filter(el=>{const r=el.getBoundingClientRect();return r.bottom>0&&r.top<innerHeight&&r.right>0&&r.left<innerWidth;});
      return setSelected((visibleList.length?visibleList:list).sort((a,b)=>rect(a).y-rect(b).y||rect(a).x-rect(b).x)[0]);
    }
    const origin=rect(selected);
    const vertical=direction==='UP'||direction==='DOWN',sgn=(direction==='DOWN'||direction==='RIGHT')?1:-1;
    let best=null,bestScore=Infinity;
    for(const el of list){
      if(el===selected||el.contains(selected))continue;
      const r=rect(el),forward=sgn*(vertical?r.y-origin.y:r.x-origin.x);
      if(forward<5)continue;
      const secondary=Math.abs(vertical?r.x-origin.x:r.y-origin.y);
      const overlap=vertical?
        Math.max(0,Math.min(r.r,origin.r)-Math.max(r.l,origin.l)):
        Math.max(0,Math.min(r.b,origin.b)-Math.max(r.t,origin.t));
      const score=forward+secondary*(overlap>0?.20:1.6)+(secondary>forward*2?300:0);
      if(score<bestScore){best=el;bestScore=score;}
    }
    if(best) return setSelected(best);
    const axis=vertical?'y':'x', container=scrollContainer(selected,axis);
    const d=sgn*(vertical?innerHeight*.66:innerWidth*.6);
    try{container.scrollBy({top:vertical?d:0,left:vertical?0:d,behavior:'smooth'});}
    catch(_){window.scrollBy(0,vertical?d:0);}
    return true;
  }
  function activate(){
    if(cinemaTarget)return togglePlayback();
    if(!selected||!selected.isConnected)return move('DOWN');
    if(editable(selected)){
      editing=true;selected.focus();selected.click();return 'keyboard';
    }
    let target=selected;
    if(isFavoriteButton(target)){
      const card=cardAncestor(target);
      if(card)target=card;
    }
    if(target.matches('video'))return togglePlayback();
    const t=((target.textContent||'')+' '+(target.getAttribute('aria-label')||'')).trim().toLowerCase();
    if(/\b(watch|play|stream|episode|channel|live)\b/.test(t)||isMediaCard(target)){
      watchIntentAt=Date.now();manualCinemaExit=false;
    }
    target.click();
    setTimeout(()=>tryAutoCinema(false),800);
    setTimeout(()=>tryAutoCinema(false),1900);
    return 'clicked';
  }

  function videos(){
    return Array.from(document.querySelectorAll('video'))
      .filter(v=>v.isConnected&&(v.readyState>=1||v.currentSrc));
  }
  function activeVideo(){
    const vs=videos();
    return vs.find(v=>!v.paused&&!v.ended)||vs.find(visible)||vs[0]||null;
  }
  function iframePlayer(){
    const list=Array.from(document.querySelectorAll('iframe')).filter(f=>{
      if(!visible(f))return false;
      const r=f.getBoundingClientRect();
      return r.width>260&&r.height>145&&r.width/r.height>.7&&!/doubleclick|adservice|googlesyndication|popads|monetag|adsterra/i.test(f.src||'');
    });
    return list.sort((a,b)=>b.clientWidth*b.clientHeight-a.clientWidth*a.clientHeight)[0]||null;
  }
  function hasPlayer(){
    const v=activeVideo();
    return !!(cinemaTarget||v&&!v.paused||watchIntentAt&&Date.now()-watchIntentAt<10000&&iframePlayer());
  }
  // Isolate the video's entire ancestor path. Hiding siblings also hides the
  // website's fixed header even when it uses a higher stacking-context z-index.
  function enterCinema(target){
    if(!target||!target.isConnected)return false;
    if(cinemaTarget===target)return true;
    exitCinema(false);
    oldScroll={x:window.scrollX,y:window.scrollY};
    cinemaTarget=target;
    manualCinemaExit=false;
    document.documentElement.classList.add('pftv-cinema');
    document.documentElement.style.zoom='1';
    target.classList.add('pftv-cinema-target');
    for(let node=target;node&&node!==document.documentElement;node=node.parentElement) {
      node.classList.add('pftv-cinema-chain');
      chain.push(node);
      const par=node.parentElement;
      if(!par)break;
      for(const sibling of Array.from(par.children)) {
        if(sibling===node||sibling===style||sibling.id==='pftv-toast')continue;
        if(sibling.tagName==='STYLE'||sibling.tagName==='SCRIPT'||sibling.tagName==='LINK')continue;
        if(!sibling.classList.contains('pftv-cinema-hide')){
          sibling.classList.add('pftv-cinema-hide');hidden.push(sibling);
        }
      }
    }
    toast('Fullscreen • Left/Right to seek • Hold OK for player options');
    return true;
  }
  function reinforceCinema() {
    if(!cinemaTarget||!cinemaTarget.isConnected)return;
    for(let node=cinemaTarget;node&&node!==document.documentElement;node=node.parentElement){
      node.classList.add('pftv-cinema-chain');
      const parent=node.parentElement;
      if(!parent)break;
      for(const sibling of Array.from(parent.children)){
        if(sibling===node||sibling===style||sibling.id==='pftv-toast')continue;
        if(['STYLE','SCRIPT','LINK'].includes(sibling.tagName))continue;
        if(!sibling.classList.contains('pftv-cinema-hide')){
          sibling.classList.add('pftv-cinema-hide');hidden.push(sibling);
        }
      }
    }
  }
  function exitCinema(userAction){
    if(!cinemaTarget)return false;
    if(userAction) manualCinemaExit=true;
    cinemaTarget.classList.remove('pftv-cinema-target');
    for(const node of hidden)node.classList.remove('pftv-cinema-hide');
    for(const node of chain)node.classList.remove('pftv-cinema-chain');
    hidden.length=0;chain.length=0;
    cinemaTarget=null;
    document.documentElement.classList.remove('pftv-cinema');
    setZoom(zoomPercent);
    window.scrollTo(oldScroll.x,oldScroll.y);
    return true;
  }
  function pickTarget(media){
    let p=media.parentElement;
    if(!p||p===document.body)return media;
    const r=p.getBoundingClientRect();
    // Preserve an adjacent player control bar, but never capture an entire list or page.
    const containsPageControls=!!p.querySelector('header,nav,[role=banner]');
    if(!containsPageControls&&r.width>=100&&r.height>=100&&
       r.height<innerHeight*1.25&&p.querySelectorAll('iframe,video').length===1)return p;
    return media;
  }
  function tryAutoCinema(force){
    if(cinemaTarget&&cinemaTarget.isConnected)return true;
    if(manualCinemaExit&&!force)return false;
    const video=activeVideo();
    if(video&&(!video.paused||force))return enterCinema(pickTarget(video));
    if(!force&&Date.now()-watchIntentAt>9000)return false;
    const iframe=iframePlayer();
    return iframe?enterCinema(pickTarget(iframe)):false;
  }

  function clock(t){
    t=Math.max(0,Math.floor(t));
    return Math.floor(t/3600)>0?
      Math.floor(t/3600)+':'+String(Math.floor(t/60)%60).padStart(2,'0')+':'+String(t%60).padStart(2,'0'):
      Math.floor(t/60)+':'+String(t%60).padStart(2,'0');
  }
  function seekDirect(kind,value){
    const v=activeVideo();
    if(!v||!Number.isFinite(v.duration)||v.duration<=0)return false;
    let target=kind==='relative'?v.currentTime+value:
      kind==='percent'?v.duration*value/100:value;
    target=Math.max(0,Math.min(v.duration-0.1,target));
    try {
      v.currentTime=target;
      toast(clock(target)+' / '+clock(v.duration));
      return true;
    }catch(_){return false;}
  }
  function relayFrame(kind,value){
    const id='req'+(++requestNo);
    let sent=false;
    const frames=document.querySelectorAll('iframe');
    for(const iframe of frames){
      try {
        if(iframe.contentWindow){
          iframe.contentWindow.postMessage({__PFTV_MEDIA_COMMAND:'pftv/v3',id:id,kind:kind,value:value},'*');
          sent=true;
        }
      }catch(_){}
    }
    if(sent)pendingSeek={id:id,at:Date.now()};
    return sent;
  }
  window.addEventListener('message',event=>{
    const data=event.data;
    if(!data||data.__PFTV_MEDIA_RESULT!=='pftv/v3'||!pendingSeek)return;
    if(data.id!==pendingSeek.id||!data.ok)return;
    pendingSeek=null;
    if(Number.isFinite(data.position)&&Number.isFinite(data.duration)){
      toast(clock(data.position)+' / '+clock(data.duration));
    }else toast('Seek applied');
  });
  function seek(seconds){
    if(seekDirect('relative',seconds))return 'direct';
    return relayFrame('relative',seconds)?'queued':'unavailable';
  }
  function seekPercent(pct) {
    if(seekDirect('percent',pct))return 'direct';
    return relayFrame('percent',pct)?'queued':'unavailable';
  }
  function seekTo(seconds) {
    if(seekDirect('time',seconds))return 'direct';
    return relayFrame('time',seconds)?'queued':'unavailable';
  }
  function togglePlayback(){
    const v=activeVideo();
    if(v){
      if(v.paused)v.play().catch(()=>{});else v.pause();
      toast(v.paused?'Paused':'Playing');
      return 'direct';
    }
    return relayFrame('toggle',0)?'queued':'unavailable';
  }
  function findSources(){
    exitCinema(true);
    const found=candidates().find(n=>{
      const s=((n.textContent||'')+' '+(n.getAttribute('aria-label')||'')).trim().replace(/\s+/g,' ');
      return s.length<100&&/(?:source|server|mirror|backup|change player|select player)/i.test(s);
    });
    if(found){setSelected(found);toast('Press OK to choose a source');return true;}
    toast('Source controls depend on the video host');return false;
  }
  function setZoom(percent){
    zoomPercent=Math.max(85,Math.min(150,Number(percent)||115));
    if(!cinemaTarget)document.documentElement.style.zoom=(zoomPercent/100).toFixed(2);
    return zoomPercent;
  }
  function back(){
    if(editing){editing=false;document.activeElement?.blur();return true;}
    return exitCinema(true);
  }
  function key(k){
    if(['UP','DOWN','LEFT','RIGHT'].includes(k))return move(k);
    if(k==='OK')return activate();
    return false;
  }
  document.addEventListener('click',e=>{
    const el=e.target;
    if(!el?.closest||cinemaTarget)return;
    let item=el.closest('button,a[href],input,textarea,[role=button],[role=tab],[class*=cursor-pointer]');
    const card=item&&cardAncestor(item);
    if(card&&isFavoriteButton(item))item=card;
    if(item&&visible(item))setSelected(item);
  },true);
  document.addEventListener('focusin',e=>{
    if(editable(e.target)&&e.isTrusted)editing=true;
  },true);
  document.addEventListener('focusout',e=>{
    if(editable(e.target))editing=false;
  },true);
  document.addEventListener('play',e=>{
    if(e.target instanceof HTMLMediaElement&&!manualCinemaExit)
      setTimeout(()=>tryAutoCinema(false),260);
  },true);
  document.addEventListener('keydown',e=>{
    if(editing||e.defaultPrevented)return;
    if(['ArrowDown','ArrowUp','ArrowLeft','ArrowRight'].includes(e.key)){
      e.preventDefault();e.stopImmediatePropagation();key(e.key.substring(5).toUpperCase());
    }
  },true);
  setInterval(()=>{
    if(location.href!==lastUrl){
      lastUrl=location.href;
      exitCinema(false);manualCinemaExit=false;watchIntentAt=Date.now();
      selected?.classList.remove('pftv-active');selected=null;
      setTimeout(()=>tryAutoCinema(false),1050);
    }
    if(cinemaTarget&&cinemaTarget.isConnected)reinforceCinema();
    if(cinemaTarget&&!cinemaTarget.isConnected){
      exitCinema(false);manualCinemaExit=false;
    }
    if(!cinemaTarget&&!manualCinemaExit&&activeVideo()&&!activeVideo().paused)
      tryAutoCinema(false);
  },1400);

  window.PFTV={version:3,key:key,activate:activate,seek:seek,seekPercent:seekPercent,
    seekTo:seekTo,togglePlayback:togglePlayback,cinema:tryAutoCinema,
    exitCinema:()=>exitCinema(true),isCinema:()=>!!cinemaTarget,hasPlayer:hasPlayer,
    back:back,findSources:findSources,setZoom:setZoom,isEditing:()=>editing,toast:toast};
  setZoom(window.__PFTV_ZOOM||115);
})();
