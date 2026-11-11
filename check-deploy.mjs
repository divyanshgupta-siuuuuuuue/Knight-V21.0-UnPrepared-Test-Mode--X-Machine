// Checks a deployed Knight backend and tells you exactly what to fix.
//   node scripts/check-deploy.mjs https://your-backend.onrender.com https://your-site.workers.dev
// Needs Node 20+. The first request can take a minute while a free server wakes up.
const [backend, site] = process.argv.slice(2);
if (!backend) { console.log('Usage: node scripts/check-deploy.mjs <backend-url> [site-url]'); process.exit(1); }
const base = backend.replace(/\/+$/, ''), origin = site ? site.replace(/\/+$/, '') : '';
let bad = 0;
const ok = (m) => console.log('  PASS  ' + m);
const no = (m, fix) => { bad++; console.log('  FAIL  ' + m + (fix ? '\n        -> ' + fix : '')); };
const timeout = (ms) => AbortSignal.timeout(ms);

console.log('Checking ' + base + (origin ? ' for the site ' + origin : '') + '\n');
if (!/^https:\/\//.test(base) && !/localhost|127\.0\.0\.1/.test(base)) no('Backend address is not https', 'Phones and https sites block http backends. Use the https:// address Render gives you.'); else ok('Backend address is secure (or local)');

let up = false;
try {
  console.log('  ...   waiting for the server (up to 90 s if it is asleep)');
  const r = await fetch(base + '/api/health', { signal: timeout(90000) });
  const j = await r.json().catch(() => ({}));
  if (r.ok && j.status === 'up') { up = true; ok('Backend is up (/api/health)'); } else no('Backend answered ' + r.status + ' on /api/health', 'Check the Render logs for a startup error (usually DATABASE_URL).');
} catch (e) { no('Cannot reach the backend', 'Is the Render service running? Open ' + base + '/api/health in a browser.'); }

if (up && origin) {
  try {
    const r = await fetch(base + '/api/health', { method: 'OPTIONS', headers: { Origin: origin, 'Access-Control-Request-Method': 'GET', 'Access-Control-Request-Headers': 'authorization' }, signal: timeout(20000) });
    const allow = r.headers.get('access-control-allow-origin');
    if (allow === origin || allow === '*') ok('CORS allows your site'); else no('CORS does not allow ' + origin, 'On Render set KNIGHT_ALLOWED_ORIGINS=' + origin + ' and redeploy.');
  } catch (e) { no('CORS check failed', String(e.message || e)); }
}
if (up) {
  try {
    const wsUrl = base.replace(/^http/, 'ws') + '/ws/knight?userId=check&username=check';
    const opened = await new Promise((res) => { try { const w = new WebSocket(wsUrl); const t = setTimeout(() => { try { w.close(); } catch {} res(false); }, 12000); w.onopen = () => { clearTimeout(t); w.close(); res(true); }; w.onerror = () => { clearTimeout(t); res(false); }; } catch { res(false); } });
    opened ? ok('Live multiplayer WebSocket works') : no('Live multiplayer WebSocket failed', 'Add spring-boot-starter-websocket to pom.xml (see backend/pom.xml.example) and redeploy.');
  } catch (e) { no('WebSocket check failed', String(e.message || e)); }
  try {
    const r = await fetch(base + '/api/terminal/unlock', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: '{"pin":""}', signal: timeout(20000) });
    if (r.status === 401) ok('Terminal PIN is configured (wrong PIN is refused)');
    else if (r.status === 503) no('Terminal PIN is not set', 'Add KNIGHT_TERMINAL_PIN in the Render dashboard. Until then the terminal stays locked.');
    else no('Terminal unlock answered ' + r.status, 'Redeploy the latest backend (it needs KnightRunner.java).');
  } catch (e) { no('Terminal check failed', String(e.message || e)); }
  try {
    const r = await fetch(base + '/api/run', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: '{"language":"python","code":"print(1)"}', signal: timeout(20000) });
    r.status === 401 ? ok('Compiler refuses to run without the PIN token') : no('Compiler answered ' + r.status + ' without a token', 'It must answer 401. Redeploy the latest KnightRunner.java.');
  } catch (e) { no('Compiler check failed', String(e.message || e)); }
  try {
    const r = await fetch(base + '/api/auth/business', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: '{"code":"definitely-wrong"}', signal: timeout(20000) });
    r.status === 401 ? ok('Business access refuses a wrong code') : no('Business access answered ' + r.status, 'Redeploy the latest backend.');
  } catch (e) { no('Business access check failed', String(e.message || e)); }
}
console.log('\n' + (bad ? bad + ' problem(s) found. Fix the lines marked FAIL and run this again.' : 'Everything checks out.'));
process.exit(bad ? 1 : 0);
