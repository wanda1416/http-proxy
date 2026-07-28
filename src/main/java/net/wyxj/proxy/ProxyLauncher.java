package net.wyxj.proxy;

import okhttp3.Headers;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Transparent single-target HTTP proxy: forwards every incoming request to {@code forward.url}
 * unchanged and records the full request/response of each exchange via {@link CaptureRecorder}.
 */
@RestController
@SpringBootApplication
@EnableConfigurationProperties(CaptureProperties.class)
public class ProxyLauncher {

    private static final Logger logger = LoggerFactory.getLogger(ProxyLauncher.class);

    /**
     * Hop-by-hop headers that are connection-specific and must not be forwarded end-to-end
     * (per RFC 7230 6.1). {@code Content-Length} is dropped so it is recomputed from the body.
     */
    private static final Set<String> HOP_BY_HOP = Set.of(
            "connection", "keep-alive", "transfer-encoding", "te", "trailer",
            "upgrade", "proxy-authenticate", "proxy-authorization", "content-length");

    /** Methods that never carry a request body. */
    private static final Set<String> BODYLESS_METHODS = Set.of("GET", "HEAD");

    @Value("${forward.url}")
    private String forwardUrl;

    @Value("${authorize:false}")
    private boolean authorize;

    @Value("${username:}")
    private String username;

    @Value("${password:}")
    private String password;

    private final OkHttpClient httpClient;
    private final CaptureRecorder recorder;

    public ProxyLauncher(OkHttpClient httpClient, CaptureRecorder recorder) {
        this.httpClient = httpClient;
        this.recorder = recorder;
    }

    @PostConstruct
    public void init() {
        logger.info("All requests will be forwarded to {}", forwardUrl);
        if (authorize) {
            logger.info("Basic authorization is enabled for user '{}'", username);
        }
    }

    @RequestMapping("/**")
    public ResponseEntity<byte[]> forward(HttpServletRequest request, HttpEntity<byte[]> httpEntity) {
        byte[] requestBody = httpEntity.getBody();
        String method = request.getMethod().toUpperCase(Locale.ROOT);
        String targetUrl = forwardUrl + request.getRequestURI();
        if (request.getQueryString() != null) {
            targetUrl = targetUrl + "?" + request.getQueryString();
        }

        List<CaptureRecorder.HeaderEntry> requestHeaders = new ArrayList<>();
        Request.Builder upstream = new Request.Builder().url(targetUrl);
        Enumeration<String> names = request.getHeaderNames();
        while (names.hasMoreElements()) {
            String name = names.nextElement();
            Enumeration<String> values = request.getHeaders(name);
            while (values.hasMoreElements()) {
                String value = values.nextElement();
                requestHeaders.add(new CaptureRecorder.HeaderEntry(name, value));
                // Host is reset by OkHttp from the target URL; hop-by-hop headers are dropped.
                if (!"host".equalsIgnoreCase(name) && !HOP_BY_HOP.contains(name.toLowerCase(Locale.ROOT))) {
                    upstream.addHeader(name, value);
                }
            }
        }

        if (authorize) {
            String token = Base64.getEncoder()
                    .encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8));
            upstream.header("Authorization", "Basic " + token);
            requestHeaders.add(new CaptureRecorder.HeaderEntry("Authorization", "Basic " + token));
        }

        upstream.method(method, buildRequestBody(method, requestBody, request.getContentType()));

        String displayPath = request.getRequestURI();
        long start = System.currentTimeMillis();
        try (Response response = httpClient.newCall(upstream.build()).execute()) {
            byte[] responseBody = bodyBytes(response);
            long duration = System.currentTimeMillis() - start;

            List<CaptureRecorder.HeaderEntry> responseHeaders = toEntries(response.headers());
            recorder.record(new CaptureRecorder.TransactionRecord(
                    method, targetUrl, displayPath, requestHeaders, requestBody,
                    response.code(), response.message(), responseHeaders, responseBody, duration));

            return buildClientResponse(response, responseBody);
        } catch (IOException e) {
            long duration = System.currentTimeMillis() - start;
            logger.warn("Failed to forward {} {}: {}", method, targetUrl, e.getMessage());
            byte[] body = ("Proxy failed to reach upstream: " + e.getMessage())
                    .getBytes(StandardCharsets.UTF_8);
            recorder.record(new CaptureRecorder.TransactionRecord(
                    method, targetUrl, displayPath, requestHeaders, requestBody,
                    HttpStatus.BAD_GATEWAY.value(), "Bad Gateway", List.of(), body, duration));
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(body);
        }
    }

    private RequestBody buildRequestBody(String method, byte[] body, String contentType) {
        if (BODYLESS_METHODS.contains(method)) {
            return null;
        }
        MediaType mediaType = contentType != null ? MediaType.parse(contentType) : null;
        return RequestBody.create(body != null ? body : new byte[0], mediaType);
    }

    private static byte[] bodyBytes(Response response) throws IOException {
        ResponseBody body = response.body();
        return body != null ? body.bytes() : new byte[0];
    }

    private static List<CaptureRecorder.HeaderEntry> toEntries(Headers headers) {
        List<CaptureRecorder.HeaderEntry> entries = new ArrayList<>();
        for (int i = 0; i < headers.size(); i++) {
            entries.add(new CaptureRecorder.HeaderEntry(headers.name(i), headers.value(i)));
        }
        return entries;
    }

    private ResponseEntity<byte[]> buildClientResponse(Response response, byte[] body) {
        HttpHeaders headers = new HttpHeaders();
        Headers upstreamHeaders = response.headers();
        for (int i = 0; i < upstreamHeaders.size(); i++) {
            String name = upstreamHeaders.name(i);
            if (!HOP_BY_HOP.contains(name.toLowerCase(Locale.ROOT))) {
                headers.add(name, upstreamHeaders.value(i));
            }
        }
        return new ResponseEntity<>(body, headers, response.code());
    }

    public static void main(String[] args) {
        SpringApplication.run(ProxyLauncher.class, args);
    }
}
