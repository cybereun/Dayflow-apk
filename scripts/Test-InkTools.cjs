const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path');
const root=path.resolve(__dirname,'..');
test('freehand tools do not install a competing recognition keyboard',()=>{
 const source=fs.readFileSync(path.join(root,'app/src/main/assets/desktop/assets/handwriting-service.js'),'utf8');
 assert.ok(!source.includes('mountHandwritingInput'));
 assert.ok(!fs.readFileSync(path.join(root,'app/build.gradle.kts'),'utf8').includes('digital-ink-recognition'));
});
test('settings popup is outside animated and transformed palette subtree',()=>{
 const source=fs.readFileSync(path.join(root,'app/src/main/assets/desktop/assets/handwriting-service.js'),'utf8');
 assert.ok(source.includes('document.body.append(details)'));
 assert.ok(!source.includes('toolbar.append(details)'));
});
