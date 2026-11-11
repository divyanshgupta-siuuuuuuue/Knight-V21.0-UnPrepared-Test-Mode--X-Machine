/* Knight for Desktop. Runs in the tray, keeps "Hey Knight" listening, and can act on your computer ONLY for the things
   you switch on in Setup. Nothing here runs text from the internet as a command: every action is a fixed, allow-listed job. */
const { app, BrowserWindow, Tray, Menu, session, shell, systemPreferences, globalShortcut, desktopCapturer, nativeImage, ipcMain } = require('electron');
const path = require('path'), fs = require('fs'), http = require('http'), { execFile } = require('child_process');

if (!app.requestSingleInstanceLock()) { app.quit(); }
let win = null, tray = null, quitting = false, origin = '';
const PORT = 47831;                                    // fixed so the app keeps the same saved data every launch
const prefsFile = () => path.join(app.getPath('userData'), 'knight-prefs.json');
const DEFAULTS = { background: false, autostart: false, control: false, screenshots: false };
function loadPrefs() { try { return { ...DEFAULTS, ...JSON.parse(fs.readFileSync(prefsFile(), 'utf8')) }; } catch (e) { return { ...DEFAULTS }; } }
function savePrefs(p) { try { fs.writeFileSync(prefsFile(), JSON.stringify(p, null, 2)); } catch (e) {} }
let prefs = DEFAULTS;

/* serve the bundled website from 127.0.0.1 (a secure origin: the microphone and the backend's CORS rules both work) */
function startServer() {
  const root = path.join(__dirname, 'app');
  const types = { '.html': 'text/html', '.js': 'text/javascript', '.css': 'text/css', '.json': 'application/json', '.svg': 'image/svg+xml', '.png': 'image/png', '.ico': 'image/x-icon', '.webmanifest': 'application/manifest+json' };
  const server = http.createServer((req, res) => {
    let p = decodeURIComponent((req.url || '/').split('?')[0]); if (p === '/') p = '/index.html';
    const file = path.normalize(path.join(root, p));
    if (!file.startsWith(root)) { res.writeHead(403); return res.end(); }          // no path traversal
    fs.readFile(file, (err, data) => { if (err) { res.writeHead(404); return res.end('Not found'); } res.writeHead(200, { 'Content-Type': types[path.extname(file)] || 'application/octet-stream', 'Cache-Control': 'no-cache' }); res.end(data); });
  });
  return new Promise((resolve) => {
    const tryPort = (p) => { server.once('error', () => tryPort(p + 1)); server.listen(p, '127.0.0.1', () => resolve('http://127.0.0.1:' + p)); };
    tryPort(PORT);
  });
}

