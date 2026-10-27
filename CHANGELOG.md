# Changelog

All notable changes to this project are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and versions follow
[Semantic Versioning](https://semver.org/spec/v2.0.0.html) once 1.0.0 is reached.

## [Unreleased]

### Added
- Nothing yet.

### Removed
- The standalone build guide and the installation instructions from the
  documentation.

## [0.1.0] - 2026-10-27

### Added
- NeoForge 1.21.1 single-loader project with a committed Gradle wrapper and Java 21 toolchain.
- Server-side TOML configuration covering conversion triggers, wheel/rail contact, mass estimates, resync thresholds and debug logging.
- Dependency-free physics model for carriage mass and inertia, curve radius estimation, lateral acceleration limits, wheel adhesion with slip, and resync hysteresis, with JUnit tests.
- Soft dependency on Sable through a bundled Sable Companion jar. Without Sable the add-on logs a warning and leaves Create's train movement unchanged.
- Conversion of a derailed carriage's blocks into a Sable sub-level, with the initial velocity taken from the train's speed and track direction.
- Resynchronisation back to Create's TrackGraph using Create's own `TrainMigration` and `TravellingPoint.migrateTo` API.
- Datapack definitions for Sable block physics properties applied to Create bogeys, casings and train controls.
- Project documentation: contribution rules, code of conduct, security policy, support channels and citation metadata.

### Changed
- The repository README now describes the mod instead of the repository owner.

### Known limitations in this release
- No prebuilt jar is published yet.
- The Sable integration is written against Sable 2.0.6's API but has not been validated in a live game build.
- Rebinding requires a straight track segment that fits the whole train, or that the original graph still resolves the old travelling points.

[Unreleased]: https://github.com/GoShadowS/create-trains-sable/compare/v0.1.0...HEAD
[0.1.0]: https://github.com/GoShadowS/create-trains-sable/releases/tag/v0.1.0
