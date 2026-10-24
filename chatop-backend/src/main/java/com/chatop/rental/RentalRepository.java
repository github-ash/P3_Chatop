package com.chatop.rental;

import org.springframework.data.jpa.repository.JpaRepository;

/** Accès aux opérations de persistance JPA sur les annonces. */
public interface RentalRepository extends JpaRepository<Rental, Integer> {
}
