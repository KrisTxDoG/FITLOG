package com.fitlog;

import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import java.util.Locale;

@Configuration
public class SecurityConfig {
    static String memberId() { return SecurityContextHolder.getContext().getAuthentication().getName(); }
    @Bean PasswordEncoder passwords() { return new BCryptPasswordEncoder(12); }
    @Bean UserDetailsService users(JdbcTemplate jdbc) {
        return email -> jdbc.query("SELECT id,password_hash FROM members WHERE email=?", (rs,n) ->
            User.withUsername(rs.getString("id")).password(rs.getString("password_hash")).roles("MEMBER").build(),
            email.trim().toLowerCase(Locale.ROOT)).stream().findFirst().orElseThrow(() -> new UsernameNotFoundException("帳號或密碼錯誤"));
    }
    @Bean SecurityFilterChain security(HttpSecurity http, GoogleLogin google) throws Exception {
        http.authorizeHttpRequests(a -> a.requestMatchers("/api/auth/csrf", "/api/auth/register", "/api/auth/login", "/api/auth/google/**").permitAll()
            .requestMatchers("/api/**").authenticated().anyRequest().permitAll())
            .exceptionHandling(e -> e.authenticationEntryPoint((req,res,ex) -> res.sendError(401)))
            .formLogin(f -> f.loginProcessingUrl("/api/auth/login")
                .successHandler((req,res,auth) -> res.setStatus(204))
                .failureHandler((req,res,ex) -> res.sendError(401)))
            .logout(l -> l.logoutUrl("/api/auth/logout").deleteCookies("JSESSIONID")
                .logoutSuccessHandler((req,res,auth) -> res.setStatus(204)))
            .requestCache(c -> c.disable());
        if(google.enabled)http.oauth2Login(o -> o
            .authorizationEndpoint(a -> a.baseUri("/api/auth/google/start"))
            .redirectionEndpoint(r -> r.baseUri("/api/auth/google/callback/*"))
            .successHandler(google::success)
            .failureHandler((req,res,ex) -> {if(req.getSession(false)!=null)req.getSession(false).removeAttribute(GoogleLogin.PENDING);res.sendRedirect("/?google=failed");}));
        return http.build();
    }
}
