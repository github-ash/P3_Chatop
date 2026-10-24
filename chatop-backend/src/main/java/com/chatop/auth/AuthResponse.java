package com.chatop.auth;

/** Réponse d'authentification contenant le JWT à utiliser par le client. */
public record AuthResponse(String token) {
}
