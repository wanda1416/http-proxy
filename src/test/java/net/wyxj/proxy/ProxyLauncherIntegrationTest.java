package net.wyxj.proxy;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ProxyLauncherIntegrationTest {

    private static MockWebServer upstream;

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @BeforeAll
    static void startUpstream() throws IOException {
        upstream = new MockWebServer();
        upstream.start();
    }

    @AfterAll
    static void stopUpstream() throws IOException {
        upstream.shutdown();
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("forward.url", () -> "http://localhost:" + upstream.getPort());
        registry.add("capture.dir", () -> System.getProperty("java.io.tmpdir") + "/http-proxy-it");
    }

    @Test
    void forwardsPostWithBodyAndHeadersPreservingQueryString() throws InterruptedException {
        upstream.enqueue(new MockResponse()
                .setResponseCode(201)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"created\":true}"));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.add("X-Custom", "abc");
        ResponseEntity<String> response = restTemplate.exchange(
                "http://localhost:" + port + "/orders?id=42",
                HttpMethod.POST,
                new HttpEntity<>("{\"name\":\"x\"}", headers),
                String.class);

        assertEquals(201, response.getStatusCode().value());
        assertEquals("{\"created\":true}", response.getBody());

        RecordedRequest forwarded = upstream.takeRequest(5, TimeUnit.SECONDS);
        assertEquals("POST", forwarded.getMethod());
        assertEquals("/orders?id=42", forwarded.getPath());
        assertEquals("abc", forwarded.getHeader("X-Custom"));
        assertEquals("{\"name\":\"x\"}", forwarded.getBody().readUtf8());
    }

    @Test
    void forwardsSimpleGetRequest() {
        upstream.enqueue(new MockResponse().setResponseCode(200).setBody("ok"));
        ResponseEntity<String> response = restTemplate.getForEntity(
                "http://localhost:" + port + "/ping", String.class);
        assertTrue(response.getStatusCode().is2xxSuccessful());
        assertEquals("ok", response.getBody());
    }
}
