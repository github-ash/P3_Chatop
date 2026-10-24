package com.chatop.message;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

/** Expose l'envoi des messages liés à une annonce. */
@RestController
@RequestMapping("/api/messages")
@SecurityRequirement(name = "bearerAuth")
public class MessageController {
    private final MessageService messages;

    /** Reçoit le service chargé de valider et d'enregistrer les messages. */
    public MessageController(MessageService messages) { this.messages = messages; }

    /**
     * Enregistre un message en vérifiant que son expéditeur correspond au JWT.
     *
     * @param request identifiants de l'annonce et de l'expéditeur, et contenu du message
     * @param jwt jeton de l'utilisateur authentifié
     * @return confirmation d'envoi
     */
    @Operation(summary = "Envoyer un message au sujet d'une location")
    @PostMapping
    public Map<String, String> send(@Valid @RequestBody MessageRequest request, @AuthenticationPrincipal Jwt jwt) {
        messages.send(request, jwt.getSubject());
        return Map.of("message", "Message send with success");
    }
}
