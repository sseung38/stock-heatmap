dependencies {
    api(project(":core"))
    api("org.springframework:spring-web")
    api("software.amazon.awssdk:s3")
    api("software.amazon.awssdk:dynamodb")
    api("software.amazon.awssdk:ssm")
    implementation("software.amazon.awssdk:url-connection-client")
    implementation("org.slf4j:slf4j-api")
}
