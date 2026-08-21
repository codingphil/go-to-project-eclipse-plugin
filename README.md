# Go to Project Eclipse Plug-in

Go to Project quickly selects a workspace project in Project Explorer or Package Explorer. Closed projects remain searchable and are opened when selected.

## Requirements

- Go to Project 2.0.0 requires Eclipse IDE 2026-06 (Platform 4.40) or a compatible later 4.x release.
- Eclipse and the plug-in require Java 21.
- The Eclipse Platform and JDT features must be installed; the update site references the Eclipse 2026-06 repository for dependency resolution.

## Installation

Use the unchanged Eclipse update-site URL:

<https://codingphil.github.io/go-to-project-eclipse-plugin/updatesite>

The public site contains Go to Project 2.0.0 and retains the historical 1.x releases so existing installations can update in place. All of these artifacts are unsigned. Eclipse 2026-06 is therefore expected to show a trust prompt for unsigned content during installation or update; review and accept that prompt to continue.

## What is new in 2.0.0

- Explorer handling is null-safe when Project Explorer or Package Explorer has not been created or is only partially initialized.
- Project selection and focus are deterministic: visible explorers are updated when possible, the active explorer keeps focus, and Project Explorer is opened when neither explorer is visible.
- Closed projects remain searchable and are opened when selected; cancellation or a failed open stops safely.

See [RELEASE_NOTES.md](RELEASE_NOTES.md) for the complete release summary and release-verification status.

## Development

The current development version is `2.0.0.qualifier`. Build and verify it with Java 21:

```powershell
.\mvnw.cmd clean verify
```
