package uk.co.motormovers.platform.common.storage;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/** What can be uploaded, and the storage keys the API gives out. A key is never built from a file's own name. */
public final class FileTypes {

    private static final Map<String, String> EXTENSIONS = Map.ofEntries(
            Map.entry("video/mp4", "mp4"),
            Map.entry("video/webm", "webm"),
            Map.entry("audio/mpeg", "mp3"),
            Map.entry("audio/mp4", "m4a"),
            Map.entry("audio/x-m4a", "m4a"),
            Map.entry("audio/wav", "wav"),
            Map.entry("audio/x-wav", "wav"),
            Map.entry("application/pdf", "pdf"),
            Map.entry("image/png", "png"),
            Map.entry("image/jpeg", "jpg"));

    /** The one type each extension is served as; browsers name some of these types differently. */
    private static final Map<String, String> SERVED_AS = Map.of("mp4", "video/mp4", "webm", "video/webm",
            "mp3", "audio/mpeg", "m4a", "audio/mp4", "wav", "audio/wav", "pdf", "application/pdf",
            "png", "image/png", "jpg", "image/jpeg");

    private static final Pattern KEY = Pattern.compile("uploads/[0-9a-f-]{36}\\.[a-z0-9]{3,4}");

    /** The formats in words, for messages. */
    public static final String FORMATS = "MP4 or WebM video, MP3, M4A or WAV audio, PDF, PNG or JPEG";

    private FileTypes() {
    }

    public static List<String> allowed() {
        return EXTENSIONS.keySet().stream().sorted().toList();
    }

    public static boolean isAllowed(String contentType) {
        return EXTENSIONS.containsKey(contentType);
    }

    public static String newKey(String contentType) {
        return "uploads/" + UUID.randomUUID() + "." + EXTENSIONS.get(contentType);
    }

    public static boolean isKey(String key) {
        return key != null && KEY.matcher(key).matches();
    }

    /**
     * A safe name to save a file under: the title's letters, digits and apostrophes (O'Brien), in any language, and the
     * file's extension.
     */
    public static String fileName(String title, String key) {
        String name = Normalizer.normalize(title, Normalizer.Form.NFC).replaceAll("[^\\p{L}\\p{N}'\u2019 _-]", "")
                .strip();
        name = name.length() > 80 ? name.substring(0, 80).strip() : name;
        return (name.isEmpty() ? "Motor Movers file" : name) + key.substring(key.lastIndexOf('.'));
    }

    /**
     * The header that saves a file under this name: filename* carries the real name, and filename a plain version for
     * anything that cannot read it ("Rím" becomes "Rim").
     */
    public static String contentDisposition(String fileName) {
        String plain = Normalizer.normalize(fileName.replace('\u2019', '\''), Normalizer.Form.NFD)
                .replaceAll("[^\\x20-\\x7E]", "");
        String encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
        return "attachment; filename=\"" + plain + "\"; filename*=UTF-8''" + encoded;
    }

    public static String servedAs(String key) {
        return SERVED_AS.getOrDefault(key.substring(key.lastIndexOf('.') + 1), "application/octet-stream");
    }
}
