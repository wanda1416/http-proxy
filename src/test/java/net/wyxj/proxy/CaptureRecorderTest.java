package net.wyxj.proxy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CaptureRecorderTest {

    @Test
    void decodesGzipBody() throws IOException {
        byte[] gzipped = gzip("hello world");
        byte[] decoded = CaptureRecorder.decode(gzipped, "gzip");
        assertEquals("hello world", new String(decoded, StandardCharsets.UTF_8));
    }

    @Test
    void returnsBodyUnchangedForUnknownEncoding() {
        byte[] raw = "plain".getBytes(StandardCharsets.UTF_8);
        assertEquals(raw, CaptureRecorder.decode(raw, "identity"));
        assertEquals(raw, CaptureRecorder.decode(raw, null));
    }

    @Test
    void detectsTextContentTypesIgnoringParameters() {
        assertTrue(CaptureRecorder.isTextContentType("application/json; charset=utf-8"));
        assertTrue(CaptureRecorder.isTextContentType("text/html"));
        assertTrue(CaptureRecorder.isTextContentType("application/vnd.api+json"));
        assertFalse(CaptureRecorder.isTextContentType("application/octet-stream"));
        assertFalse(CaptureRecorder.isTextContentType("image/png"));
        assertFalse(CaptureRecorder.isTextContentType(null));
    }

    @Test
    void inlinesTextBodyAndWritesTransactionFile(@TempDir Path tmp) throws IOException {
        CaptureRecorder recorder = newRecorder(tmp, 1024);
        Path file = recorder.record(new CaptureRecorder.TransactionRecord(
                "POST", "http://upstream/api", "/api",
                List.of(new CaptureRecorder.HeaderEntry("Content-Type", "application/json")),
                "{\"a\":1}".getBytes(StandardCharsets.UTF_8),
                200, "OK",
                List.of(new CaptureRecorder.HeaderEntry("Content-Type", "application/json"),
                        new CaptureRecorder.HeaderEntry("Content-Encoding", "gzip")),
                gzip("{\"ok\":true}"), 12));

        assertTrue(Files.exists(file));
        String content = Files.readString(file);
        assertTrue(content.contains("POST http://upstream/api"));
        assertTrue(content.contains("{\"a\":1}"));
        // Response body was gzipped upstream but must be inlined decompressed.
        assertTrue(content.contains("{\"ok\":true}"));
    }

    @Test
    void spillsLargeBodyToSideCarFile(@TempDir Path tmp) throws IOException {
        CaptureRecorder recorder = newRecorder(tmp, 8);
        byte[] big = "this text is definitely longer than eight bytes".getBytes(StandardCharsets.UTF_8);
        Path file = recorder.record(new CaptureRecorder.TransactionRecord(
                "GET", "http://upstream/big", "/big",
                List.of(), null,
                200, "OK",
                List.of(new CaptureRecorder.HeaderEntry("Content-Type", "text/plain")),
                big, 5));

        String content = Files.readString(file);
        assertTrue(content.contains("saved to"));
        try (var sidecars = Files.list(tmp)) {
            assertTrue(sidecars.anyMatch(p -> p.getFileName().toString().endsWith(".resp.bin")));
        }
    }

    private CaptureRecorder newRecorder(Path dir, long maxBody) throws IOException {
        CaptureProperties props = new CaptureProperties();
        props.setDir(dir.toString());
        props.setMaxBody(maxBody);
        CaptureRecorder recorder = new CaptureRecorder(props);
        recorder.init();
        return recorder;
    }

    private static byte[] gzip(String text) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (GZIPOutputStream gz = new GZIPOutputStream(out)) {
            gz.write(text.getBytes(StandardCharsets.UTF_8));
        }
        return out.toByteArray();
    }
}
