# Drift Locator - Project Guide for AI Agents

## Project Overview

**Drift Locator** is a database schema comparison tool with IntelliJ IDEA plugin integration. The project identifies and reports differences between database schemas across different database management systems (currently PostgreSQL is supported).

The tool allows developers to:
- Compare database schemas between different environments
- Detect added, removed, or modified database objects
- Export schema differences to human-readable text files
- Manage database connections through an IntelliJ IDEA plugin UI

## Technology Stack

- **Language**: Kotlin (JVM)
- **Build System**: Gradle 8.6 with Kotlin DSL
- **JDK**: Java 21
- **Kotlin Version**: 2.3.10
- **Testing**: JUnit 5 (Jupiter)
- **Code Quality**: Detekt 1.23.8, KtLint 12.1.0

### Key Dependencies
- PostgreSQL JDBC Driver 42.7.10
- KotliQuery 1.9.0 (SQL wrapper library)
- Testcontainers 1.21.4 (for integration testing)
- IntelliJ Platform SDK 2025.2

## Project Structure

The project is organized as a multi-module Gradle build:

```
drift-locator/
├── core/               # Domain models and core interfaces
├── postgresql/         # PostgreSQL-specific implementation
├── jetbrains-plugin/   # IntelliJ IDEA plugin
├── build.gradle.kts    # Root build configuration
├── settings.gradle.kts # Project settings
├── detekt.yaml         # Static analysis configuration
└── .editorconfig       # Code style settings
```

### Module Details

#### Core Module (`core/`)
Contains the domain layer with no external dependencies:
- `DatabaseObject` - Interface for database objects (tables, columns, indexes, etc.)
- `DatabaseSchema` - Collection of database objects
- `SchemaComparator` - Interface for schema comparison logic
- `SchemaDiff` - Data class representing differences between schemas
- `DiffExporter` - Exports schema differences to text files

#### PostgreSQL Module (`postgresql/`)
PostgreSQL-specific implementation using JDBC:
- `PostgresSchemaComparator` - Fetches schema from PostgreSQL and performs comparison
- `PostgresConnectionTester` - Validates database connectivity
- `PostgresObjects` - Data classes for PostgreSQL objects (tables, columns, indexes, constraints, views, functions, procedures, sequences)
- `PostgresRowMappers` - Maps JDBC ResultSet rows to domain objects

**Database Objects Supported:**
- Tables with columns, indexes, constraints
- Views
- Functions
- Procedures
- Sequences

#### JetBrains Plugin Module (`jetbrains-plugin/`)
IntelliJ IDEA plugin providing UI for schema comparison:
- `DriftLocatorToolWindowFactory` - Creates the tool window
- `DriftLocatorProjectService` - Project-level service for managing connections
- `DriftLocatorApplicationService` - Application-level service
- `ConnectionValidation.kt` - Background connection validation

Plugin configuration in `src/main/resources/META-INF/plugin.xml`:
- Plugin ID: `com.github.yamert89.drift-locator`
- Supported builds: 231-253.*
- Depends on: `com.intellij.modules.platform`, `org.jetbrains.plugins.yaml`

## Build Commands

### Build All Modules
```bash
./gradlew build
```

### Run Tests
```bash
# All tests
./gradlew test

# Specific module
./gradlew :core:test
./gradlew :postgresql:test
./gradlew :jetbrains-plugin:test
```

### Code Quality Checks
```bash
# Run Detekt (static analysis)
./gradlew detekt

# Run KtLint (formatting)
./gradlew ktlintCheck

# Auto-format code with KtLint
./gradlew ktlintFormat
```

### Plugin Development
```bash
# Run IntelliJ IDEA with plugin installed (development mode)
./gradlew :jetbrains-plugin:runIde

# Build plugin distribution
./gradlew :jetbrains-plugin:buildPlugin

# Verify plugin compatibility
./gradlew :jetbrains-plugin:runPluginVerifier

# Publish plugin (requires environment variables)
./gradlew :jetbrains-plugin:publishPlugin
```

