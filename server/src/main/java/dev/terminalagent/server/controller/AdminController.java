package dev.terminalagent.server.controller;

import dev.terminalagent.server.service.AuthService;
import dev.terminalagent.server.service.QuotaService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
class AdminController {
    private final AuthService auth;
    private final QuotaService quota;
    private final JdbcTemplate database;

    AdminController(AuthService auth, QuotaService quota, JdbcTemplate database) {
        this.auth = auth;
        this.quota = quota;
        this.database = database;
    }

    private AuthService.UserInfo requireAdmin(HttpServletRequest request) {
        var user = auth.current(request);
        if (!user.role().equals("ADMIN")) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        return user;
    }

    record Adjustment(long quota, String reason) {}
    record Reconciliation(long input, long output, String reason) {}

    @GetMapping("/api/quota")
    Object balance(HttpServletRequest request) {
        return quota.balance(auth.current(request).id());
    }

    @GetMapping("/api/admin/users")
    Object users(HttpServletRequest request,
                 @RequestParam(name = "search", defaultValue = "") String search,
                 @RequestParam(name = "offset", defaultValue = "0") int offset) {
        requireAdmin(request);
        if (offset < 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        return database.queryForList("""
            select u.id, u.username, u.status, coalesce(a.quota_total, 0) quota_total,
                   coalesce(a.settled_total, 0) settled_total,
                   coalesce(a.reserved_total, 0) reserved_total,
                   coalesce(a.quota_total, 0) - coalesce(a.settled_total, 0) -
                       coalesce(a.reserved_total, 0) available
            from users u left join token_accounts a on a.user_id = u.id
            where u.username ilike ? order by u.username limit 50 offset ?
            """, "%" + search + "%", offset);
    }

    @GetMapping("/api/admin/users/{id}/requests")
    Object requests(HttpServletRequest request, @PathVariable("id") UUID userId) {
        requireAdmin(request);
        return database.queryForList("""
            select id, model, reserved_tokens, input_tokens, output_tokens, status, created_at, updated_at
            from token_requests where user_id = ? order by created_at desc limit 100
            """, userId);
    }

    @PostMapping("/api/admin/users/{id}/quota")
    void adjust(HttpServletRequest request, @PathVariable("id") UUID userId, @RequestBody Adjustment input) {
        var admin = requireAdmin(request);
        quota.adjust(admin.id(), userId, input.quota(), input.reason());
    }

    @PostMapping("/api/admin/requests/{id}/resolve")
    void resolve(HttpServletRequest request, @PathVariable("id") UUID requestId, @RequestBody Reconciliation input) {
        var admin = requireAdmin(request);
        quota.resolve(requestId, input.input(), input.output(), admin.id(), input.reason());
    }
}
