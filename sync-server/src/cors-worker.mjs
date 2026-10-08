import protocol from './worker.mjs';
export {SyncGroup,PairDirectory} from './worker.mjs';
export {OriginalDirectory,OriginalSyncGroup} from './worker.mjs';

const ANDROID_ORIGIN='https://appassets.androidplatform.net';
const METHODS=['GET','HEAD','POST','PUT','PATCH','DELETE'];
const HEADERS=['authorization','content-type','if-match','if-none-match'];
export default {
 async fetch(request,env,ctx){
  const origin=request.headers.get('origin');
  const allowed=origin===ANDROID_ORIGIN;
  if(request.method==='OPTIONS'){
   const method=request.headers.get('access-control-request-method');
   const headers=(request.headers.get('access-control-request-headers')??'').toLowerCase().split(',').map(s=>s.trim()).filter(Boolean);
   if(!allowed||!METHODS.includes(method)||headers.some(h=>!HEADERS.includes(h)))return new Response(null,{status:403});
   return new Response(null,{status:204,headers:{
    'access-control-allow-origin':ANDROID_ORIGIN,
    'access-control-allow-methods':METHODS.join(', '),
    'access-control-allow-headers':HEADERS.join(', '),
    'access-control-max-age':'600',
    'vary':'Origin, Access-Control-Request-Method, Access-Control-Request-Headers'
   }});
  }
  const response=await protocol.fetch(request,env,ctx);
  // WebSocket handshakes are not CORS fetch responses; preserve their socket.
  if(!allowed||response.status===101)return response;
  const headers=new Headers(response.headers);
  headers.set('access-control-allow-origin',ANDROID_ORIGIN);
  headers.set('access-control-expose-headers','etag');
  headers.append('vary','Origin');
  return new Response(response.body,{status:response.status,statusText:response.statusText,headers});
 }
};