## Code Style Guidelines

### EditorConfig Settings (`.editorconfig`)
- Indent: 4 spaces
- Kotlin code style: `ktlint_official`
- Max line length: 140 characters (Detekt)
- Force multiline for function/class signatures with 3+ parameters

### Detekt Configuration (`detekt.yaml`)
Key rules:
- **Max Line Length**: 140 characters
- **Long Method**: threshold 65 lines (relaxed from default 110)
- **Long Parameter List**: function threshold 6, constructor threshold 12
- **Too Many Functions**: threshold 25 per file/class
- **Nested Block Depth**: threshold 4
- **Return Count**: max 3 per function
- **Throws Count**: max 2 per function

Special exclusions:
- Magic number checks exclude `**/test/**`, `**/internal/**`, `**/ui/**`
- Wildcard imports are allowed (disabled rule)

### KtLint Configuration
- Version: 1.7.1
- Output to console enabled
- Fail build on violations
- Wildcard imports suppressed in some files via `@file:Suppress` annotations

## Testing Strategy

### Unit Tests
- **Framework**: JUnit 5 (Jupiter)
- **Location**: `src/test/kotlin/...`
- Test classes use JUnit's `@TempDir` for temporary files

### Integration Tests
- **Framework**: Testcontainers
- Uses PostgreSQL 15 container for database testing
- Tests validate actual schema fetching and comparison against real PostgreSQL instance

### Running Tests
```bash
# Requires Docker for Testcontainers tests
./gradlew test

# Run with verbose output
./gradlew test --info
```

## Security Considerations

### Database Connections
- Connection passwords are stored in memory only (not persisted)
- Connection validation happens on background thread
- JDBC URLs constructed programmatically, not from user input directly

### Plugin Signing (Production)
Environment variables required for plugin signing:
- `CERTIFICATE_CHAIN` - Certificate chain for signing
- `PRIVATE_KEY` - Private key
- `PRIVATE_KEY_PASSWORD` - Password for private key

### Plugin Publishing
Environment variable required:
- `PUBLISH_TOKEN` - JetBrains Marketplace API token

## Development Workflow

### Adding a New Database Type
1. Create new module (e.g., `mysql/`)
2. Implement `SchemaComparator` interface
3. Create database-specific object classes implementing `DatabaseObject`
4. Add module to `settings.gradle.kts`
5. Add dependency to `jetbrains-plugin` if UI integration needed

### Adding Plugin Features
1. Add actions
2. Register actions in `plugin.xml`
3. Create dialogs in ui/ if needed
4. Update services in `DriftLocatorProjectService.kt` for state management

### Common Tasks

**Clean build:**
```bash
./gradlew clean
```

**Rebuild specific module:**
```bash
./gradlew :postgresql:clean :postgresql:build
```

**Check all code quality:**
```bash
./gradlew check
```

## Configuration Files Reference

| File | Purpose |
|------|---------|
| `build.gradle.kts` | Root build configuration, shared dependencies |
| `settings.gradle.kts` | Module declarations |
| `gradle.properties` | Plugin version and IntelliJ platform settings |
| `detekt.yaml` | Static analysis rules |
| `.editorconfig` | Code formatting rules |
| `jetbrains-plugin/src/main/resources/META-INF/plugin.xml` | Plugin manifest |
| `jetbrains-plugin/CHANGELOG.md` | Plugin changelog |

## IDE Support

The project includes IntelliJ IDEA configuration files in `.idea/` directory:
- Code style settings
- Run configurations
- Inspection profiles

## Troubleshooting

### Testcontainers Issues
- Ensure Docker is running
- On Linux, user must be in `docker` group or use sudo

### Plugin Development Issues
- Clear sandbox: `rm -rf jetbrains-plugin/build/idea-sandbox/`
- Reset Gradle: `./gradlew clean --refresh-dependencies`

### Detekt Failures
- View report: `cat core/build/reports/detekt/detekt.md`
- Auto-correct is enabled for some rules in plugin module only
