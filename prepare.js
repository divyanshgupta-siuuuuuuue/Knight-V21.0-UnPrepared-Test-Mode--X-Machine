// Copies the Knight website (../frontend) into ./app and adds the offline speech engine.
const fs = require('fs'), path = require('path');
const src = path.join(__dirname, '..', '..', 'frontend'), dst = path.join(__dirname, '..', 'app');
if (!fs.existsSync(src)) { console.error('Cannot find ../frontend. Run this from the knight project (desktop/ must sit beside frontend/).'); process.exit(1); }
fs.rmSync(dst, { recursive: true, force: true });
fs.cpSync(src, dst, { recursive: true });
fs.rmSync(path.join(dst, 'sw.js'), { force: true });            // the desktop app does not need the web service worker
fs.mkdirSync(path.join(dst, 'vendor'), { recursive: true });
const vosk = path.join(__dirname, '..', 'node_modules', 'vosk-browser', 'dist', 'vosk.js');
if (fs.existsSync(vosk)) fs.copyFileSync(vosk, path.join(dst, 'vendor', 'vosk.js'));
else console.warn('vosk-browser is not installed yet. Run "npm install" first, or Hey Knight will use push-to-talk only.');
if (!fs.existsSync(path.join(dst, 'config.js'))) console.warn('No frontend/config.js found. Copy config.example.js to config.js and set apiBase before building.');
console.log('App files ready in', dst);
