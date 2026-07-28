package net.wyxj.proxy;

import okhttp3.OkHttpClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * Infrastructure beans for the proxy. Kept separate from {@link ProxyLauncher} so the
 * controller can inject the {@link OkHttpClient} without a self-referential bean cycle.
 */
@Configuration
public class ProxyConfig {

    @Bean
    public OkHttpClient okHttpClient() {
        return new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                // Do not follow redirects: forward the upstream 3xx response verbatim.
                .followRedirects(false)
                .followSslRedirects(false)
                .build();
    }
}
