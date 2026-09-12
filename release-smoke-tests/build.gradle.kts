plugins { application }

repositories {
    maven {
        url = uri(providers.gradleProperty("bundleRepository").get())
        content { includeGroup("de.subhransu") }
    }
    mavenCentral { content { excludeGroup("de.subhransu") } }
}

dependencies {
    implementation("de.subhransu:openrouter-spring-ai-starter:${providers.gradleProperty("releaseVersion").get()}")
}

java { sourceCompatibility = JavaVersion.VERSION_17 }
application { mainClass = "example.Consumer" }
