package com.chatop.user;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.web.bind.annotation.*;

/** Expose la consultation des profils utilisateur authentifiée. */
@RestController
@RequestMapping("/api/user")
@SecurityRequirement(name = "bearerAuth")
public class UserController {
    private final UserService users;

    /** Reçoit le service de consultation des utilisateurs. */
    public UserController(UserService users) { this.users = users; }

    /**
     * Renvoie les informations publiques d'un utilisateur, sans son mot de passe.
     *
     * @param id identifiant de l'utilisateur recherché
     * @return profil public correspondant à cet identifiant
     */
    @Operation(summary = "Consulter un utilisateur sans exposer son mot de passe")
    @GetMapping("/{id}")
    public UserResponse find(@PathVariable Integer id) { return users.find(id); }
}
