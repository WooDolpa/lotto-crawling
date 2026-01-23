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

# Clean build artifacts
./gradlew clean
```

## Architecture

Base package: `com.manage.lotto`

### Controller Layer
- **MVC Controllers** (`controller/`): Return Thymeleaf view names for server-side rendering (use `@Controller`)
- **REST API Controllers** (`controller/api/`): Return JSON responses (use `@RestController`)

### Resources
- **Templates**: `src/main/resources/templates/` - Thymeleaf HTML templates (Korean locale)
- **Configuration**: `src/main/resources/application.yml` - Thymeleaf caching disabled for development
