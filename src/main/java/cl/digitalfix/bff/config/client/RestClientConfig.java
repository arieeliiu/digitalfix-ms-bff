package cl.digitalfix.bff.config.client;

import java.net.http.HttpClient;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class RestClientConfig {
    @Bean
    JdkClientHttpRequestFactory dominioRequestFactory() {
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3)).build());
        factory.setReadTimeout(Duration.ofSeconds(10));
        return factory;
    }

    @Bean
    RestClient catalogRestClient(@Value("${digitalfix.catalog-url}") String url,
            JdkClientHttpRequestFactory dominioRequestFactory) {
        return RestClient.builder().baseUrl(url).requestFactory(dominioRequestFactory).build();
    }

    @Bean
    RestClient workordersRestClient(@Value("${digitalfix.workorders-url}") String url,
            JdkClientHttpRequestFactory dominioRequestFactory) {
        return RestClient.builder().baseUrl(url).requestFactory(dominioRequestFactory).build();
    }
}
