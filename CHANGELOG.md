# Changelog

All notable changes to this project will be documented in this file.

## [Unreleased]

### Added
- Export related files action (usages, inheritors, nested classes).
- Export dialog with file preview and progress bar.
- Support for absolute and relative paths, and path-list mode.
- Persistent export preferences via `PropertiesComponent`.

### Fixed
- Corrected `ArchiveMode` enum mapping in the export dialog.
- Action now extracts `PsiClass` from both editor and project view.
- Replaced eager byte array loading with streaming in `ArchiveWriter` to prevent OOM.