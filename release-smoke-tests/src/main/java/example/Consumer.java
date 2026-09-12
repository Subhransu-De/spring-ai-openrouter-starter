package example;

import java.util.Map;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.image.ImageModel;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableAutoConfiguration
public class Consumer {

    public static void main(String[] args) {
        try (var context = new SpringApplicationBuilder(Consumer.class)
                .web(WebApplicationType.NONE)
                .properties(Map.of(
                        "spring.ai.openrouter.api-key", "synthetic-packaging-test-key",
                        "spring.ai.model.chat", "openrouter",
                        "spring.ai.model.embedding", "openrouter",
                        "spring.ai.model.image", "openrouter"))
                .run()) {
            context.getBean(ChatModel.class);
            context.getBean(EmbeddingModel.class);
            context.getBean(ImageModel.class);
            context.getBean(ChatClient.Builder.class).build();
        }
    }
}
