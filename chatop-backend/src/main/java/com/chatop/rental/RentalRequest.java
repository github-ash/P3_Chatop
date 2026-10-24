package com.chatop.rental;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

/** Champs d'une annonce reçus en création ou en modification, avec leurs contraintes. */
public record RentalRequest(
        @NotBlank @Size(max = 255) String name,
        @NotNull @DecimalMin("1") @Digits(integer = 10, fraction = 0) BigDecimal surface,
        @NotNull @DecimalMin("0") @Digits(integer = 10, fraction = 0) BigDecimal price,
        @NotBlank @Size(max = 2000) String description) {
}
