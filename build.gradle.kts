import com.bmuschko.gradle.docker.tasks.image.DockerBuildImage
import com.bmuschko.gradle.docker.tasks.image.DockerPushImage
import com.bmuschko.gradle.docker.tasks.image.Dockerfile
import java.io.ByteArrayOutputStream

plugins {
    java
    id("com.bmuschko.docker-java-application") version "9.4.0"
    id("io.freefair.lombok") version "8.6"
}

group = "ch.tyderion"
version = "4.0.0-SNAPSHOT-5"

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
    implementation("info.picocli:picocli:4.7.6")
    annotationProcessor("info.picocli:picocli-codegen:4.7.6")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.17.2")
    implementation("org.apache.commons:commons-lang3:3.0")
    implementation("com.google.code.gson:gson:2.11.0")
    implementation("org.java-websocket:Java-WebSocket:1.5.6")
}

tasks.named<JavaCompile>("compileJava") {
    options.compilerArgs.add("-Aproject=${project.group}/${project.name}")
    options.compilerArgs.add("--enable-preview")
}

tasks.register<Jar>("fatJar") {
    group = "build"
    dependsOn("build")
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

tasks.register("release") {
    group = "release"
    dependsOn("createGitTag")
    doLast {
        exec {
            commandLine("git", "push", "origin", "tag", version)
        }
        println("Release $version as Docker image tyderion/aniadd-cli:$version")
    }
}


tasks.register("prepareForRelease") {
    group = "release"
    dependsOn("fatJar")
    doLast {
        copy {
            val fatJarTask = tasks.named<Jar>("fatJar").get()
            from(fatJarTask.archiveFile.get().asFile)
            into(project.layout.buildDirectory.dir("docker").get().asFile)
        }
        copy {
            from(file("watch-folder.sh"))
            into(project.layout.buildDirectory.dir("docker").get().asFile)
        }
        copy {
            from(file("handle-kodi.sh"))
            into(project.layout.buildDirectory.dir("docker").get().asFile)
        }
    }
}

tasks.register<Dockerfile>("createDockerfile") {
    group = "docker"
    dependsOn("prepareForRelease")
    from("amazoncorretto:21.0.3-al2023-headless")
    label(mapOf("maintainer" to "Tyderion"))

    val fatJarTask = tasks.named<Jar>("fatJar").get()
    val jarFileName = fatJarTask.archiveFileName.get()
    runCommand("yum install -y findutils")
    runCommand("mkdir /app")
    copyFile(jarFileName, "/app/aniadd-cli.jar")
    copyFile("watch-folder.sh", "/app/run.sh")
    copyFile("handle-kodi.sh", "/app/kodi.sh")
    defaultCommand("/app/run.sh")
}

tasks.register<DockerBuildImage>("buildDockerImage") {
    group = "docker"
    dependsOn("createDockerfile")
    description = "Builds a Docker image for the application"
    inputDir = project.layout.buildDirectory.dir("docker").get().asFile
    dockerFile = tasks.named<Dockerfile>("createDockerfile").get().destFile.asFile
    images.add("tyderion/aniadd-cli:$version")
}

tasks.register<DockerPushImage>("pushDockerImage") {
    group = "docker"
    dependsOn("buildDockerImage")
    description = "Pushes the Docker image for the application to Docker Hub"
    images.add("tyderion/aniadd-cli:$version")
}

tasks.register<Exec>("createGitTag") {
    group = "release"
    dependsOn("pushDockerImage")
    description = "Creates a Git tag for the release"

    val branch: String = ByteArrayOutputStream().use { outputStream ->
        project.exec {
            commandLine("git", "rev-parse", "--abbrev-ref", "HEAD")
            standardOutput = outputStream
        }
        outputStream.toString()
    }
    if (branch.trim() == "master" || version.toString().contains("-SNAPSHOT")) {
        commandLine("git", "tag", "-a", version, "-m", "Release $version")
        if (branch.trim() == "master") {
            commandLine("git", "push", "origin", "tag", version)
        }
    } else {
        throw GradleException("Not on master branch, no tag created")
    }
}


tasks {
    withType<JavaCompile> {
        options.isDeprecation = true
    }
    named("dockerBuildImage") {
        enabled = false
        setDependsOn(emptySet<Task>())
    }
    named("dockerPushImage") {
        enabled = false
        setDependsOn(emptySet<Task>())
    }
    named("dockerCreateDockerfile") {
        enabled = false
        setDependsOn(emptySet<Task>())
    }
    named("dockerPushImage") {
        enabled = false
        setDependsOn(emptySet<Task>())
    }
    named("dockerSyncBuildContext") {
        enabled = false
        setDependsOn(emptySet<Task>())
    }
}
