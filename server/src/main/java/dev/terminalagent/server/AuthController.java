package dev.terminalagent.server;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class AuthController {
    private final JdbcTemplate db;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    private final SecureRandom random = new SecureRandom();
    // Per-instance limit; use a shared rate limiter before multi-instance deployment.
    private final Map<String, ArrayDeque<Long>> attempts = new LinkedHashMap<>();

    AuthController(JdbcTemplate db) {
        this.db = db;
    }

    record Credentials(String username, String password) {
    }

    record UserInfo(UUID id, String username, String role) {
    }

    record LoginResponse(String token, Instant expiresAt) {
    }

    private static String hash(String v) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(v.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private synchronized void limit(String ip) {
        long now = System.currentTimeMillis();
        attempts.entrySet().removeIf(e -> e.getValue().isEmpty() || e.getValue().peekLast() < now - 60000);
        if (attempts.size() >= 10000 && !attempts.containsKey(ip))
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS);
        var q = attempts.computeIfAbsent(ip, k -> new ArrayDeque<>());
        while (!q.isEmpty() && q.peekFirst() < now - 60000) q.removeFirst();
        if (q.size() >= 10) throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS);
        q.addLast(now);
    }

    @PostMapping("/api/login")
    public LoginResponse login(@RequestBody Credentials c, HttpServletRequest req) {
        limit(req.getRemoteAddr());
        if (c.username() == null || c.password() == null || c.username().length() > 100 || c.password().getBytes(StandardCharsets.UTF_8).length > 72)
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
        var rows = db.queryForList("select id,password_hash,status from users where username=?", c.username());
        if (rows.isEmpty() || !"ACTIVE".equals(rows.get(0).get("status")) || !encoder.matches(c.password(), (String) rows.get(0).get("password_hash")))
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant expiry = Instant.now().plusSeconds(28800);
        db.update("insert into auth_sessions(id,user_id,token_hash,expires_at) values (?,?,?,?)", UUID.randomUUID(), rows.get(0).get("id"), hash(token), Timestamp.from(expiry));
        return new LoginResponse(token, expiry);
    }

    private String token(HttpServletRequest req) {
        String h = req.getHeader("Authorization");
        if (h == null || !h.startsWith("Bearer ") || h.length() > 200)
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        return h.substring(7);
    }

    UserInfo current(HttpServletRequest req) {
        var result = db.query("select u.id,u.username,u.role from auth_sessions s join users u on u.id=s.user_id where s.token_hash=? and s.revoked_at is null and s.expires_at>now() and u.status='ACTIVE'", (rs, n) -> new UserInfo((UUID) rs.getObject(1), rs.getString(2), rs.getString(3)), hash(token(req)));
        if (result.isEmpty()) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        return result.get(0);
    }

    @GetMapping("/api/me")
    public UserInfo me(HttpServletRequest req) {
        return current(req);
    }

    @PostMapping("/api/logout")
    public void logout(HttpServletRequest req) {
        current(req);
        db.update("update auth_sessions set revoked_at=now() where token_hash=?", hash(token(req)));
    }

    @PostMapping("/api/users")
    @ResponseStatus(HttpStatus.CREATED)
    public void create(@RequestBody Credentials c, HttpServletRequest req) {
        if (!current(req).role().equals("ADMIN")) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        if (c.username() == null || !c.username().matches("[A-Za-z0-9_.-]{3,100}") || c.password() == null || c.password().length() < 12 || c.password().getBytes(StandardCharsets.UTF_8).length > 72)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid username or password");
        try {
            db.update("insert into users(id,username,password_hash,role,status) values (?,?,?,'USER','ACTIVE')", UUID.randomUUID(), c.username(), encoder.encode(c.password()));
        } catch (org.springframework.dao.DuplicateKeyException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Username already exists");
        }
    }
}
