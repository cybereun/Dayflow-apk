import {validateStroke} from './handwriting-model.js';
import {cutStroke,intersectsLasso} from './handwriting-geometry.js';
const same=(a,b)=>JSON.stringify(a&&{...a,stamp:null})===JSON.stringify(b&&{...b,stamp:null});
export function createInkEditor({storage,bookId,kind,date,deviceId,makeId=()=>crypto.randomUUID(),onChanged=()=>{}}){
 let chain=Promise.resolve(),selection=new Set();const undo=[],redo=[];
 const run=fn=>{const result=chain.catch(()=>{}).then(fn);chain=result.catch(()=>{});return result;};
 const read=()=>storage.loadPage(bookId,kind,date);
 const stamp=(record,previous)=>({...record,stamp:{time:Math.max(Date.now(),(previous?.stamp.time??0)+1,record.stamp.time+1),device:deviceId}});
 async function commit(changes,records){if(!changes.length)return false;const after=changes.map(s=>validateStroke(stamp(s,records.find(r=>r.id===s.id))));await storage.applyStrokes(after,true);undo.push({before:after.map(s=>records.find(r=>r.id===s.id)??null),after,applied:after});redo.length=0;selection.clear();onChanged();return true;}
 async function reverse(from,to,restore){const action=from.at(-1);if(!action)return false;const records=await read();for(const expected of action.applied)if(!same(records.find(r=>r.id===expected.id),expected))throw Error('다른 기기에서 바뀐 필기가 있어 되돌리기를 중단했습니다. 최신 기록은 유지합니다.');
  const changed=action.after.map((s,i)=>stamp(restore?s:(action.before[i]??{...s,deleted:true,points:[]}),records.find(r=>r.id===s.id))).map(validateStroke);await storage.applyStrokes(changed,true);from.pop();to.push({...action,applied:changed});selection.clear();onChanged();return true;}
 return {
  add:record=>run(async()=>{if(record.bookId!==bookId||record.kind!==kind||record.date!==date)throw Error('필기 페이지가 다릅니다.');return commit([record],await read());}),
  erase:(path,radius,whole=false)=>run(async()=>{const records=await read(),changes=[];for(const record of records){if(record.deleted)continue;const pieces=cutStroke(record.points,path,radius+record.width/2);if(pieces===null)continue;changes.push({...record,deleted:true,points:[]});if(!whole)for(const points of pieces)changes.push({...record,id:makeId(),points,deleted:false});}return commit(changes,records);}),
  select:polygon=>run(async()=>{selection=new Set((await read()).filter(s=>!s.deleted&&intersectsLasso(s.points,polygon)).map(s=>s.id));return [...selection];}),
  deleteSelected:()=>run(async()=>{const records=await read();return commit(records.filter(s=>!s.deleted&&selection.has(s.id)).map(s=>({...s,deleted:true,points:[]})),records);}),
  clear:()=>run(async()=>{const records=await read();return commit(records.filter(s=>!s.deleted).map(s=>({...s,deleted:true,points:[]})),records);}),
  selected:()=>new Set(selection),undo:()=>run(()=>reverse(undo,redo,false)),redo:()=>run(()=>reverse(redo,undo,true)),
 };
}
