package dev.terminalagent.server.controller;

import dev.terminalagent.server.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AuthController {
    private final AuthService auth;

    public AuthController(AuthService auth) { this.auth = auth; }

    @PostMapping("/api/login")
    public AuthService.LoginResponse login(@RequestBody AuthService.Credentials credentials, HttpServletRequest request) {
        return auth.login(credentials, request);
    }

    @GetMapping("/api/me")
    public AuthService.UserInfo me(HttpServletRequest request) { return auth.current(request); }

    @PostMapping("/api/logout")
    public void logout(HttpServletRequest request) { auth.logout(request); }

    @PostMapping("/api/users")
    @ResponseStatus(HttpStatus.CREATED)
    public void create(@RequestBody AuthService.Credentials credentials, HttpServletRequest request) {
        auth.create(credentials, request);
    }
}
