package com.chatop.rental;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/** Expose les opérations HTTP de consultation et de gestion des annonces. */
@RestController
@RequestMapping("/api/rentals")
@SecurityRequirement(name = "bearerAuth")
public class RentalController {
    private final RentalService rentals;

    /** Reçoit le service qui applique les règles métier des annonces. */
    public RentalController(RentalService rentals) { this.rentals = rentals; }

    /**
     * Renvoie la liste des annonces disponibles.
     *
     * @return les annonces sous la propriété « rentals »
     */
    @Operation(summary = "Lister les locations")
    @GetMapping
    public Map<String, List<RentalResponse>> all() { return Map.of("rentals", rentals.all()); }

    /**
     * Renvoie le détail d'une annonce.
     *
     * @param id identifiant de l'annonce
     * @return l'annonce correspondante
     */
    @Operation(summary = "Consulter une location")
    @GetMapping("/{id}")
    public RentalResponse detail(@PathVariable Integer id) { return rentals.detail(id); }

    /**
     * Crée une annonce avec son image et l'associe à l'utilisateur connecté.
     *
     * @param request champs descriptifs de l'annonce
     * @param picture image PNG ou JPEG envoyée avec le formulaire
     * @param jwt jeton de l'utilisateur authentifié
     * @return confirmation de création
     * @throws IOException si l'enregistrement de l'image échoue
     */
    @Operation(summary = "Creer une location avec une image PNG ou JPEG")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, String> create(@Valid @ModelAttribute RentalRequest request,
            @RequestParam MultipartFile picture, @AuthenticationPrincipal Jwt jwt) throws IOException {
        rentals.create(request, picture, jwt.getSubject());
        return Map.of("message", "Rental created !");
    }

    /**
     * Modifie les champs d'une annonce sans remplacer son image existante.
     *
     * @param id identifiant de l'annonce à modifier
     * @param request nouveaux champs descriptifs de l'annonce
     * @param jwt jeton du propriétaire authentifié
     * @return confirmation de modification
     */
    @Operation(summary = "Modifier sa location en conservant son image")
    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, String> update(@PathVariable Integer id, @Valid @ModelAttribute RentalRequest request,
                                      @AuthenticationPrincipal Jwt jwt) {
        rentals.update(id, request, jwt.getSubject());
        return Map.of("message", "Rental updated !");
    }
}
