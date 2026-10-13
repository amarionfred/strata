// Run with PLAYWRIGHT_MODULE=/absolute/path/to/playwright/index.mjs node .../browser.mjs
// The JS interface is adapted to the real native broker. Android lifecycle/IME
// still require a device; this checks the shipped frontend at phone dimensions.
import {createServer} from 'node:http';
import {readFile, mkdir} from 'node:fs/promises';
import {spawn} from 'node:child_process';
import {createInterface} from 'node:readline';
import {connect} from 'node:net';
import {createHmac, randomBytes} from 'node:crypto';
import {resolve, dirname} from 'node:path';
import {fileURLToPath} from 'node:url';
import assert from 'node:assert/strict';

const here = dirname(fileURLToPath(import.meta.url));
const root = resolve(here, '../../..');
const assets = resolve(root, 'system/companion/src/main/assets/workspace');
const artifacts = resolve(root, '.local/workspace-qa');
await mkdir(artifacts, {recursive:true});
const {chromium} = await import(process.env.PLAYWRIGHT_MODULE || 'playwright');
const python = spawn('python3', [resolve(here, 'browser_server.py')], {stdio:['ignore','pipe','inherit']});
const info = await new Promise((resolve, reject) => {
  createInterface({input:python.stdout}).once('line', line => resolve(JSON.parse(line)));
  python.once('exit', code => reject(new Error('Runtime exited: ' + code)));
});
const connections = new Set();
async function channel() {
  const socket = connect(info.port, '127.0.0.1'); connections.add(socket);
  socket.on('error', () => {});
  await new Promise((resolve, reject) => { socket.once('connect', resolve); socket.once('error', reject); });
  let buffer = '', ended = false, waiting = [], frames = [];
  socket.on('data', bytes => {
    buffer += bytes.toString(); let boundary;
    while ((boundary = buffer.indexOf('\n')) >= 0) {
      const value = JSON.parse(buffer.slice(0,boundary)); buffer = buffer.slice(boundary+1);
      if (waiting.length) waiting.shift().resolve(value); else frames.push(value);
    }
  });
  socket.on('close', () => { ended = true; for (const item of waiting) item.reject(new Error('Disconnected')); waiting = []; connections.delete(socket); });
  const read = () => frames.length ? Promise.resolve(frames.shift()) : ended ? Promise.reject(new Error('Disconnected')) : new Promise((resolve,reject) => waiting.push({resolve,reject}));
  const send = value => socket.write(JSON.stringify(value)+'\n');
  const hello = await read(), nonce = randomBytes(32).toString('hex'), value = hello.challenge + ':' + nonce;
  const proof = role => createHmac('sha256',info.token).update(role + ':' + value).digest('hex');
  send({nonce,auth:proof('client')}); assert.equal((await read()).authenticated,proof('server'));
  return {read,send,close:() => socket.destroy()};
}
async function rpc(value) { const socket = await channel(); try { socket.send(value); return await socket.read(); } finally { socket.close(); } }
const http = createServer(async (request,response) => {
  try {
    const path = request.url === '/' ? '/index.html' : request.url;
    if (!/^\/[\w/.-]+$/.test(path) || path.includes('..')) throw new Error();
    response.setHeader('Content-Type', path.endsWith('.js') ? 'application/javascript' : path.endsWith('.css') ? 'text/css' : 'text/html');
    response.end(await readFile(assets + path));
  } catch { response.writeHead(404).end(); }
});
await new Promise(resolve => http.listen(0,'127.0.0.1',resolve));
const browser = await chromium.launch({headless:true, executablePath:process.env.CHROMIUM_PATH});
const page = await browser.newPage({viewport:{width:393,height:700},deviceScaleFactor:1});
let active = null, draft = {}, lastSession = '', generation = 0;
const errors = [];
page.on('pageerror', e => errors.push(e.message));
await page.exposeFunction('nativeRequest', async (id,payload) => {
  const result = await rpc(JSON.parse(payload));
  await page.evaluate(({id,result}) => workspace.reply(id,result),{id,result});
});
await page.exposeFunction('nativeStart', async () => {
  const state = await rpc({op:'status'}); await page.evaluate(state => workspace.connected(state),state);
});
await page.exposeFunction('nativeAttach', async payload => {
  const own = ++generation; active?.close();
  const connection = await channel(); if (own !== generation) { connection.close(); return; }
  active = connection; const request = JSON.parse(payload); if (request.name) lastSession = request.name;
  connection.send(request); const ready = await connection.read();
  await page.evaluate(({ready,display}) => display ? workspace.displayAttached() : workspace.attached(ready),{ready,display:request.op==='display'});
  (async () => { try { while (own === generation) { const event = await connection.read(); await page.evaluate(event => workspace.output(event),event); } } catch {} })();
});
await page.exposeFunction('nativeInput', payload => active?.send(JSON.parse(payload)));
await page.exposeFunction('nativeDetach', () => { generation++; active?.close(); active = null; });
await page.exposeFunction('nativeDraft', value => { draft = JSON.parse(value); });
await page.addInitScript(() => { window.Strata = {request:nativeRequest,start:nativeStart,prepare:()=>{},attach:nativeAttach,input:nativeInput,detach:nativeDetach,copy:()=>{},paste:()=>{},draft:nativeDraft}; });
const theme = {background:'#1a1b26',foreground:'#c0caf5',lighter_background:'#24283b',accent:'#7aa2f7',light_foreground:'#a9b1d6',mode:'dark'};
['#15161e','#f7768e','#9ece6a','#e0af68','#7aa2f7','#bb9af7','#7dcfff','#a9b1d6','#414868','#f7768e','#9ece6a','#e0af68','#7aa2f7','#bb9af7','#7dcfff','#c0caf5'].forEach((value,index)=>theme['color'+index]=value);
async function open(section) {
  generation++; active?.close(); active = null;
  await page.goto('http://127.0.0.1:'+http.address().port);
  await page.evaluate(options=>workspace.init(options),{theme,page:section,session:lastSession,draft});
  await page.waitForFunction(()=>document.querySelector('#identity').textContent.startsWith('Native'));
}
async function prompt(button, text) {
  await page.click(button); await page.locator('#prompt-value').fill(text); await page.locator('#prompt button[value=ok]').click();
}
try {
  await open('Terminal');
  await page.waitForFunction(()=>document.querySelector('#sessions').value === 'main');
  await page.locator('.xterm-helper-textarea').focus();
  await page.keyboard.type("printf 'FRONTEND_OK\\n'; python3 -c 'print(6 * 7)'",{delay:2}); await page.keyboard.press('Enter');
  await page.waitForFunction(()=>document.querySelector('.xterm-screen').textContent.includes('42'));
  await page.waitForTimeout(300);
  await page.screenshot({path:resolve(artifacts,'terminal.png')});
  await page.setViewportSize({width:700,height:393});
  await page.waitForTimeout(200);
  await page.locator('.xterm-helper-textarea').focus(); await page.keyboard.type('stty size'); await page.keyboard.press('Enter');
  await page.screenshot({path:resolve(artifacts,'terminal-landscape.png')});
  await page.setViewportSize({width:393,height:700});
  await open('Files');
  await prompt('#new-file','frontend.py');
  await page.fill('#file-text','print("saved through Strata")\n');
  await page.click('#save-file'); await page.waitForFunction(()=>document.querySelector('#notice').textContent.includes('Saved'));
  assert.equal(await readFile(resolve(info.home,'frontend.py'),'utf8'),'print("saved through Strata")\n');
  await page.fill('#file-text','unsaved draft survives navigation');
  await open('Terminal'); await open('Files');
  assert.equal(await page.locator('#file-text').inputValue(),'unsaved draft survives navigation');
  await page.screenshot({path:resolve(artifacts,'files.png')});
  await open('Services');
  await page.fill('#service-name','browser-test'); await page.fill('#service-script','echo BROWSER_SERVICE; exec sleep 90');
  await page.click('#service-form button'); await page.getByRole('button',{name:'Start',exact:true}).click();
  await page.waitForFunction(()=>document.querySelector('#services').textContent.includes('running'));
  await page.getByRole('button',{name:'Log',exact:true}).click();
  await page.waitForFunction(()=>document.querySelector('#service-log').textContent.includes('BROWSER_SERVICE'));
  await page.screenshot({path:resolve(artifacts,'services.png')});
  await page.getByRole('button',{name:'Stop',exact:true}).click();
  await page.waitForFunction(()=>document.querySelector('#services').textContent.includes('stopped'));
  if (process.env.STRATA_X11_PROBE) {
    await open('Desktop'); await page.click('#start-desktop');
    await page.waitForFunction(()=>document.querySelector('#notice').textContent.includes('Graphical session connected'));
    await page.fill('#gui-command',process.env.STRATA_X11_PROBE); await page.click('#gui-form button:first-of-type');
    await page.waitForFunction(()=> {
      const canvas = document.querySelector('#desktop-display canvas');
      if (!canvas) return false;
      const color = canvas.getContext('2d').getImageData(30,70,1,1).data;
      return color[0]===32 && color[1]===80 && color[2]===80;
    });
    const canvas = page.locator('#desktop-display canvas');
    await canvas.click({position:{x:40,y:80}});
    await page.click('#desktop-keyboard'); await page.locator('#desktop-input').pressSequentially('native');
    await page.waitForTimeout(300);
    assert.equal(await readFile(resolve(info.home,'gui-pointer'),'utf8'),'clicked');
    assert.equal(await readFile(resolve(info.home,'gui-keys'),'utf8'),'native');
    await page.screenshot({path:resolve(artifacts,'desktop.png')});
    await prompt('#stop-desktop','stop');
    console.log('PASS: actual Xvnc/native X11 window, framebuffer pixels, pointer events and keyboard input through the embedded display.');
  }
  assert.deepEqual(errors,[]);
  console.log('PASS: native terminal/interpreter, resize, files/save/draft recovery, services/start/log/stop; phone-sized browser frontend.');
} finally {
  generation++; for (const connection of connections) connection.destroy();
  await browser.close(); http.close();
  spawn('tmux',['-S',resolve(info.home,'.state/tmux.sock'),'kill-server']);
  python.kill('SIGINT');
}
