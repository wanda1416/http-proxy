package net.wyxj.proxy;

import org.brotli.dec.BrotliInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;

/**
 * Writes each proxied request/response transaction to its own text file so it can be read
 * directly from the capture directory. Compressed bodies (gzip / deflate / br) are decoded
 * before being written; large or binary bodies are stored in a side-car file instead of
 * being inlined.
 */
@Component
public class CaptureRecorder {

    private static final Logger log = LoggerFactory.getLogger(CaptureRecorder.class);

    private static final String NL = System.lineSeparator();

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss.SSS");

    /** Content types (media type without parameters) that are safe to inline as text. */
    private static final List<String> TEXT_TYPES = Arrays.asList(
            "application/json",
            "text/json",
            "application/xml",
            "text/xml",
            "application/xhtml+xml",
            "application/javascript",
            "application/x-www-form-urlencoded",
            "text/plain",
            "text/html",
            "text/csv",
            "text/css");

    private final CaptureProperties properties;
    private final AtomicLong seq = new AtomicLong();
    private Path dir;

    public CaptureRecorder(CaptureProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    public void init() throws IOException {
        this.dir = Paths.get(properties.getDir());
        Files.createDirectories(dir);
        log.info("Captured transactions will be written to {}", dir.toAbsolutePath());
    }

    /**
     * Persist one transaction and emit a single-line summary to the log.
     *
     * @return absolute path of the transaction file that was written
     */
    public Path record(TransactionRecord tx) {
        long id = seq.incrementAndGet();
        LocalDateTime now = LocalDateTime.now();
        String base = now.format(TS) + "-" + id + "-" + tx.method() + "-" + sanitize(tx.displayPath());
        Path txtFile = dir.resolve(base + ".txt");

        StringBuilder buf = new StringBuilder();
        buf.append("************************* Request Message **************************").append(NL);
        buf.append(tx.method()).append(' ').append(tx.requestUrl()).append(NL);
        appendHeaders(buf, tx.requestHeaders());
        appendBody(buf, base, ".req.bin", tx.requestHeaders(), tx.requestBody());
        buf.append(NL);
        buf.append("************************* Response Message *************************").append(NL);
        buf.append("HTTP ").append(tx.statusCode());
        if (tx.statusText() != null && !tx.statusText().isEmpty()) {
            buf.append(' ').append(tx.statusText());
        }
        buf.append(NL);
        appendHeaders(buf, tx.responseHeaders());
        appendBody(buf, base, ".resp.bin", tx.responseHeaders(), tx.responseBody());
        buf.append("********************************************************************").append(NL);

        try {
            Files.write(txtFile, buf.toString().getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            log.warn("Failed to write capture file {}: {}", txtFile, e.getMessage());
        }

        int reqLen = tx.requestBody() == null ? 0 : tx.requestBody().length;
        int respLen = tx.responseBody() == null ? 0 : tx.responseBody().length;
        log.info("{} {} -> {} ({} ms, req {}B, resp {}B) [{}]",
                tx.method(), tx.displayPath(), tx.statusCode(), tx.durationMs(), reqLen, respLen, txtFile.getFileName());
        return txtFile;
    }

    private void appendHeaders(StringBuilder buf, List<HeaderEntry> headers) {
        for (HeaderEntry h : headers) {
            buf.append(h.name()).append(" : ").append(h.value()).append(NL);
        }
    }

    private void appendBody(StringBuilder buf, String base, String binSuffix,
                            List<HeaderEntry> headers, byte[] rawBody) {
        if (rawBody == null || rawBody.length == 0) {
            return;
        }
        buf.append(NL);
        byte[] decoded = decode(rawBody, header(headers, "Content-Encoding"));
        String contentType = header(headers, "Content-Type");
        boolean text = isTextContentType(contentType);
        if (text && decoded.length <= properties.getMaxBody()) {
            buf.append(new String(decoded, charsetOf(contentType)));
            buf.append(NL);
        } else {
            Path bin = dir.resolve(base + binSuffix);
            try {
                Files.write(bin, decoded);
            } catch (IOException e) {
                log.warn("Failed to write side-car body {}: {}", bin, e.getMessage());
            }
            String reason = text ? "too large to inline" : "non-text content";
            buf.append("<body ").append(reason).append(", ").append(decoded.length)
                    .append(" bytes saved to ").append(bin.getFileName()).append('>').append(NL);
        }
    }

    /** Decode a body according to its Content-Encoding. Unknown encodings are returned as-is. */
    static byte[] decode(byte[] body, String contentEncoding) {
        if (body == null || body.length == 0 || contentEncoding == null) {
            return body;
        }
        String enc = contentEncoding.trim().toLowerCase(Locale.ROOT);
        try {
            switch (enc) {
                case "gzip":
                case "x-gzip":
                    return readAll(new GZIPInputStream(new ByteArrayInputStream(body)));
                case "deflate":
                    return readAll(new InflaterInputStream(new ByteArrayInputStream(body)));
                case "br":
                    return readAll(new BrotliInputStream(new ByteArrayInputStream(body)));
                default:
                    return body;
            }
        } catch (IOException e) {
            // If decoding fails (e.g. raw deflate vs zlib), keep the original bytes.
            return body;
        }
    }

    private static byte[] readAll(InputStream in) throws IOException {
        try (InputStream stream = in) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] chunk = new byte[8192];
            int n;
            while ((n = stream.read(chunk)) != -1) {
                out.write(chunk, 0, n);
            }
            return out.toByteArray();
        }
    }

    static boolean isTextContentType(String contentType) {
        if (contentType == null) {
            return false;
        }
        String media = mediaType(contentType);
        if (TEXT_TYPES.contains(media)) {
            return true;
        }
        return media.startsWith("text/") || media.endsWith("+json") || media.endsWith("+xml");
    }

    /** Extract the bare media type, dropping any {@code ; charset=...} style parameters. */
    private static String mediaType(String contentType) {
        int semi = contentType.indexOf(';');
        String media = semi >= 0 ? contentType.substring(0, semi) : contentType;
        return media.trim().toLowerCase(Locale.ROOT);
    }

    private static Charset charsetOf(String contentType) {
        if (contentType != null) {
            for (String part : contentType.split(";")) {
                String p = part.trim();
                if (p.regionMatches(true, 0, "charset=", 0, 8)) {
                    try {
                        return Charset.forName(p.substring(8).trim());
                    } catch (RuntimeException ignored) {
                        // fall through to default
                    }
                }
            }
        }
        return StandardCharsets.UTF_8;
    }

    private static String header(List<HeaderEntry> headers, String name) {
        for (HeaderEntry h : headers) {
            if (h.name().equalsIgnoreCase(name)) {
                return h.value();
            }
        }
        return null;
    }

    private static String sanitize(String path) {
        if (path == null || path.isEmpty() || "/".equals(path)) {
            return "root";
        }
        String s = path.replaceAll("^/+", "").replaceAll("[^a-zA-Z0-9._-]", "_");
        if (s.isEmpty()) {
            return "root";
        }
        return s.length() > 60 ? s.substring(0, 60) : s;
    }

    /** A single HTTP header line. */
    public record HeaderEntry(String name, String value) {
    }

    /** A framework-neutral snapshot of one proxied request/response exchange. */
    public record TransactionRecord(
            String method,
            String requestUrl,
            String displayPath,
            List<HeaderEntry> requestHeaders,
            byte[] requestBody,
            int statusCode,
            String statusText,
            List<HeaderEntry> responseHeaders,
            byte[] responseBody,
            long durationMs) {
    }
}
