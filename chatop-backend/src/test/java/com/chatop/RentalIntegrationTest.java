package com.chatop;

import com.chatop.message.MessageRepository;
import com.chatop.rental.RentalRepository;
import com.chatop.security.TokenService;
import com.chatop.user.UserRepository;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.*;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:rentals;MODE=MySQL;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
class RentalIntegrationTest {
    @TempDir static Path images;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired MessageRepository messages;
    @Autowired RentalRepository rentals;
    @Autowired UserRepository users;
    @Autowired TokenService tokens;
    private String ownerToken;
    private String visitorToken;
    private Integer ownerId;
    private Integer visitorId;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        String secret = Base64.getEncoder().encodeToString(bytes);
        registry.add("chatop.jwt.secret", () -> secret);
        registry.add("chatop.images.directory", () -> images.toString());
    }

    @BeforeEach
    void setup() throws Exception {
        messages.deleteAll();
        rentals.deleteAll();
        users.deleteAll();
        try (var files = Files.list(images)) {
            for (Path file : files.toList()) { Files.delete(file); }
        }
        ownerToken = register("owner@example.com");
        visitorToken = register("visitor@example.com");
        ownerId = users.findByEmail("owner@example.com").orElseThrow().getId();
        visitorId = users.findByEmail("visitor@example.com").orElseThrow().getId();
    }

    @Test
    void fullRentalLifecyclePreservesImageAndOwner() throws Exception {
        mvc.perform(get("/api/rentals").header("Authorization", bearer(ownerToken)))
                .andExpect(status().isOk()).andExpect(content().json("{\"rentals\":[]}"));
        Integer id = create();
        String storedPicture = rentals.findById(id).orElseThrow().getPicture();
        mvc.perform(get("/api/rentals").header("Authorization", bearer(visitorToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.rentals[0].id").value(id));
        mvc.perform(get("/api/rentals/" + id).header("Authorization", bearer(visitorToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.owner_id").value(ownerId))
                .andExpect(jsonPath("$.picture").isString());
        var update = form(multipart("/api/rentals/" + id).with(request -> {
            request.setMethod("PUT");
            return request;
        }));
        mvc.perform(update.header("Authorization", bearer(ownerToken)))
                .andExpect(status().isOk()).andExpect(content().json("{\"message\":\"Rental updated !\"}"));
        var rental = rentals.findById(id).orElseThrow();
        assertEquals(storedPicture, rental.getPicture());
        assertEquals(ownerId, rental.getOwner().getId());
    }

    @Test
    void onlyOwnerCanUpdate() throws Exception {
        Integer id = create();
        mvc.perform(form(multipart("/api/rentals/" + id).with(request -> {
                    request.setMethod("PUT");
                    return request;
                })).header("Authorization", bearer(visitorToken)))
                .andExpect(status().isForbidden());
    }

    @Test
    void messagesPersistAndCannotImpersonateOtherUsers() throws Exception {
        Integer id = create();
        mvc.perform(post("/api/messages").header("Authorization", bearer(visitorToken))
                        .contentType("application/json")
                        .content(json.writeValueAsString(Map.of("rental_id", id,
                                "user_id", visitorId, "message", "Bonjour"))))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"message\":\"Message send with success\"}"));
        assertEquals(1, messages.count());
        assertEquals("Bonjour", messages.findAll().getFirst().getMessage());
        mvc.perform(post("/api/messages").header("Authorization", bearer(visitorToken))
                        .contentType("application/json")
                        .content(json.writeValueAsString(Map.of("rental_id", id,
                                "user_id", ownerId, "message", "Spoofed"))))
                .andExpect(status().isForbidden());
        assertEquals(1, messages.count());
    }

    @Test
    void userDetailsNeverExposePassword() throws Exception {
        mvc.perform(get("/api/user/" + ownerId).header("Authorization", bearer(visitorToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(ownerId))
                .andExpect(jsonPath("$.password").doesNotExist());
        mvc.perform(get("/api/user/2147483647").header("Authorization", bearer(visitorToken)))
                .andExpect(status().isNotFound());
    }

    @Test
    void signedImageUrlsWorkWithoutHeadersButCannotAccessOtherResources() throws Exception {
        Integer id = create();
        String detail = mvc.perform(get("/api/rentals/" + id).header("Authorization", bearer(ownerToken)))
                .andReturn().getResponse().getContentAsString();
        String url = json.readTree(detail).get("picture").asText();
        String path = java.net.URI.create(url).getRawPath();
        String imageToken = url.substring(url.indexOf("access_token=") + "access_token=".length());
        mvc.perform(get(path).param("access_token", imageToken))
                .andExpect(status().isOk()).andExpect(content().contentType("image/png"));
        mvc.perform(get(path)).andExpect(status().isUnauthorized());
        mvc.perform(get("/images/00000000-0000-0000-0000-000000000000.png").param("access_token", imageToken))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/auth/me").header("Authorization", bearer(imageToken)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/rentals").param("access_token", ownerToken))
                .andExpect(status().isUnauthorized());
        mvc.perform(get(path).param("access_token", "invalid")).andExpect(status().isUnauthorized());
    }

    @Test
    void missingResourcesAndMalformedIdsReturnExpectedErrors() throws Exception {
        mvc.perform(get("/api/rentals/2147483647").header("Authorization", bearer(ownerToken)))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/rentals/invalid").header("Authorization", bearer(ownerToken)))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/messages").header("Authorization", bearer(visitorToken))
                        .contentType("application/json")
                        .content(json.writeValueAsString(Map.of("rental_id", 2147483647,
                                "user_id", visitorId, "message", "Bonjour"))))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/messages").header("Authorization", bearer(visitorToken))
                        .contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void invalidFormAndFilesDoNotCreateRentalsOrImages() throws Exception {
        mvc.perform(form(multipart("/api/rentals")).header("Authorization", bearer(ownerToken)))
                .andExpect(status().isBadRequest());
        mvc.perform(form(multipart("/api/rentals")
                        .file(new MockMultipartFile("picture", "fake.png", "image/png", "not an image".getBytes())))
                        .header("Authorization", bearer(ownerToken)))
                .andExpect(status().isBadRequest());
        mvc.perform(multipart("/api/rentals").file(image()).param("name", "Maison")
                        .param("surface", "80").param("price", "-1").param("description", "Maison")
                        .header("Authorization", bearer(ownerToken)))
                .andExpect(status().isBadRequest());
        mvc.perform(multipart("/api/rentals").file(image()).param("name", "Maison")
                        .param("surface", "12.5").param("price", "120").param("description", "Maison")
                        .header("Authorization", bearer(ownerToken)))
                .andExpect(status().isBadRequest());
        assertEquals(0, rentals.count());
        try (var files = Files.list(images)) { assertEquals(0, files.count()); }
    }

    @Test
    void oversizedAndUnsupportedImagesAreRejectedWithoutLeavingFiles() throws Exception {
        mvc.perform(form(multipart("/api/rentals").file(new MockMultipartFile(
                        "picture", "large.png", "image/png", new byte[5 * 1024 * 1024 + 1])))
                        .header("Authorization", bearer(ownerToken)))
                .andExpect(status().isBadRequest());
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB), "gif", bytes);
        mvc.perform(form(multipart("/api/rentals").file(new MockMultipartFile(
                        "picture", "image.gif", "image/gif", bytes.toByteArray())))
                        .header("Authorization", bearer(ownerToken)))
                .andExpect(status().isBadRequest());
        assertEquals(0, rentals.count());
        try (var files = Files.list(images)) { assertEquals(0, files.count()); }
    }

    @Test
    void filenameIsNeverUsedAsFilesystemPathAndDeletedUsersCannotPublish() throws Exception {
        byte[] png = image().getBytes();
        mvc.perform(form(multipart("/api/rentals").file(new MockMultipartFile(
                        "picture", "../../escape.png", "text/html", png)))
                        .header("Authorization", bearer(ownerToken)))
                .andExpect(status().isOk());
        try (var files = Files.list(images)) {
            var stored = files.toList();
            assertEquals(1, stored.size());
            assertTrue(stored.getFirst().getFileName().toString().matches("[a-f0-9-]{36}\\.png"));
        }
        mvc.perform(get("/images/not-an-image.png").header("Authorization", bearer(ownerToken)))
                .andExpect(status().isNotFound());
        users.deleteById(visitorId);
        mvc.perform(form(multipart("/api/rentals").file(image()))
                        .header("Authorization", bearer(visitorToken)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void allBusinessRoutesRequireAuthentication() throws Exception {
        mvc.perform(get("/api/rentals")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/rentals/1")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/user/1")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/messages").contentType("application/json").content("{}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(form(multipart("/api/rentals").file(image()))).andExpect(status().isUnauthorized());
    }

    @Test
    void openApiDescribesAllNineBusinessOperations() throws Exception {
        String body = mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode paths = json.readTree(body).get("paths");
        for (String path : new String[]{"/api/auth/register", "/api/auth/login", "/api/auth/me",
                "/api/rentals", "/api/rentals/{id}", "/api/user/{id}", "/api/messages"}) {
            assertTrue(paths.has(path), path);
        }
        assertTrue(paths.get("/api/rentals").has("post"));
        assertTrue(paths.get("/api/rentals/{id}").has("put"));
    }

    private Integer create() throws Exception {
        mvc.perform(form(multipart("/api/rentals").file(image())).header("Authorization", bearer(ownerToken)))
                .andExpect(status().isOk()).andExpect(content().json("{\"message\":\"Rental created !\"}"));
        return rentals.findAll().getFirst().getId();
    }

    private MockMultipartHttpServletRequestBuilder form(MockMultipartHttpServletRequestBuilder builder) {
        return builder.param("name", "Maison").param("surface", "80").param("price", "120")
                .param("description", "Proche de la plage");
    }

    private MockMultipartFile image() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "png", bytes);
        return new MockMultipartFile("picture", "house.png", "image/png", bytes.toByteArray());
    }

    private String register(String email) throws Exception {
        String body = mvc.perform(post("/api/auth/register").contentType("application/json")
                        .content(json.writeValueAsString(Map.of("name", "Test User", "email", email,
                                "password", "Password-42!"))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("token").asText();
    }

    private String bearer(String token) { return "Bearer " + token; }
}
