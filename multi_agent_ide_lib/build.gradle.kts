plugins {
    id("com.hayden.spring-app")
    id("com.hayden.kotlin")
    id("com.github.node-gradle.node")
    id("com.hayden.mcp")
    id("com.hayden.paths")
    id("com.hayden.no-main-class")
    id("com.hayden.ai")
}

group = "com.hayden"
version = "1.0.0"


tasks.register("prepareKotlinBuildScriptModel") {}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-websocket")
    implementation(project(":utilitymodule"))
    implementation(project(":commit-diff-context"))
    implementation(project(":commit-diff-model"))
    implementation("org.springframework.boot:spring-boot-starter-security")
}

tasks.bootJar {
    enabled = false
}