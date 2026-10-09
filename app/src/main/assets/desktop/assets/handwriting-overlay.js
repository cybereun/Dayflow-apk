import {pagePoint,validateStroke} from './handwriting-model.js';
import {createInkEditor} from './handwriting-editor.js';
const NS='http://www.w3.org/2000/svg';
export function strokePath(points){return points.map((p,i)=>`${i?'L':'M'}${p.x.toFixed(2)},${p.y.toFixed(2)}`).join(' ');}
export function mountInkOverlay({page,bookId,kind,date,editable,storage,deviceId,options,onSaved=()=>{},onError=()=>{},onInteraction=()=>{},onSelection=()=>{}}){
 const size=kind==='daily'?{width:1277,height:2000}:{width:2000,height:1277};
 const svg=document.createElementNS(NS,'svg');svg.dataset.dayflowInk='true';svg.setAttribute('viewBox',`0 0 ${size.width} ${size.height}`);
 svg.style.cssText=`position:absolute;left:0;top:0;width:${size.width}px;height:${size.height}px;z-index:30;pointer-events:none;overflow:hidden;touch-action:none`;
 page.append(svg);let active=null,disposed=false,revision=0;const paths=new Map(),saving=new Set();
 const editor=createInkEditor({storage,bookId,kind,date,deviceId,onChanged:()=>{onSaved();void refresh();}});
 const path=(record)=>{const el=document.createElementNS(NS,'path');el.setAttribute('d',strokePath(record.points));el.setAttribute('fill','none');el.setAttribute('stroke',record.color);el.setAttribute('stroke-width',record.width);el.setAttribute('stroke-linecap','round');el.setAttribute('stroke-linejoin','round');return el;};
 async function refresh(){const r=++revision;try{const next=await storage.loadPage(bookId,kind,date);if(disposed||r!==revision)return;const live=new Set(),selected=editor.selected();for(const s of next){if(s.deleted)continue;live.add(s.id);let el=paths.get(s.id);if(!el){el=path(s);paths.set(s.id,el);svg.insertBefore(el,active?.path??null);}const attributes={d:strokePath(s.points),stroke:selected.has(s.id)?'#286bd8':s.color,'stroke-width':String(s.width),'stroke-dasharray':selected.has(s.id)?'8 4':null};for(const [key,value]of Object.entries(attributes)){if(value===null){if(el.hasAttribute(key))el.removeAttribute(key);}else if(el.getAttribute(key)!==value)el.setAttribute(key,value);}}for(const [id,el]of paths)if(!live.has(id)&&!saving.has(id)){el.remove();paths.delete(id);}}catch(e){onError(e);}}
 const perform=async fn=>{try{await fn();await refresh();}catch(e){onError(e);}};
 const position=e=>pagePoint({x:e.clientX,y:e.clientY},svg.getBoundingClientRect(),size);
 const append=e=>{if(!active)return;if(active.mode==='draw'&&active.points.length>=2048){const previous=active.points.at(-1);finish();down(e);if(active){active.points.unshift({...previous,t:0});active.path.setAttribute('d',strokePath(active.points));}return;}if(active.points.length>=512&&active.mode!=='draw')return;const p=position(e);active.points.push({x:Number(p.x.toFixed(2)),y:Number(p.y.toFixed(2)),p:Number((e.pressure||.5).toFixed(3)),t:Math.max(0,Math.round(performance.now()-active.start))});active.path.setAttribute('d',strokePath(active.points));};
 const down=e=>{const opts=options();if(!editable||!opts.enabled||active||e.pointerType!=='pen'&&!opts.finger)return;e.preventDefault();e.stopPropagation();
  onInteraction();const mode=opts.mode??(opts.erase?'stroke':'draw');
  const record={v:1,id:crypto.randomUUID(),bookId,kind,date,color:mode==='draw'?opts.color:'#286bd8',width:mode==='draw'?opts.width:2,points:[],stamp:{time:Date.now(),device:deviceId},deleted:false};active={...record,mode,radius:opts.eraseRadius??18,start:performance.now(),pointer:e.pointerId,path:path(record)};if(mode!=='draw')active.path.setAttribute('stroke-dasharray','6 6');svg.append(active.path);svg.setPointerCapture(e.pointerId);append(e);
 };
 const move=e=>{if(active&&e.pointerId===active.pointer){e.preventDefault();e.stopPropagation();const events=e.getCoalescedEvents?.();for(const point of events?.length?events:[e])append(point);}};
 const finish=e=>{if(!active||e&&e.pointerId!==active.pointer)return;if(e)append(e);const {start,pointer,path:el,mode,radius,...record}=active;active=null;if(mode!=='draw')el.remove();
  if(mode==='lasso'){void perform(async()=>{const ids=await editor.select(record.points);onSelection(ids.length);});return;}
  if(mode==='partial'||mode==='stroke'){void perform(()=>editor.erase(record.points,radius,mode==='stroke'));return;}
  if(record.points.length===1)record.points.push({...record.points[0],x:Math.min(size.width,record.points[0].x+.1)});paths.set(record.id,el);saving.add(record.id);void perform(async()=>{try{await editor.add(validateStroke(record));}finally{saving.delete(record.id);}});
 };
 svg.addEventListener('pointerdown',down);svg.addEventListener('pointermove',move);svg.addEventListener('pointerup',finish);svg.addEventListener('pointercancel',finish);
 const updateMode=()=>{svg.style.pointerEvents=editable&&options().enabled?'auto':'none';};updateMode();void refresh();
 return {refresh,updateMode,undo:()=>perform(()=>editor.undo()),redo:()=>perform(()=>editor.redo()),deleteSelected:()=>perform(()=>editor.deleteSelected()),clear:()=>perform(()=>editor.clear()),dispose(){finish();disposed=true;svg.remove();}};
}
