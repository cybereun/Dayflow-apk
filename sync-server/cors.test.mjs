import test from 'node:test';
import assert from 'node:assert/strict';
import worker from './src/cors-worker.mjs';
const origin='https://appassets.androidplatform.net';
test('Android JSON authenticated preflight is allowed',async()=>{
 const response=await worker.fetch(new Request('https://test/original/v1/pairings/claim',{method:'OPTIONS',headers:{origin,'access-control-request-method':'POST','access-control-request-headers':'authorization,content-type'}}),{});
 assert.equal(response.status,204);assert.equal(response.headers.get('access-control-allow-origin'),origin);
});
test('arbitrary website and unsafe preflight headers are not allowed',async()=>{
 for(const [o,h] of [['https://evil.example','content-type'],[origin,'x-original-gid']]){
  const response=await worker.fetch(new Request('https://test/original/v1/pairings/claim',{method:'OPTIONS',headers:{origin:o,'access-control-request-method':'POST','access-control-request-headers':h}}),{});
  assert.equal(response.status,403);
 }
});
test('normal responses expose only the Android origin without changing body',async()=>{
 const response=await worker.fetch(new Request('https://test/health',{headers:{origin}}),{});
 assert.equal(response.status,200);assert.equal(response.headers.get('access-control-allow-origin'),origin);
 assert.equal((await response.json()).service,'dayflow-sync');
});
test('desktop requests without Origin remain unchanged',async()=>{
 const response=await worker.fetch(new Request('https://test/health'),{});
 assert.equal(response.status,200);assert.equal(response.headers.get('access-control-allow-origin'),null);
});
