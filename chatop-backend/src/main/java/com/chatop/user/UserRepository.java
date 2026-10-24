package com.chatop.user;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Accès JPA aux utilisateurs, avec les recherches nécessaires à l'authentification. */
public interface UserRepository extends JpaRepository<User, Integer> {

    /** Recherche un compte par son adresse e-mail normalisée. */
    Optional<User> findByEmail(String email);

    /** Vérifie si une adresse e-mail est déjà associée à un compte. */
    boolean existsByEmail(String email);
}
