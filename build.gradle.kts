import org.flywaydb.gradle.task.FlywayMigrateTask
import org.jooq.codegen.gradle.CodegenTask
import org.testcontainers.containers.PostgreSQLContainer

buildscript {
    dependencies {
        classpath(platform(libs.spring.boot.bom))
        classpath(libs.flyway.database.postgresql)
        classpath(libs.testcontainers.postgresql)
        classpath(libs.postgresql)
    }
}

plugins {
    java
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.vaadin)
    alias(libs.plugins.jooq.codegen)
    alias(libs.plugins.flyway)
    jacoco
    `maven-publish`
}

group = "ch.martinelli.template"
version = "0.0.1-SNAPSHOT"

val javaLanguageVersion = JavaLanguageVersion.of(libs.versions.java.get())
java {
    toolchain.languageVersion = javaLanguageVersion
}

// https://vaadin.com/docs/latest/getting-started/project/gradle#all-options
vaadin {
    pnpmEnable = true
}

// Here we register service for providing our database during the build.
val dbContainerProvider: Provider<PostgresService> = project.gradle
    .sharedServices
    .registerIfAbsent("postgres", PostgresService::class.java) {}

jooq {
    configuration {
        generator {
            database {
                name = "org.jooq.meta.postgres.PostgresDatabase"
                inputSchema = "public"
                excludes = "flyway_schema_history"
                /* Used for optimistic locking. See VjJooqConfiguration class */
                recordVersionFields = "version"
            }
            target {
                packageName = "ch.martinelli.vj.db"
            }
        }
    }
}

dependencies {
    implementation(enforcedPlatform(libs.spring.boot.bom))
    implementation(enforcedPlatform(libs.vaadin.bom))

    implementation(libs.spring.boot.starter.security)
    implementation(libs.spring.boot.starter.validation)
    implementation(libs.spring.boot.starter.jooq)
    implementation(libs.io72.vaadin.jooq)
    implementation(libs.spring.security.oauth2.jose)
    implementation(libs.spring.security.oauth2.resource.server)
    implementation(libs.flyway.database.postgresql)
    implementation(libs.vaadin.core) {
        if (vaadin.effective.productionMode.get()) {
            exclude(module = "vaadin-dev")
        }
    }
    implementation(libs.vaadin.spring.boot.starter)

    developmentOnly(enforcedPlatform(libs.spring.boot.bom))
    developmentOnly(libs.spring.boot.devtools)

    runtimeOnly(enforcedPlatform(libs.spring.boot.bom))
    runtimeOnly(libs.postgresql)

    testImplementation(enforcedPlatform(libs.spring.boot.bom))
    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.spring.boot.testcontainers)
    testImplementation(libs.testcontainers.junit.jupiter)
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.karibu.testing)
    testImplementation(libs.microsoft.playwright)
    testImplementation(libs.archunit)
    testImplementation(libs.virit.mopo)
}

publishing {
    publications.create<MavenPublication>("maven") {
        from(components["java"])
    }
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.compilerArgs.add("-parameters")
}

val flywayMigrateTask = tasks.withType<FlywayMigrateTask> {
    usesService(dbContainerProvider)

    inputs.files(fileTree("src/main/resources/db/migration"))

    doFirst {
        val container = dbContainerProvider.get().container
        url = container.jdbcUrl
        user = container.username
        password = container.password
    }
}

val jooqCodeGen = tasks.withType<CodegenTask> {
    usesService(dbContainerProvider)
    dependsOn(flywayMigrateTask)

    inputs.files(fileTree("src/main/resources/db/migration"))

    doFirst {
        val dbContainer = dbContainerProvider.get().container
        jooq {
            configuration {
                jdbc {
                    driver = "org.postgresql.Driver"
                    url = dbContainer.jdbcUrl
                    user = dbContainer.username
                    password = dbContainer.password
                }
            }
        }
    }
}

tasks.withType<JavaCompile> {
    dependsOn(jooqCodeGen)
}

tasks.withType<Test> {
    useJUnitPlatform()
}

tasks.withType<Javadoc> {
    options.encoding = "UTF-8"
}

/**
 * Build service for providing database container.
 */
abstract class PostgresService : BuildService<BuildServiceParameters.None>, AutoCloseable {
    val container: PostgreSQLContainer<*> = PostgreSQLContainer("postgres:16-alpine")

    init {
        // Services are initialized lazily, on first request to them, so we start container immediately.
        container.start()
    }

    override fun close() {
        container.stop()
    }

}
