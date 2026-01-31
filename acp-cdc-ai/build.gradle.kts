
plugins {
    id("com.hayden.no-main-class")
    id("com.hayden.log")
    id("com.hayden.kotlin")
    id("com.hayden.messaging")
    id("com.hayden.ai-nd")
    id("com.hayden.security")
    id("com.hayden.bom-plugin")
    id("com.hayden.ai")
    id("com.hayden.git")
    id("com.hayden.mcp")
    id("com.hayden.java-conventions")
}

group = "com.hayden"
version = "0.0.1-SNAPSHOT"
description = "acp-cdc-ai"

var utilLib = ""

if (project.parent?.name?.contains("multi_agent_ide_java_parent") ?: false) {
    utilLib = ":multi_agent_ide_java_parent"
} else {
    utilLib = ""
}

dependencies {
    implementation(project("${utilLib}:utilitymodule"))
}