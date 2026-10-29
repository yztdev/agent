package dev.terminalagent.server.service;

import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

/** Owns quota changes. Every reservation and settlement is committed atomically. */
@Service
public class QuotaService {
    private final JdbcTemplate database;
    private final TransactionTemplate transactions;

    public QuotaService(JdbcTemplate database, TransactionTemplate transactions) {
        this.database = database;
        this.transactions = transactions;
    }

    private void ensureAccount(UUID userId) {
        database.update("insert into token_accounts(user_id) values (?) on conflict do nothing", userId);
    }

    public Map<String, Object> balance(UUID userId) {
        ensureAccount(userId);
        return database.queryForMap("""
            select quota_total, settled_total, reserved_total,
                   quota_total - settled_total - reserved_total as available
            from token_accounts where user_id = ?
            """, userId);
    }

    public UUID reserve(UUID userId, String model, long budget) {
        if (budget < 0) throw new IllegalArgumentException("Reservation cannot be negative");
        return transactions.execute(status -> {
            ensureAccount(userId);
            // Usage is tracked, but the user's remaining quota does not block requests.
            database.update("""
                update token_accounts set reserved_total = reserved_total + ?, updated_at = now()
                where user_id = ?
                """, budget, userId);
            UUID requestId = UUID.randomUUID();
            database.update("""
                insert into token_requests(id, user_id, model, reserved_tokens, status)
                values (?, ?, ?, ?, 'reserved')
                """, requestId, userId, model, budget);
            return requestId;
        });
    }

    public void settle(UUID requestId, long inputTokens, long outputTokens) {
        if (inputTokens < 0 || outputTokens < 0) throw new IllegalArgumentException("Invalid token usage");
        long total = Math.addExact(inputTokens, outputTokens);
        transactions.executeWithoutResult(status -> {
            Map<String, Object> request = database.queryForMap(
                    "select user_id, reserved_tokens, status from token_requests where id = ? for update", requestId);
            if (!request.get("status").equals("reserved") && !request.get("status").equals("pending_review")) return;
            database.update("""
                update token_accounts set reserved_total = reserved_total - ?,
                    settled_total = settled_total + ?, updated_at = now() where user_id = ?
                """, request.get("reserved_tokens"), total, request.get("user_id"));
            database.update("""
                update token_requests set status = 'settled', input_tokens = ?, output_tokens = ?,
                    updated_at = now() where id = ?
                """, inputTokens, outputTokens, requestId);
        });
    }

    /** Only use when a request is known not to have reached the model provider. */
    public void releaseBeforeSend(UUID requestId) {
        transactions.executeWithoutResult(status -> {
            Map<String, Object> request = database.queryForMap(
                "select user_id, reserved_tokens, status from token_requests where id = ? for update", requestId);
            if (!request.get("status").equals("reserved")) return;
            database.update("""
                update token_accounts set reserved_total = reserved_total - ?, updated_at = now()
                where user_id = ?
                """, request.get("reserved_tokens"), request.get("user_id"));
            database.update("update token_requests set status = 'released', updated_at = now() where id = ?", requestId);
        });
    }

    public void markPending(UUID requestId) {
        database.update("update token_requests set status = 'pending_review', updated_at = now() where id = ? and status = 'reserved'", requestId);
    }

    public void resolve(UUID requestId, long inputTokens, long outputTokens, UUID actorId, String reason) {
        validateReason(reason);
        transactions.executeWithoutResult(status -> {
            var rows = database.queryForList("select status from token_requests where id = ? for update", requestId);
            if (rows.isEmpty() || !rows.get(0).get("status").equals("pending_review")) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Request is not pending review");
            }
            settle(requestId, inputTokens, outputTokens);
            database.update("""
                insert into token_reconciliations(id, request_id, actor_user_id, input_tokens, output_tokens, reason)
                values (?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), requestId, actorId, inputTokens, outputTokens, reason);
        });
    }

    public void adjust(UUID actorId, UUID userId, long newQuota, String reason) {
        if (newQuota < 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Quota cannot be negative");
        validateReason(reason);
        transactions.executeWithoutResult(status -> {
            ensureAccount(userId);
            Long previous = database.queryForObject("select quota_total from token_accounts where user_id = ? for update", Long.class, userId);
            database.update("update token_accounts set quota_total = ?, updated_at = now() where user_id = ?", newQuota, userId);
            database.update("""
                insert into token_adjustments(id, user_id, actor_user_id, quota_before, quota_after, reason)
                values (?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), userId, actorId, previous, newQuota, reason);
        });
    }

    private static void validateReason(String reason) {
        if (reason == null || reason.isBlank() || reason.length() > 500) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A reason is required");
        }
    }
}
