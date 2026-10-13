'use strict';
const workspace = (() => {
  const $ = id => document.getElementById(id);
  const pending = new Map();
  let serial = 0, page = 'Terminal', selected = '', path = '', editing = null, revision = null;
  let originalText = '', online = false, ctrl = false, term, fit, attached = false, desktop, palette = {};
  const message = value => { $('notice').textContent = value || ''; };
  const error = value => { message(value); };
  function request(op, args = {}) {
    const id = String(++serial);
    return new Promise((resolve, reject) => {
      const timeout = setTimeout(() => { pending.delete(id); reject(new Error('Workspace request timed out. Reconnect to check its state.')); }, 35000);
      pending.set(id, {resolve, reject, timeout});
      Strata.request(id, JSON.stringify({op, ...args}));
    });
  }
  function reply(id, response) {
    const wait = pending.get(id); if (!wait) return;
    clearTimeout(wait.timeout); pending.delete(id);
    if (response.ok) wait.resolve(response); else wait.reject(new Error(response.error || 'Workspace action failed'));
  }
  function action(id, fn) { $(id).addEventListener('click', () => Promise.resolve().then(fn).catch(e => error(e.message))); }
  function ask(label, initial = '') {
    $('prompt-label').textContent = label; $('prompt-value').value = initial;
    $('prompt').showModal(); $('prompt-value').focus();
    return new Promise(resolve => { $('prompt').onclose = () => resolve($('prompt').returnValue === 'ok' ? $('prompt-value').value : null); });
  }
  function commandName(prefix) { return prefix + '-' + Date.now().toString(36); }
  function send(data) {
    if (!attached) return;
    // Chunk before encoding to stay within the runtime's bounded input frames.
    const bytes = new TextEncoder().encode(data);
    for (let start = 0; start < bytes.length; start += 8192) {
      Strata.input(JSON.stringify({op:'input', data:btoa(String.fromCharCode(...bytes.subarray(start, start + 8192)))}));
    }
  }
  function resize() {
    if (!term || page !== 'Terminal') return;
    fit.fit();
    if (attached) Strata.input(JSON.stringify({op:'resize', cols:term.cols, rows:term.rows}));
  }
  function attach(name) {
    selected = name; attached = false; term.reset();
    Strata.attach(JSON.stringify({op:'attach', name, cols:term.cols, rows:term.rows}));
  }
  function sessions(items) {
    $('sessions').replaceChildren();
    for (const item of items) {
      const option = document.createElement('option'); option.value = item.name;
      option.textContent = item.name + (item.running ? '' : ' · exited ' + (item.exit ?? '?'));
      $('sessions').append(option);
    }
    if (!items.some(item => item.name === selected)) selected = items[0]?.name || '';
    $('sessions').value = selected;
  }
  async function refreshSessions(reattach = true) {
    const state = await request('status'); sessions(state.sessions);
    if (selected && reattach) attach(selected);
  }
  async function show(next) {
    if (page === 'Terminal' && next !== page) { attached = false; Strata.detach(); }
    if (page === 'Desktop' && next !== page) { desktop?.close(); desktop = null; }
    page = next; $('title').textContent = next;
    for (const name of ['Terminal', 'Files', 'Software', 'Services', 'Desktop']) $(name.toLowerCase() + '-page').hidden = name !== page;
    if (page === 'Terminal') { resize(); if (online) await refreshSessions(); }
    if (online && page === 'Files') await listFiles();
    if (online && page === 'Services') await listServices();
  }
  async function connected(state) {
    try {
      online = true; $('setup').hidden = true; message('');
      $('identity').textContent = 'Native · UID ' + state.uid;
      sessions(state.sessions);
      if (page === 'Terminal' && !selected) {
        const created = await request('create', {name:'main'}); selected = created.name;
      }
      await show(page);
    } catch (e) { error(e.message); }
  }
  async function run(command, prefix) {
    const created = await request('create', {name:commandName(prefix), command});
    selected = created.name; await show('Terminal');
  }
  function filePath(name) { return path ? path + '/' + name : name; }
  function dirty() { return editing !== null && $('file-text').value !== originalText; }
  async function mayDiscard() { return !dirty() || await ask('Type discard to leave your unsaved edits') === 'discard'; }
  async function listFiles() {
    const result = await request('files', {path});
    path = result.path === '.' ? '' : result.path;
    $('path').textContent = '~/' + path; $('entries').replaceChildren();
    for (const item of result.entries) {
      const button = document.createElement('button'); button.className = 'entry';
      button.textContent = (item.directory ? '▸ ' : '  ') + item.name + (item.link ? ' ↗' : '');
      button.onclick = async () => {
        try {
          if (!await mayDiscard()) return;
          if (item.directory) { const oldPath = path; path = filePath(item.name);
            try { await listFiles(); closeEditor(); } catch (e) { path = oldPath; throw e; }
          } else {
            const name = filePath(item.name), value = await request('read', {path:name});
            openEditor(name, value.text, value.revision);
          }
        } catch (e) { error(e.message); }
      };
      $('entries').append(button);
    }
  }
  function openEditor(name, text, version) {
    editing = name; revision = version; originalText = text;
    $('filename').textContent = name; $('file-text').value = text; $('editor').hidden = false;
    $('editor').scrollIntoView();
    saveDraft();
  }
  function saveDraft() {
    Strata.draft(JSON.stringify({path, editing, revision, originalText, text:$('file-text').value,
      serviceName:$('service-name').value, serviceScript:$('service-script').value}));
  }
  function closeEditor() { editing = null; $('editor').hidden = true; saveDraft(); }
  async function listServices() {
    const result = await request('services'); $('services').replaceChildren();
    for (const item of result.services) {
      const row = document.createElement('div'); row.className = 'service';
      const label = document.createElement('div');
      label.textContent = item.name + ' · ' + (item.running ? 'running' : item.exit !== null ? 'exited ' + item.exit : 'stopped');
      row.append(label);
      for (const [title, op] of [['Start', 'service-start'], ['Stop', 'service-stop'], ['Log', 'service-log'], ['Edit', 'service-read']]) {
        const button = document.createElement('button'); button.textContent = title;
        button.onclick = async () => {
          try { const response = await request(op, {name:item.name});
            if (op === 'service-log') { $('service-log').textContent = response.text; $('service-log').hidden = false; }
            else if (op === 'service-read') { $('service-name').value = item.name; $('service-script').value = response.script; saveDraft(); }
            else await listServices();
          } catch (e) { error(e.message); }
        }; row.append(button);
      }
      $('services').append(row);
    }
  }
  function init(options) {
    const colors = options.theme;
    palette = colors;
    for (const [key, color] of [['bg','background'], ['fg','foreground'], ['surface','lighter_background'], ['accent','accent'], ['muted','light_foreground']])
      document.documentElement.style.setProperty('--' + key, colors[color]);
    document.documentElement.style.colorScheme = colors.mode === 'light' ? 'light' : 'dark';
    const theme = {background:colors.background, foreground:colors.foreground, cursor:colors.accent};
    ['black','red','green','yellow','blue','magenta','cyan','white','brightBlack','brightRed','brightGreen','brightYellow','brightBlue','brightMagenta','brightCyan','brightWhite'].forEach((key,index) => theme[key] = colors['color' + index]);
    term = new Terminal({theme, fontSize:14, cursorBlink:true, scrollback:3000, allowProposedApi:false, allowTransparency:false});
    fit = new FitAddon.FitAddon(); term.loadAddon(fit); term.open($('terminal'));
    term.onData(data => {
      if (ctrl && data.length === 1) { const code = data.toUpperCase().charCodeAt(0);
        if (code >= 64 && code <= 95) data = String.fromCharCode(code - 64);
        ctrl = false; $('ctrl').classList.remove('active');
      } send(data);
    });
    // Disable automatic URL activation and OSC clipboard changes; copying is explicit.
    term.onBinary(data => { if (attached) Strata.input(JSON.stringify({op:'input',data:btoa(data)})); });
    new ResizeObserver(resize).observe($('terminal'));
    selected = options.session; page = options.page;
    const draft = options.draft || {};
    path = draft.path || '';
    $('service-name').value = draft.serviceName || ''; $('service-script').value = draft.serviceScript || '';
    if (draft.editing != null) {
      openEditor(draft.editing, draft.originalText || '', draft.revision);
      $('file-text').value = draft.text || ''; saveDraft();
    }
    show(page).catch(e => error(e.message));
    $('setup').hidden = false; Strata.start();
  }
  action('reconnect', () => { message('Connecting…'); Strata.start(); });
  action('stop-workspace', async () => {
    if (await ask('Type stop to end all workspace terminals, graphics and services. Files are kept.') !== 'stop') return;
    Strata.detach(); attached = false; desktop?.close(); desktop = null;
    await request('stop'); online = false; selected = ''; $('identity').textContent = 'Stopped'; message('Workspace stopped. Reconnect to start it again.');
  });
  action('prepare', () => Strata.prepare());
  action('new-session', async () => { const name = await ask('Session name', commandName('shell')); if (!name) return;
    await request('create', {name}); selected = name; await refreshSessions(); });
  action('close-session', async () => { if (!selected || await ask('Type close to end ' + selected + ' and its running programs') !== 'close') return;
    Strata.detach(); attached = false; await request('close', {name:selected}); selected = ''; await refreshSessions(); });
  $('sessions').onchange = () => attach($('sessions').value);
  $('keys').addEventListener('click', event => {
    const key = event.target.dataset.key; if (!key) return;
    if (key === 'ctrl') { ctrl = !ctrl; $('ctrl').classList.toggle('active', ctrl); }
    else send({esc:'\x1b',tab:'\t',up:'\x1b[A',down:'\x1b[B',left:'\x1b[D',right:'\x1b[C'}[key]);
    term.focus();
  });
  action('copy', () => { if (term.hasSelection()) Strata.copy(term.getSelection()); else message('Select terminal text first.'); });
  action('paste', () => Strata.paste());
  action('up', async () => { if (!await mayDiscard()) return; path = path.split('/').slice(0,-1).join('/'); closeEditor(); await listFiles(); });
  action('refresh-files', listFiles);
  action('new-file', async () => { if (!await mayDiscard()) return; const name = await ask('New file name'); if (name) openEditor(filePath(name), '', null); });
  action('new-folder', async () => { const name = await ask('New folder name'); if (name) { await request('mkdir', {path:filePath(name)}); await listFiles(); } });
  action('import-file', async () => { if (await mayDiscard()) Strata.importFile(); });
  action('export-file', () => { if (editing !== null) Strata.exportFile(editing, $('file-text').value); });
  action('save-file', async () => { if (editing === null) return; const text = $('file-text').value;
    const result = await request('write', {path:editing, text, revision}); revision = result.revision; originalText = text; saveDraft(); message('Saved ' + editing); await listFiles(); });
  for (const id of ['file-text','service-name','service-script']) $(id).addEventListener('input', saveDraft);
  action('close-file', async () => { if (await mayDiscard()) closeEditor(); });
  $('install-form').onsubmit = event => { event.preventDefault(); const name = $('package').value.trim();
    if (!/^[a-z0-9][a-z0-9+.-]{0,79}$/.test(name)) return error('Enter one package name.');
    run('pkg install ' + name, 'install').catch(e => error(e.message)); };
  action('package-refresh', () => run('pkg update', 'repositories'));
  action('package-list', () => run('dpkg-query -W; read -r -p "Press Enter to finish"', 'packages'));
  action('refresh-services', listServices);
  $('service-form').onsubmit = event => { event.preventDefault();
    request('service-save', {name:$('service-name').value.trim(), script:$('service-script').value})
      .then(() => { message('Service saved. Start it when ready.'); return listServices(); }).catch(e => error(e.message)); };
  async function startDesktop() {
    message('Starting native graphical session…');
    await request('desktop-start',{theme:palette});
    desktop?.close();
    const {Desktop} = await import('./desktop.js');
    desktop = new Desktop($('desktop-display'),message);
    Strata.attach(JSON.stringify({op:'display'}));
  }
  action('start-desktop', startDesktop);
  action('install-desktop', () => run('pkg install -y x11-repo && pkg install tigervnc openbox aterm xorg-xauth', 'graphics-setup'));
  action('stop-desktop', async () => {
    if (await ask('Type stop to close the desktop and its graphical programs') !== 'stop') return;
    desktop?.close(); desktop = null; await request('desktop-stop'); message('Desktop stopped');
  });
  $('gui-form').onsubmit = event => { event.preventDefault();
    request('desktop-run',{command:$('gui-command').value}).then(() => message('Program started; its output is available in Terminal.')).catch(e=>error(e.message)); };
  action('desktop-keyboard', () => { $('desktop-input').hidden = !$('desktop-input').hidden; if (!$('desktop-input').hidden) $('desktop-input').focus(); });
  $('desktop-input').addEventListener('input', event => {
    if (event.isComposing) return;
    if (event.inputType === 'deleteContentBackward') desktop?.key(0xff08);
    else if ($('desktop-input').value) desktop?.text($('desktop-input').value);
    $('desktop-input').value = '';
  });
  $('desktop-input').addEventListener('compositionend', () => { desktop?.text($('desktop-input').value); $('desktop-input').value = ''; });
  $('desktop-input').addEventListener('keydown', event => {
    const keys = {Enter:0xff0d,Backspace:0xff08,Tab:0xff09,Escape:0xff1b,ArrowUp:0xff52,ArrowDown:0xff54,ArrowLeft:0xff51,ArrowRight:0xff53};
    if (keys[event.key]) { event.preventDefault(); desktop?.key(keys[event.key]); }
  });
  return {init, reply, connected, message, error, paste:text => term.paste(text),
    imported:(name,text) => { openEditor(filePath(name.replaceAll('/', '_')),text,null); message('Imported draft. Save to add it to your native home.'); },
    attached:() => { attached = true; resize(); message(''); },
    displayAttached:() => desktop?.open(),
    output:(event, acknowledge = null) => { if (event.data) term.write(Uint8Array.from(atob(event.data), c => c.charCodeAt(0)), () => { if (acknowledge !== null) Strata.rendered(acknowledge); });
      if (event.displayData) desktop?.data(event.displayData);
      if (!event.data && acknowledge !== null) Strata.rendered(acknowledge);
      if (event.detached) { attached = false; message('Session detached. Reconnect to reopen it.'); } },
    suspend:() => { attached = false; desktop?.close(); desktop = null; Strata.detach(); },
    resume:() => { if (online) Strata.start(); }
  };
})();
