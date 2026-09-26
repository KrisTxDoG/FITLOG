package com.fitlog;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwords;
    public AuthController(JdbcTemplate jdbc, PasswordEncoder passwords) { this.jdbc=jdbc;this.passwords=passwords; }
    public record Registration(@NotBlank @Email @Size(max=254) String email,
        @NotBlank @Size(max=60) String displayName, @NotNull @Size(min=12,max=72) String password) {}
    public record Member(String id,String email,String displayName) {}
    @GetMapping("/csrf") public Map<String,String> csrf(CsrfToken token) {
        return Map.of("token",token.getToken(),"headerName",token.getHeaderName());
    }
    @GetMapping("/me") public Member me() {
        return jdbc.query("SELECT * FROM members WHERE id=?",(rs,n)->new Member(rs.getString("id"),rs.getString("email"),rs.getString("display_name")),SecurityConfig.memberId())
            .stream().findFirst().orElseThrow(()->new ResponseStatusException(HttpStatus.UNAUTHORIZED));
    }
    @PostMapping("/register") @ResponseStatus(HttpStatus.CREATED) @Transactional
    public Member register(@Valid @RequestBody Registration input) {
        if(input.password().getBytes(StandardCharsets.UTF_8).length>72)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"密碼最多為 72 UTF-8 位元組");
        String hash=passwords.encode(input.password());
        boolean claimed=jdbc.queryForObject("SELECT legacy_claimed FROM membership_state WHERE id=1 FOR UPDATE",Boolean.class);
        String email=input.email().trim().toLowerCase(Locale.ROOT);
        if(jdbc.queryForObject("SELECT COUNT(*) FROM members WHERE email=?",Integer.class,email)>0)
            throw new ResponseStatusException(HttpStatus.CONFLICT,"無法使用此電子郵件註冊");
        String id=UUID.randomUUID().toString();
        jdbc.update("INSERT INTO members(id,email,display_name,password_hash) VALUES(?,?,?,?)",id,email,input.displayName().trim(),hash);
        if(!claimed) {
            for(String table:List.of("workouts","templates","custom_exercises"))
                jdbc.update("UPDATE "+table+" SET owner_id=? WHERE owner_id IS NULL",id);
            jdbc.update("UPDATE membership_state SET legacy_claimed=TRUE WHERE id=1");
        }
        return new Member(id,email,input.displayName().trim());
    }
}
