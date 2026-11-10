package uk.co.motormovers.platform.common.storage;

import java.time.Clock;
import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
    private final UnsavedUploadRepository unsaved;
    private final Clock clock;

    public FileUploadController(FileStorage storage, StorageProperties properties, AccessPolicy access,
            UnsavedUploadRepository unsaved, Clock clock) {
        this.storage = storage;
        this.properties = properties;
        this.access = access;
        this.unsaved = unsaved;
        this.clock = clock;
    }

    public record UploadRules(boolean available, int maxMegabytes, List<String> types, String formats) {
    }

    public record NewUpload(String contentType, long size) {
    }

    public record Upload(String key, String url, String contentType) {
    }

    @GetMapping
    public UploadRules rules() {
        access.requireAdmin();
        return new UploadRules(storage.available(), properties.maxUploadMb(), FileTypes.allowed(),
                FileTypes.FORMATS);
    }

    @PostMapping
    public Upload start(@RequestBody NewUpload request) {
        access.requireAdmin();
        if (!storage.available()) {
            throw new IllegalArgumentException("File uploads are not set up here. Paste a link instead.");
        }
        if (request.contentType() == null || request.contentType().isBlank()) {
            throw new IllegalArgumentException("We could not tell what kind of file that is. Please use "
                    + FileTypes.FORMATS + ".");
        }
        if (!FileTypes.isAllowed(request.contentType())) {
            throw new IllegalArgumentException("That kind of file cannot be uploaded. Please use " + FileTypes.FORMATS
                    + ". A MOV film needs saving as MP4 first.");
        }
        if (request.size() <= 0) {
            throw new IllegalArgumentException("That file is empty. Please choose another.");
        }
        if (request.size() > properties.maxUploadMb() * 1024L * 1024L) {
            throw new IllegalArgumentException("That file is too large: the most is " + properties.maxUploadMb() + " MB.");
        }
        String key = FileTypes.newKey(request.contentType());
        unsaved.save(new UnsavedUpload(key, clock.instant()));
        return new Upload(key, storage.uploadLink(key, request.contentType(), request.size()), request.contentType());
    }
}