function createWindow(startHidden) {
  win = new BrowserWindow({
    width: 1180, height: 820, minWidth: 420, minHeight: 600, show: !startHidden, backgroundColor: '#100f11', title: 'Knight', icon: path.join(__dirname, 'build', 'icon.png'),
    webPreferences: { preload: path.join(__dirname, 'preload.js'), contextIsolation: true, nodeIntegration: false, sandbox: true, backgroundThrottling: false },   // keep listening while hidden
  });
  win.setMenuBarVisibility(false);
  win.loadURL(process.env.KNIGHT_URL || origin + '/index.html#/app');
  win.webContents.setWindowOpenHandler(({ url }) => { if (/^https?:\/\//.test(url)) shell.openExternal(url); return { action: 'deny' }; });
  win.webContents.on('will-navigate', (e, url) => { if (!url.startsWith(origin) && !(process.env.KNIGHT_URL && url.startsWith(process.env.KNIGHT_URL))) { e.preventDefault(); if (/^https?:\/\//.test(url)) shell.openExternal(url); } });
  win.on('close', (e) => { if (!quitting && prefs.background) { e.preventDefault(); win.hide(); } });     // keep running in the tray
}
function showWin() { if (!win) return; if (win.isMinimized()) win.restore(); win.show(); win.focus(); }

function buildTray() {
  tray = new Tray(nativeImage.createFromPath(path.join(__dirname, 'build', 'icon.png')).resize({ width: 18, height: 18 }));
  tray.setToolTip('Knight');
  const rebuild = () => tray.setContextMenu(Menu.buildFromTemplate([
    { label: 'Open Knight', click: showWin },
    { label: 'Hey Knight over my device', type: 'checkbox', checked: prefs.background, click: (i) => setPref('background', i.checked) },
    { type: 'separator' },
    { label: 'Quit Knight', click: () => { quitting = true; app.quit(); } },
  ]));
  rebuild(); tray.on('click', showWin); tray.rebuild = rebuild;
}

function setPref(key, value) {
  if (!(key in DEFAULTS)) return prefs;
  prefs[key] = !!value; savePrefs(prefs);
  if (key === 'autostart') app.setLoginItemSettings({ openAtLogin: prefs.autostart, args: ['--hidden'] });
  if (key === 'background' && win) win.webContents.send('kd:wakeToggle', prefs.background);
  if (tray && tray.rebuild) tray.rebuild();
  return prefs;
}

/* ---- the only things Knight can do to your computer. Each needs the matching switch in Setup. ---- */
const APPS = {
  win32: { calculator: ['calc.exe'], notepad: ['notepad.exe'], files: ['explorer.exe'], terminal: ['cmd.exe', ['/c', 'start', 'cmd.exe']], settings: null },
  darwin: { calculator: ['open', ['-a', 'Calculator']], notepad: ['open', ['-a', 'TextEdit']], files: ['open', [require('os').homedir()]], terminal: ['open', ['-a', 'Terminal']], settings: ['open', ['-a', 'System Settings']] },
  linux: { calculator: ['gnome-calculator'], notepad: ['gedit'], files: ['xdg-open', [require('os').homedir()]], terminal: ['x-terminal-emulator'], settings: ['gnome-control-center'] },
};
const run = (cmd, args) => new Promise((res) => execFile(cmd, args || [], { timeout: 8000 }, (err) => res(!err)));
async function volume(dir) {
  if (process.platform === 'win32') { const code = { up: 175, down: 174, mute: 173 }[dir]; return run('powershell', ['-NoProfile', '-Command', `(New-Object -ComObject WScript.Shell).SendKeys([char]${code})`]); }
  if (process.platform === 'darwin') { const s = { up: 'set volume output volume ((output volume of (get volume settings)) + 10)', down: 'set volume output volume ((output volume of (get volume settings)) - 10)', mute: 'set volume output muted not (output muted of (get volume settings))' }[dir]; return run('osascript', ['-e', s]); }
  return run('amixer', ['set', 'Master', { up: '5%+', down: '5%-', mute: 'toggle' }[dir]]);
}
async function doRun(op, arg) {
  if (op === 'show') { showWin(); return 'Opened Knight.'; }
  if (op === 'hide') { win && win.hide(); return 'Hidden. I am still listening.'; }
  if (op === 'screenshot') {
    if (!prefs.screenshots) return 'Screenshots are off. Turn them on in Knight setup first.';
    const { screen } = require('electron'), d = screen.getPrimaryDisplay();
    const srcs = await desktopCapturer.getSources({ types: ['screen'], thumbnailSize: { width: d.size.width * d.scaleFactor, height: d.size.height * d.scaleFactor } });
    if (!srcs.length) return 'I could not capture the screen.';
    const dir = path.join(app.getPath('pictures'), 'Knight'); fs.mkdirSync(dir, { recursive: true });
    const file = path.join(dir, 'screenshot-' + new Date().toISOString().replace(/[:.]/g, '-') + '.png'); fs.writeFileSync(file, srcs[0].thumbnail.toPNG());
    shell.showItemInFolder(file); return 'Saved a screenshot to your Pictures folder.';
  }
  if (!prefs.control) return 'Computer control is off. Turn on “Let Knight open apps, change volume and lock the screen” in setup.';
  if (op === 'open_url') { if (!/^https?:\/\/[^\s]+$/.test(String(arg))) return 'That is not a web address I can open.'; await shell.openExternal(arg); return 'Opened it in your browser.'; }
  if (op === 'open_app') {
    const entry = (APPS[process.platform] || {})[arg];
    if (arg === 'settings' && process.platform === 'win32') { await shell.openExternal('ms-settings:'); return 'Opened Settings.'; }
    if (!entry) return 'I do not know how to open ' + arg + ' on this computer.';
    const ok = await run(entry[0], entry[1]); return ok ? 'Opened ' + arg + '.' : 'I could not open ' + arg + '.';
  }
  if (op === 'volume') { if (!['up', 'down', 'mute'].includes(arg)) return 'Say volume up, down or mute.'; return (await volume(arg)) ? 'Volume ' + arg + '.' : 'I could not change the volume.'; }
  if (op === 'lock') {
    const ok = process.platform === 'win32' ? await run('rundll32.exe', ['user32.dll,LockWorkStation']) : process.platform === 'darwin' ? await run('pmset', ['displaysleepnow']) : await run('loginctl', ['lock-session']);
    return ok ? 'Locked.' : 'I could not lock the screen.';
  }
  return 'I cannot do that.';
}

ipcMain.handle('kd:state', () => ({ prefs, platform: process.platform, version: app.getVersion(), mic: process.platform === 'darwin' ? systemPreferences.getMediaAccessStatus('microphone') : 'unknown' }));
ipcMain.handle('kd:setPref', (_e, k, v) => setPref(k, v));
ipcMain.handle('kd:askMic', async () => { if (process.platform === 'darwin') return (await systemPreferences.askForMediaAccess('microphone')) ? 'granted' : 'denied'; return 'unknown'; });
ipcMain.handle('kd:run', (_e, op, arg) => doRun(String(op), arg == null ? '' : String(arg)).catch(() => 'That did not work.'));

app.on('second-instance', showWin);
app.whenReady().then(async () => {
  prefs = loadPrefs();
  origin = await startServer();
  // Only our own pages may use the microphone and friends.
  const allowed = ['media', 'notifications', 'clipboard-read', 'clipboard-sanitized-write', 'geolocation', 'fullscreen'];
  session.defaultSession.setPermissionRequestHandler((wc, perm, cb) => cb(allowed.includes(perm) && (wc.getURL().startsWith(origin) || wc.getURL().startsWith(process.env.KNIGHT_URL || '\u0000'))));
  session.defaultSession.setPermissionCheckHandler((wc, perm) => allowed.includes(perm));
  createWindow(process.argv.includes('--hidden') && prefs.background);
  buildTray();
  globalShortcut.register('CommandOrControl+Shift+K', () => { showWin(); win && win.webContents.send('kd:hotkey'); });
});
app.on('before-quit', () => { quitting = true; });
app.on('will-quit', () => globalShortcut.unregisterAll());
app.on('window-all-closed', () => { if (!prefs.background) app.quit(); });
