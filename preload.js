// The only bridge between the Knight website and your computer. Every call is checked again in main.js.
const { contextBridge, ipcRenderer } = require('electron');
contextBridge.exposeInMainWorld('knightDesktop', {
  isDesktop: true,
  platform: process.platform,
  modelUrl: 'https://ccoreilly.github.io/vosk-browser/models/vosk-model-small-en-us-0.15.tar.gz',
  getState: () => ipcRenderer.invoke('kd:state'),
  setPref: (key, value) => ipcRenderer.invoke('kd:setPref', key, value),
  askMic: () => ipcRenderer.invoke('kd:askMic'),
  run: (op, arg) => ipcRenderer.invoke('kd:run', op, arg),
  onHotkey: (cb) => ipcRenderer.on('kd:hotkey', () => cb()),
  onWakeToggle: (cb) => ipcRenderer.on('kd:wakeToggle', (_e, on) => cb(on)),
});
