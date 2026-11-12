# Media Converter Backend

A Spring Boot backend project for handling media-conversion requests. The Maven configuration identifies a Java service with web, validation and file-handling dependencies, together with FFmpeg-related conversion tooling.

Use the application only with media you own or are authorised to process, and comply with the terms and rights that apply to the source material.

## Technology

- **Language:** Java 17
- **Framework:** Spring Boot 3.2.2
- **Web layer:** Spring Boot Web
- **Validation:** Spring Boot Validation
- **File utilities:** Apache Commons IO
- **JSON:** Jackson
- **Conversion tooling:** FFmpeg-related tooling and the configured VGet dependency
- **Build:** Maven

## Repository structure

- `src/main/java/` — Java application source
- `src/main/resources/` — application resources and configuration
- `pom.xml` — dependencies and build configuration
- `Dockerfile` — container build instructions
- `Procfile` — process declaration for compatible hosting platforms

## Requirements

- Java 17
- Maven (or a Maven Wrapper, if added to the repository)
- FFmpeg installed and available to the application where conversion is performed

## Build and run

Build the project:

```bash
mvn clean package
```

Run the Spring Boot application:

```bash
mvn spring-boot:run
```

Or run the packaged JAR:

```bash
java -jar target/mediaconverter-0.0.1-SNAPSHOT.jar
```

The exact JAR name depends on the Maven build output.

## Configuration

Review the application configuration and source before deployment. Configure temporary storage, upload limits, conversion-tool paths and server settings for the environment in which the service runs.

Do not commit secrets, private media, personal data or generated upload files.

## Testing and deployment status

The presence of a Dockerfile and Procfile does not by itself confirm a successful container build or a working public deployment. Run the project locally, verify the conversion pipeline with authorised test media, and document the exact checks that pass before presenting deployment as verified.

## Engineering concepts

- Java backend development with Spring Boot
- HTTP request handling
- File-upload and file-processing concepts
- Input validation
- Integration with external command-line tooling
- Maven dependency and build management
