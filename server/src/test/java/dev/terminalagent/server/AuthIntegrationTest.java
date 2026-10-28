package dev.terminalagent.server;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import com.fasterxml.jackson.databind.ObjectMapper;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@SpringBootTest @AutoConfigureMockMvc
@TestPropertySource(properties={"server.ssl.enabled=false", "spring.datasource.url=${TEST_DATABASE_URL}", "spring.datasource.username=${TEST_DATABASE_USER}", "spring.datasource.password=${TEST_DATABASE_PASSWORD}"})
@EnabledIfEnvironmentVariable(named="TEST_DATABASE_URL", matches=".+")
class AuthIntegrationTest {
 @Autowired MockMvc mvc; @Autowired JdbcTemplate db; @Autowired ObjectMapper json;
 @BeforeEach void setup(){ db.update("delete from auth_sessions"); db.update("delete from users"); db.update("insert into users(id,username,password_hash,role,status) values (?,?,?,'ADMIN','ACTIVE')",UUID.randomUUID(),"admin",new BCryptPasswordEncoder().encode("admin-password-123")); }
 String login() throws Exception { String body=mvc.perform(post("/api/login").contentType("application/json").content("{\"username\":\"admin\",\"password\":\"admin-password-123\"}")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(); return json.readTree(body).get("token").asText(); }
 @Test void authLifecycleAndAuthorization() throws Exception { String t=login(); mvc.perform(get("/api/me").header("Authorization","Bearer "+t)).andExpect(status().isOk()).andExpect(jsonPath("$.role").value("ADMIN")); String u="{\"username\":\"alice\",\"password\":\"password-12345\"}"; mvc.perform(post("/api/users").header("Authorization","Bearer "+t).contentType("application/json").content(u)).andExpect(status().isCreated()); mvc.perform(post("/api/users").header("Authorization","Bearer "+t).contentType("application/json").content(u)).andExpect(status().isConflict()); mvc.perform(post("/api/logout").header("Authorization","Bearer "+t)).andExpect(status().isOk()); mvc.perform(get("/api/me").header("Authorization","Bearer "+t)).andExpect(status().isUnauthorized()); }
 @Test void expiryAndDisabledAccount() throws Exception { String t=login(); db.update("update auth_sessions set expires_at=now()-interval '1 second'"); mvc.perform(get("/api/me").header("Authorization","Bearer "+t)).andExpect(status().isUnauthorized()); db.update("update users set status='DISABLED'"); mvc.perform(post("/api/login").contentType("application/json").content("{\"username\":\"admin\",\"password\":\"admin-password-123\"}")).andExpect(status().isUnauthorized()); }
 @Test void passwordAndTokenAreNotStoredInPlaintext() throws Exception { String t=login(); Assertions.assertEquals(0,db.queryForObject("select count(*) from auth_sessions where token_hash=?",Integer.class,t)); Assertions.assertEquals(0,db.queryForObject("select count(*) from users where password_hash=?",Integer.class,"admin-password-123")); }
}
