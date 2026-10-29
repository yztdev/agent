package dev.terminalagent.server.service;
import java.util.*;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class AuthServiceTest {
 @Test void invalidPasswordIsRejected() { JdbcTemplate db=mock(JdbcTemplate.class); HttpServletRequest req=mock(HttpServletRequest.class); when(req.getRemoteAddr()).thenReturn("127.0.0.1"); when(db.queryForList(anyString(),any(Object.class))).thenReturn(List.of(Map.of("id",UUID.randomUUID(),"status","ACTIVE","password_hash",new BCryptPasswordEncoder().encode("correct-password")))); var a=new AuthService(db); assertThrows(ResponseStatusException.class,()->a.login(new AuthService.Credentials("user","wrong-password"),req)); verify(db,never()).update(anyString(),any(),any(),any(),any()); }
 @Test void missingAuthorizationIsRejected() { var a=new AuthService(mock(JdbcTemplate.class)); var req=mock(HttpServletRequest.class); assertThrows(ResponseStatusException.class,()->a.me(req)); }
 @Test void loginAttemptsAreRateLimited() { JdbcTemplate db=mock(JdbcTemplate.class); var req=mock(HttpServletRequest.class); when(req.getRemoteAddr()).thenReturn("127.0.0.1"); when(db.queryForList(anyString(),any(Object.class))).thenReturn(List.of()); var a=new AuthService(db); for(int i=0;i<10;i++) assertThrows(ResponseStatusException.class,()->a.login(new AuthService.Credentials("user","bad"),req)); var e=assertThrows(ResponseStatusException.class,()->a.login(new AuthService.Credentials("user","bad"),req)); assertEquals(429,e.getStatusCode().value()); }
}
