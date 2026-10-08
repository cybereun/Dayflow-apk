const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');
const source = fs.readFileSync(path.join(__dirname, '../app/src/main/assets/desktop/android-bridge.js'), 'utf8');
function load() {
  const calls = [];
  const native = new Proxy({}, {get: (_, method) => (...args) => {
    calls.push([method, ...args]);
    if (method === 'version') return '1.0.17';
    if (method === 'remoteApplyVersion') return 0;
    if (method === 'sharedGet') return '{}';
    if (method === 'originalSettings') return '"{}"';
    return '{"ok":true}';
  }});
  const window = {AndroidDayflow:native,setInterval:()=>0}; window.parent=window;
  vm.runInNewContext(source, {window,document:{addEventListener:()=>{}},location:{href:'https://appassets.androidplatform.net/assets/desktop/index.html?view=main',search:'?view=main'},URL,URLSearchParams,console});
  return {bridge:window.dayflow,calls};
}
test('v17 controller receives commands without legacy native syncCall', async () => {
  const {bridge,calls}=load();
  bridge.sync.onCommand(({id,action,arg}) => bridge.sync.reply(id,{ok:true,action,arg}));
  const result=await bridge.sync.call('sync.join.start',{code:'ABCD-EFGH'});
  assert.equal(result.action,'sync.join.start');
  assert.equal(result.arg.code,'ABCD-EFGH');
  assert.equal(calls.some(([method])=>method==='syncCall'),false);
  assert.equal(bridge.kind,'capacitor');
  assert.equal(bridge.sync.envUrl,'https://dayflow-sync.cybereunny.workers.dev/original');
});
test('original controller settings contract remains serialized JSON', async () => {
  const {bridge,calls}=load();
  assert.equal(await bridge.sync.readSettings(),'{}');
  await bridge.sync.writeSettings('{"deviceName":"Galaxy"}');
  assert.ok(calls.some(([method,text])=>method==='originalSetSettings'&&text==='{"deviceName":"Galaxy"}'));
});
test('new subscribers immediately receive the current original-engine state', () => {
  const {bridge}=load();
  bridge.sync.publish({ready:true,inGroup:true,state:'idle'});
  let state; const off=bridge.sync.onStatus(value=>state=value);
  assert.equal(state.inGroup,true); off();
});
