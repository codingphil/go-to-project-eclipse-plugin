# Go to Project Eclipse Plug-in

Go to Project quickly selects a workspace project in Project Explorer or Package Explorer. Closed projects remain searchable and are opened when selected.

## Requirements

- Go to Project 2.0.0 requires Eclipse IDE 2026-06 (Platform 4.40) or a compatible later 4.x release.
- Eclipse and the plug-in require Java 21.
- The Eclipse Platform and JDT features must be installed; the update site references the Eclipse 2026-06 repository for dependency resolution.

## Installation

Use the unchanged Eclipse update-site URL:

<https://codingphil.github.io/go-to-project-eclipse-plugin/updatesite>

The public site currently contains the unsigned 1.x release. Eclipse may show an unsigned-content trust prompt. The signing policy for 2.0.0 will be documented before that release is published.

## Development

The current development version is `2.0.0.qualifier`. Build and verify it with Java 21:

```powershell
.\mvnw.cmd clean verify
```
