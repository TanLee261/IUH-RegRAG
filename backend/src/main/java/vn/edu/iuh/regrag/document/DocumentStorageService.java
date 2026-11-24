package vn.edu.iuh.regrag.document;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
@Profile("db")
public class DocumentStorageService {

    private final Path uploadDir;

    public DocumentStorageService(@Value("${app.document.upload-dir:uploads/documents}") String uploadDir) {
        this.uploadDir = Paths.get(uploadDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.uploadDir);
        } catch (IOException e) {
            throw new IllegalStateException("Could not create document upload directory: " + this.uploadDir, e);
        }
    }

    /**
     * Saves a multipart file to the upload directory.
     * Returns metadata including the storage key and SHA-256 hash.
     */
    public StoredFile store(UUID documentId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File must not be empty");
        }
        String originalName = sanitizeFilename(file.getOriginalFilename());
        String storageKey = documentId + "_" + originalName;
        Path destination = uploadDir.resolve(storageKey).normalize();

        // Guard against path traversal
        if (!destination.startsWith(uploadDir)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid file name");
        }

        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }

        try (InputStream in = new DigestInputStream(file.getInputStream(), digest)) {
            Files.copy(in, destination, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to store file: " + e.getMessage());
        }

        String sha256 = HexFormat.of().formatHex(digest.digest());
        return new StoredFile(storageKey, originalName, sha256, file.getSize());
    }

    /**
     * Deletes the physical file associated with the given storage key.
     * Silently ignores if the file does not exist.
     */
    public void delete(String storageKey) {
        if (storageKey == null || storageKey.isBlank()) {
            return;
        }
        Path target = uploadDir.resolve(storageKey).normalize();
        if (!target.startsWith(uploadDir)) {
            return; // Guard against path traversal
        }
        try {
            Files.deleteIfExists(target);
        } catch (IOException e) {
            // Log and continue; do not block the DB delete
        }
    }

    private static String sanitizeFilename(String name) {
        if (name == null || name.isBlank()) {
            return "unnamed";
        }
        // Keep only safe characters
        return name.trim()
                .replaceAll("[^a-zA-Z0-9._\\-]", "_")
                .replaceAll("_{2,}", "_");
    }

    public record StoredFile(String storageKey, String originalFilename, String contentSha256, long size) {}
}
