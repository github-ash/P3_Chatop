package com.chatop.rental;

import com.chatop.image.ImageService;
import com.chatop.user.User;
import com.chatop.user.UserService;
import com.chatop.security.TokenService;
import java.io.IOException;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

/** Applique les règles métier de consultation, création et modification des annonces. */
@Service
@Transactional(readOnly = true)
public class RentalService {
    private final RentalRepository rentals;
    private final UserService users;
    private final ImageService images;
    private final TokenService tokens;

    public RentalService(RentalRepository rentals, UserService users, ImageService images, TokenService tokens) {
        this.rentals = rentals;
        this.users = users;
        this.images = images;
        this.tokens = tokens;
    }

    /** Retourne les annonces triées par identifiant avec leurs URLs d'image accessibles. */
    public List<RentalResponse> all() {
        return rentals.findAll(Sort.by("id")).stream().map(this::response).toList();
    }

    /** Retourne le détail d'une annonce, ou une erreur 404 si elle n'existe pas. */
    public RentalResponse detail(Integer id) { return response(find(id)); }

    private RentalResponse response(Rental rental) {
        return RentalResponse.from(rental, tokens.imageUrl(rental.getPicture()));
    }

    /** Recherche une annonce pour les traitements métier et échoue en 404 si elle manque. */
    public Rental find(Integer id) {
        return rentals.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Rental not found"));
    }

    /**
     * Enregistre l'image puis crée l'annonce appartenant à l'utilisateur authentifié.
     *
     * @param request champs validés de l'annonce
     * @param picture image reçue dans le formulaire multipart
     * @param subject identifiant du propriétaire extrait du JWT
     * @throws IOException si l'écriture de l'image échoue
     */
    @Transactional(rollbackFor = IOException.class)
    public void create(RentalRequest request, MultipartFile picture, String subject) throws IOException {
        User owner = users.authenticated(subject);
        String url = images.store(picture);
        rentals.saveAndFlush(new Rental(request, owner, url));
    }

    /**
     * Modifie une annonce après avoir vérifié que l'utilisateur connecté en est propriétaire.
     *
     * @param id identifiant de l'annonce
     * @param request nouveaux champs de l'annonce
     * @param subject identifiant utilisateur extrait du JWT
     */
    @Transactional
    public void update(Integer id, RentalRequest request, String subject) {
        User user = users.authenticated(subject);
        Rental rental = find(id);
        if (!rental.getOwner().getId().equals(user.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the owner can update this rental");
        }
        rental.update(request);
    }
}
