package com.chatop.rental;

import com.chatop.user.User;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/** Entité JPA représentant une annonce et son propriétaire. */
@Entity
@Table(name = "RENTALS")
public class Rental {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    private String name;
    @Column(precision = 10, scale = 0)
    private BigDecimal surface;
    @Column(precision = 10, scale = 0)
    private BigDecimal price;
    private String picture;
    @Column(length = 2000)
    private String description;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;
    @Column(name = "created_at", columnDefinition = "timestamp")
    private LocalDateTime createdAt;
    @Column(name = "updated_at", columnDefinition = "timestamp")
    private LocalDateTime updatedAt;

    protected Rental() {
    }

    /** Crée une annonce et initialise ses dates à l'heure UTC. */
    public Rental(RentalRequest request, User owner, String picture) {
        this.owner = owner;
        this.picture = picture;
        this.createdAt = LocalDateTime.now(ZoneOffset.UTC);
        update(request);
    }

    /** Met à jour les champs éditables et la date de modification de l'annonce. */
    public void update(RentalRequest request) {
        name = request.name().strip();
        surface = request.surface();
        price = request.price();
        description = request.description().strip();
        updatedAt = LocalDateTime.now(ZoneOffset.UTC);
    }

    public Integer getId() { return id; }
    public String getName() { return name; }
    public BigDecimal getSurface() { return surface; }
    public BigDecimal getPrice() { return price; }
    public String getPicture() { return picture; }
    public String getDescription() { return description; }
    public User getOwner() { return owner; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
