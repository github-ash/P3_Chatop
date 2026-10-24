package com.chatop.security;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.util.Base64;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;

/** Configure le décodage des JWT, le hachage des mots de passe et les accès HTTP. */
@Configuration
public class SecurityConfig {

    /** Construit la clé de signature à partir du secret Base64 de l'environnement. */
    @Bean
    SecretKey jwtKey(@Value("${chatop.jwt.secret}") String encodedSecret) {
        byte[] bytes = Base64.getDecoder().decode(encodedSecret);
        if (bytes.length < 32) {
            throw new IllegalArgumentException("CHATOP_JWT_SECRET must contain at least 32 random bytes in Base64");
        }
        return new SecretKeySpec(bytes, "HmacSHA256");
    }

    /** Fournit l'encodeur JWT utilisant la clé symétrique de l'application. */
    @Bean
    JwtEncoder jwtEncoder(SecretKey key) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(key));
    }

    /** Vérifie la signature, la durée de validité et l'émetteur des JWT entrants. */
    @Bean
    JwtDecoder jwtDecoder(SecretKey key) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(key)
                .macAlgorithm(MacAlgorithm.HS256).build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer("chatop"));
        return decoder;
    }

    /** Hache les mots de passe avec BCrypt avant leur persistance. */
    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /** Définit les routes publiques et les règles d'autorisation des requêtes HTTP. */
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/api/auth/register", "/api/auth/login").permitAll()
                        .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/images/*").access((authentication, context) -> {
                            var principal = authentication.get();
                            if (!(principal instanceof JwtAuthenticationToken jwt)) {
                                return new AuthorizationDecision(false);
                            }
                            String image = jwt.getToken().getClaimAsString("image");
                            return new AuthorizationDecision(image == null
                                    || context.getRequest().getRequestURI().equals("/images/" + image));
                        })
                        .anyRequest().access((authentication, context) -> {
                            var principal = authentication.get();
                            return new AuthorizationDecision(principal instanceof JwtAuthenticationToken jwt
                                    && jwt.getToken().getClaimAsString("image") == null);
                        }))
                .oauth2ResourceServer(resource -> resource
                        .bearerTokenResolver(request -> {
                            DefaultBearerTokenResolver resolver = new DefaultBearerTokenResolver();
                            resolver.setAllowUriQueryParameter("GET".equals(request.getMethod())
                                    && request.getRequestURI().startsWith("/images/"));
                            return resolver.resolve(request);
                        })
                        .jwt(jwt -> {})
                        .authenticationEntryPoint((request, response, exception) -> {
                            response.setStatus(401);
                            response.setHeader("WWW-Authenticate", "Bearer");
                            response.setContentType("application/json");
                            response.getWriter().write("{}");
                        }))
                .build();
    }
}
