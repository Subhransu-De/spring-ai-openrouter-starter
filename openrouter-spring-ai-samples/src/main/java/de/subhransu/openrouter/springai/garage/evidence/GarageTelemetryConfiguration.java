package de.subhransu.openrouter.springai.garage.evidence;

import io.micrometer.core.instrument.observation.DefaultMeterObservationHandler;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.ai.chat.observation.ChatModelObservationConvention;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Installs a real observation and meter path for the Garage application. */
@Configuration(proxyBeanMethods = false)
public class GarageTelemetryConfiguration {

  @Bean
  SimpleMeterRegistry garageMeterRegistry() {
    return new SimpleMeterRegistry();
  }

  @Bean
  GarageTelemetry garageTelemetry(
      SimpleMeterRegistry meterRegistry, GarageEvidence evidence) {
    return new GarageTelemetry(meterRegistry, evidence);
  }

  @Bean
  ObservationRegistry observationRegistry(
      SimpleMeterRegistry meterRegistry, GarageTelemetry telemetry) {
    ObservationRegistry registry = ObservationRegistry.create();
    registry
        .observationConfig()
        .observationHandler(new DefaultMeterObservationHandler(meterRegistry))
        .observationHandler(telemetry);
    return registry;
  }

  @Bean
  ChatModelObservationConvention garageObservationConvention() {
    return new GarageObservationConvention();
  }
}
