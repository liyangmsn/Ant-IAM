package com.antiam.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health").permitAll()
                .requestMatchers(HttpMethod.GET, "/.well-known/openid-configuration").permitAll()
                .requestMatchers(HttpMethod.GET, "/oauth2/jwks").permitAll()
                .requestMatchers(HttpMethod.GET, "/saml2/metadata").permitAll()
                .requestMatchers(HttpMethod.GET, "/cas/serviceValidate").permitAll()
                .requestMatchers(HttpMethod.POST, "/oauth2/token").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/users/password-reset-tickets/consumptions").permitAll()
                .requestMatchers(HttpMethod.GET, "/oauth2/userinfo").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/catalog").permitAll()
                .anyRequest().authenticated())
            .httpBasic(Customizer.withDefaults());
        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
