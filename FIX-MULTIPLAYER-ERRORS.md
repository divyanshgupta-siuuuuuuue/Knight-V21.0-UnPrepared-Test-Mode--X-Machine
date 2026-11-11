# Multiplayer errors: cause and fix (backend port 8080)
Run `node scripts/check-deploy.mjs https://YOUR-BACKEND https://YOUR-SITE` first. It names the problem.
1. ERR_CONNECTION_REFUSED -> backend not running / wrong port / websocket dependency missing (see backend/pom.xml.example).
2. CORS blocked -> localhost (any port) is allowed; for others set KNIGHT_ALLOWED_ORIGINS to your site URL.
3. EADDRINUSE -> Mac/Linux: lsof -ti:8080 | xargs kill -9 ; Windows: netstat -ano | findstr :8080 then taskkill /PID <pid> /F ; or PORT=8081 mvn spring-boot:run
4. Transport close / ping timeout -> client pings every 25s and reconnects automatically.
5. Clients overriding each other -> connections keyed by session id, people by Account ID.
