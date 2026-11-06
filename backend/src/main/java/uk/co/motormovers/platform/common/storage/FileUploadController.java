package uk.co.motormovers.platform.common.storage;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

import uk.co.motormovers.platform.security.service.AccessPolicy;

/**
 * The team's uploads. The API only agrees to a file and hands back a link; the browser sends the file straight to the
 * store, so a film never passes through nginx or the API.
 */
@RestController
@RequestMapping("/api/admin/files")
public class FileUploadController {

    private final FileStorage storage;
    private final StorageProperties properties;
    private final AccessPolicy access;

    public FileUploadController(FileStorage storage, StorageProperties properties, AccessPolicy access) {
        this.storage = storage;
        this.properties = properties;
        this.access = access;
    }

    public record UploadRules(boolean available, int maxMegabytes, List<String> types) {
    }

    public record NewUpload(@NotBlank String contentType, @Positive long size) {
    }

    public record Upload(String key, String url, String contentType) {
    }

    @GetMapping
    public UploadRules rules() {
        access.requireAdmin();
        return new UploadRules(storage.available(), properties.maxUploadMb(), FileTypes.allowed());
    }

    @PostMapping
    public Upload start(@Valid @RequestBody NewUpload request) {
        access.requireAdmin();
        if (!storage.available()) {
            throw new IllegalArgumentException("File uploads are not set up here. Paste a link instead.");
        }
        if (!FileTypes.isAllowed(request.contentType())) {
            throw new IllegalArgumentException("Only video, audio, PDF and image files can be uploaded.");
        }
        if (request.size() > properties.maxUploadMb() * 1024L * 1024L) {
            throw new IllegalArgumentException("That file is too large: the most is " + properties.maxUploadMb() + " MB.");
        }
        String key = FileTypes.newKey(request.contentType());
        return new Upload(key, storage.uploadLink(key, request.contentType(), request.size()), request.contentType());
    }
}
