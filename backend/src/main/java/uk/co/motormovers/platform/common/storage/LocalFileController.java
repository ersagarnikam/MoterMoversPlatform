package uk.co.motormovers.platform.common.storage;

import java.io.IOException;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Stands in for the bucket when files are on this machine: a video element cannot send a sign-in token, so these are
 * open and the signature in the link is the check, as with a presigned link. With a real bucket, every request here is
 * refused. Returning a Resource gives range requests, so seeking can be tried locally.
 */
@RestController
public class LocalFileController {

    private final FileStorage storage;

    public LocalFileController(FileStorage storage) {
        this.storage = storage;
    }

    @PutMapping("/api/files/local/uploads/{name}")
    public ResponseEntity<Void> upload(@PathVariable String name, @RequestParam long expires,
            @RequestParam String type, @RequestParam long size, @RequestParam String sig,
            @RequestHeader(HttpHeaders.CONTENT_TYPE) String sentType, HttpServletRequest request) throws IOException {
        local().save(key(name), expires, type, size, sig, sentType, request.getInputStream());
        return ResponseEntity.ok().build();
    }

    @GetMapping("/api/files/local/uploads/{name}")
    public ResponseEntity<Resource> read(@PathVariable String name, @RequestParam long expires,
            @RequestParam String sig) {
        String key = key(name);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(FileTypes.servedAs(key)))
                .header("X-Content-Type-Options", "nosniff")
                .body(new FileSystemResource(local().open(key, expires, sig)));
    }

    private LocalFileStorage local() {
        if (storage instanceof LocalFileStorage local) {
            return local;
        }
        throw new AccessDeniedException("Files are not kept here");
    }

    private static String key(String name) {
        String key = "uploads/" + name;
        if (!FileTypes.isKey(key)) {
            throw new AccessDeniedException("This file link is not valid or has expired");
        }
        return key;
    }
}
