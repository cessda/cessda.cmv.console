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

## [1.0.0] - 2021-11-25
[![DOI](https://zenodo.org/badge/DOI/10.5281/zenodo.5711087.svg)](https://doi.org/10.5281/zenodo.5711087)

### Additions

Log the profile name when outputting the sum of valid and invalid records.

Only output the constraint violations rather than the entire validation report.

Add structured logging to schema violation errors.

Read the source XML into a byte array, move the XML schema validation into validateDocuments() Reading the XML into a byte array prevents having to read it from disk multiple times.

Validate against the DDI codebook schema. The CDC aggregator client expects fully compliant DDI 2.5 records, and CMV doesn't guarantee that this is the case. Validating NESSTAR records is not supported yet.

Add the ability to copy validated records to another directory.

Only log invalid records, use a counter to log the number of validated and invalid records.

Ensure that the schema violation and constraint violation strings are set to null if there are no schema or constraint violations.

Improve reporting of schema and profile violations. Report profile violations even if schema violations are found. Log all the schema errors present in the source document, rather than just the first encountered.

Fix logging the entire file path instead of the OAI record id when schema validation fails, make sure that schema validations are counted correctly.

Convert Configuration and Repository to use Java records.

Update OpenJDK to 17. 

Add a timeout to the pipeline.

Fix SND and CSDA configuration, add configuration for SASD and SoDaNet.

Add OutOfMemoryError handling to more locations, update the CMV profiles. [#330](https://bitbucket.org/cessda/cessda.cdc.versions/issues/330)

Log the validation profile used. [#333](https://bitbucket.org/cessda/cessda.cdc.versions/issues/333)

Log the timestamp of each run. [#330](https://bitbucket.org/cessda/cessda.cdc.versions/issues/330)

Handle Out of Memory errors when validating particularly large XML files. [#330](https://bitbucket.org/cessda/cessda.cdc.versions/issues/330)

Don't nest the reports inside the OAI-PMH identifier. Nesting the reports inside the OAI-PMH identifier quickly leads to field exhaustion in Elasticsearch. [#330](https://bitbucket.org/cessda/cessda.cdc.versions/issues/330)

Fix missing imports. [#330](https://bitbucket.org/cessda/cessda.cdc.versions/issues/330)

Don't embed the JSON, instead emit it as an escaped field. [#330](https://bitbucket.org/cessda/cessda.cdc.versions/issues/330)

Reintroduce the delay after startup. [#330](https://bitbucket.org/cessda/cessda.cdc.versions/issues/330)

Use YAMLMapper to read the configuration file, only instance the ObjectMapper once. new YAMLMapper() is equivalent in functionality to new ObjectMapper(new YAMLFactory) but is more readable. [#330](https://bitbucket.org/cessda/cessda.cdc.versions/issues/330)

Add the validation gate used to the output. [#330](https://bitbucket.org/cessda/cessda.cdc.versions/issues/330)

Allow the validator to take the base directory as a command line argument. This allows the configuration to default to the working directory for testing purposes. [#330](https://bitbucket.org/cessda/cessda.cdc.versions/issues/330)

Add a readme, use the Jackson BOM to standardise Jackson versioning.

Paralellise the validator, handle errors if the validation profile cannot be loaded.

Don't abort on SonarQube quality gate failure.

Add the ability to configure validation gates, cache the validation profile.

Handle IOExceptions, normalise directories before starting the validation.

#303: Move the validation to an instance method. [#303](https://bitbucket.org/cessda/cessda.cdc.versions/issues/303)

#303: Log validation exceptions. [#303](https://bitbucket.org/cessda/cessda.cdc.versions/issues/303)

#303: Fix accidentally closing the path stream before it can be used. [#303](https://bitbucket.org/cessda/cessda.cdc.versions/issues/303)

#303: Wait 5 seconds on startup. [#303](https://bitbucket.org/cessda/cessda.cdc.versions/issues/303)

Log each record separately.

Remove CMV profile.

