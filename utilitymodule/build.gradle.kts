import com.hayden.haydenbomplugin.BuildSrcVersionCatalogCollector

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

description = "utilitymodule"


val vC = project.extensions.getByType(BuildSrcVersionCatalogCollector::class.java)

dependencies {

    vC.bundles.opentelemetryBundle.inBundle()
        .map { implementation(it) }
    implementation("org.springframework.graphql:spring-graphql")

    implementation("com.squareup:javapoet:1.13.0")
//    annotationProcessor(project(":tracing_apt")) {
//        exclude("org.junit")
//    }
//    api(project(":tracing_apt"))
    api(project(":tracing_aspect"))
//    annotationProcessor(project(":inject_fields"))
//    testAnnotationProcessor(project(":inject_fields"))
//    api(project(":inject_fields"))
}
