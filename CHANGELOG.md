# Changelog

All notable changes to CMV Console will be documented in this file.

The format is based on [Keep a Changelog](http://keepachangelog.com/en/1.0.0/)
and this project adheres to [Semantic Versioning](http://semver.org/spec/v2.0.0.html).

*For each release, use the following sub-sections:*

- *Added (for new features)*
- *Changed (for changes in existing functionality)*
- *Deprecated (for soon-to-be removed features)*
- *Removed (for now removed features)*
- *Fixed (for any bug fixes)*
- *Security (in case of vulnerabilities)*

## [4.0.0] - 2025-06-16

[![DOI](https://zenodo.org/badge/DOI/10.5281/zenodo.15630785.svg)](https://doi.org/10.5281/zenodo.15630785)

### Changed

- Skip validating records marked as deleted in OAI-PMH responses ([#679](https://github.com/cessda/cessda.cdc.versions/679))
- Use the system property `logging.json` to control JSON logging ([PR-135](https://github.com/cessda/cessda.cmv.console/pull/135))

## [3.8.0] - 2025-02-25

[![DOI](https://zenodo.org/badge/DOI/10.5281/zenodo.14916314.svg)](https://doi.org/10.5281/zenodo.14916314)

This release updates CMV Core to version 4.0.0. This is a major release with significant functional changes. See <https://github.com/cessda/cessda.cmv.core/releases/tag/4.0.0> for details.

## [3.7.0] - 2024-11-12

### Fixed

* Fix validations failing due to OOM errors

## [3.6.0] - 2024-06-18

[![DOI](https://zenodo.org/badge/DOI/10.5281/zenodo.11915471.svg)](https://doi.org/10.5281/zenodo.11915471)

### Added

* Report details about valid persistent identifiers in addition to invalid persistent identifiers ([#635](https://github.com/cessda/cessda.cdc.versions/issues/635))

### Changed

* Updated OpenJDK to version 21 ([#659](https://github.com/cessda/cessda.cdc.versions/issues/659))
* Updated CMV Core to version 3.0.0 ([PR-72](https://github.com/cessda/cessda.cmv.console/pull/72))

### Fixed

* Fixed validations failing when files are deleted from the validation directory ([PR-77](https://github.com/cessda/cessda.cmv.console/pull/77))

## [3.4.0] - 2023-08-29

[![DOI](https://zenodo.org/badge/DOI/10.5281/zenodo.8277085.svg)](https://doi.org/10.5281/zenodo.8277085)

### Added

- Add the CDC identifier to the output ([#514](https://github.com/cessda/cessda.cdc.versions/issues/514))

### Changed

- Use a single instance of SchemaValidator with all schemas configured rather than a specific instance for each DDIVersion ([PR-20](https://github.com/cessda/cessda.cmv.console/pull/20))

### Removed

- Removed the ability to copy a separate set of records when validation passes ([#565](https://github.com/cessda/cessda.cdc.versions/issues/565))

## [3.3.0] - 2023-06-13

[![DOI](https://zenodo.org/badge/DOI/10.5281/zenodo.8021255.svg)](https://doi.org/10.5281/zenodo.8021255)

### Added

- Added tests for DDI 3.2 persistent identifiers ([#391](https://github.com/cessda/cessda.cdc.versions/issues/391))
- Added a general end-to-end test running the validator from the command
  line ([PR-6](https://github.com/cessda/cessda.cmv.console/pull/6))

### Changed

- Disabled enforcing XML Schema validation ([#545](https://github.com/cessda/cessda.cdc.versions/issues/545))
- Changed the report output so that each validated file only outputs one
  entry ([PR-12](https://github.com/cessda/cessda.cmv.console/pull/12))

## [3.2.1] - 2023-03-28

### Fixes

- Fix crash when a pipeline.json file is invalid ([PR-3](https://github.com/cessda/cessda.cmv.console/pull/3))

## [3.1.1] - 2022-11-08

### Fixes

- Fix the validator deleting orphaned records from the source directory rather than the destination
  directory ([#485](https://github.com/cessda/cessda.cdc.versions/issues/485))

## 3.0.2 - 2022-09-06

### Changes

- Optimised the parallelization of the validator by making the validator
  asynchronous ([#445](https://github.com/cessda/cessda.cdc.versions/issues/445))
- Rename `XPathContext` to `DDIVersion`, the new name reflects the purpose of the
  enum ([#445](https://github.com/cessda/cessda.cdc.versions/issues/445))

### Fixes

- Fixed incorrect validation of DDI 3.2 persistent identifiers ([#449](https://github.com/cessda/cessda.cdc.versions/issues/449))
- Fixed not including the validator job ID in the output ([#451](https://github.com/cessda/cessda.cdc.versions/issues/451))

## [2.0.0] - 2022-06-07

[![DOI](https://zenodo.org/badge/DOI/10.5281/zenodo.6577771.svg)](https://doi.org/10.5281/zenodo.6577771)

### Additions

- Added Jenkins and SonarQube badges [#435](https://github.com/cessda/cessda.cdc.versions/issues/435)
- Added new Progedo OAI-PMH endpoint [#403](https://github.com/cessda/cessda.cdc.versions/issues/403)
- Added a persistent identifier check [#320](https://github.com/cessda/cessda.cdc.versions/issues/320)

### Changes

- Refactored the validator to read configuration from common file, removed local configuration and moved all configuration to command line arguments [#409](https://github.com/cessda/cessda.cdc.versions/issues/409)
- Improved the test coverage of the console validator [#391](https://github.com/cessda/cessda.cdc.versions/issues/391)

### Fixed

- Reinstated OAI-PMH set name to harvester storage file path [#411](https://github.com/cessda/cessda.cdc.versions/issues/411)
- Workaround for XML schema violations caused by incorrect DDI serialisation [#388](https://github.com/cessda/cessda.cdc.versions/issues/388)

## [1.0.0] - 2021-11-25

[![DOI](https://zenodo.org/badge/DOI/10.5281/zenodo.5711087.svg)](https://doi.org/10.5281/zenodo.5711087)

### Additions

- Log the profile name when outputting the sum of valid and invalid records.
- Only output the constraint violations rather than the entire validation report.
- Add structured logging to schema violation errors.
- Read the source XML into a byte array, move the XML schema validation into validateDocuments() Reading the XML into a byte array prevents having to read it from disk multiple times.
- Validate against the DDI codebook schema. The CDC aggregator client expects fully compliant DDI 2.5 records, and CMV doesn't guarantee that this is the case. Validating NESSTAR records is not supported yet.
- Add the ability to copy validated records to another directory.
- Only log invalid records, use a counter to log the number of validated and invalid records.
- Ensure that the schema violation and constraint violation strings are set to null if there are no schema or constraint violations.
- Improve reporting of schema and profile violations. Report profile violations even if schema violations are found. Log all the schema errors present in the source document, rather than just the first encountered.
- Fix logging the entire file path instead of the OAI record id when schema validation fails, make sure that schema validations are counted correctly.
- Convert Configuration and Repository to use Java records.
- Update OpenJDK to 17.
- Add a timeout to the pipeline.
- Fix SND and CSDA configuration, add configuration for SASD and SoDaNet.
- Add OutOfMemoryError handling to more locations, update the CMV profiles. [#330](https://github.com/cessda/cessda.cdc.versions/issues/330)
- Log the validation profile used. [#333](https://github.com/cessda/cessda.cdc.versions/issues/333)
- Log the timestamp of each run. [#330](https://github.com/cessda/cessda.cdc.versions/issues/330)
- Handle Out of Memory errors when validating particularly large XML files. [#330](https://github.com/cessda/cessda.cdc.versions/issues/330)
- Don't nest the reports inside the OAI-PMH identifier. Nesting the reports inside the OAI-PMH identifier quickly leads to field exhaustion in Elasticsearch. [#330](https://github.com/cessda/cessda.cdc.versions/issues/330)
- Fix missing imports. [#330](https://github.com/cessda/cessda.cdc.versions/issues/330)
- Don't embed the JSON, instead emit it as an escaped field. [#330](https://github.com/cessda/cessda.cdc.versions/issues/330)
- Reintroduce the delay after startup. [#330](https://github.com/cessda/cessda.cdc.versions/issues/330)
- Use YAMLMapper to read the configuration file, only instance the ObjectMapper once. new YAMLMapper() is equivalent in functionality to new ObjectMapper(new YAMLFactory) but is more readable. [#330](https://github.com/cessda/cessda.cdc.versions/issues/330)
- Add the validation gate used to the output. [#330](https://github.com/cessda/cessda.cdc.versions/issues/330)
- Allow the validator to take the base directory as a command line argument. This allows the configuration to default to the working directory for testing purposes. [#330](https://github.com/cessda/cessda.cdc.versions/issues/330)
- Add a readme, use the Jackson BOM to standardise Jackson versioning.
- Parallelise the validator, handle errors if the validation profile cannot be loaded.
- Don't abort on SonarQube quality gate failure.
- Add the ability to configure validation gates, cache the validation profile.
- Handle IOExceptions, normalise directories before starting the validation.
- Move the validation to an instance method. [#303](https://github.com/cessda/cessda.cdc.versions/issues/303)
- Log validation exceptions. [#303](https://github.com/cessda/cessda.cdc.versions/issues/303)
- Fix accidentally closing the path stream before it can be used. [#303](https://github.com/cessda/cessda.cdc.versions/issues/303)
- Wait 5 seconds on startup. [#303](https://github.com/cessda/cessda.cdc.versions/issues/303)
- Log each record separately.
- Remove CMV profile.

[3.8.0]: https://github.com/cessda/cessda.cmv.console/releases/tag/3.8.0
[3.7.0]: https://github.com/cessda/cessda.cmv.console/releases/tag/3.7.0
[3.6.0]: https://github.com/cessda/cessda.cmv.console/releases/tag/3.6.0
[3.4.0]: https://github.com/cessda/cessda.cmv.console/releases/tag/3.4.0
[3.3.0]: https://github.com/cessda/cessda.cmv.console/releases/tag/3.3.0
[3.2.1]: https://github.com/cessda/cessda.cmv.console/releases/tag/3.2.1
[3.1.1]: https://github.com/cessda/cessda.cmv.console/releases/tag/3.1.1
[2.0.0]: https://github.com/cessda/cessda.cmv.console/releases/tag/2.0.0
[1.0.0]: https://github.com/cessda/cessda.cmv.console/releases/tag/1.0.0
