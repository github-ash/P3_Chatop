package com.chatop.image;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.util.Iterator;
import java.util.UUID;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

/** Valide, stocke et retrouve les images utilisées par les annonces. */
@Service
public class ImageService {
    private static final Logger LOGGER = LoggerFactory.getLogger(ImageService.class);
    private final Path directory;
    private final String baseUrl;

    /** Prépare le répertoire de stockage local et l'URL publique des images. */
    public ImageService(@Value("${chatop.images.directory}") String directory,
                        @Value("${chatop.images.base-url}") String baseUrl) throws IOException {
        this.directory = Path.of(directory).toAbsolutePath().normalize();
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        Files.createDirectories(this.directory);
    }

    /**
     * Valide une image PNG/JPEG, la réencode en PNG et la stocke sous un nom UUID.
     *
     * @param file image reçue depuis le formulaire
     * @return URL stable du fichier enregistré
     * @throws IOException si la lecture ou l'écriture du fichier échoue
     */
    public String store(MultipartFile file) throws IOException {
        if (file.isEmpty() || file.getSize() > 5 * 1024 * 1024) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Image must be between 1 byte and 5 MB");
        }
        BufferedImage image;
        try (InputStream stream = file.getInputStream();
             ImageInputStream input = ImageIO.createImageInputStream(stream)) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid image");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(input);
                String format = reader.getFormatName();
                if (!format.equalsIgnoreCase("png") && !format.equalsIgnoreCase("jpeg")) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only PNG and JPEG are supported");
                }
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width <= 0 || height <= 0 || (long) width * height > 16_000_000) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Image exceeds 16 megapixels");
                }
                image = reader.read(0);
            } catch (javax.imageio.IIOException exception) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unreadable image", exception);
            } finally {
                reader.dispose();
            }
        }
        String name = UUID.randomUUID() + ".png";
        Path path = directory.resolve(name);
        try {
            if (!ImageIO.write(image, "png", path.toFile())) {
                throw new IOException("PNG writer unavailable");
            }
        } catch (IOException exception) {
            Files.deleteIfExists(path);
            throw exception;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException exception) {
                        LOGGER.error("Cannot clean up image after rollback: {}", name, exception);
                    }
                }
            }
        });
        String url = baseUrl + "/" + name;
        if (url.length() > 255) {
            throw new IllegalStateException("Image URL exceeds database column length");
        }
        return url;
    }

    /**
     * Résout un nom UUID vers un fichier régulier du répertoire d'images.
     *
     * @param name nom de fichier demandé
     * @return chemin de l'image existante
     */
    public Path find(String name) {
        if (!name.matches("[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}\\.png")) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Image not found");
        }
        Path path = directory.resolve(name);
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Image not found");
        }
        return path;
    }
}
