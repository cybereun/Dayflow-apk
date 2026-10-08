// Dayflow store boundary for the unmodified original SyncEngine host.
export function adaptPlannerStore(store,backend,onApplied=()=>{}){
 const local=new Set(),saved=new Set();let remote=0,beforeSave=null,saveChain=Promise.resolve();
 const initialSave=store.getState().saveNow;
 const off=store.subscribe((next,previous)=>{if(!remote&&next.library.activeID===previous.library.activeID&&next.data!==previous.data)for(const fn of local)fn(previous.data,next.data);});
 const saveNow=()=>{
  const run=saveChain.catch(()=>{}).then(async()=>{
   await beforeSave?.();const current=store.getState(),id=current.library.activeID,dirty=current.dirty,data=current.data;
   const fn=current.saveNow===saveNow?initialSave:current.saveNow;await fn();
   if(store.getState().dirty)throw Error('플래너 저장을 완료하지 못했습니다. 동기화 전환을 중단했어요.');
   if(dirty&&id)for(const listener of saved)listener(id,data);
  });saveChain=run;return run;
 };
 store.setState({saveNow});
 const methods={
  saveNow,
  subscribeLocalEdits:fn=>{local.add(fn);return()=>local.delete(fn);},
  subscribeSaved:fn=>{saved.add(fn);return()=>saved.delete(fn);},
  setBeforeSave:fn=>{beforeSave=fn;},
  async syncSetLibrary(library){
   const state=store.getState();if(state.blocked||state.libraryUnreadable)throw Error('책장을 읽을 수 없어 동기화를 중단했어요.');
   await backend.writeLibrary(JSON.stringify(library));remote++;try{store.setState({library});}finally{remote--;}onApplied();
  },
  async syncApplyOpenBook(fn){
   const state=store.getState(),id=state.library.activeID;if(!id||state.status!=='ready'||state.blocked||state.unreadableBooks[id])throw Error('열린 플래너를 읽을 수 없어 동기화를 중단했어요.');
   const data=fn(state.data);remote++;try{store.setState({data,version:state.version+1,dirty:true});state.clearHistory?.();}finally{remote--;}
   await saveNow();onApplied();return true;
  },
  syncApplyLive(fn){
   const state=store.getState(),id=state.library.activeID;if(!id||state.status!=='ready'||state.blocked||state.unreadableBooks[id])return false;
   const data=fn(state.data);if(data!==state.data){remote++;try{store.setState({data,version:state.version+1});state.clearHistory?.();store.getState().scheduleSave();}finally{remote--;}onApplied();}return true;
  },
  syncNoteMissing(){}
 };
 return{getState:()=>({...store.getState(),missingFiles:{},libraryCreated:false,...methods}),dispose(){off();local.clear();saved.clear();if(store.getState().saveNow===saveNow)store.setState({saveNow:initialSave});}};
}
