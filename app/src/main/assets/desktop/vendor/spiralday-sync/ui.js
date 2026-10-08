const bridge=window.dayflow??window.parent.dayflow;
window.spiralday=bridge;
const {mountNativeSync}=await import('./runtime/assets/index-BQNbWZEG.js');
mountNativeSync(document.getElementById('sync-root'),bridge);
const root=document.getElementById('sync-root');
const observer=new ResizeObserver(()=>{if(window.parent!==window)window.parent.postMessage({type:'dayflow-sync-height',height:Math.ceil(root.getBoundingClientRect().height)+16},location.origin);});
observer.observe(root);
