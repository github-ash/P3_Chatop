package com.chatop.image;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.Parameter;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** Expose la lecture des fichiers image stockés par l'API. */
@RestController
@RequestMapping("/images")
public class ImageController {
    private final ImageService images;

    /** Reçoit le service chargé de retrouver les fichiers image autorisés. */
    public ImageController(ImageService images) { this.images = images; }

    /**
     * Sert une image après vérification du jeton et du nom de fichier.
     *
     * @param name nom UUID du fichier image
     * @param access_token jeton temporaire d'accès à l'image, si fourni dans l'URL
     * @return l'image PNG avec des en-têtes de sécurité
     */
    @Operation(summary = "Afficher une image avec un JWT utilisateur ou un jeton signe limite a cette image")
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping(value = "/{name}", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<Resource> get(@PathVariable String name,
            @Parameter(description = "JWT signe limite a cette image, fourni dans son URL")
            @RequestParam(required = false) String access_token) {
        return ResponseEntity.ok().header("X-Content-Type-Options", "nosniff")
                .header("Cache-Control", "no-store").header("Referrer-Policy", "no-referrer")
                .body(new FileSystemResource(images.find(name)));
    }
}
