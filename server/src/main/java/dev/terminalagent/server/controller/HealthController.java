package dev.terminalagent.server.controller;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
@RestController public class HealthController {
 private final JdbcTemplate db; HealthController(JdbcTemplate db){this.db=db;}
 @GetMapping("/health") public Map<String,String> health(){db.queryForObject("select 1",Integer.class); return Map.of("status","ok");}
}
