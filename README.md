# CESSDA Metadata Validator: Command Line Runner

[![Build Status](https://jenkins.cessda.eu/buildStatus/icon?job=cessda.cmv.console%2Fmain)](https://jenkins.cessda.eu/job/cessda.cmv.console/job/main/)
[![Quality Gate Status](https://sonarqube.cessda.eu/api/project_badges/measure?project=eu.cessda.cmv%3Acmv-console&metric=alert_status)](https://sonarqube.cessda.eu/dashboard?id=eu.cessda.cmv%3Acmv-console)
[![Coverage](https://sonarqube.cessda.eu/api/project_badges/measure?project=eu.cessda.cmv%3Acmv-console&metric=coverage)](https://sonarqube.cessda.eu/dashboard?id=eu.cessda.cmv%3Acmv-console)
[![SQAaaS badge shields.io](https://img.shields.io/badge/sqaaas%20software-silver-lightgrey)](https://api.eu.badgr.io/public/assertions/8SNeVp5IRnuOh-3slKwOMg "SQAaaS silver badge achieved")

This repository contains the source code for a command line application of the CESSDA Metadata Validator.

## Prerequisites

Java 11 or greater is required to build and run this application.

## Quick Start

1. Check prerequisites and install any required software.
2. Clone the repository to your local workspace.
3. Build the application using `.\mvnw clean verify`.
4. Run the application using the following command: `.\mvnw exec:java`.

## Project Structure

This project uses the standard Maven project structure.

```
<ROOT>
├── .mvn                # Maven wrapper.
├── src                 # Contains all source code and assets for the application.
|   ├── main
|   |   ├── java        # Contains release source code of the application.
|   |   └── resources   # Contains release resources assets.
|   └── test
|       ├── java        # Contains test source code.
|       └── resources   # Contains test resource assets.
└── target              # The output directory for the build.
```

## Technology Stack

Several frameworks are used in this application.

| Framework/Technology                                                              | Description                               |
|-----------------------------------------------------------------------------------|-------------------------------------------|
| [CESSDA Metadata Validator](https://cmv.cessda.eu)                                | Validates XMLs according to the CMM       | 
| [Jib](https://github.com/GoogleContainerTools/jib)                                | Java Docker/OCI image builder             |
| [Jackson](https://github.com/FasterXML/jackson)                                   | JSON/YAML Serializer/Deserializer         |
| [logstash-logback-encoder](https://github.com/logstash/logstash-logback-encoder/) | JSON Encoder for Logback                  |
| [Apache Commons IO](https://commons.apache.org/proper/commons-io/)                | Library of utilities for IO functionality |

## Configuration

The validator is configured using [`configuration.yaml`](src/main/resources/configuration.yaml).

```yaml
rootDirectory:
repositories:
  - code: UKDS # Friendly name of the repository.
    directory: UKDS/ # Base directory to search for metadata.
    profile: file://validation-profile.xml # URL to the validation profile.
    validationGate: BASIC # The validation gate to use. Acceptable values are BASIC, BASICPLUS, STANDARD, EXTENDED and STRICT.
```
