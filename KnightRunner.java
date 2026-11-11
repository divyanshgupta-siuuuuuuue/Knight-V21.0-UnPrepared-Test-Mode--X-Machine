package com.knight.backend;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/* ============================================================================
   KNIGHT COMPILER — POST /api/run  { language, code, stdin }
   Does NOT execute anything on your server. It forwards the code to a sandboxed
   Judge0 service (default: the public https://ce.judge0.com) and returns the output.
   Point it at your own Judge0 with the JUDGE0_URL (and optional JUDGE0_TOKEN) env vars.
============================================================================ */
@RestController
@RequestMapping("/api")
class RunController {
    private static final String BASE = Optional.ofNullable(System.getenv("JUDGE0_URL"))
            .filter(s -> !s.isBlank()).orElse("https://ce.judge0.com").replaceAll("/+$", "");
    private static final String TOKEN = System.getenv("JUDGE0_TOKEN");
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final ObjectMapper json = new ObjectMapper();
    private final Map<String, Long> langIds = new ConcurrentHashMap<>();   // our key -> Judge0 language id
    private final Map<String, long[]> hits = new ConcurrentHashMap<>();    // ip -> [windowStart, count]

    /** our language key -> regex over Judge0 language names */
    private static final Map<String, Pattern> NAMES = new LinkedHashMap<>();
    static {
        String[][] p = {
            {"python", "^Python \\(3"}, {"javascript", "^JavaScript"}, {"typescript", "^TypeScript"},
            {"java", "^Java \\("}, {"c", "^C \\("}, {"cpp", "^C\\+\\+ \\("}, {"csharp", "^C# \\("},
            {"go", "^Go \\("}, {"rust", "^Rust"}, {"ruby", "^Ruby"}, {"php", "^PHP"}, {"swift", "^Swift"},
            {"kotlin", "^Kotlin"}, {"scala", "^Scala"}, {"bash", "^Bash"}, {"lua", "^Lua"}, {"perl", "^Perl"},
            {"r", "^R \\("}, {"haskell", "^Haskell"}, {"dart", "^Dart"}, {"sql", "^SQL"}, {"fsharp", "^F#"},
            {"pascal", "^Pascal"}, {"fortran", "^Fortran"}, {"cobol", "^COBOL"}, {"ocaml", "^OCaml"},
            {"elixir", "^Elixir"}, {"groovy", "^Groovy"}, {"prolog", "^Prolog"}, {"assembly", "^Assembly"}
        };
        for (String[] e : p) NAMES.put(e[0], Pattern.compile(e[1]));
    }

    public static class RunRequest { public String language, code, stdin; }
    public static class PinRequest { public String pin; }

    /* ---------- terminal PIN ----------
       The PIN lives only in the KNIGHT_TERMINAL_PIN setting on the server (never in a file or the website).
       Unlocking gives a 30-minute token that /api/run requires. Five wrong tries lock that address out for 15 minutes.
       If KNIGHT_TERMINAL_PIN is not set, the terminal stays locked: secure by default. */
    private static final long TOKEN_MS = 30L * 60_000, LOCKOUT_MS = 15L * 60_000;
    private final Map<String, Long> tokens = new ConcurrentHashMap<>();            // token -> expiry
    private final Map<String, long[]> pinFails = new ConcurrentHashMap<>();         // ip -> [failures, lockedUntil]
    private final java.security.SecureRandom rng = new java.security.SecureRandom();

