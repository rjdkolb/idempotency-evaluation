import org.gradle.api.tasks.compile.JavaCompile

plugins {
	java
	id("org.springframework.boot") version "4.0.6"
	id("io.spring.dependency-management") version "1.1.7"
	id("com.diffplug.spotless") version "7.0.0"
}

group = "io.r3k.idempotency.evaluate"
version = "0.0.1-SNAPSHOT"

val jvmVersion = 25

dependencyManagement {
	dependencies {
		dependency("io.github.arun0009:idempotent-rds:2.4.1")
	}
}

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(jvmVersion)
	}
}

repositories {
	mavenCentral()
}

dependencies {
	implementation("org.springframework.boot:spring-boot-starter-data-jpa")
	implementation("org.springframework.boot:spring-boot-starter-liquibase")
	implementation("org.springframework.boot:spring-boot-starter-webmvc")
	implementation("io.github.arun0009:idempotent-rds")
	implementation("org.springframework.boot:spring-boot-starter-validation")
	implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.0.3")
	implementation("org.mapstruct:mapstruct:1.6.3")
	compileOnly("org.projectlombok:lombok")
	annotationProcessor("org.projectlombok:lombok")
	annotationProcessor("org.mapstruct:mapstruct-processor:1.6.3")
	annotationProcessor("org.projectlombok:lombok-mapstruct-binding:0.2.0")
	developmentOnly("org.springframework.boot:spring-boot-docker-compose")
	runtimeOnly("org.postgresql:postgresql")
	testImplementation("org.springframework.boot:spring-boot-testcontainers")
	testImplementation(platform("org.testcontainers:testcontainers-bom:1.21.1"))
	testImplementation("org.springframework.boot:spring-boot-starter-liquibase-test")
	testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<JavaCompile> {
	options.release = jvmVersion
}

spotless {
	java {
		googleJavaFormat("1.30.0")
	}
}

tasks.withType<Test> {
	useJUnitPlatform()
}
