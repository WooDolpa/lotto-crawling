# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Spring Boot 4.0.1 web application using Java 17 and Gradle 9.2.1. Server-side rendered HTML using Thymeleaf templating engine with Lombok for boilerplate reduction.

## Build & Development Commands

```bash
# Run the development server (default port 8080)
./gradlew bootRun

# Build the project
./gradlew build

# Create executable JAR
./gradlew bootJar

# Clean build artifacts
./gradlew clean
```

## Architecture

- **Entry Point**: `src/main/java/com/manage/lotto/Application.java` - Spring Boot main class
- **Controllers**: `src/main/java/com/manage/lotto/controller/` - MVC controllers
- **Templates**: `src/main/resources/templates/` - Thymeleaf HTML templates
- **Static Assets**: `src/main/resources/static/` - CSS, JS, images
- **Configuration**: `src/main/resources/application.yml` - Spring Boot config

## Key Dependencies

- Spring Boot Web MVC (spring-boot-starter-webmvc)
- Thymeleaf (spring-boot-starter-thymeleaf)
- Lombok (compile-time annotation processing)
