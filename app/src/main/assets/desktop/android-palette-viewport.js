// Keep the original palette in screen space while the planner is pinch-zoomed.
(() => {
  let unoccludedHeight=Math.max(100,innerHeight);
  const isEditable = element => element?.matches?.('input,textarea,select,[contenteditable="true"]')??false;
  const updateSettingsKeyboard = visibleHeight => {
    if(!document.body) return;
    const field=document.activeElement;
    if(!isEditable(field)) unoccludedHeight=visibleHeight;
    else unoccludedHeight=Math.max(unoccludedHeight,innerHeight,visibleHeight);
    const layout=window.DayflowAndroidKeyboardLayout?.computeSettingsDialogLayout({
      layoutHeight:unoccludedHeight,
      visualHeight:visibleHeight,
      visualTop:window.visualViewport?.offsetTop??0,
    })??null;
    document.body.classList.toggle('android-ime-open',!!layout);
    const activeDialog=isEditable(field)?field.closest('body[data-android-view="settings"] [role="dialog"]'):null;
    document.querySelectorAll('body[data-android-view="settings"] [role="dialog"].android-ime-pinned').forEach(dialog=>{
      if(dialog!==activeDialog || !layout){
        dialog.classList.remove('android-ime-pinned');
        dialog.style.removeProperty('--android-dialog-top');
        dialog.style.removeProperty('--android-dialog-max-height');
      }
    });
    if(layout && activeDialog){
      activeDialog.classList.add('android-ime-pinned');
      activeDialog.style.setProperty('--android-dialog-top',`${layout.top}px`);
      activeDialog.style.setProperty('--android-dialog-max-height',`${layout.maxHeight}px`);
    }
  };
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
    document.documentElement.style.setProperty('--android-visual-top',`${viewport?.offsetTop??0}px`);
    updateSettingsKeyboard(visibleHeight);
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
  window.visualViewport?.addEventListener('resize',()=>{schedule();setTimeout(keepFocusedFieldVisible,80);setTimeout(schedule,250);});
  document.addEventListener('DOMContentLoaded', () => {
    new MutationObserver(schedule).observe(document.body,{childList:true});
    schedule();
  });
})();
