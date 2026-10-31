/* PirateFlix TV 2.1 — site-backed TV catalog and native remote adapter.
 * No fabricated catalog entries. The website remains the source of navigation,
 * episode/stream selection, and user data. */
(() => {
  'use strict';
  if (window !== window.top) return;
  if (window.PFTV?.version === 5) { window.PFTV.zoom(window.__PFTV_ZOOM || 85); return; }
  const sections = ['Movies','TV Shows','Live TV','Watch Later'];
  const state = {mode:'tv', section:'Movies', area:'rail', rail:0, selected:0,
    cards:[], key:'', cinema:null, lastURL:location.href, zoom:85,
    originalHeader:null, lastSeek:'', lastPlayer:false, nativePip:false, usFilterApplied:false, gatherStep:0, searching:false};
  const style=document.createElement('style');style.id='pftv-tv-css';
  style.textContent=`
  #pftv-shell{position:fixed!important;inset:0!important;z-index:2147483000!important;
    display:flex!important;background:#07101e;color:#eaf5ff;font:17px/1.4 system-ui,Arial,sans-serif;
    letter-spacing:normal;overflow:hidden;}
  #pftv-shell *{box-sizing:border-box;}
  #pftv-shell button{font:inherit;cursor:pointer;}
  #pftv-rail{width:182px;min-width:182px;display:flex;flex-direction:column;gap:7px;
    padding:20px 10px;background:#0c182a;border-right:1px solid #253e53;}
  #pftv-brand{font-size:20px;font-weight:800;margin:8px 12px 22px;color:#40e8d2;}
  .pftv-nav{border:0;border-radius:9px;background:transparent;color:#aec4d7;text-align:left;
    padding:12px 12px;font-weight:600!important;min-height:48px;}
  .pftv-nav.is-current{color:#fff;background:#17354a;}
  .pftv-nav.is-focused,#pftv-shell .pftv-focused{outline:3px solid #42f2d4!important;outline-offset:2px!important;
    box-shadow:0 0 0 4px rgba(5,14,25,.7);}
  #pftv-rail-footer{margin-top:auto;color:#8da6bb;font-size:12px;padding:8px 10px;}
  #pftv-main{flex:1;min-width:0;display:flex;flex-direction:column;}
  #pftv-top{height:65px;min-height:65px;display:flex;align-items:center;gap:20px;
    padding:7px 18px;border-bottom:1px solid #203248;}
  #pftv-heading{font-weight:750;font-size:24px;margin:0;white-space:nowrap;}
  #pftv-count{font-size:14px;color:#91b0c6;}
  #pftv-search{margin-left:auto;border-radius:10px;border:1px solid #3a5266;
    outline:none;min-width:190px;width:min(38%,440px);height:43px;background:#101f33;
    color:#fff;padding:0 16px;font-size:18px;}
  #pftv-search.pftv-focused{border:2px solid #40e8d2;}
  #pftv-content{flex:1;min-height:0;overflow:auto;scrollbar-width:thin;
    padding:8px 14px 8px;overscroll-behavior:contain;}
  #pftv-grid{display:grid;grid-template-columns:repeat(5,minmax(0,1fr));
    grid-auto-rows:calc((100% - 16px)/3);height:100%;min-height:0;
    gap:8px;align-content:start;}
  .pftv-tile{background:#122339;border:1px solid #243c54;border-radius:12px;
    display:flex;flex-direction:column;text-align:left;padding:0;overflow:hidden;
    color:white;min-height:0;height:100%;}
  .pftv-poster{width:100%;height:calc(100% - 31px);min-height:0;object-fit:contain;display:block;background:#16273b;}
  .pftv-tile.is-live .pftv-poster{object-fit:contain;padding:5px;}
  .pftv-no-poster{width:100%;height:calc(100% - 31px);display:grid;place-items:center;
    background:linear-gradient(140deg,#1b3b4f,#182037);font-size:38px;}
  .pftv-caption{padding:4px 8px;display:block;font-size:13px;font-weight:600;
    flex:none;min-height:31px;height:31px;overflow:hidden;line-height:1.35;}
  #pftv-shell.is-switching{opacity:0;transition:opacity .27s ease;}
  .pftv-pip-label{color:#47eddb;font-size:12px;}
  #pftv-empty{padding:40px;max-width:620px;color:#c6d5e4;font-size:19px;}
  #pftv-empty button{margin-top:18px;border:1px solid #42f2d4;background:#163445;
    border-radius:10px;color:#fff;padding:13px 22px;}
  #pftv-hint{font-size:12px;color:#9db6c7;padding:4px 16px 7px;}
  .pftv-site-focus{outline:4px solid #42f2d4!important;outline-offset:3px!important;
    box-shadow:0 0 15px rgba(50,240,214,.7)!important;}
  html.pftv-web-mode .pftv-original-header{display:none!important;}
  html.pftv-web-mode body{scroll-padding-top:0!important;}
  html.pftv-cinema,html.pftv-cinema body{overflow:hidden!important;}
  html.pftv-cinema .pftv-cinema-hidden{visibility:hidden!important;pointer-events:none!important;}
  html.pftv-cinema .pftv-cinema-path{visibility:visible!important;overflow:visible!important;
    transform:none!important;filter:none!important;contain:none!important;clip-path:none!important;
    isolation:auto!important;}
  html.pftv-cinema .pftv-cinema-player{position:fixed!important;inset:0!important;
    width:100vw!important;height:100vh!important;max-width:none!important;max-height:none!important;
    min-width:100vw!important;min-height:100vh!important;margin:0!important;padding:0!important;
    z-index:2147483400!important;transform:none!important;opacity:1!important;
    background:#000!important;visibility:visible!important;}
  .pftv-cinema-player video,.pftv-cinema-player iframe{width:100%!important;height:100%!important;
    max-width:100%!important;max-height:100%!important;object-fit:contain!important;}
  #pftv-message{position:fixed;display:none;bottom:8%;left:50%;transform:translateX(-50%);
    border-radius:12px;background:rgba(6,13,22,.94);color:white;z-index:2147483647;
    padding:13px 22px;font:18px system-ui,sans-serif;max-width:80vw;text-align:center;
    pointer-events:none;}
  `;
  document.documentElement.appendChild(style);
  const shell=document.createElement('div');shell.id='pftv-shell';
  shell.innerHTML='<aside id="pftv-rail"><div id="pftv-brand">▣ PirateFlix TV</div>'+sections.map((s,i)=>
    `<button type="button" class="pftv-nav" data-section="${i}">${s}</button>`).join('')+
    '<button type="button" class="pftv-nav" data-section="4">Website view</button>'+ 
    '<div id="pftv-rail-footer">D-pad to move · OK to open<br>Menu for settings and player</div></aside>'+ 
    '<main id="pftv-main"><header id="pftv-top"><div><h1 id="pftv-heading">Movies</h1><span id="pftv-count"></span></div>'+ 
    '<input id="pftv-search" type="search" autocomplete="off" placeholder="Search movies / channels" aria-label="Search"></header>'+ 
    '<section id="pftv-content"><div id="pftv-grid"></div><div id="pftv-empty" hidden></div></section>'+ 
    '<footer id="pftv-hint">Select a title to open its details and player. Use Back to return to this grid.</footer></main>';
  document.documentElement.appendChild(shell);
  const $=s=>document.querySelector(s);
  const rail=Array.from(shell.querySelectorAll('.pftv-nav'));
  const grid=$('#pftv-grid'), content=$('#pftv-content'), search=$('#pftv-search');
  const message=document.createElement('div');message.id='pftv-message';document.documentElement.appendChild(message);
  function toast(s){message.textContent=s;message.style.display='block';clearTimeout(toast.timer);
    toast.timer=setTimeout(()=>message.style.display='none',2300);}
  function visible(el){if(!el?.isConnected)return false;const r=el.getBoundingClientRect();
    if(r.width<9||r.height<9)return false;
    for(let p=el;p&&p.nodeType===1;p=p.parentElement){if(p===shell)return false;
      const cs=getComputedStyle(p);if(cs.display==='none'||cs.visibility==='hidden')return false;}return true;}
  function text(el){return(el?.innerText||el?.textContent||el?.getAttribute('aria-label')||'').trim().replace(/\s+/g,' ');}
  function originalNav(target){const controls=Array.from(document.querySelectorAll('a,button,[role=tab]'))
    .filter(el=>!shell.contains(el)&&visible(el));
    return controls.find(el=>text(el).toLowerCase()===target.toLowerCase()) ||
      controls.find(el=>el.getAttribute('aria-label')?.toLowerCase()===target.toLowerCase());}
  function chooseSection(idx){
    if(idx===4){enterWebsite();return;}
    const el=originalNav(sections[idx]);
    if(!el){toast('The website has no '+sections[idx]+' tab yet');return;}
    state.section=sections[idx];state.selected=0;state.area='grid';state.cache=new Map();state.usFilterApplied=false;
    shell.querySelector('#pftv-heading').textContent=state.section;
    el.click();setTimeout(()=>{refresh(true);gatherMore();},700);setTimeout(()=>refresh(false),1600);renderFocus();
  }
  rail.forEach((btn,i)=>btn.addEventListener('click',()=>chooseSection(i)));
  function mediaTarget(img){
    let p=img;
    for(let i=0;p&&i<7;i++,p=p.parentElement){
      if(p===document.body||p===shell||p.tagName==='HEADER'||p.tagName==='NAV')break;
      if(p.matches?.('a[href], [role=button], [onclick], [class*=cursor-pointer], [class*=channel-card], [class*=movie-card], [class*=poster]')){
        if(!p.matches('button'))return p;
      }
    }
    return img;
  }
  function meaningfulImg(img){
    if(shell.contains(img)||img.closest('header,nav,footer'))return false;
    // Include offscreen lazy-loaded posters; do not require the image to be in the viewport.
    if(!img.isConnected || img.closest('[hidden],[aria-hidden=true]'))return false;
    const r=img.getBoundingClientRect(), src=posterURL(img);
    if(r.width<30||r.height<25||!src||src.startsWith('data:image/svg+xml'))return false;
    if(/logo\.svg|favicon|avatar|user-icon|profile\.png/i.test(src))return false;
    const anc=mediaTarget(img);
    if(anc.closest('header,nav,footer'))return false;
    return true;
  }
  function posterURL(img){
    if(!img)return '';
    const raw=[img.getAttribute('data-src'),img.getAttribute('data-lazy-src'),img.currentSrc,
      img.getAttribute('src'),img.getAttribute('srcset')?.split(',').pop()?.trim().split(/\s+/)[0],
      img.parentElement?.querySelector('source[srcset]')?.srcset?.split(',').pop()?.trim().split(/\s+/)[0]].find(x=>x&& !x.startsWith('data:image/svg')&&!x.startsWith('blob:'));
    if(!raw)return '';
    try{return new URL(raw,location.href).href;}catch(_){return '';}
  }
  function cardFromImage(img,i){
    const node=mediaTarget(img);let title=img.alt?.trim()||img.title?.trim()||'';
    const container=node===img?img.parentElement:node;
    if(!title||title.length<2||/^(poster|image|cover|logo|thumbnail)$/i.test(title)){
      title=text(container?.querySelector('[class*=title],[class*=name],h2,h3,h4,[aria-label]'))||'';
    }
    if(!title||title.length<2){
      const p=container?.parentElement;title=text(p?.querySelector('[class*=title],[class*=name],h2,h3,h4'))||'';
    }
    if(!title||title.length<2){const raw=text(container);title=raw.split(/\n|Favorite|Watch Later|\bHD\b/)[0].slice(0,70).trim();}
    if(!title||title.length<2)title=img.alt||`Title ${i+1}`;
    const src=posterURL(img);
    return {title:title.slice(0,90),image:src,target:node,sourceImg:img};
  }
  function tryUSFilter(){
    if(state.section!=='Live TV')return;
    // Prefer the site's own US filter so countryless channel cards inherit a known US-only view.
    const select=Array.from(document.querySelectorAll('select')).find(el=>!shell.contains(el)&&
      Array.from(el.options).some(o=>/^(us|usa|united states)$/i.test(o.value.trim())||/^(us|usa|united states)$/i.test(o.text.trim())));
    if(select){const option=Array.from(select.options).find(o=>/^(us|usa|united states)$/i.test(o.value.trim())||/^(us|usa|united states)$/i.test(o.text.trim()));
      if(select.value!==option.value){select.value=option.value;select.dispatchEvent(new Event('input',{bubbles:true}));select.dispatchEvent(new Event('change',{bubbles:true}));}
      state.usFilterApplied=true;return;
    }
    const buttons=Array.from(document.querySelectorAll('button,[role=option],[role=radio]')).filter(el=>!shell.contains(el));
    const us=buttons.find(el=>/^(us|usa|united states|🇺🇸\s*us)$/i.test(text(el)));
    if(us && !us.getAttribute('aria-pressed')?.includes('true') && !state.usFilterApplied){us.click();state.usFilterApplied=true;}
  }
  function countryOf(node){
    const attrs=['data-country','data-country-code','data-region','data-iso','data-countryname'];
    for(let el=node;el&&el!==document.body;el=el.parentElement){
      for(const name of attrs){const x=el.getAttribute?.(name);if(x)return x.toLowerCase().trim();}
      if(el.matches?.('[data-channel-list],[class*=channel-grid]'))break;
    }
    const flags=Array.from(node.querySelectorAll('[title],[aria-label],img[alt]'))
      .map(e=>[e.getAttribute('title'),e.getAttribute('aria-label'),e.getAttribute('alt')].filter(Boolean).join(' ')).join(' ');
    const match=flags.match(/(?:country|region)\s*[:=-]?\s*(united states|usa|us|poland|pl|canada|ca|uk|gb|world)/i);
    return match?match[1].toLowerCase():'';
  }
  function liveCards(){
    const selector='[data-channel-id],[data-channel],[class*=channel-card],[class*=channel-item],[class*=channel-tile],'+
      '[class*=station-card],[class*=station-item],[role=listitem],.channel,article';
    const nodes=Array.from(document.querySelectorAll(selector))
      .filter(n=>!shell.contains(n)&&n.isConnected&&!n.closest('header,nav,footer')&&n.getBoundingClientRect().width>40);
    const items=[];
    for(const node of nodes){
      const label=node.querySelector('[class*=channel-name],[class*=station-name],[class*=name],h2,h3,h4,strong');
      const title=text(label||node).replace(/\b(favorite|unfavorite|watch now|watch|play)\b/ig,'').trim().slice(0,90);
      if(!title||title.length<2||title.length>85)continue;
      const country=countryOf(node);
      // Unknown country may be shown only when the site's US filter has been applied.
      if(country && !['us','usa','united states','united states of america','🇺🇸'].includes(country))continue;
      if(!country&&!state.usFilterApplied)continue;
      const img=node.querySelector('img');
      const target=node.querySelector('a[href]:not([title*=avorite]),[role=button]:not([title*=avorite])')||node;
      items.push({title,image:posterURL(img),target,sourceImg:img && !img.closest('button[title*=avorite]')?img:null});
    }
    return items;
  }
  function collect(){
    if(state.section==='Live TV')tryUSFilter();
    const images=Array.from(document.querySelectorAll('img')).filter(meaningfulImg);
    let cards=images.map(cardFromImage);
    if(state.section==='Live TV'){
      const explicit=liveCards();
      // An image alone is not evidence of its station's country.
      cards=explicit;
    }
    const seen=new Set();return cards.filter(c=>{
      const label=(c.title+'|'+c.image).toLowerCase();
      if(seen.has(label))return false;seen.add(label);return true;
    }).slice(0,1000);
  }
  function gatherMore(){
    if(state.mode!=='tv'||state.searching)return;
    const pools=[document.scrollingElement,document.documentElement,...Array.from(document.querySelectorAll('main,section,[class*=list],[class*=content],[class*=grid]'))].filter(Boolean);
    const scroll=pools.filter(el=>!shell.contains(el)&&el.scrollHeight>el.clientHeight+160)
      .sort((a,b)=>(b.scrollHeight-b.clientHeight)-(a.scrollHeight-a.clientHeight))[0];
    if(!scroll)return;
    const initial=scroll.scrollTop;
    let step=0;
    const scan=()=>{
      if(state.mode!=='tv'||step>=12){scroll.scrollTop=initial;refresh(true);return;}
      scroll.scrollTop=Math.min(scroll.scrollHeight-scroll.clientHeight,step++*Math.max(300,innerHeight*.65));
      refresh(true);setTimeout(scan,240);
    };
    setTimeout(scan,380);
  }
  function refresh(force){
    if(!shell.isConnected)return;
    const incoming=collect();
    // Keep previously harvested cards so virtual/lazy lists don't collapse back to 3 titles.
    state.cache=state.cache||new Map();
    for(const c of incoming)state.cache.set((c.title+'|'+c.image).toLowerCase(),c);
    const cards=Array.from(state.cache.values());
    const next=cards.map(c=>c.title+'|'+c.image).join('\n');
    if(!force&&next===state.key)return;
    state.key=next;state.cards=cards;
    grid.replaceChildren();
    for(let i=0;i<cards.length;i++){
      const c=cards[i],button=document.createElement('button');button.type='button';
      button.className='pftv-tile'+(state.section==='Live TV'?' is-live':'');
      if(c.image){const img=document.createElement('img');img.className='pftv-poster';img.src=c.image;
        img.loading='eager';img.decoding='async';img.alt=c.title;
        img.onerror=()=>{img.style.display='none';button.classList.add('pftv-img-error');};button.appendChild(img);}
      else {const span=document.createElement('span');span.className='pftv-no-poster';span.textContent='▶';button.appendChild(span);}
      const caption=document.createElement('span');caption.className='pftv-caption';caption.textContent=c.title;
      button.appendChild(caption);button.addEventListener('click',()=>openCard(i));grid.appendChild(button);
    }
    shell.querySelector('#pftv-count').textContent=cards.length?`${cards.length} titles`:'Awaiting website results';
    const empty=$('#pftv-empty');empty.hidden=cards.length>0;
    if(!cards.length)empty.innerHTML=state.section==='Live TV'
      ? 'No confirmed US channels have been found. Check the Live TV region/filter in Website view. <button id="pftv-open-web">Open website view</button>'
      : 'Waiting for the website to load titles. <button id="pftv-open-web">Open website view</button>';
    empty.querySelector('button')?.addEventListener('click',enterWebsite);
    state.selected=Math.min(Math.max(state.selected,0),Math.max(cards.length-1,0));renderFocus();
  }
  function openCard(i){
    const c=state.cards[i];if(!c||!c.target?.isConnected){refresh(true);toast('Content changed; try again');return;}
    state.selected=i;state.lastPlayer=false;shell.classList.add('is-switching');setTimeout(()=>{enterWebsite();shell.classList.remove('is-switching');},190);
    // Click the media image first, which bubbles to the actual card listener,
    // not its separate Favorite button.
    setTimeout(()=>{if(c.sourceImg?.isConnected)c.sourceImg.click();else c.target.click();},200);
    setTimeout(()=>{if(state.mode==='web')locateHeader();},550);
    setTimeout(()=>autoCinema(false),1450);
  }
  function focusElement(){if(state.area==='rail')return rail[state.rail];
    if(state.area==='search')return search;
    return grid.children[state.selected];}
  function renderFocus(){shell.querySelectorAll('.pftv-focused,.is-focused').forEach(el=>el.classList.remove('pftv-focused','is-focused'));
    rail.forEach((el,i)=>el.classList.toggle('is-current',sections[i]===state.section));
    const el=focusElement();if(!el)return;
    el.classList.add('pftv-focused');
    if(state.area==='grid')el.scrollIntoView({behavior:'auto',block:'nearest',inline:'nearest'});
  }
  function columnCount(){if(!grid.children.length)return 1;
    const first=grid.children[0].getBoundingClientRect();let n=0;
    for(const ch of grid.children){if(Math.abs(ch.getBoundingClientRect().top-first.top)<8)n++;else break;}
    return Math.max(1,n);}
  function navigate(dir){
    if(state.mode==='tv'){
      if(document.activeElement===search && state.area==='search' && document.activeElement.matches(':focus')){
        if(dir==='DOWN'){search.blur();state.area='grid';state.selected=0;renderFocus();}return true;
      }
      if(state.area==='rail'){
        if(dir==='UP')state.rail=Math.max(0,state.rail-1);
        if(dir==='DOWN')state.rail=Math.min(4,state.rail+1);
        if(dir==='RIGHT')state.area=state.cards.length?'grid':'search';
        if(dir==='LEFT'&&state.nativePip)return 'pip';
      } else if(state.area==='search'){
        if(dir==='LEFT')state.area='rail';if(dir==='DOWN')state.area='grid';
      } else {
        const cols=columnCount(),max=state.cards.length-1;
        if(dir==='UP'){
          if(state.selected<cols)state.area='search';else state.selected-=cols;
        }
        if(dir==='DOWN'&&state.selected+cols<=max)state.selected+=cols;
        if(dir==='LEFT'){
          if(state.selected%cols===0)state.area='rail';else state.selected--;
        }
        if(dir==='RIGHT')state.selected=Math.min(max,state.selected+1);
      }
      renderFocus();return true;
    }
    if(state.cinema){return true;}
    return navigateSite(dir);
  }
  let siteFocus=null;
  function siteCandidates(){const selector='button,a[href],input,textarea,select,[role=button],[role=tab],[onclick],[tabindex],[class*=cursor-pointer]';
    return Array.from(document.querySelectorAll(selector)).filter(el=>!shell.contains(el)&&el!==message&&visible(el)&&
      !el.closest('.pftv-original-header')&&el.getBoundingClientRect().width<innerWidth*1.5&&
      !el.matches('[disabled],[aria-disabled=true]'));
  }
  function navigateSite(dir){
    const list=siteCandidates();
    if(!list.length){window.scrollBy(0,dir==='DOWN'?360:dir==='UP'?-360:0);return true;}
    if(!siteFocus||!list.includes(siteFocus)){
      const a=list.filter(el=>{const r=el.getBoundingClientRect();return r.bottom>0&&r.top<innerHeight;});
      siteFocus=(a[0]||list[0]);
    }else {
      const o=siteFocus.getBoundingClientRect();const ox=(o.left+o.right)/2,oy=(o.top+o.bottom)/2;
      const vertical=dir==='UP'||dir==='DOWN',sign=dir==='DOWN'||dir==='RIGHT'?1:-1;
      let best=null,score=Infinity;
      for(const el of list){if(el===siteFocus)continue;const r=el.getBoundingClientRect();
        const x=(r.left+r.right)/2,y=(r.top+r.bottom)/2;
        const forward=sign*(vertical?y-oy:x-ox);if(forward<5)continue;
        const secondary=Math.abs(vertical?x-ox:y-oy);
        const value=forward+secondary*(secondary>forward?1.7:.45);
        if(value<score){score=value;best=el;}
      }
      if(best)siteFocus=best;else{
        let scroll=siteFocus;
        while(scroll&&scroll!==document.body){const s=getComputedStyle(scroll);
          if(dir==='UP'||dir==='DOWN'?scroll.scrollHeight>scroll.clientHeight+20&&/auto|scroll/.test(s.overflowY):
            scroll.scrollWidth>scroll.clientWidth+20&&/auto|scroll/.test(s.overflowX))break;scroll=scroll.parentElement;}
        (scroll||window).scrollBy({top:dir==='DOWN'?innerHeight*.58:dir==='UP'?-innerHeight*.58:0,
          left:dir==='RIGHT'?innerWidth*.4:dir==='LEFT'?-innerWidth*.4:0,behavior:'smooth'});
      }
    }
    document.querySelectorAll('.pftv-site-focus').forEach(el=>el.classList.remove('pftv-site-focus'));
    siteFocus?.classList.add('pftv-site-focus');siteFocus?.scrollIntoView({block:'nearest',inline:'nearest'});
    return true;
  }
  function searchSite(q){
    const searchField=Array.from(document.querySelectorAll('input[type=search],input[placeholder*="Search"],input[placeholder*="search"]'))
      .find(el=>!shell.contains(el));
    if(!searchField){toast('The website has no search field');return;}
    const setValue=Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value')?.set;
    setValue?.call(searchField,q);
    searchField.dispatchEvent(new Event('input',{bubbles:true}));
    searchField.dispatchEvent(new Event('change',{bubbles:true}));
    searchField.dispatchEvent(new KeyboardEvent('keydown',{key:'Enter',code:'Enter',bubbles:true}));
    const button=originalNav('Search');button?.click();
    state.section='Search';state.cache=new Map();state.searching=true;setTimeout(()=>{state.searching=false;gatherMore();},1800);shell.querySelector('#pftv-heading').textContent='Search results';
    state.area='grid';state.selected=0;setTimeout(()=>refresh(true),480);setTimeout(()=>refresh(true),1800);
    search.blur();renderFocus();
  }
  search.addEventListener('keydown',e=>{if(e.key==='Enter'){e.preventDefault();searchSite(search.value);}});
  // The Android native search dialog handles IME; the HTML field is never focused by TV navigation.
  search.addEventListener('focus',()=>{if(state.mode==='tv')search.blur();});
  function locateHeader(){
    if(state.originalHeader?.isConnected)return;
    const nodes=Array.from(document.querySelectorAll('header,[role=banner],nav'))
      .filter(el=>!shell.contains(el));
    const banner=nodes.find(el=>{
      const s=text(el).slice(0,450).toLowerCase();
      return s.includes('pirateflix')&&(s.includes('movies')||s.includes('live tv')||s.includes('search'));
    });
    if(banner){state.originalHeader=banner;banner.classList.add('pftv-original-header');}
  }
  function enterWebsite(){
    state.mode='web';shell.style.setProperty('display','none','important');document.documentElement.classList.add('pftv-web-mode');
    locateHeader();zoom(state.zoom);
  }
  function enterTV(){
    if(state.cinema)exitCinema(true);
    state.mode='tv';document.documentElement.classList.remove('pftv-web-mode');
    shell.style.setProperty('display','flex','important');zoom(state.zoom);refresh(true);renderFocus();
  }
  // Isolated cinema: hide siblings of the actual media element at EVERY ancestor depth,
  // ensuring the page's sticky header and unrelated fixed elements cannot sit above it.
  const cinemaHidden=[],cinemaPath=[];let savedScroll={x:0,y:0};let userExitedCinema=false;
  function playerElement(){
    const vs=Array.from(document.querySelectorAll('video')).filter(el=>!shell.contains(el)&&visible(el));
    const video=vs.find(v=>!v.paused&&!v.ended)||vs[0];if(video)return video;
    const frames=Array.from(document.querySelectorAll('iframe')).filter(el=>!shell.contains(el)&&visible(el));
    return frames.filter(el=>{const r=el.getBoundingClientRect();return r.width>260&&r.height>145;})
      .sort((a,b)=>b.clientWidth*b.clientHeight-a.clientWidth*a.clientHeight)[0]||null;
  }
  function hasPlayer(){return state.mode==='web' && !!(state.cinema||playerElement());}
  function cinema(force){
    if(state.cinema?.isConnected)return true;
    if(!force&&state.mode==='tv')return false;
    if(!force&&userExitedCinema)return false;
    const media=playerElement();if(!media)return false;
    // Use immediate wrapper only when it is an isolated video player, never the whole page.
    const p=media.parentElement;
    const r=p?.getBoundingClientRect();
    const target=p&&p!==document.body&&p!==document.documentElement&&r.width>=200&&
      r.height<=innerHeight*1.2&&p.querySelectorAll('video,iframe').length===1&&!p.querySelector('nav,header')?p:media;
    if(state.mode==='tv')enterWebsite();
    savedScroll={x:scrollX,y:scrollY};state.cinema=target;userExitedCinema=false;
    document.documentElement.classList.add('pftv-cinema');
    document.documentElement.style.zoom='1';
    target.classList.add('pftv-cinema-player');
    for(let n=target;n&&n!==document.documentElement;n=n.parentElement){
      n.classList.add('pftv-cinema-path');cinemaPath.push(n);
      for(const sib of Array.from(n.parentElement?.children||[])){
        if(sib===n||sib===style||sib===message||sib.tagName==='SCRIPT'||sib.tagName==='STYLE')continue;
        if(!sib.classList.contains('pftv-cinema-hidden')){sib.classList.add('pftv-cinema-hidden');cinemaHidden.push(sib);}
      }
    }
    toast('Fullscreen player · ◀ ▶ to seek · Menu for controls');return true;
  }
  function exitCinema(markExit){
    const el=state.cinema;if(!el)return false;if(markExit)userExitedCinema=true;
    el.classList.remove('pftv-cinema-player');
    cinemaHidden.forEach(n=>n.classList.remove('pftv-cinema-hidden'));
    cinemaPath.forEach(n=>n.classList.remove('pftv-cinema-path'));
    cinemaHidden.length=0;cinemaPath.length=0;state.cinema=null;
    document.documentElement.classList.remove('pftv-cinema');zoom(state.zoom);
    scrollTo(savedScroll.x,savedScroll.y);return true;
  }
  function autoCinema(force){if(state.mode!=='web'||state.cinema)return false;
    const v=playerElement();if(!v)return false;
    const active=v.tagName==='VIDEO'&&!v.paused;
    if(!force&&!active)return false;
    return cinema(true);
  }
  let lastRequest=null;
  function activeVideo(){const vs=Array.from(document.querySelectorAll('video')).filter(v=>!shell.contains(v));
    return vs.find(v=>!v.paused&&!v.ended)||vs[0]||null;}
  function localMedia(kind,value){
    const video=activeVideo();if(!video)return null;
    if(kind==='toggle'){try{video.paused?video.play():video.pause();return 'ok';}catch(_){return null;}}
    if(!Number.isFinite(video.duration)||video.duration<=0)return null;
    const target=kind==='relative'?video.currentTime+value:kind==='percent'?video.duration*value/100:value;
    if(!Number.isFinite(target))return null;
    try{video.currentTime=Math.max(0,Math.min(video.duration-.1,target));return 'ok';}catch(_){return null;}
  }
  function command(kind,value){
    if(state.mode==='tv')return 'no-player';
    if(localMedia(kind,value)==='ok'){state.lastSeek='ok';return 'ok';}
    const frames=Array.from(document.querySelectorAll('iframe')).filter(f=>!shell.contains(f));
    if(!frames.length){state.lastSeek='unsupported';return 'unsupported';}
    if(lastRequest && !lastRequest.done && Date.now()-lastRequest.at < 1200)return 'pending';
    if(state.lastSeek==='unsupported' && lastRequest && Date.now()-lastRequest.at<4000)return 'unsupported';
    const id='pftv'+Date.now()+Math.random().toString(36).slice(2);
    lastRequest={id,done:false,at:Date.now()};
    const payload={__PFTV_MEDIA_COMMAND:'pftv/v3',id,kind,value};
    for(const f of frames){try{f.contentWindow?.postMessage(payload,'*');}catch(_){}}
    setTimeout(()=>{if(lastRequest?.id===id&&!lastRequest.done){state.lastSeek='unsupported';
      toast('This source does not allow remote seeking. Try another source.');}},1000);
    return 'pending';
  }
  window.addEventListener('message',ev=>{
    const d=ev.data;if(!d||d.__PFTV_MEDIA_RESULT!=='pftv/v3'||!lastRequest||d.id!==lastRequest.id)return;
    if(d.ok){lastRequest.done=true;state.lastSeek='ok';toast('Player position updated');}
  });
  function seek(x){return command('relative',x);}
  function seekPercent(x){return command('percent',x);}
  function seekTo(x){return command('time',x);}
  function togglePlayback(){return command('toggle',0);}
  function focusPlayer(){
    const el=playerElement();
    if(!el){toast('No video element is available');return false;}
    const target=el.tagName==='IFRAME'?el:el;
    target.setAttribute('tabindex',target.getAttribute('tabindex')||'0');
    try{target.focus({preventScroll:true});}catch(_){target.focus();}
    toast('Remote keys forwarded to the embedded player');
    return true;
  }
  function chooseSources(){
    exitCinema(true);if(state.mode==='tv')enterWebsite();
    const list=siteCandidates();
    const el=list.find(n=>/\b(server|source|mirror|backup|change player|select player)\b/i.test(text(n).slice(0,90)));
    if(!el){toast('No source picker found on this page');return false;}
    siteFocus=el;el.classList.add('pftv-site-focus');el.scrollIntoView({block:'center'});return true;
  }
  function zoom(percent){state.zoom=Math.max(40,Math.min(160,Number(percent)||85));
    if(state.mode==='web'&&!state.cinema)document.documentElement.style.zoom=(state.zoom/100).toFixed(2);
    else document.documentElement.style.zoom='1';return state.zoom;}
  function activate(){
    if(state.mode==='tv'){
      if(state.area==='rail'){chooseSection(state.rail);return 'ok';}
      if(state.area==='search'){return 'search';}
      const c=state.cards[state.selected];if(!c)return 'empty';openCard(state.selected);return 'ok';
    }
    if(state.cinema)return togglePlayback();
    if(!siteFocus?.isConnected){navigateSite('DOWN');return 'ok';}
    if(siteFocus.matches('input,textarea,[contenteditable=true]')){
      siteFocus.focus();siteFocus.click();return 'keyboard';
    }
    siteFocus.click();setTimeout(()=>autoCinema(false),900);return 'ok';
  }
  function back(){
    if(state.cinema)return exitCinema(true);
    if(state.mode==='web'){enterTV();return true;}
    return false;
  }
  function resetOnNavigation(){if(location.href===state.lastURL)return;
    state.lastURL=location.href;userExitedCinema=false;exitCinema(false);state.key='';
    lastRequest=null;state.lastSeek='';
    if(state.mode==='tv')setTimeout(()=>refresh(true),550);
  }
  setInterval(()=>{resetOnNavigation();if(state.mode==='tv')refresh(false);
    if(state.mode==='web'&&!state.cinema){locateHeader();autoCinema(false);}
    if(state.cinema&&!state.cinema.isConnected)exitCinema(false);
  },1550);
  document.addEventListener('play',e=>{if(e.target.tagName==='VIDEO')setTimeout(()=>autoCinema(false),300);},true);
  // No keyboard focus on the Search box during ordinary D-pad navigation.
  document.addEventListener('keydown',e=>{
    if(state.mode==='tv'&&document.activeElement!==search&&['ArrowUp','ArrowDown','ArrowLeft','ArrowRight'].includes(e.key)){
      e.preventDefault();e.stopImmediatePropagation();navigate(e.key.slice(5).toUpperCase());
    }
  },true);

  function directStream(){
    // Only ordinary exposed media URLs. Never sniff, decrypt, or bypass an iframe/DRM host.
    const supported=/\.(?:m3u8|mpd|mp4|m4v|webm|mkv|mp3|aac)(?:$|[?#])/i;
    const sources=[];
    for(const v of document.querySelectorAll('video')){
      if(!shell.contains(v))sources.push(v.currentSrc,v.src,...Array.from(v.querySelectorAll('source')).map(n=>n.src));
    }
    for(const a of document.querySelectorAll('a[href],source[src]')){
      if(!shell.contains(a))sources.push(a.href||a.src);
    }
    const url=sources.find(u=>{
      try{return u && /^https?:$/i.test(new URL(u,location.href).protocol)&&supported.test(u);}catch(_){return false;}
    });
    return url?JSON.stringify({url:new URL(url,location.href).href,referer:location.href}):'';
  }
  function nativePip(on){state.nativePip=!!on;const foot=shell.querySelector('#pftv-rail-footer');if(foot)foot.textContent=on?'◀ from sidebar: Mini-player\nOK: play/pause or expand':'D-pad to move · OK to open\nMenu for settings and player';return true;}
  window.PFTV={version:5,key(k){if(k==='OK')return activate();return navigate(k);},activate,
    back,enterTV,enterWebsite,isTvMode:()=>state.mode==='tv',isCinema:()=>!!state.cinema,
    hasPlayer,cinema,exitCinema,zoom,setZoom:zoom,seek,seekPercent,seekTo,togglePlayback,chooseSources,focusPlayer,
    isEditing:()=>false,search:searchSite,directStream,nativePip,toast};
  zoom(window.__PFTV_ZOOM||85);refresh(true);renderFocus();setTimeout(gatherMore,900);
})();
