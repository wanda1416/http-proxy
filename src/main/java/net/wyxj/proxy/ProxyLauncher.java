package net.wyxj.proxy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import javax.annotation.PostConstruct;
import javax.servlet.http.HttpServletRequest;
import java.util.Objects;

@RestController
@SpringBootApplication
public class ProxyLauncher {

    private static final Logger logger = LoggerFactory.getLogger(ProxyLauncher.class);

    @Value("${forward.url}")
    private String forwardUrl;

    @PostConstruct
    public void init() {
        logger.info("Now, all request will forward to {}", forwardUrl);
    }

    @RequestMapping("/**")
    public ResponseEntity<String> forward(HttpServletRequest request, HttpEntity<String> httpEntity) {
        String method = request.getMethod();
        String url = forwardUrl + request.getRequestURI();
        if (request.getQueryString() != null) {
            url = url + "?" + request.getQueryString();
        }
        RestTemplate restTemplate = new RestTemplate();
        HttpLogUtils.log(request, httpEntity.getBody());
        ResponseEntity<String> response = restTemplate.exchange(
                url,
                Objects.requireNonNull(HttpMethod.resolve(method)),
                httpEntity, String.class);
        HttpLogUtils.log(response, response.getBody());
        return response;
    }

    public static void main(String[] args) {
        SpringApplication.run(ProxyLauncher.class, args);
    }

}
