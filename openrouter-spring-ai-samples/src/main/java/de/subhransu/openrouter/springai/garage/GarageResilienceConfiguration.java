package de.subhransu.openrouter.springai.garage;

import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;

/** Demonstrates injection of a bounded retry policy into the auto-configured chat model. */
@Configuration(proxyBeanMethods = false)
public class GarageResilienceConfiguration {

  @Bean
  RetryTemplate garageRetryTemplate() {
    RetryPolicy policy =
        RetryPolicy.builder()
            .maxRetries(2)
            .delay(Duration.ofMillis(200))
            .includes(RuntimeException.class)
            .build();
    return new RetryTemplate(policy);
  }
}
