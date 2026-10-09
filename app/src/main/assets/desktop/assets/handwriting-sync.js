import {mergeStrokes} from './handwriting-model.js';

export function startInkSync({bridge,storage,onChanged=()=>{},onError=()=>{},interval=5000}) {
  let closed=false,running=false,cursor=0;const sequences=new Map();
  async function syncNow(){
    if(closed||running)return;running=true;
    try {
      let more=true;
      while(more&&!closed){
        const batch=await bridge.sync.inkCall('pull',{since:cursor});
        if(!Number.isSafeInteger(batch.cursor)||batch.cursor<cursor||!Array.isArray(batch.records)||typeof batch.more!=='boolean'||batch.more&&batch.cursor===cursor)throw Error('필기 서버 응답이 올바르지 않습니다.');
        for(const item of batch.records)sequences.set(item.stroke.id,item.seq);
        await storage.applyStrokes(batch.records.map(item=>item.stroke),false);
        cursor=batch.cursor;more=batch.more;onChanged();
      }
      for(const queued of await storage.pendingRecords()){
        if(closed)break;
        let stroke=queued;
        for(let attempt=0;attempt<3;attempt++){
          const result=await bridge.sync.inkCall('push',{stroke,baseSeq:sequences.get(stroke.id)??0});
          if(result.ok){sequences.set(stroke.id,result.seq);await storage.ackRecords([{id:stroke.id,stamp:stroke.stamp}]);break;}
          if(!result.record)throw Error('필기 충돌 내용을 읽지 못했습니다. 원본은 유지합니다.');
          stroke=mergeStrokes([stroke],[result.record.stroke])[0];
          sequences.set(stroke.id,result.record.seq);await storage.applyStrokes([stroke],true);onChanged();
          if(attempt===2)throw Error('다른 기기에서 필기를 수정 중입니다. 잠시 후 다시 맞춥니다.');
        }
      }
    } catch(error){if(!closed)onError(error);} finally {running=false;}
  }
  const trigger=()=>{void syncNow();};
  const timer=setInterval(trigger,interval);
  window.addEventListener('online',trigger);window.addEventListener('focus',trigger);
  void syncNow();
  const dispose=()=>{closed=true;clearInterval(timer);window.removeEventListener('online',trigger);window.removeEventListener('focus',trigger);};
  dispose.syncNow=syncNow;return dispose;
}
