
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

dependencies {
    implementation(project(":utilitymodule"))
}