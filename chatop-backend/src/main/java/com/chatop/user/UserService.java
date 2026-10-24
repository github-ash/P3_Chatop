package com.chatop.user;

import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true)
/** Fournit les opérations métier de consultation et de résolution des utilisateurs. */
public class UserService {
    private final UserRepository users;

    public UserService(UserRepository users) {
        this.users = users;
    }

    /** Résout l'utilisateur depuis le sujet du JWT ou rejette un jeton sans compte valide. */
    public User authenticated(String subject) {
        try {
            return users.findById(Integer.valueOf(subject))
                    .orElseThrow(() -> new BadCredentialsException("User no longer exists"));
        } catch (NumberFormatException exception) {
            throw new BadCredentialsException("Invalid token subject", exception);
        }
    }

    /** Retourne le profil public d'un utilisateur ou une erreur 404. */
    public UserResponse find(Integer id) {
        return users.findById(id).map(UserResponse::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }
}
