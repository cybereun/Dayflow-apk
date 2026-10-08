(() => {
  document.addEventListener('DOMContentLoaded', () => {
    document.body.dataset.androidView = new URLSearchParams(location.search).get('view') ?? 'main';
  });
  const native = window.AndroidDayflow;
  if (!native) return;
  if (window.parent !== window && window.parent.dayflow) {
    window.dayflow = window.parent.dayflow;
    window.spiralday = window.dayflow;
    return;
  }

  const parse = (value) => JSON.parse(value);
  const invoke = (method, ...args) => {
    const result = parse(native[method](...args));
    if (result && result.__error) throw new Error(result.__error);
    return result;
  };
  const listeners = { status: new Set(), applied: new Set(), shared: new Set(), bus: new Set(), updates: new Set(), maximized: new Set(), tutorialClose: new Set() };
  const pendingFiles = new Map();
  let lastApplied = Number(native.remoteApplyVersion());
  let lastStatus = '';
  let sharedState = invoke('sharedGet');
  const commands = new Set();
  const pendingCommands = new Map();
  let syncState = { ready: false, known: false, inGroup: false, state: 'off' };
  let commandId = 0;
  let settingsFrame;

  const requestFile = (action, name, text) => new Promise((resolve) => {
    const id = `${Date.now()}-${Math.random().toString(36).slice(2)}`;
    pendingFiles.set(id, resolve);
    native.requestTextFile(action, name || '', text || '', id);
  });
  window.__dayflowAndroidResolve = (id, result) => {
    const resolve = pendingFiles.get(id);
    if (!resolve) return;
    pendingFiles.delete(id);
    resolve(result);
  };

  const bridge = {
    kind: 'capacitor',
    platform: 'android',
    version: native.version(),
    storage: {
      readLibrary: async () => {
        const result = invoke('readLibrary');
        if (result.status === 'ok') {
          const library = JSON.parse(result.text);
          for (const book of library.books ?? []) for (const field of ['start', 'created']) {
            if (/^\d{4}-\d{2}-\d{2}$/.test(book[field])) book[field] += 'T00:00:00Z';
          }
          result.text = JSON.stringify(library);
        }
        return result;
      },
      writeLibrary: async (text) => invoke('writeLibrary', text),
      readBook: async (id) => {
        const result = invoke('readBook', id);
        if (result.status === 'ok') {
          const book = JSON.parse(result.text);
          // Legacy empty timetable cells can be JSON null. Keep the stored
          // original intact; the desktop renderer uses -1 for an empty cell.
          for (const day of Object.values(book.days ?? {})) if (Array.isArray(day.slots)) {
            day.slots = day.slots.map(value => value === null ? -1 : value);
          }
          result.text = JSON.stringify(book);
        }
        return result;
      },
      writeBook: async (id, text) => invoke('writeBook', id, text),
      deleteBook: async (id) => invoke('deleteBook', id),
      listBooks: async () => invoke('listBooks'),
      dataDir: async () => invoke('dataDir'),
      backupNow: async () => invoke('backupNow'),
      snapshotNow: async () => invoke('originalSnapshot'),
      revealDataDir: async () => undefined,
    },
    sync: {
      call: (action, arg) => new Promise((resolve, reject) => {
        if (action === 'status') { resolve(syncState); return; }
        if (!commands.size) { reject(new Error('동기화 엔진을 준비하는 중입니다. 잠시 후 다시 시도해 주세요.')); return; }
        const id = ++commandId;
        pendingCommands.set(id, resolve);
        commands.forEach(callback => callback({id, action, arg: arg ?? {}}));
      }),
      onCommand: callback => (commands.add(callback), () => commands.delete(callback)),
      reply: (id, result) => { const resolve = pendingCommands.get(id); pendingCommands.delete(id); resolve?.(result); },
      publish: value => { syncState = value; listeners.status.forEach(callback => callback(value)); },
      getCredentials: async () => invoke('originalCredentials'),
      setCredentials: async value => invoke('originalSetCredentials', JSON.stringify(value ?? null)),
      readSettings: async () => invoke('originalSettings'),
      writeSettings: async value => invoke('originalSetSettings', typeof value === 'string' ? value : JSON.stringify(value)),
      deviceInfo: async () => invoke('originalDeviceInfo'),
      prepareMigration: async () => invoke('originalSnapshot'),
      envUrl: 'https://dayflow-sync.cybereunny.workers.dev/original',
      onStatus: (callback) => { listeners.status.add(callback); callback(syncState); return () => listeners.status.delete(callback); },
      onApplied: (callback) => (listeners.applied.add(callback), () => listeners.applied.delete(callback)),
    },
    window: {
      minimize: () => undefined,
      toggleMaximize: () => undefined,
      close: () => invoke('closeApp'),
      isMaximized: async () => false,
      onMaximizedChange: (callback) => (listeners.maximized.add(callback), () => listeners.maximized.delete(callback)),
      setKind: async (value) => bridge.shared.set({ kind: value }),
    },
    palette: { show: () => undefined, hide: () => undefined, setSize: () => undefined, setFocusable: () => undefined },
    rings: { onLayout: () => () => undefined },
    shared: {
      get: async () => ({ ...sharedState }),
      set: (patch) => {
        sharedState = invoke('sharedSet', JSON.stringify(patch || {}));
        listeners.shared.forEach((callback) => callback({ ...sharedState }));
      },
      onChange: (callback) => (listeners.shared.add(callback), () => listeners.shared.delete(callback)),
    },
    bus: {
      post: (message) => listeners.bus.forEach((callback) => callback(message)),
      on: (callback) => (listeners.bus.add(callback), () => listeners.bus.delete(callback)),
    },
    updates: {
      check: async () => ({ state: 'disabled', reason: 'android' }),
      install: () => undefined,
      onStatus: (callback) => { callback({ state: 'disabled', reason: 'android' }); listeners.updates.add(callback); return () => listeners.updates.delete(callback); },
    },
    snap: { active: new URLSearchParams(location.search).get('view') === 'snap', ready: () => undefined },
    shell: {
        popupMenu: (items) => new Promise(resolve => {
          const overlay=document.createElement('div');
          overlay.style.cssText='position:fixed;inset:0;z-index:100001;background:#0005;display:flex;align-items:center;justify-content:center;padding:20px;box-sizing:border-box';
          const panel=document.createElement('div');
          panel.setAttribute('role','dialog');panel.setAttribute('aria-label','플래너 선택');
          panel.style.cssText='width:100%;max-width:400px;max-height:75%;overflow:auto;background:#fcfbf7;border-radius:18px;padding:16px;box-shadow:0 8px 30px #0003;font-family:var(--font-ui,sans-serif)';
          const title=document.createElement('div');title.textContent='플래너 선택';title.style.cssText='font-size:20px;font-weight:700;margin-bottom:12px';panel.append(title);
          const finish=id=>{overlay.remove();resolve(id);};
          for(const item of items){
            if(item.type==='separator'){panel.append(document.createElement('hr'));continue;}
            const button=document.createElement('button');button.type='button';
            button.textContent=(item.checked?'✓  ':'')+item.label;
            button.style.cssText='display:block;width:100%;min-height:48px;text-align:left;border:0;border-radius:10px;margin:4px 0;padding:12px;font-size:16px;color:#34343a;background:'+(item.checked?'#dcefeb':'#f1efea');
            button.disabled=!!item.disabled;button.onclick=()=>finish(item.id);panel.append(button);
          }
          const close=document.createElement('button');close.textContent='취소';close.style.cssText='width:100%;min-height:44px;margin-top:12px;border:0;border-radius:10px;font-size:16px;background:#eae8e3';close.onclick=()=>finish(null);panel.append(close);
          overlay.onclick=e=>{if(e.target===overlay)finish(null);};overlay.append(panel);document.body.append(overlay);
        }),
      openSettings: (pane) => {
        const url = new URL(location.href); url.searchParams.set('view', 'settings');
        if (pane) url.searchParams.set('pane', pane);
        if (!settingsFrame) {
          settingsFrame = document.createElement('iframe');
          settingsFrame.title = 'Dayflow 설정';
          settingsFrame.style.cssText = 'position:fixed;inset:0;width:100%;height:100%;border:0;z-index:100000;background:#f8f7f3';
          document.body.append(settingsFrame);
        }
        settingsFrame.src = url.href;
      },
      closeSettings: () => { settingsFrame?.remove(); settingsFrame = undefined; bridge.sync.call('sync.settingsClosed').catch(console.error); },
      openExternal: (url) => invoke('openExternal', String(url || '')),
      focusMain: () => undefined,
      saveTextFile: (name, text) => requestFile('save', name, text),
      openTextFile: () => requestFile('open', '', ''),
      revealPath: () => undefined,
      listBackups: async () => invoke('listBackups'),
      readBackupBook: async (day, id) => invoke('readBackupBook', day, id),
      bookFileInfo: async (id) => invoke('bookFileInfo', id),
      appInfo: async () => ({ name: 'Dayflow', version: native.version(), platform: 'Android' }),
      telemetry: async () => false,
      setTelemetryEnabled: async () => false,
    },
    print: {
      openForm: () => undefined,
      run: async (request) => invoke('printCurrentPage', JSON.stringify(request || {})),
      cancel: () => undefined,
      onProgress: () => () => undefined,
      onCollect: () => () => undefined,
      snapshot: () => undefined,
      onRender: () => () => undefined,
      viewReady: () => undefined,
      rendered: () => undefined,
    },
    star: { status: async () => ({ state: 'disabled' }), answered: () => undefined },
    tutorial: {
      flags: async () => invoke('tutorialFlags'),
      setFlags: (patch) => invoke('tutorialSetFlags', JSON.stringify(patch || {})),
      openOnboarding: () => undefined,
      finishOnboarding: async () => ({ ok: true }),
      onCloseRequest: (callback) => (listeners.tutorialClose.add(callback), () => listeners.tutorialClose.delete(callback)),
    },
  };

  window.dayflow = bridge;
  window.spiralday = bridge;
  window.__dayflowAndroidBack = () => {
    if (settingsFrame) bridge.shell.closeSettings();
    else bridge.window.close();
  };

  window.setInterval(() => {
    try {
      const version = Number(native.remoteApplyVersion());
      if (version !== lastApplied) {
        lastApplied = version;
        listeners.applied.forEach((callback) => callback([]));
      }
      const status = syncState;
      const signature = JSON.stringify(status);
      if (signature !== lastStatus) {
        lastStatus = signature;
        listeners.status.forEach((callback) => callback(status));
      }
    } catch {}
  }, 1200);
})();
