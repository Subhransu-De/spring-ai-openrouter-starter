package de.subhransu.openrouter.springai.garage.evidence;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;

/** Demonstrates the starter's custom blocking and streaming HTTP builder extension points. */
@Configuration(proxyBeanMethods = false)
public class GarageTransportConfiguration {

  @Bean
  RestClient.Builder garageRestClientBuilder(GarageTransportEvidence transportEvidence) {
    return RestClient.builder().requestInterceptor(transportEvidence.restInterceptor());
  }

  @Bean
  WebClient.Builder garageWebClientBuilder(GarageTransportEvidence transportEvidence) {
    return WebClient.builder().filter(transportEvidence.webFilter());
  }
}
