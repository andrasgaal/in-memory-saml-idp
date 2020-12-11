plugins {
    kotlin("jvm") version "1.4.10"
    id("com.github.johnrengelman.shadow") version "6.1.0"
}

repositories {
    mavenCentral()
}

val http4kVersion = "3.271.0"
val openSamlVersion = "3.4.5"
val jUnitVersions = "5.7.0"
val bouncyCastleVersion = "1.66"
val junitVersion = "5.7.0"
val hamcrestVersion = "2.2"
val jsoupVersion = "1.10.3"
dependencies {
    implementation("org.http4k:http4k-core:$http4kVersion")
    implementation("org.http4k:http4k-server-netty:$http4kVersion")
    implementation("org.http4k:http4k-client-apache:$http4kVersion")
    implementation("org.opensaml:opensaml-core:$openSamlVersion")
    implementation("org.opensaml:opensaml-saml-api:$openSamlVersion")
    implementation("org.opensaml:opensaml-saml-impl:$openSamlVersion")
    implementation("org.bouncycastle:bcprov-jdk15on:$bouncyCastleVersion")
    implementation("org.bouncycastle:bcpkix-jdk15on:$bouncyCastleVersion")

    testImplementation(platform("org.junit:junit-bom:$jUnitVersions"))
    testImplementation("org.junit.jupiter:junit-jupiter:$junitVersion")
    testImplementation("org.hamcrest:hamcrest:$hamcrestVersion")
    testImplementation("org.jsoup:jsoup:$jsoupVersion")
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
    }
}