package com.chatop.rental;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;

/** Représentation d'une annonce renvoyée au frontend, sans exposer l'entité JPA. */
public record RentalResponse(Integer id, String name, BigDecimal surface, BigDecimal price,
        String picture, String description, Integer owner_id, String created_at, String updated_at) {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy/MM/dd");

    /** Convertit une annonce et l'URL d'image signée au format de réponse API. */
    public static RentalResponse from(Rental rental, String imageUrl) {
        return new RentalResponse(rental.getId(), rental.getName(), rental.getSurface(), rental.getPrice(),
                imageUrl, rental.getDescription(), rental.getOwner().getId(),
                rental.getCreatedAt().format(DATE), rental.getUpdatedAt().format(DATE));
    }
}
