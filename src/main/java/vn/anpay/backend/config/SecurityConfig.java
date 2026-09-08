package vn.anpay.backend.config;

import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import vn.anpay.backend.common.security.AuthenticationFilter;

@Configuration
public class SecurityConfig {
    @Bean PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);

    }
    @Bean SecurityFilterChain filterChain(HttpSecurity http,AuthenticationFilter filter)throws Exception {
        return http.csrf(c->c.disable()).sessionManagement(s->s.sessionCreationPolicy(SessionCreationPolicy.STATELESS)) .authorizeHttpRequests(a->a.requestMatchers("/api/v1/auth/register","/api/v1/auth/login","/api/v1/auth/email/**","/api/v1/auth/phone","/api/v1/auth/phone/**","/api/v1/auth/refresh","/api/v1/auth/forgot-password/**","/api/v1/dev/mock-payments/**","/mock-payment.html","/actuator/health").permitAll().anyRequest().authenticated()) .addFilterBefore(filter, UsernamePasswordAuthenticationFilter.class).build();

    }

}
