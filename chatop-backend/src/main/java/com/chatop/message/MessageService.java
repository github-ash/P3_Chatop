package com.chatop.message;

import com.chatop.rental.RentalService;
import com.chatop.user.User;
import com.chatop.user.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Gère l'enregistrement des messages et vérifie l'identité de leur expéditeur. */
@Service
public class MessageService {
    private final MessageRepository messages;
    private final RentalService rentals;
    private final UserService users;

    public MessageService(MessageRepository messages, RentalService rentals, UserService users) {
        this.messages = messages;
        this.rentals = rentals;
        this.users = users;
    }

    /**
     * Enregistre un message si son expéditeur est bien l'utilisateur du JWT.
     *
     * @param request annonce, expéditeur et contenu du message
     * @param subject identifiant utilisateur extrait du JWT vérifié
     */
    @Transactional
    public void send(MessageRequest request, String subject) {
        User sender = users.authenticated(subject);
        if (!sender.getId().equals(request.user_id())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Message sender must match authenticated user");
        }
        messages.save(new Message(rentals.find(request.rental_id()), sender, request.message()));
    }
}
