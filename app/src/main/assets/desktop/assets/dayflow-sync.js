// The original Windows sync pane is isolated so its styles cannot alter other settings.
export function mountSync(container,bridge){
 container.classList.add('dayflow-sync-pane');
 if(!bridge?.sync){container.textContent='동기화는 Dayflow Windows 앱에서 사용할 수 있습니다.';return()=>{};}
 const frame=document.createElement('iframe');frame.src=new URL('../vendor/spiralday-sync/ui.html',import.meta.url).href;frame.title='Dayflow 동기화';frame.style.cssText='width:100%;height:780px;border:0;display:block;background:transparent';container.replaceChildren(frame);
 const resize=event=>{if(event.source!==frame.contentWindow||event.data?.type!=='dayflow-sync-height')return;const height=Number(event.data.height);if(Number.isFinite(height)&&height>0&&height<12000)frame.style.height=Math.ceil(height)+'px';};
 window.addEventListener('message',resize);
 return()=>{window.removeEventListener('message',resize);bridge.sync.call('sync.settingsClosed').catch(()=>{});frame.remove();};
}
