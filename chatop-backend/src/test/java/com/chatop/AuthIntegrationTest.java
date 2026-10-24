package com.chatop;

import com.chatop.user.UserRepository;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class AuthIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder passwords;
    @Autowired JwtEncoder encoder;

    @DynamicPropertySource
    static void jwtSecret(DynamicPropertyRegistry registry) {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        String secret = Base64.getEncoder().encodeToString(bytes);
        registry.add("chatop.jwt.secret", () -> secret);
    }

    @BeforeEach
    void cleanDatabase() {
        users.deleteAll();
    }

    @Test
    void registerHashesPasswordAndReturnsUsableToken() throws Exception {
        String token = register("Alice@Example.com");
        var user = users.findByEmail("alice@example.com").orElseThrow();
        assertNotEquals("Password-42!", user.getPassword());
        assertTrue(passwords.matches("Password-42!", user.getPassword()));
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(user.getId()))
                .andExpect(jsonPath("$.email").value("alice@example.com"))
                .andExpect(jsonPath("$.created_at").isString())
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    void loginReturnsTokenAndRejectsIncorrectCredentials() throws Exception {
        register("alice@example.com");
        mvc.perform(post("/api/auth/login").contentType("application/json")
                        .content(body("alice@example.com", "Password-42!")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.token").isNotEmpty());
        for (String email : new String[]{"alice@example.com", "unknown@example.com"}) {
            mvc.perform(post("/api/auth/login").contentType("application/json")
                            .content(body(email, "Wrong-password")))
                    .andExpect(status().isUnauthorized()).andExpect(content().json("{\"message\":\"error\"}"));
        }
    }

    @Test
    void rejectsDuplicateEmailAndInvalidInput() throws Exception {
        register("alice@example.com");
        mvc.perform(post("/api/auth/register").contentType("application/json")
                        .content(registration("ALICE@example.com", "Password-42!")))
                .andExpect(status().isBadRequest()).andExpect(content().json("{}"));
        for (String input : new String[]{"{}", "{", registration("invalid", "short"),
                registration("other@example.com", "\u00e9".repeat(40))}) {
            mvc.perform(post("/api/auth/register").contentType("application/json").content(input))
                    .andExpect(status().isBadRequest());
        }
        assertEquals(1, users.count());
    }

    @Test
    void protectsAllOtherRoutesAndRejectsInvalidTokens() throws Exception {
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/rentals")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer invalid"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token("chatop", true)))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token("other", false)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsTokensForDeletedUsers() throws Exception {
        String token = register("alice@example.com");
        users.deleteAll();
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsTamperedTokenAndReturnsDifferentUsersProfiles() throws Exception {
        String aliceToken = register("alice@example.com");
        String bobToken = register("bob@example.com");
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + bobToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.email").value("bob@example.com"));
        String[] parts = aliceToken.split("\\.");
        char replacement = parts[2].charAt(0) == 'A' ? 'B' : 'A';
        String tampered = parts[0] + "." + parts[1] + "." + replacement + parts[2].substring(1);
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + tampered))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void swaggerIsPublicAndDescribesBearerAuthentication() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/auth/register']").exists())
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"));
        mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
    }

    @Test
    void authenticatedMissingRoutesReturnNotFoundInsteadOfServerError() throws Exception {
        String token = register("alice@example.com");
        mvc.perform(get("/api/unknown").header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    private String register(String email) throws Exception {
        String response = mvc.perform(post("/api/auth/register").contentType("application/json")
                        .content(registration(email, "Password-42!")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode result = json.readTree(response);
        return result.get("token").asText();
    }

    private String registration(String email, String password) {
        return json.writeValueAsString(Map.of("name", "Alice", "email", email, "password", password));
    }

    private String body(String email, String password) {
        return json.writeValueAsString(Map.of("email", email, "password", password));
    }

    private String token(String issuer, boolean expired) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder().issuer(issuer).subject("1")
                .issuedAt(now.minusSeconds(7200))
                .expiresAt(expired ? now.minusSeconds(3600) : now.plusSeconds(3600)).build();
        return encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }
}
