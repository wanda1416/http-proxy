package net.wyxj.proxy;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

import javax.servlet.http.HttpServletRequest;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Slf4j
public class HttpLogUtils {

    private static final String NL = System.getProperty("line.separator");

    private static final List<String> TEXT_TYPES = Arrays.asList(
            "application/json",
            "text/json",
            "application/xml",
            "text/xml",
            "application/xhtml+xml",
            "text/plain",
            "text/html");

    private HttpLogUtils() {
    }

    public static void log(HttpServletRequest request, byte[] requestBody) {
        StringBuilder buf = new StringBuilder();
        buf.append(NL);
        buf.append("************************* Request Message **************************").append(NL);
        String requestUri = request.getRequestURL().toString();
        if (request.getQueryString() != null) {
            requestUri = requestUri + "?" + request.getQueryString();
        }
        buf.append(request.getMethod()).append(" ").append(requestUri);
        buf.append(NL);
        Enumeration<String> headerIter = request.getHeaderNames();
        while (headerIter.hasMoreElements()) {
            String headerName = headerIter.nextElement();
            Enumeration<String> values = request.getHeaders(headerName);
            while (values.hasMoreElements()) {
                buf.append(headerName).append(" : ").append(values.nextElement());
                buf.append(NL);
            }
        }
        if (requestBody != null) {
            buf.append(NL);
            String content = new String(requestBody, getCharsetEncoding(request, null));
            buf.append(content);
            buf.append(NL);
        }
        buf.append("********************************************************************");
        log.info(buf.toString());
    }

    public static void log(HttpServletRequest request, ResponseEntity<byte[]> response, byte[] responseBody) {
        StringBuilder buf = new StringBuilder();
        buf.append(NL);
        buf.append("************************* Response Message *************************").append(NL);
        buf.append("HTTP/1.1 ").append(response.getStatusCode()).append(NL);
        HttpHeaders headers = response.getHeaders();
        for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
            String headerName = entry.getKey();
            List<String> values = entry.getValue();
            for (String value : Objects.requireNonNull(values)) {
                buf.append(headerName).append(" : ").append(value);
                buf.append(NL);
            }
        }
        if (responseBody != null && isLogResponseBody(headers)) {
            buf.append(NL);
            String content = new String(responseBody, getCharsetEncoding(request, headers));
            buf.append(content);
            buf.append(NL);
        }
        buf.append("********************************************************************");
        log.info(buf.toString());
    }

    @SneakyThrows
    private static Charset getCharsetEncoding(HttpServletRequest request, HttpHeaders headers) {
        Charset charset = null;
        if (headers != null) {
            if (headers.getContentType() != null) {
                charset = headers.getContentType().getCharset();
            }
        }
        if (charset == null) {
            String encoding = request.getCharacterEncoding();
            if (encoding != null) {
                charset = Charset.forName(encoding);
            }
        }
        if (charset == null) {
            charset = StandardCharsets.UTF_8;
        }
        return charset;
    }

    private static boolean isLogResponseBody(HttpHeaders headers) {
        List<String> contentTypes = headers.get("Content-Type");
        if (contentTypes == null || contentTypes.isEmpty()) {
            return false;
        }
        String contentType = contentTypes.get(0);
        return TEXT_TYPES.contains(contentType);
    }

}
