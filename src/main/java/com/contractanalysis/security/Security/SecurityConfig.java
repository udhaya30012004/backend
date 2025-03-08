// src/main/java/com/contractanalysis/security/SecurityConfig.java
package com.contractanalysis.security.Security;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

import com.contractanalysis.security.OAuth2UserService;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final OAuth2UserService oAuth2UserService;
    
    @Value("${app.client.url}")
    private String clientUrl;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/status", "/auth/**").permitAll()
                        .requestMatchers("/payments/webhook").permitAll()
                        .anyRequest().authenticated()
                )
                .oauth2Login(oauth2 -> oauth2
                        .userInfoEndpoint(userInfo -> userInfo
                                .userService(oAuth2UserService)
                        )
                        .successHandler((request, response, authentication) -> 
                            response.sendRedirect(clientUrl + "/dashboard")
                        )
                        .failureHandler((request, response, exception) -> 
                            response.sendRedirect(clientUrl + "/login")
                        )
                )
                .logout(logout -> logout
                        .logoutSuccessUrl(clientUrl + "/login")
                        .invalidateHttpSession(true)
                        .clearAuthentication(true)
                )
                .build();
    }
}