
plugins {
    id("com.hayden.spring-app")
    id("com.hayden.kotlin")
    id("com.github.node-gradle.node")
    id("com.hayden.mcp")
    id("com.hayden.ai")
    id("com.hayden.paths")
    id("com.hayden.git")
    id("com.hayden.jpa-persistence")
    id("com.hayden.docker-compose")
}

group = "com.hayden"
version = "0.0.1-SNAPSHOT"
description = "multi-agent-ide"

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-websocket")
    implementation(project(":multi_agent_ide_java_parent:utilitymodule"))
    implementation(project(":multi_agent_ide_java_parent:acp-cdc-ai"))
    implementation("com.agentclientprotocol:acp:0.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor:1.9.0")
    implementation("com.ag-ui.community:kotlin-core-jvm:0.2.4")
    implementation("com.embabel.agent:embabel-agent-starter-openai:0.3.2-SNAPSHOT")
    implementation("org.springframework.ai:spring-ai-starter-mcp-server-webmvc")
    implementation("org.jspecify:jspecify:1.0.0")
    implementation(project(":commit-diff-context"))
    implementation(project(":commit-diff-model"))
    implementation(project(":multi_agent_ide_java_parent:multi_agent_ide_lib"))
    implementation(project(":persistence"))
    implementation(project(":jpa-persistence"))
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("com.embabel.agent:embabel-agent-skills:0.3.2")
    implementation("org.springframework.shell:spring-shell-starter:3.4.0")
    testImplementation("org.springframework.shell:spring-shell-test:3.4.0")
    testImplementation("org.springframework.shell:spring-shell-test-autoconfigure:3.4.0")
}

tasks.bootJar {
    mainClass = "com.hayden.multiagentide.MultiAgentIdeApplication"
}
tasks.bootRun {
    mainClass.set("com.hayden.multiagentide.MultiAgentIdeApplication")
}

// Node.js and npm configuration
node {
   version.set("20.11.0")
   npmVersion.set("10.2.4")
   download.set(true)
   workDir.set(file("${project.layout.buildDirectory.get()}/nodejs"))
   npmWorkDir.set(file("${project.layout.buildDirectory.get()}/npm"))
}

val buildReact = project.property("build-react")?.toString()?.toBoolean()?.or(false) ?: false

if (buildReact) {

    tasks.register<com.github.gradle.node.npm.task.NpmTask>("installFrontend") {
        description = "Build Next.js frontend application"
        workingDir.set(file("${project.projectDir}/fe"))

        args.set(listOf("install"))

        finalizedBy("buildFrontend")
    }

    //Build the Next.js frontend
    tasks.register<com.github.gradle.node.npm.task.NpmTask>("buildFrontend") {
        description = "Build Next.js frontend application"
        workingDir.set(file("${project.projectDir}/fe"))

        args.set(listOf("run", "build"))

        inputs.files("${project.projectDir}/fe/src")
        inputs.file("${project.projectDir}/fe/package.json")
        inputs.file("${project.projectDir}/fe/next.config.ts")

        outputs.dir("${project.projectDir}/fe/.next")

        dependsOn("installFrontend")
        finalizedBy("copyFrontendBuild")
    }

//Build the Next.js frontend
    tasks.register<com.github.gradle.node.npm.task.NpmTask>("testFrontend") {
        description = "Test the frontend application"
        workingDir.set(file("${project.projectDir}/fe"))

        args.set(listOf("run", "test"))

        inputs.files("${project.projectDir}/fe")

        outputs.dir("${project.projectDir}/fe/.next")

        dependsOn("buildFrontend")
    }

// Copy built frontend to static resources
    tasks.register<Copy>("copyFrontendBuild") {

        doFirst {
            delete(file("${project.projectDir}/src/main/resources/static"))
        }

        description = "Copy Next.js build output to static resources"
        dependsOn("installFrontend", "buildFrontend", "testFrontend")

        from("${project.projectDir}/fe/out")
        into("${project.layout.projectDirectory}/src/main/resources/static")

    }

    tasks.getByPath("processResources").dependsOn("copyFrontendBuild")

// Make bootJar depend on frontend build
    tasks.getByPath("bootJar").dependsOn("copyFrontendBuild")
}

tasks.register<Copy>("copyToolGateway") {
    dependsOn(project(":mcp-tool-gateway").tasks.named("bootJar"))
    val sourcePaths = file(project(":mcp-tool-gateway").layout.buildDirectory).resolve("libs/mcp-tool-gateway.jar")
    from(sourcePaths)
    into(file(layout.buildDirectory).resolve("libs"))
    // Optionally rename it to a fixed name
    rename { "mcp-tool-gateway.jar" }
}

tasks.register<Copy>("testAcpClient") {
    dependsOn(project(":test-acp-client").tasks.named("build"))
}

tasks.compileJava {
    dependsOn("processYmlFiles")
}
tasks.test {
    if (project.findProperty("profile") == "integration") {
        include("**/integration/**")
    } else if (project.findProperty("profile") == "acp-integration") {
        include("**/acp_tests/**")
    } else {
        exclude("**/acp_tests/**", "**/integration/**")
    }

    dependsOn("processYmlFiles")
}
