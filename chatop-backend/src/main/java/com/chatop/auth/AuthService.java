package com.chatop.auth;

import com.chatop.security.TokenService;
import com.chatop.user.User;
import com.chatop.user.UserRepository;
import com.chatop.user.UserResponse;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Locale;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Applique les règles métier d'inscription, de connexion et de profil. */
@Service
public class AuthService {

    private final UserRepository users;
    private final PasswordEncoder passwords;
    private final TokenService tokens;
    private final String dummyPassword;

    public AuthService(UserRepository users, PasswordEncoder passwords, TokenService tokens) {
        this.users = users;
        this.passwords = passwords;
        this.tokens = tokens;
        this.dummyPassword = passwords.encode("unused-password-for-timing");
    }

    /**
     * Crée un utilisateur après validation et renvoie un JWT.
     *
     * @param request informations d'inscription validées par le contrôleur
     * @return jeton d'authentification du compte créé
     */
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        validatePasswordLength(request.password());
        String email = normalizeEmail(request.email());
        if (users.existsByEmail(email)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email already registered");
        }
        User user = new User(email, request.name().strip(), passwords.encode(request.password()),
                LocalDateTime.now(ZoneOffset.UTC));
        try {
            users.saveAndFlush(user);
        } catch (DataIntegrityViolationException exception) {
            if (exception.getMostSpecificCause() instanceof SQLException sql
                    && (sql.getErrorCode() == 1062 || "23505".equals(sql.getSQLState()))) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email already registered", exception);
            }
            throw exception;
        }
        return new AuthResponse(tokens.create(user.getId()));
    }

    /**
     * Vérifie les identifiants et émet un JWT pour l'utilisateur.
     *
     * @param request adresse e-mail et mot de passe
     * @return jeton d'authentification
     */
    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        validatePasswordLength(request.password());
        User user = users.findByEmail(normalizeEmail(request.email())).orElse(null);
        String hash = user == null ? dummyPassword : user.getPassword();
        boolean matches = passwords.matches(request.password(), hash);
        if (user == null || !matches) {
            throw new BadCredentialsException("Invalid credentials");
        }
        return new AuthResponse(tokens.create(user.getId()));
    }

    /**
     * Charge le profil correspondant au sujet du JWT.
     *
     * @param subject identifiant utilisateur contenu dans le jeton
     * @return profil public de l'utilisateur
     */
    @Transactional(readOnly = true)
    public UserResponse me(String subject) {
        int id;
        try {
            id = Integer.parseInt(subject);
        } catch (NumberFormatException exception) {
            throw new BadCredentialsException("Invalid token subject", exception);
        }
        return users.findById(id).map(UserResponse::from)
                .orElseThrow(() -> new BadCredentialsException("User no longer exists"));
    }

    private String normalizeEmail(String email) {
        return email.strip().toLowerCase(Locale.ROOT);
    }

    private void validatePasswordLength(String password) {
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password exceeds 72 bytes");
        }
    }
}
