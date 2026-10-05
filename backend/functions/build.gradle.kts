dependencies {
    implementation(project(":jobs"))
    implementation("org.springframework.boot:spring-boot-starter")
    implementation("org.springframework.cloud:spring-cloud-function-adapter-aws") {
        // The adapter drags in WebFlux + Netty (~20 MB) for a local web-server mode we don't use.
        exclude(group = "org.springframework.boot", module = "spring-boot-starter-webflux")
    }
    implementation("com.amazonaws:aws-lambda-java-events:3.16.1")
    implementation("software.amazon.awssdk:url-connection-client")
}

// The SDK's default Apache/Netty HTTP clients are not needed (we use url-connection-client):
// leaving them out keeps the Lambda package smaller and cold starts faster.
configurations.runtimeClasspath {
    exclude(group = "software.amazon.awssdk", module = "apache-client")
    exclude(group = "software.amazon.awssdk", module = "netty-nio-client")
}

// AWS's recommended Java packaging: our classes at the zip root, every dependency jar in lib/.
// (A "fat"/shaded jar tends to break Spring's META-INF configuration files.)
tasks.register<Zip>("lambdaZip") {
    group = "build"
    description = "Packages the Lambda deployment zip"
    archiveFileName = "functions-lambda.zip"
    destinationDirectory = layout.buildDirectory.dir("distributions")
    from(tasks.compileJava)
    from(tasks.processResources)
    into("lib") {
        from(configurations.runtimeClasspath)
    }
}
