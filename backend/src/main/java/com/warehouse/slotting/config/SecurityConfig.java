package com.warehouse.slotting.config;

import com.warehouse.slotting.repository.UserRepository;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import org.springframework.security.web.SecurityFilterChain;

import jakarta.servlet.DispatcherType;

import java.util.List;
import java.util.Locale;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration configuration
    ) throws Exception {

        return configuration.getAuthenticationManager();
    }

    @Bean
    public Converter<Jwt, AbstractAuthenticationToken>
    jwtAuthenticationConverter(UserRepository userRepository) {

        return jwt -> {

            Long userId;

            try {
                userId = Long.valueOf(jwt.getSubject());
            } catch (NumberFormatException exception) {
                throw new InvalidBearerTokenException(
                        "Invalid token subject"
                );
            }

            var user = userRepository.findById(userId)
                    .filter(account -> account.isActive())
                    .orElseThrow(() ->
                            new InvalidBearerTokenException(
                                    "Invalid account"
                            )
                    );

            String authority = "ROLE_" +
                    user.getRole().name()
                            .toUpperCase(Locale.ROOT);

            return new JwtAuthenticationToken(
                    jwt,
                    List.of(
                            new SimpleGrantedAuthority(authority)
                    ),
                    user.getLogin()
            );
        };
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            Converter<Jwt, AbstractAuthenticationToken>
                    jwtAuthenticationConverter
    ) throws Exception {

        return http

                .csrf(csrf -> csrf.disable())

                .formLogin(form -> form.disable())

                .httpBasic(basic -> basic.disable())

                .sessionManagement(session -> session
                        .sessionCreationPolicy(
                                SessionCreationPolicy.STATELESS
                        )
                )

                .authorizeHttpRequests(auth -> auth

                        // Internal error dispatch
                        .dispatcherTypeMatchers(
                                DispatcherType.ERROR
                        ).permitAll()

                        // Public API documentation
                        .requestMatchers(
                                "/swagger-ui.html",
                                "/swagger-ui/**",
                                "/v3/api-docs/**",
                                "/openapi.yaml"
                        ).permitAll()

                        // Public authentication endpoint
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/auth/login"
                        ).permitAll()

                        // Current authenticated user
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/auth/me"
                        ).authenticated()

                        // Product catalog
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/products"
                        ).hasRole("PLANNER")

                        // Incoming batch registration
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/batches"
                        ).hasRole("OPERATOR")

                        // Warehouse data
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/warehouses/*/batches",
                                "/api/warehouses/*/locations"
                        ).hasAnyRole(
                                "OPERATOR",
                                "MANAGER",
                                "PLANNER"
                        )

                        // Constraint validation
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/constraints/batches/*/locations/*"
                        ).hasRole("OPERATOR")

                        // Slotting recommendations
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/recommendations/batches/*"
                        ).hasRole("OPERATOR")

                        // ABC/XYZ classification
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/classifications/run"
                        ).hasRole("PLANNER")

                        // Deny all unspecified endpoints
                        .anyRequest().denyAll()
                )

                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt
                                .jwtAuthenticationConverter(
                                        jwtAuthenticationConverter
                                )
                        )
                )

                .build();
    }
}