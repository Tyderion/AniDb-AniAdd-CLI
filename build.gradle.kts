plugins {
    java
}

group = "ch.tyderion"
version = "3.0"

java {
    targetCompatibility = JavaVersion.VERSION_21
}

tasks.compileJava {
    options.encoding = "UTF-8"
}

repositories {
    mavenCentral()
}

dependencies {
    testImplementation("junit:junit:4.13.1")
    implementation("commons-cli:commons-cli:1.3.1")
    implementation("org.yaml:snakeyaml:2.0")
    implementation("org.jetbrains:annotations:13.0")
}

tasks.register<Jar>("fatJar") {
    manifest {
        attributes(
                "Implementation-Title" to "AniAdd CLI Version",
                "Implementation-Version" to version,
                "Main-Class" to "aniAdd.startup.Main"
        )
    }
    archiveBaseName.set(project.name + "-all")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
    with(tasks.jar.get())
}

tasks {
    withType<JavaCompile> {
        options.isDeprecation = true
    }
}
