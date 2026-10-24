package com.chatop;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Point d'entrée de l'API Spring Boot ChâTop. */
@SpringBootApplication
public class ChatopApplication {

	/** Démarre le contexte Spring et le serveur HTTP. */
	public static void main(String[] args) {
		SpringApplication.run(ChatopApplication.class, args);
	}

}
