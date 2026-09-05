import org.gradle.api.plugins.quality.Checkstyle
import org.gradle.api.plugins.quality.CheckstyleExtension
import org.gradle.api.plugins.quality.Pmd
import org.gradle.api.plugins.quality.PmdExtension
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.bundling.ZipEntryCompression
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.register
import org.gradle.testing.jacoco.tasks.JacocoReport
import org.gradle.testing.jacoco.plugins.JacocoPluginExtension

plugins {
	base
	id("org.springframework.boot") apply false
	id("org.graalvm.buildtools.native") apply false
}

val pomText = file("pom.xml").readText()
fun pomProperty(name: String): String =
	Regex("<${Regex.escape(name)}>([^<]+)</${Regex.escape(name)}>")
		.find(pomText)
		?.groupValues
		?.get(1)
		?: error("Missing <$name> in the authoritative Maven POM")

val javaVersion = pomProperty("java.version").toInt()
val revision = pomProperty("revision")
val springAiVersion = pomProperty("spring-ai.version")
val springBootVersion = pomProperty("spring-boot.version")
val jackson3Version = pomProperty("jackson3.version")
val archunitVersion = pomProperty("archunit.version")
val checkstyleVersion = pomProperty("checkstyle.version")
val pmdVersion = pomProperty("pmd.version")
val springJavaFormatVersion = pomProperty("spring-javaformat.version")
val jacocoVersion = pomProperty("jacoco-maven-plugin.version")
val libraryProjects =
	setOf("openrouter-spring-ai", "openrouter-spring-ai-autoconfigure", "openrouter-spring-ai-starter")

extra["archunitVersion"] = archunitVersion

allprojects {
	group = "de.subhransu"
	version = revision
}

subprojects {
	apply(plugin = "java-library")
	apply(plugin = "checkstyle")
	apply(plugin = "jacoco")
	apply(plugin = "pmd")
	if (name in libraryProjects) {
		apply(plugin = "maven-publish")
	}

	tasks.withType<JavaCompile>().configureEach {
		options.encoding = "UTF-8"
		options.release.set(javaVersion)
		options.compilerArgs.add("-Xpkginfo:always")
	}

	dependencies {
		"api"(platform("tools.jackson:jackson-bom:$jackson3Version"))
		"api"(platform("org.springframework.boot:spring-boot-dependencies:$springBootVersion"))
		"api"(platform("org.springframework.ai:spring-ai-bom:$springAiVersion"))
		"annotationProcessor"(platform("org.springframework.boot:spring-boot-dependencies:$springBootVersion"))
		"testImplementation"(platform("org.springframework.boot:spring-boot-dependencies:$springBootVersion"))
		"testImplementation"(platform("org.springframework.ai:spring-ai-bom:$springAiVersion"))
		"testRuntimeOnly"("org.junit.platform:junit-platform-launcher")
		"checkstyle"("com.puppycrawl.tools:checkstyle:$checkstyleVersion")
		"checkstyle"("io.spring.javaformat:spring-javaformat-checkstyle:$springJavaFormatVersion")
	}

	configure<CheckstyleExtension> {
		toolVersion = checkstyleVersion
		configFile = rootProject.file("config/checkstyle/checkstyle.xml")
		isShowViolations = true
	}

	configure<PmdExtension> {
		toolVersion = pmdVersion
		isConsoleOutput = true
		ruleSets = emptyList()
		ruleSetFiles = rootProject.files("config/pmd/pmd-main.xml", "config/pmd/pmd-test.xml")
	}

	configure<JacocoPluginExtension> {
		toolVersion = jacocoVersion
	}

	tasks.withType<Jar>().configureEach {
		isPreserveFileTimestamps = false
		isReproducibleFileOrder = true
		entryCompression = ZipEntryCompression.STORED
	}

	tasks.withType<Checkstyle>().configureEach {
		// Checkstyle 13.x is compiled for Java 21; style is enforced by the JDK 21+ CI legs.
		enabled = JavaVersion.current() >= JavaVersion.VERSION_21
		reports {
			xml.required.set(true)
			html.required.set(true)
		}
	}

	tasks.withType<Pmd>().configureEach {
		reports {
			xml.required.set(true)
			html.required.set(true)
		}
	}

	tasks.withType<Test>().configureEach {
		useJUnitPlatform()
		finalizedBy(tasks.named<JacocoReport>("jacocoTestReport"))
	}

	tasks.named<JacocoReport>("jacocoTestReport") {
		dependsOn(tasks.withType<Test>())
		reports {
			xml.required.set(true)
			html.required.set(true)
		}
	}

	if (name == "openrouter-spring-ai-samples") {
		tasks.withType<Checkstyle>().configureEach {
			enabled = false
		}
		tasks.withType<Pmd>().configureEach {
			enabled = false
		}
	}

	if (name in libraryProjects) {
		tasks.named<Jar>("jar") {
			doLast {
				val javaExecutable =
					file("${System.getProperty("java.home")}/bin/java${if (System.getProperty("os.name").startsWith("Windows")) ".exe" else ""}")
				providers
					.exec {
						commandLine(
							javaExecutable,
							rootProject.file("scripts/ReproducibleJar.java"),
							layout.buildDirectory.dir("classes/java/main").get().asFile,
							layout.buildDirectory.dir("resources/main").get().asFile,
							archiveFile.get().asFile,
						)
					}.result
					.get()
			}
		}

		configure<PublishingExtension> {
			publications {
				create<MavenPublication>("mavenJava") {
					from(components["java"])
					versionMapping {
						usage("java-api") {
							fromResolutionOf("runtimeClasspath")
						}
						usage("java-runtime") {
							fromResolutionResult()
						}
					}
				}
			}
		}

		tasks.register("writeParityRuntimeGraph") {
			val outputFile = layout.buildDirectory.file("parity/runtime-dependencies.txt")
			outputs.file(outputFile)
			outputs.upToDateWhen { false }
			doLast {
				val dependencies =
					configurations
						.getByName("runtimeClasspath")
						.resolvedConfiguration
						.resolvedArtifacts
						.map { "${it.moduleVersion.id.group}:${it.name}:${it.moduleVersion.id.version}" }
						.sorted()
				val file = outputFile.get().asFile
				file.parentFile.mkdirs()
				file.writeText(dependencies.joinToString(separator = "\n", postfix = "\n"))
			}
		}

		tasks.register("writeParityTestGraph") {
			val outputFile = layout.buildDirectory.file("parity/test-dependencies.txt")
			outputs.file(outputFile)
			outputs.upToDateWhen { false }
			doLast {
				val dependencies =
					configurations
						.getByName("testRuntimeClasspath")
						.resolvedConfiguration
						.resolvedArtifacts
						.map { "${it.moduleVersion.id.group}:${it.name}:${it.moduleVersion.id.version}" }
						.sorted()
				val file = outputFile.get().asFile
				file.parentFile.mkdirs()
				file.writeText(dependencies.joinToString(separator = "\n", postfix = "\n"))
			}
		}
	}
}
