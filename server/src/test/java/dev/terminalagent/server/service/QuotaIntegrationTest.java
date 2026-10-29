package dev.terminalagent.server.service;
import java.util.*; import java.util.concurrent.*;
import org.junit.jupiter.api.*; import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired; import org.springframework.boot.test.context.SpringBootTest; import org.springframework.jdbc.core.JdbcTemplate; import org.springframework.test.context.TestPropertySource;
@SpringBootTest @TestPropertySource(properties={"server.ssl.enabled=false", "spring.datasource.url=${TEST_DATABASE_URL}", "spring.datasource.username=${TEST_DATABASE_USER}", "spring.datasource.password=${TEST_DATABASE_PASSWORD}"}) @EnabledIfEnvironmentVariable(named="TEST_DATABASE_URL", matches=".+")
class QuotaIntegrationTest {
 @Autowired JdbcTemplate db; @Autowired QuotaService service; UUID user;
 @BeforeEach void setup(){user=UUID.randomUUID();db.update("insert into users(id,username,password_hash,role,status) values (?,?,?,'USER','ACTIVE')",user,"quota-"+user,"hash");service.adjust(user,user,100,"test");}
 @Test void zeroQuotaDoesNotBlockRequests() {
  service.adjust(user, user, 0, "test zero quota");
  UUID request = service.reserve(user, "test", 0);
  service.settle(request, 10, 5);
  service.settle(request, 10, 5);
  var balance = service.balance(user);
  Assertions.assertEquals(15L, ((Number) balance.get("settled_total")).longValue());
  Assertions.assertEquals(-15L, ((Number) balance.get("available")).longValue());
 }
 @Test void unknownUsageRemainsReserved(){UUID id=service.reserve(user,"test",60);service.markPending(id);Assertions.assertEquals(60L,((Number)service.balance(user).get("reserved_total")).longValue());service.resolve(id,8,7,user,"verified usage");Assertions.assertEquals(15L,((Number)service.balance(user).get("settled_total")).longValue());Assertions.assertThrows(org.springframework.web.server.ResponseStatusException.class,()->service.resolve(id,8,7,user,"verified usage"));}
 @Test void rejectedBeforeSendReleasesReservation(){ UUID id=service.reserve(user,"test",60); service.releaseBeforeSend(id); service.releaseBeforeSend(id); Assertions.assertEquals(0L,((Number)service.balance(user).get("reserved_total")).longValue()); }
}
