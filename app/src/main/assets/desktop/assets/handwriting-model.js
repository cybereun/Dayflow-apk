// Separate from planner serialization so older clients cannot discard ink.
export const MAX_POINTS = 8192;
export const MAX_RECORD_BYTES = 256 * 1024;
const identifier = value => typeof value === 'string' && /^[A-Za-z0-9_-]{1,128}$/.test(value);
const finite = (n, min, max) => typeof n === 'number' && Number.isFinite(n) && n >= min && n <= max;
export function validateStroke(value) {
  const s = value;
  if (!s || s.v !== 1 || !identifier(s.id) || !identifier(s.bookId) || !['daily','weekly'].includes(s.kind)
      || typeof s.date !== 'string' || !/^\d{4}-\d{2}-\d{2}$/.test(s.date)
      || !Number.isFinite(Date.parse(s.date)) || new Date(s.date).toISOString().slice(0,10) !== s.date
      || !/^#[0-9a-f]{6}$/i.test(s.color) || !finite(s.width, .5, 30)
      || typeof s.deleted !== 'boolean' || !Number.isSafeInteger(s.stamp?.time) || s.stamp.time < 0 || !identifier(s.stamp.device)
      || !Array.isArray(s.points) || s.points.length > MAX_POINTS || (!s.deleted && !s.points.length)) throw Error('올바르지 않은 필기 데이터입니다. 원본을 유지합니다.');
  const size = s.kind === 'daily' ? {width:1277,height:2000} : {width:2000,height:1277};
  let lastTime = -1;
  for (const point of s.points) {
    if (!point || !finite(point.x,0,size.width) || !finite(point.y,0,size.height) || !finite(point.p,0,1)
        || !finite(point.t,0,86400000) || point.t < lastTime) throw Error('필기 좌표가 올바르지 않습니다.');
    lastTime = point.t;
  }
  const clean = {v:1,id:s.id,bookId:s.bookId,kind:s.kind,date:s.date,points:s.points.map(p=>({x:p.x,y:p.y,p:p.p,t:p.t})),color:s.color.toLowerCase(),width:s.width,stamp:{time:s.stamp.time,device:s.stamp.device},deleted:s.deleted};
  if (new TextEncoder().encode(JSON.stringify(clean)).byteLength > MAX_RECORD_BYTES) throw Error('필기 한 획이 너무 큽니다. 초안은 유지합니다.');
  return clean;
}
function compare(a,b) { return a.stamp.time-b.stamp.time || (a.stamp.device<b.stamp.device?-1:a.stamp.device>b.stamp.device?1:0) || Number(a.deleted)-Number(b.deleted) || (JSON.stringify(a)<JSON.stringify(b)?-1:JSON.stringify(a)>JSON.stringify(b)?1:0); }
export function mergeStrokes(local,remote) {
  const result = new Map();
  for (const item of [...local,...remote]) {
    const incoming=validateStroke(item),old=result.get(incoming.id);
    if (old && (old.bookId!==incoming.bookId || old.kind!==incoming.kind || old.date!==incoming.date)) throw Error('같은 필기 ID의 페이지가 다릅니다. 기존 필기를 유지합니다.');
    if (!old || compare(incoming,old)>0) result.set(incoming.id,incoming);
  }
  return [...result.values()].sort((a,b)=>a.id<b.id?-1:a.id>b.id?1:0);
}
export function pagePoint(point,rect,size) {
  if (![point.x,point.y,rect.left,rect.top,rect.width,rect.height,size.width,size.height].every(Number.isFinite) || rect.width<=0 || rect.height<=0 || size.width<=0 || size.height<=0) throw Error('필기 영역을 확인하지 못했습니다.');
  return {x:Math.max(0,Math.min(size.width,(point.x-rect.left)*size.width/rect.width)),y:Math.max(0,Math.min(size.height,(point.y-rect.top)*size.height/rect.height))};
}
