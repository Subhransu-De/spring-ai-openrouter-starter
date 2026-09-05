description = "Starter that pulls in autoconfigure and core for one-line app integration."

dependencies {
	api(project(":openrouter-spring-ai-autoconfigure"))
	api("org.springframework.ai:spring-ai-client-chat")
	api("org.springframework.ai:spring-ai-autoconfigure-model-chat-client")
	api("org.springframework.ai:spring-ai-autoconfigure-model-chat-memory")
	api("org.springframework.boot:spring-boot-starter-restclient")
	api("org.springframework.boot:spring-boot-starter-webclient")

	testImplementation("org.springframework.boot:spring-boot-starter-test")
	testImplementation("org.springframework.boot:spring-boot-starter-actuator")
}
