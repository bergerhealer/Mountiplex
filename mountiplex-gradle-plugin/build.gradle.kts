plugins {
    id("com.gradle.plugin-publish") version "1.0.0"
}

group = rootProject.group
version = rootProject.version

gradlePlugin {
    plugins {
        create("mountiplex") {
            id = "com.bergerkiller.mountiplex"
            implementationClass = "com.bergerkiller.mountiplex.gradle.MountiplexPlugin"
        }
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(rootProject)
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
}

tasks {
    withType<JavaCompile>().configureEach {
        sourceCompatibility = "1.8"
        targetCompatibility = "1.8"
    }
}
