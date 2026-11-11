# Knight for Desktop

The website cannot listen over your whole computer. This app can. It is the Knight website running inside a desktop shell that:
- lives in the system tray and keeps **"Hey Knight"** listening while the window is hidden (offline speech engine, nothing is sent anywhere),
- asks the operating system for the microphone,
- can open apps, change the volume, lock the screen and take screenshots **only if you switch those on** in Knight setup,
- starts when you sign in to your computer, if you choose.

## Build the installer (Windows .exe, macOS .dmg, Linux AppImage/.deb)
You need Node.js 20+. The `desktop/` folder must sit next to `frontend/`.

    cd desktop
    npm install
    npm run dist          # installers appear in desktop/dist

Try it without building: `npm start`.

Before building, copy `frontend/config.example.js` to `frontend/config.js` and set `apiBase`.

## Let GitHub build all three for you
`.github/workflows/desktop.yml` builds Windows, macOS and Linux installers on every push to `main`
(Actions tab > Knight Desktop > Artifacts). Push a tag like `v1.0.0` and it attaches them to a GitHub Release.
Put that Release page in `frontend/config.js` as `desktopDownloadUrl` and the website's "Download for desktop" button points there.

## What it can and cannot do
It can use the microphone, the tray, the global shortcut (Ctrl/Cmd+Shift+K for push-to-talk), and the fixed list of jobs in `main.js`.
It cannot read your files or other apps, install software, or do anything outside that list. Windows and macOS may show an
"unknown publisher" warning until you sign the installer with your own certificate.
The first time Hey Knight starts it downloads a small speech model (about 40 MB) and then works offline.
