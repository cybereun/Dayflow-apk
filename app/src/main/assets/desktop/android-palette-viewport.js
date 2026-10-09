// Keep the original palette in screen space while the planner is pinch-zoomed.
(() => {
  const keepFocusedFieldVisible = () => {
    const field=document.activeElement;
    if(!field?.matches('input,textarea,select,[contenteditable="true"]')) return;
    requestAnimationFrame(() => {
      const viewport=window.visualViewport;
      const visibleTop=viewport?.offsetTop??0;
      const visibleBottom=visibleTop+(viewport?.height??innerHeight);
      const rect=field.getBoundingClientRect();
      const margin=24;
      if(rect.bottom>visibleBottom-margin || rect.top<visibleTop+margin) {
        field.scrollIntoView({block:rect.bottom>visibleBottom-margin?'end':'nearest',inline:'nearest',behavior:'smooth'});
      }
    });
  };
  const update = () => {
    const viewport = window.visualViewport;
    const visibleHeight=Math.max(100,Math.min(innerHeight,viewport?.height??innerHeight));
    document.documentElement.style.setProperty('--android-visual-height',`${visibleHeight}px`);
    const stage=document.querySelector('.android-tablet-spread,.dayflow-planner-stage');
    if(stage) {
      const bottomCase=Math.min(screen.width,screen.height)<600 && innerWidth<innerHeight ? 90 : 0;
      const contentTop=parseFloat(document.documentElement.style.getPropertyValue('--android-content-top'))||48;
      stage.style.height=`${Math.max(100,visibleHeight-contentTop-bottomCase)}px`;
      const field=document.activeElement;
      if(field?.matches('input,textarea') && stage.contains(field)) {
        const rect=field.getBoundingClientRect(), bounds=stage.getBoundingClientRect();
        if(rect.bottom>bounds.bottom-40 || rect.top<bounds.top+20) stage.scrollTop+=rect.top-bounds.top-60;
      }
    }
    const root = document.getElementById('android-palette');
    if (!root) return;
    const scale = viewport?.scale ?? 1;
    const width = viewport?.width ?? innerWidth;
    const height = viewport?.height ?? innerHeight;
    const left = viewport?.offsetLeft ?? 0;
    const top = viewport?.offsetTop ?? 0;
    const landscape = (screen.orientation?.type?.startsWith('landscape') ?? Math.abs(window.orientation??0)===90) || Math.min(screen.width,screen.height)>=600;
    const toolbar=document.getElementById('android-toolbar');
    const paletteWidth = 96;
    if(toolbar)Object.assign(toolbar.style,{left:`${left}px`,top:`${top}px`,right:'auto',width:`${width*scale-(landscape?paletteWidth:0)}px`,height:'48px',transformOrigin:'top left',transform:`scale(${1/scale})`});
    const inkSettings=document.querySelector('[data-ink-settings]');
    if(inkSettings)Object.assign(inkSettings.style,{left:`${left}px`,top:`${top+48/scale}px`,width:`${width*scale-(landscape?paletteWidth:0)}px`,transform:`scale(${1/scale})`});
    Object.assign(root.style, {
      right:'auto', bottom:'auto', transformOrigin:'top left',
      transform:`scale(${1 / scale})`,
      left:`${landscape ? left + width - paletteWidth / scale : left}px`,
      top:`${landscape ? top : top + height - 90 / scale}px`,
      width:`${landscape ? paletteWidth : width * scale}px`,
      height:`${landscape ? height * scale : 90}px`,
    });
  };
  const schedule = () => requestAnimationFrame(update);
  window.visualViewport?.addEventListener('resize', schedule);
  window.visualViewport?.addEventListener('scroll', schedule);
  window.addEventListener('resize', schedule);
  window.addEventListener('dayflow-ink-layout',schedule);
  document.addEventListener('focusin',()=>{
    schedule();
    setTimeout(schedule,120);setTimeout(schedule,350);setTimeout(schedule,700);
    setTimeout(keepFocusedFieldVisible,120);setTimeout(keepFocusedFieldVisible,350);setTimeout(keepFocusedFieldVisible,700);
  });
  window.visualViewport?.addEventListener('resize',()=>{schedule();setTimeout(keepFocusedFieldVisible,80);});
  document.addEventListener('DOMContentLoaded', () => {
    new MutationObserver(schedule).observe(document.body,{childList:true});
    schedule();
  });
})();
