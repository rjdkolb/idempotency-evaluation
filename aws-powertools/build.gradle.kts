import org.gradle.api.tasks.compile.JavaCompile

plugins {
	java
	alias(libs.plugins.aspectj.weaving)
	alias(libs.plugins.spotless)
}

group = "io.r3k.idempotency.evaluate"
version = "0.0.1-SNAPSHOT"

val jvmVersion = libs.versions.jvm.get().toInt()

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(jvmVersion)
	}
}

tasks.withType<JavaCompile> {
	options.release = jvmVersion
}

repositories {
	mavenCentral()
}

dependencies {
	implementation(libs.aws.lambda.core)
	implementation(libs.aws.lambda.events)
	implementation(libs.powertools.idempotency.dynamodb)
	implementation(libs.jackson.databind)
	implementation(libs.aws.sdk.dynamodb)
	implementation(libs.aws.sdk.url.connection)
	implementation(libs.aspectjrt)

	compileOnly(libs.lombok)
	annotationProcessor(libs.lombok)

	aspect(libs.powertools.idempotency.dynamodb)
}

spotless {
	java {
		googleJavaFormat(libs.versions.google.java.format.get())
	}
}

tasks.register<Zip>("lambdaZip") {
	archiveFileName.set("lambda.zip")
	destinationDirectory.set(layout.buildDirectory.dir("dist"))
	from(sourceSets.main.get().output)
	into("lib") {
		from(configurations.runtimeClasspath)
	}
}

tasks.named("build") {
	dependsOn("lambdaZip")
}
