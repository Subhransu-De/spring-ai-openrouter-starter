description = "OpenRouterApi client, DTOs, options, mappers, ChatModel, EmbeddingModel."

val archunitVersion = rootProject.extra["archunitVersion"] as String

dependencies {
	api("org.springframework.ai:spring-ai-model")
	api("org.springframework.ai:spring-ai-retry")
	api("org.springframework:spring-web")
	api("org.springframework:spring-webflux")
	api("tools.jackson.core:jackson-databind")

	testImplementation("org.springframework.ai:spring-ai-client-chat")
	testImplementation("io.micrometer:micrometer-observation-test")
	testImplementation("org.springframework.boot:spring-boot-starter-test")
	testImplementation("io.projectreactor:reactor-test")
	testImplementation("com.tngtech.archunit:archunit-junit5:$archunitVersion")
}
