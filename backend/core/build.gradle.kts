// Plain Java: no Spring here, so the core logic stays fast to test and easy to reason about.
dependencies {
    api("tools.jackson.core:jackson-databind")
    implementation("org.jsoup:jsoup:1.23.2")
}

// ContractTest reads ../contract/*.json; declare it so Gradle re-runs tests when the samples change.
tasks.test {
    inputs.dir(rootProject.file("../contract")).withPropertyName("contractSamples")
}
