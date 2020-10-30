plugins {
    kotlin("jvm") version "1.4.10"
}

repositories {
    mavenCentral()
}

val http4kVersion = "3.271.0"
val openSamlVersion = "3.4.5"
val jUnitVersions = "5.7.0"
dependencies {
    implementation("org.http4k:http4k-core:$http4kVersion")
    implementation("org.http4k:http4k-server-netty:$http4kVersion")
    implementation("org.http4k:http4k-client-apache:$http4kVersion")
    implementation("org.opensaml:opensaml-core:$openSamlVersion")
    implementation("org.opensaml:opensaml-saml-api:$openSamlVersion")
    implementation("org.opensaml:opensaml-saml-impl:$openSamlVersion")

    testImplementation(platform("org.junit:junit-bom:$jUnitVersions"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
    }
}