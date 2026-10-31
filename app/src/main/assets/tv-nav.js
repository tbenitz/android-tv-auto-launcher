/* PirateFlix TV D-pad navigator. Runs only inside the first-party top-level page.
 * All input movement is virtual until OK is pressed: selecting Search never opens the keyboard.
 */
(function () {
  'use strict';
  if (window !== window.top) return;
  if (window.PFTV && window.PFTV.version === 2) return;

  var selected = null, editing = false, cinemaTarget = null, lastVideo = null;
  var watchIntentAt = 0, lastUrl = location.href, zoomPercent = 115;
  var highlightClass = 'pftv-active';
  var CSS_ID = 'pftv-remote-style';
  var style = document.getElementById(CSS_ID) || document.createElement('style');
  style.id = CSS_ID;
  style.textContent = `
    .pftv-active { outline: 4px solid #20e5cc !important; outline-offset: 3px !important;
      box-shadow: 0 0 0 7px rgba(5,12,25,.55), 0 0 20px rgba(32,229,204,.75) !important;
      border-radius: 5px !important; }
    .pftv-cinema-target { position: fixed !important; inset: 0 !important; top: 0 !important;
      left: 0 !important; right: 0 !important; bottom: 0 !important; width: 100vw !important;
      height: 100vh !important; max-width: none !important; max-height: none !important;
      margin: 0 !important; padding: 0 !important; transform: none !important;
      z-index: 2147483000 !important; background: black !important; }
    .pftv-cinema-target video, .pftv-cinema-target iframe { width: 100% !important;
      height: 100% !important; max-width: 100% !important; max-height: 100% !important;
      object-fit: contain !important; }
    #pftv-toast { pointer-events: none; position: fixed; left: 50%; bottom: 7%;
      transform: translateX(-50%); padding: 12px 22px; border-radius: 12px;
      color: white; background: rgba(8,15,26,.92); font: 18px sans-serif;
      z-index: 2147483500; display:none; }
  `;
  (document.head || document.documentElement).appendChild(style);

  function toast(msg) {
    var node = document.getElementById('pftv-toast');
    if (!node) { node = document.createElement('div'); node.id = 'pftv-toast'; document.documentElement.appendChild(node); }
    node.textContent = msg; node.style.display = 'block';
    clearTimeout(toast.timer); toast.timer = setTimeout(function(){ node.style.display='none'; }, 1900);
  }

  function visible(el) {
    if (!el || !el.isConnected || !(el instanceof Element)) return false;
    var r = el.getBoundingClientRect();
    if (r.width < 8 || r.height < 8) return false;
    for (var p = el; p && p.nodeType === 1; p = p.parentElement) {
      if (p.hidden || p.getAttribute('aria-hidden') === 'true' || p.inert) return false;
      var s = getComputedStyle(p);
      if (s.display === 'none' || s.visibility === 'hidden' || s.visibility === 'collapse') return false;
      if (p === el && +s.opacity === 0) return false;
    }
    return true;
  }

  function editable(el) { return el && (el.matches('input,textarea,select,[contenteditable=true],[role=textbox],[role=searchbox]') || el.isContentEditable); }
  function clickable(el) {
    if (!el || el.disabled || el.closest('[disabled],[aria-disabled=true]')) return false;
    if (el.matches('a[href],button,input,textarea,select,summary,[contenteditable=true],[role=button],[role=tab],[role=link],[role=option],[role=checkbox],[role=combobox],[role=menuitem],[role=searchbox],video')) return true;
    if (el.hasAttribute('onclick') || el.hasAttribute('tabindex') && el.tabIndex >= 0) return true;
    var cls = typeof el.className === 'string' ? el.className : '';
    if (/\bcursor-pointer\b|\bclickable\b/.test(cls)) return true;
    return getComputedStyle(el).cursor === 'pointer';
  }

  function candidates() {
    var all = document.body ? document.body.querySelectorAll('a[href],button,input,textarea,select,summary,[contenteditable=true],[role=button],[role=tab],[role=link],[role=option],[role=checkbox],[role=combobox],[role=menuitem],[role=searchbox],[onclick],[tabindex],[class*=cursor-pointer],[class*=clickable],video') : [];
    var result = [], n = Math.min(all.length, 5000);
    for (var i = 0; i < n; i++) {
      var el = all[i];
      if (!clickable(el) || !visible(el) || el.closest('#pftv-toast')) continue;
      var r = el.getBoundingClientRect();
      // Skip giant wrappers and clickable ancestors of small direct action targets.
      if (r.width > innerWidth * 1.5 || r.height > innerHeight * 1.6) continue;
      if (el !== document.activeElement && el.querySelector('input,textarea,select,button,a[href],[role=button]') &&
          !el.matches('a[href],button')) continue;
      result.push(el);
      if (result.length >= 2500) break;
    }
    return result;
  }

  function rect(el) { var r = el.getBoundingClientRect(); return {x:(r.left+r.right)/2, y:(r.top+r.bottom)/2, l:r.left, r:r.right, t:r.top,b:r.bottom}; }
  function setSelected(el) {
    if (!el || !el.isConnected) return false;
    if (selected && selected !== el) selected.classList.remove(highlightClass);
    selected = el; el.classList.add(highlightClass);
    // Focusing an input while moving would reopen the soft keyboard: never do it.
    if (!editable(el) && el.tabIndex >= 0) {
      try { el.focus({preventScroll:true}); } catch (_) { }
    }
    try { el.scrollIntoView({block:'nearest', inline:'nearest', behavior:'smooth'}); } catch (_) { }
    return true;
  }

  function scrollContainer(el, axis) {
    for (var p = el; p && p !== document.body; p = p.parentElement) {
      var s = getComputedStyle(p);
      if (axis === 'y' && /(auto|scroll)/.test(s.overflowY) && p.scrollHeight > p.clientHeight + 15) return p;
      if (axis === 'x' && /(auto|scroll)/.test(s.overflowX) && p.scrollWidth > p.clientWidth + 15) return p;
    }
    return document.scrollingElement || document.documentElement;
  }

  function move(direction) {
    if (editing) {
      if (direction === 'DOWN' || direction === 'UP') {
        if (document.activeElement && document.activeElement.blur) document.activeElement.blur();
        editing = false;
      } else return true;
    }
    var list = candidates();
    if (!list.length) {
      window.scrollBy({top:direction==='DOWN'?innerHeight*.65:direction==='UP'?-innerHeight*.65:0,
        left:direction==='RIGHT'?innerWidth*.6:direction==='LEFT'?-innerWidth*.6:0,behavior:'smooth'});
      return true;
    }
    if (!selected || !selected.isConnected || !visible(selected) || !list.includes(selected)) {
      var onScreen = list.filter(function(el){ var r=el.getBoundingClientRect();return r.bottom>0 && r.top<innerHeight && r.right>0 && r.left<innerWidth; });
      var ranked = (onScreen.length?onScreen:list).sort(function(a,b){var A=rect(a),B=rect(b);return A.y-B.y || A.x-B.x;});
      return setSelected(ranked[0]);
    }
    var origin=rect(selected), best=null, bestScore=Infinity;
    var vertical=direction==='UP'||direction==='DOWN', sign=direction==='DOWN'||direction==='RIGHT'?1:-1;
    for(var i=0;i<list.length;i++) {
      var el=list[i]; if (el===selected || el.contains(selected)) continue;
      var r=rect(el), forward=sign * (vertical?r.y-origin.y:r.x-origin.x);
      if (forward < 5) continue;
      var secondary=Math.abs(vertical?r.x-origin.x:r.y-origin.y);
      // Favor same column/row even when the next option is below a scrolling boundary.
      var overlap=vertical ? Math.max(0,Math.min(r.r,origin.r)-Math.max(r.l,origin.l)) :
                              Math.max(0,Math.min(r.b,origin.b)-Math.max(r.t,origin.t));
      var score=forward + secondary*(overlap>0?0.20:1.6) + (secondary>forward*2?300:0);
      if (score<bestScore){best=el;bestScore=score;}
    }
    if(best) return setSelected(best);
    var axis=vertical?'y':'x', container=scrollContainer(selected,axis);
    var delta=sign*(vertical?innerHeight*.60:innerWidth*.60);
    try { container.scrollBy({top:vertical?delta:0,left:vertical?0:delta,behavior:'smooth'}); }
    catch (_) { window.scrollBy(0,vertical?delta:0); }
    return true;
  }

  function activate() {
    if (!selected || !selected.isConnected) return move('DOWN');
    if (editable(selected)) {
      editing=true;
      selected.focus();
      selected.click();
      // No native keyboard during navigation; Android requests IME only on this explicit activation.
      return 'keyboard';
    }
    if (selected.matches('video')) return togglePlayback();
    var t=(selected.textContent||selected.getAttribute('aria-label')||'').trim().toLowerCase();
    if (/\b(watch|play|stream|episode)\b/.test(t)) watchIntentAt=Date.now();
    selected.click();
    setTimeout(function(){tryAutoCinema(false);},650);
    setTimeout(function(){tryAutoCinema(false);},1550);
    return 'clicked';
  }

  function findVideos() {
    var videos = Array.from(document.querySelectorAll('video'));
    try { for (var f of document.querySelectorAll('iframe')) {
      try { if (f.contentDocument) videos.push.apply(videos,Array.from(f.contentDocument.querySelectorAll('video'))); }
      catch (_) { /* Cross-origin players cannot expose playback APIs. */ }
    } } catch (_) { }
    return videos.filter(function(v){return v.readyState >= 1 || v.currentSrc;});
  }

  function activeVideo() {
    var v=findVideos().filter(function(v){return v.isConnected;});
    return v.find(function(v){return !v.paused && !v.ended;}) || v.find(function(v){return visible(v);}) || v[0] || null;
  }

  function enterCinema(target) {
    if (!target || !target.isConnected || target===cinemaTarget) return false;
    exitCinema();
    cinemaTarget=target;
    document.documentElement.style.zoom='1';
    target.classList.add('pftv-cinema-target');
    toast('Fullscreen player  •  Hold OK for options');
    return true;
  }

  function tryAutoCinema(force) {
    if (cinemaTarget && cinemaTarget.isConnected) return true;
    var v=activeVideo();
    if(v && (!v.paused || force)) {
      lastVideo=v;
      var wrap=v.parentElement;
      // Keep custom controls if attached to the immediate player wrapper.
      var node=wrap && wrap!==document.body && wrap.getBoundingClientRect().width < innerWidth*.98 ? wrap : v;
      return enterCinema(node);
    }
    if (!force && Date.now()-watchIntentAt>8500) return false;
    var iframes=Array.from(document.querySelectorAll('iframe')).filter(function(f){
      if(!visible(f)) return false;
      var r=f.getBoundingClientRect(), src=f.src||'';
      return r.width>260 && r.height>145 && !/doubleclick|adservice|googlesyndication|popads|monetag|adsterra/i.test(src);
    });
    var player=iframes.sort(function(a,b){return (b.clientWidth*b.clientHeight)-(a.clientWidth*a.clientHeight);})[0];
    return player ? enterCinema(player) : false;
  }

  function exitCinema() {
    if(cinemaTarget){cinemaTarget.classList.remove('pftv-cinema-target');cinemaTarget=null;setZoom(zoomPercent);return true;}
    return false;
  }

  function togglePlayback() {
    var v=activeVideo(); if(!v) {toast('Use the embedded player controls to pause');return false;}
    if(v.paused) v.play().catch(function(){}); else v.pause();
    toast(v.paused?'Paused':'Playing');return true;
  }

  function seek(seconds) {
    var v=activeVideo();
    if (!v || !Number.isFinite(v.duration) || v.duration <= 0) return false;
    var target=Math.max(0,Math.min(v.duration-.1,v.currentTime+seconds));
    try { v.currentTime=target; toast((seconds>0?'+':'')+Math.round(seconds)+'s  •  '+clock(target)+' / '+clock(v.duration));return true;}
    catch(_) {return false;}
  }
  function clock(t){t=Math.max(0,Math.floor(t));return Math.floor(t/3600)>0?Math.floor(t/3600)+':'+String(Math.floor(t/60)%60).padStart(2,'0')+':'+String(t%60).padStart(2,'0'):Math.floor(t/60)+':'+String(t%60).padStart(2,'0');}

  function findSources() {
    exitCinema();
    // The website owns the actual source list; never invent sources or URLs.
    var nodes=candidates(), found=nodes.find(function(n){
      var s=((n.textContent||'')+' '+(n.getAttribute('aria-label')||'')).trim().replace(/\s+/g,' ');
      return s.length<90 && /(?:source|server|mirror|backup|change player|select player)/i.test(s);
    });
    if(found){setSelected(found);toast('Press OK to choose a source');return true;}
    toast('Video page: use its source/server selector');return false;
  }

  function setZoom(percent) {
    zoomPercent=Math.max(85,Math.min(150,Number(percent)||115));
    document.documentElement.style.zoom=(zoomPercent/100).toFixed(2);
    if(selected && selected.isConnected) selected.scrollIntoView({block:'nearest',inline:'nearest'});
    return zoomPercent;
  }

  function back() {
    if(editing){ editing=false; if(document.activeElement) document.activeElement.blur();return true; }
    return exitCinema();
  }

  function key(k) {
    if(k==='UP'||k==='DOWN'||k==='LEFT'||k==='RIGHT') return move(k);
    if(k==='OK') return activate();
    return false;
  }

  // Keyboard/touch events from the site may change what is selected; never keep an obsolete highlight.
  document.addEventListener('click',function(e){
    var target=e.target;
    if(!target||!target.closest)return;
    var item=target.closest('button,a[href],input,textarea,[role=button],[role=tab],[class*=cursor-pointer]');
    if(item && visible(item)) setSelected(item);
  },true);
  document.addEventListener('focusin',function(e){
    if(editable(e.target) && e.isTrusted) editing=true;
  },true);
  document.addEventListener('focusout',function(e){
    if(editable(e.target)) editing=false;
  },true);
  document.addEventListener('play',function(e){
    if(e.target instanceof HTMLMediaElement) setTimeout(function(){tryAutoCinema(false);}, 200);
  },true);
  document.addEventListener('keydown',function(e){
    if(editing)return;
    if(e.key==='ArrowDown'||e.key==='ArrowUp'||e.key==='ArrowLeft'||e.key==='ArrowRight'){
      e.preventDefault();e.stopImmediatePropagation();key(e.key.substring(5).toUpperCase());
    }
  },true);
  setInterval(function(){
    if(location.href!==lastUrl){lastUrl=location.href;exitCinema();if(selected)selected.classList.remove(highlightClass);selected=null;watchIntentAt=Date.now();setTimeout(function(){tryAutoCinema(false);},900);}
    if(cinemaTarget && !cinemaTarget.isConnected) cinemaTarget=null;
    var playing=activeVideo();if(playing && !playing.paused && !cinemaTarget) tryAutoCinema(false);
  },1300);

  window.PFTV = {version:2, key:key, move:move, activate:activate, seek:seek,
    togglePlayback:togglePlayback, cinema:tryAutoCinema, exitCinema:exitCinema,
    isCinema:function(){return !!cinemaTarget;}, back:back, findSources:findSources,
    setZoom:setZoom, isEditing:function(){return editing;}, toast:toast};
  setZoom(window.__PFTV_ZOOM||115);
})();
