plugins {
    id("org.springframework.boot")
}

dependencies {
    implementation(project(":jobs"))
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
}

// Run from the repository root so .env, .data/ and web/config.remote.json resolve as documented.
tasks.named<org.springframework.boot.gradle.tasks.run.BootRun>("bootRun") {
    workingDir = rootProject.projectDir.parentFile
}
