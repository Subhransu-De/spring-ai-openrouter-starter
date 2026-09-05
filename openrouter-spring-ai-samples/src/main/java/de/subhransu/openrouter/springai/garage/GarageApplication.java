package de.subhransu.openrouter.springai.garage;

import tools.jackson.databind.ObjectMapper;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
@ConfigurationPropertiesScan
public class GarageApplication {

  public static void main(String[] args) {
    SpringApplication.run(GarageApplication.class, args);
  }

  @Bean
  ObjectMapper objectMapper() {
    return new ObjectMapper();
  }
}
