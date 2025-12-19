import com.google.protobuf.gradle.*

plugins {
    application
    id("org.graalvm.buildtools.native") version "0.11.1"
    id("com.google.protobuf") version "0.9.5"
}

repositories {
    mavenCentral()
}

application {
    mainClass = "io.corrlang.cli.Runner"
}


graalvmNative {
    binaries {
        named("main") {
            imageName.set("corrl")
            javaLauncher.set(javaToolchains.launcherFor {
                languageVersion.set(JavaLanguageVersion.of(25))
            })
            mainClass.set("io.corrlang.cli.Runner")
            useFatJar.set(true)
            // Protobuf/gRPC uses some unsafe memory access that needs to be allowed explicitly.
            jvmArgs.set(listOf("--sun-misc-unsafe-memory-access=allow"))
            buildArgs.add("--enable-url-protocols=https")
        }
    }
}

dependencies {
    implementation("commons-cli:commons-cli:1.10.0")
    runtimeOnly("io.grpc:grpc-netty:1.77.0")
    implementation("io.grpc:grpc-protobuf:1.77.0")
    implementation("io.grpc:grpc-stub:1.77.0")

    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.3")
    testImplementation("org.slf4j:slf4j-api:2.0.17")
    testImplementation("org.slf4j:slf4j-simple:2.0.17")
}

// Apply a specific Java toolchain to ease working on different environments.
java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

protobuf {
    protoc {
         artifact = "com.google.protobuf:protoc:3.25.8"
    }
    plugins {
        id("grpc") {
            artifact = "io.grpc:protoc-gen-grpc-java:1.77.0"
        }
    }
    generateProtoTasks {
        ofSourceSet("main").forEach {
            it.plugins {
                id("grpc") {}
            }
        }
    }
}


tasks.withType(JavaCompile::class) {
    options.setDeprecation(true)
}



tasks.named<Test>("test") {
    useJUnitPlatform()
}
