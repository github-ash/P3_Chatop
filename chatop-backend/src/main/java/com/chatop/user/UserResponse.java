package com.chatop.user;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/** Profil utilisateur public ; le mot de passe n'est jamais inclus dans cette réponse. */
public record UserResponse(
        Integer id, String name, String email, String created_at, String updated_at) {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy/MM/dd");

    /** Convertit une entité utilisateur en profil API avec des dates formatées. */
    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getName(), user.getEmail(),
                format(user.getCreatedAt()), format(user.getUpdatedAt()));
    }

    private static String format(LocalDateTime date) {
        return date == null ? null : date.format(DATE_FORMAT);
    }
}
