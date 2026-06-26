# Drift Locator Changelog

## [Unreleased]

### Changed
- Exported schema reports now include connection identification details so source and target snapshots are easier to distinguish
- PostgreSQL schema fetching now has version-matrix integration coverage and normalizes procedure signatures consistently across supported server versions
- MySQL schema fetching now has version-matrix integration coverage across supported server versions

## [1.3.0]

### Added
- SQLite 3.x support for file-based schema comparison

### Changed
- Connection configuration is now database-engine aware, with a dedicated SQLite file picker flow

## [1.2.0]

### Added
- MySQL 8.0+ support
- Database engine selector in connection dialogs

### Changed
- Existing serialized connections without a database engine are treated as PostgreSQL
- PostgreSQL schema snapshots and diffs now distinguish overloaded routines and operators by signature instead of bare name
- PostgreSQL metadata export now includes richer details for views, materialized views, triggers, constraints, indexes, grants, and FTS configurations
- Sensitive subscription connection data is masked before it appears in PostgreSQL schema metadata

## [1.1.1]

### Fixed
- Unexpected creation of a system directory

## [1.1.0]

### Added
- Option to save passwords via IntelliJ Platform's PasswordSafe
- Minor improvements

### Fixed
- Incorrect query for materialized view

## [1.0.0]

### Added
- Initial release with PostgreSQL support
- Add, edit, and delete database connections
- Compare schemas between two database instances
- Support for tables, views, functions, procedures, sequences
- Built-in IntelliJ IDEA diff viewer integration
- Auto-save connections and auto-fill from last used
- Schema snapshots export to `.driftLocator/YYYY_MM_DD_HH_MM/`
