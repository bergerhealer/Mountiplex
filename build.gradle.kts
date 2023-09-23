plugins {
    id("java-library")
    id("maven-publish")
}

group = "com.bergerkiller.mountiplex"
version = "2.98"

repositories {
    mavenCentral()
}

dependencies {
    api("org.ow2.asm:asm:9.2")
    api("org.objenesis:objenesis:3.1")
    api("org.javassist:javassist:3.27.0-GA")
    testImplementation("junit:junit:4.13")
    testImplementation("org.ow2.asm:asm-util:9.2")
}

java {
    withJavadocJar()
    withSourcesJar()
}

publishing {
    repositories {
        maven("https://ci.mg-dev.eu/plugin/repository/everything") {
            name = "MGDev"
            credentials(PasswordCredentials::class)
            authentication {
                create<BasicAuthentication>("basic")
            }
        }
    }

    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }
}

tasks {
    withType<JavaCompile>().configureEach {
        sourceCompatibility = "1.8"
        targetCompatibility = "1.8"
    }

    javadoc {
        // TODO fix those errors
        isFailOnError = false
        (options as StandardJavadocDocletOptions).addStringOption("Xdoclint:all,-missing", "-quiet")
    }
}
