package com.chatop.message;

import jakarta.validation.constraints.*;

/** Données reçues pour envoyer un message ; les contraintes sont vérifiées par Bean Validation. */
public record MessageRequest(@NotNull @Positive Integer rental_id,
        @NotNull @Positive Integer user_id, @NotBlank @Size(max = 2000) String message) {
}
