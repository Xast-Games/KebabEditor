plugins {
    `java-library`
    `maven-publish`
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.formdev:flatlaf:3.7.2")
    implementation("com.formdev:flatlaf-extras:3.7.2")
    implementation("com.formdev:flatlaf-intellij-themes:3.7.2")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.20.0")
    implementation(libs.guava)
    implementation("org.jogamp.jogl:jogl-all-main:2.6.0")
    implementation("org.jogamp.gluegen:gluegen-rt-main:2.6.0")

    compileOnly("org.projectlombok:lombok:1.18.48")
    annotationProcessor("org.projectlombok:lombok:1.18.48")

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testCompileOnly("org.projectlombok:lombok:1.18.48")
    testAnnotationProcessor("org.projectlombok:lombok:1.18.48")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
    withSourcesJar()
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            artifactId = "kebab-editor"
            from(components["java"])
        }
    }
}

tasks.processResources {
    val appVersion = project.version.toString()
    inputs.property("version", appVersion)
    filesMatching("application.properties") {
        expand("version" to appVersion)
    }
}

tasks.named<Test>("test") {
    useJUnitPlatform()
}
