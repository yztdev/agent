package dev.terminalagent.server;
import java.io.Console;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
@Component public class Bootstrap implements ApplicationRunner {
 private final JdbcTemplate db; Bootstrap(JdbcTemplate db){this.db=db;}
 @org.springframework.transaction.annotation.Transactional
 public void run(ApplicationArguments args) { if(!args.containsOption("init-admin")) return; Console c=System.console(); if(c==null) throw new IllegalStateException("Admin initialization requires an interactive console"); String name=c.readLine("Admin username: "); char[] pass=c.readPassword("Admin password (at least 12 characters): "); try { if(name==null || !name.matches("[A-Za-z0-9_.-]{3,100}") || pass==null || pass.length<12 || new String(pass).getBytes(StandardCharsets.UTF_8).length>72) throw new IllegalArgumentException("Invalid credentials"); db.execute("SELECT pg_advisory_xact_lock(918273)"); if(db.queryForObject("select count(*) from users where role='ADMIN'",Integer.class)>0) throw new IllegalStateException("Admin already exists"); db.update("insert into users(id,username,password_hash,role,status) values (?,?,?,'ADMIN','ACTIVE')",UUID.randomUUID(),name,new BCryptPasswordEncoder().encode(new String(pass))); System.out.println("Admin created; stop the process and restart normally."); } finally { if(pass!=null) java.util.Arrays.fill(pass,'\0'); } }
}
