plugins {
    kotlin("jvm") version "1.4.10"
}

repositories {
    mavenCentral()
}

val http4kVersion = "3.271.0"
dependencies {
    implementation("org.http4k:http4k-core:$http4kVersion")
    implementation("org.http4k:http4k-server-jetty:$http4kVersion")
    implementation("org.http4k:http4k-client-apache:$http4kVersion")
}