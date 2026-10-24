package com.chatop.message;

import com.chatop.rental.Rental;
import com.chatop.user.User;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/** Entité JPA représentant un message envoyé au sujet d'une annonce. */
@Entity
@Table(name = "MESSAGES")
public class Message {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rental_id")
    private Rental rental;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;
    @Column(length = 2000)
    private String message;
    @Column(name = "created_at", columnDefinition = "timestamp")
    private LocalDateTime createdAt;
    @Column(name = "updated_at", columnDefinition = "timestamp")
    private LocalDateTime updatedAt;

    protected Message() {
    }

    /** Crée un message lié à son annonce et à son expéditeur, avec les dates UTC. */
    public Message(Rental rental, User user, String message) {
        this.rental = rental;
        this.user = user;
        this.message = message.strip();
        createdAt = LocalDateTime.now(ZoneOffset.UTC);
        updatedAt = createdAt;
    }

    public String getMessage() { return message; }
    public User getUser() { return user; }
    public Rental getRental() { return rental; }
}