    private static byte[] sha(String s) {
        try { return java.security.MessageDigest.getInstance("SHA-256").digest(s.getBytes(java.nio.charset.StandardCharsets.UTF_8)); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }
    private boolean tokenOk(String t) {
        if (t == null || t.isBlank()) return false;
        Long exp = tokens.get(t);
        if (exp == null) return false;
        if (exp < System.currentTimeMillis()) { tokens.remove(t); return false; }
        return true;
    }

    @PostMapping("/terminal/unlock")
    ResponseEntity<?> unlock(@RequestBody PinRequest req, @RequestHeader(value = "X-Forwarded-For", required = false) String xff) {
        String expected = System.getenv("KNIGHT_TERMINAL_PIN");
        if (expected == null || expected.isBlank())
            return ResponseEntity.status(503).body(Map.of("error", "The terminal PIN is not set on the server yet. Add KNIGHT_TERMINAL_PIN in the Render dashboard."));
        String ip = xff == null ? "local" : xff.split(",")[0].trim();
        long now = System.currentTimeMillis();
        long[] f = pinFails.computeIfAbsent(ip, k -> new long[]{0, 0});
        synchronized (f) {
            if (f[1] > now) return ResponseEntity.status(429).body(Map.of("error", "Too many wrong PINs. Try again in " + ((f[1] - now) / 60000 + 1) + " minutes."));
            String given = req == null || req.pin == null ? "" : req.pin;
            if (!java.security.MessageDigest.isEqual(sha(given), sha(expected))) {
                if (++f[0] >= 5) { f[1] = now + LOCKOUT_MS; f[0] = 0; }
                try { Thread.sleep(700); } catch (InterruptedException ignored) { }          // slows guessing
                return ResponseEntity.status(401).body(Map.of("error", "Wrong PIN."));
            }
            f[0] = 0;
        }
        byte[] raw = new byte[32]; rng.nextBytes(raw);
        StringBuilder sb = new StringBuilder(); for (byte b : raw) sb.append(String.format("%02x", b));
        tokens.values().removeIf(e -> e < now);
        tokens.put(sb.toString(), now + TOKEN_MS);
        return ResponseEntity.ok(Map.of("token", sb.toString(), "expiresInSeconds", TOKEN_MS / 1000));
    }

    @PostMapping("/terminal/lock")
    ResponseEntity<?> lock(@RequestHeader(value = "X-Terminal-Token", required = false) String token) {
        if (token != null) tokens.remove(token);
        return ResponseEntity.ok(Map.of("locked", true));
    }

    @PostMapping("/run")
    ResponseEntity<?> run(@RequestBody RunRequest req,
                                            @RequestHeader(value = "X-Forwarded-For", required = false) String xff,
                                            @RequestHeader(value = "X-Terminal-Token", required = false) String token) {
        if (!tokenOk(token)) return ResponseEntity.status(401).body(Map.of("error", "The terminal is locked. Enter the PIN to unlock it."));
        String ip = xff == null ? "local" : xff.split(",")[0].trim();
        if (tooMany(ip)) return ResponseEntity.status(429).body(Map.of("error", "Too many runs. Wait a few seconds."));
        if (req == null || req.code == null || req.code.length() > 100_000 || req.language == null)
            return ResponseEntity.badRequest().body(Map.of("error", "Send { language, code }. Code max 100 KB."));
        try {
            long id = languageId(req.language.toLowerCase());
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("language_id", id); body.put("source_code", req.code);
            body.put("stdin", req.stdin == null ? "" : req.stdin);
            body.put("cpu_time_limit", 5); body.put("wall_time_limit", 10);
            HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(BASE + "/submissions?base64_encoded=false&wait=true"))
                    .timeout(Duration.ofSeconds(40)).header("Content-Type", "application/json");
            if (TOKEN != null && !TOKEN.isBlank()) b.header("X-Auth-Token", TOKEN);
            HttpResponse<String> r = http.send(b.POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build(),
                    HttpResponse.BodyHandlers.ofString());
            if (r.statusCode() >= 400) return ResponseEntity.status(502).body(Map.of("error", "Compiler service answered " + r.statusCode()));
            JsonNode n = json.readTree(r.body());
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("stdout", n.path("stdout").asText("")); out.put("stderr", n.path("stderr").asText(""));
            out.put("compile", n.path("compile_output").asText(""));
            out.put("status", n.path("status").path("description").asText(""));
            out.put("time", n.path("time").asText("")); out.put("memory", n.path("memory").asInt(0));
            return ResponseEntity.ok(out);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(502).body(Map.of("error", "Could not reach the compiler service."));
        }
    }

    private long languageId(String key) throws Exception {
        Pattern pat = NAMES.get(key);
        if (pat == null) throw new IllegalArgumentException("Unsupported language: " + key);
        Long cached = langIds.get(key);
        if (cached != null) return cached;
        HttpResponse<String> r = http.send(HttpRequest.newBuilder(URI.create(BASE + "/languages"))
                .timeout(Duration.ofSeconds(15)).GET().build(), HttpResponse.BodyHandlers.ofString());
        long best = -1;
        for (JsonNode l : json.readTree(r.body())) {
            if (pat.matcher(l.path("name").asText("")).find()) best = Math.max(best, l.path("id").asLong());
        }
        if (best < 0) throw new IllegalArgumentException("The compiler service has no runtime for " + key + ".");
        langIds.put(key, best);
        return best;
    }

    private boolean tooMany(String ip) {
        long now = System.currentTimeMillis();
        long[] w = hits.computeIfAbsent(ip, k -> new long[]{now, 0});
        synchronized (w) {
            if (now - w[0] > 10_000) { w[0] = now; w[1] = 0; }
            return ++w[1] > 8;     // 8 runs per 10 seconds per IP
        }
    }
}
