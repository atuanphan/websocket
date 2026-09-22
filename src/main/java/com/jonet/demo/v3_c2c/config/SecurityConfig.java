package com.jonet.demo.v3_c2c.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import com.jonet.demo.v3_c2c.component.JwtFilter;

import lombok.RequiredArgsConstructor;

@Configuration
@RequiredArgsConstructor
public class SecurityConfig {
	private final JwtFilter jwtFilter;
	
	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		 http
         .csrf(csrf -> csrf.disable())   // WebSocket + REST API dùng token, không cần CSRF
         .sessionManagement(session -> 
             session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)) // JWT là stateless
         .authorizeHttpRequests(auth -> auth
             .requestMatchers("/api/auth/login").permitAll()   // login không cần token
             .requestMatchers("/ws/**").permitAll()            // WebSocket tự xử lý auth riêng ở HandshakeInterceptor
             .requestMatchers("/c2c-chat.html").permitAll()     // cho phép load file test (chỉ nên có ở dev)
             .anyRequest().authenticated()                     // các API khác bắt buộc có JWT hợp lệ
         )
         .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

     return http.build();
	}
	
	@Bean
	public PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}
	
	@Bean
	public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
		return config.getAuthenticationManager();
	}
}
