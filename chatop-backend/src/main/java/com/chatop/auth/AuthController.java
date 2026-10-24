package com.chatop.auth;

import com.chatop.user.UserResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Expose les routes d'inscription, de connexion et de consultation du profil connecté. */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService auth;

    /** Reçoit le service chargé des règles d'authentification. */
    public AuthController(AuthService auth) {
        this.auth = auth;
    }

    /**
     * Crée un compte à partir des informations reçues et renvoie son jeton JWT.
     *
     * @param request nom, adresse e-mail et mot de passe du nouveau compte
     * @return le jeton à utiliser pour les requêtes protégées
     */
    @Operation(summary = "Creer un compte et obtenir un JWT")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Compte cree"),
            @ApiResponse(responseCode = "400", description = "Champs invalides ou email deja utilise")
    })
    @PostMapping("/register")
    public AuthResponse register(@Valid @RequestBody RegisterRequest request) {
        return auth.register(request);
    }

    /**
     * Vérifie les identifiants et renvoie un jeton JWT en cas de succès.
     *
     * @param request adresse e-mail et mot de passe du compte
     * @return le jeton à utiliser pour les requêtes protégées
     */
    @Operation(summary = "Se connecter et obtenir un JWT")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Connexion reussie"),
            @ApiResponse(responseCode = "400", description = "Requete invalide"),
            @ApiResponse(responseCode = "401", description = "Identifiants incorrects")
    })
    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return auth.login(request);
    }

    /**
     * Renvoie le profil associé à l'utilisateur authentifié par le JWT.
     *
     * @param jwt jeton vérifié par Spring Security
     * @return profil public de l'utilisateur connecté
     */
    @Operation(summary = "Consulter son profil", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Profil de l'utilisateur connecte"),
            @ApiResponse(responseCode = "401", description = "Authentification invalide")
    })
    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal Jwt jwt) {
        return auth.me(jwt.getSubject());
    }
}
