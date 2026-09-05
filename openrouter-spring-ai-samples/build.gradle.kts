plugins {
	id("org.springframework.boot")
	id("org.graalvm.buildtools.native")
}

description = "Repository-local sample applications for the OpenRouter starter. Not published."

dependencies {
	implementation(project(":openrouter-spring-ai-starter"))
	implementation("org.springframework.boot:spring-boot-starter")
	implementation("io.micrometer:micrometer-core")

	compileOnly("org.springframework.boot:spring-boot-configuration-processor")
	annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")

	compileOnly("org.projectlombok:lombok")
	annotationProcessor("org.projectlombok:lombok")

	testImplementation("org.springframework.boot:spring-boot-starter-test")
}

tasks.named<org.gradle.jvm.tasks.Jar>("jar") {
	archiveClassifier.set("plain")
}

springBoot {
	mainClass.set("de.subhransu.openrouter.springai.garage.GarageApplication")
}

tasks.withType<JavaCompile>().configureEach {
	options.compilerArgs.add("-parameters")
}
