// Keep the original palette in screen space while the planner is pinch-zoomed.
(() => {
  const update = () => {
    const root = document.getElementById('android-palette');
    if (!root) return;
    const viewport = window.visualViewport;
    const scale = viewport?.scale ?? 1;
    const width = viewport?.width ?? innerWidth;
    const height = viewport?.height ?? innerHeight;
    const left = viewport?.offsetLeft ?? 0;
    const top = viewport?.offsetTop ?? 0;
    const landscape = matchMedia('(orientation: landscape)').matches || Math.min(innerWidth,innerHeight)>=600;
    const toolbar=document.getElementById('android-toolbar');
    if(toolbar)Object.assign(toolbar.style,{left:`${left}px`,top:`${top}px`,right:'auto',width:`${width*scale-(landscape?76:0)}px`,height:'48px',transformOrigin:'top left',transform:`scale(${1/scale})`});
    Object.assign(root.style, {
      right:'auto', bottom:'auto', transformOrigin:'top left',
      transform:`scale(${1 / scale})`,
      left:`${landscape ? left + width - 76 / scale : left}px`,
      top:`${landscape ? top : top + height - 90 / scale}px`,
      width:`${landscape ? 76 : width * scale}px`,
      height:`${landscape ? height * scale : 90}px`,
    });
  };
  const schedule = () => requestAnimationFrame(update);
  window.visualViewport?.addEventListener('resize', schedule);
  window.visualViewport?.addEventListener('scroll', schedule);
  window.addEventListener('resize', schedule);
  document.addEventListener('DOMContentLoaded', () => {
    new MutationObserver(schedule).observe(document.body,{childList:true});
    schedule();
  });
})();
