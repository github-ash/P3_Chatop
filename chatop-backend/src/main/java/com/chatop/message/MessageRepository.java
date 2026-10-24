package com.chatop.message;

import org.springframework.data.jpa.repository.JpaRepository;

/** Accès aux opérations de persistance JPA sur les messages. */
public interface MessageRepository extends JpaRepository<Message, Integer> {
}
