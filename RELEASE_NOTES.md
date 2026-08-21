# Go to Project 2.0.0 release notes

Go to Project 2.0.0 modernizes the plug-in for Eclipse IDE 2026-06 (Eclipse Platform 4.40) and Java 21 while preserving its bundle ID, feature ID, command behavior, and public update-site URL.

## Requirements and installation

- Eclipse IDE 2026-06 (Platform 4.40), or a compatible later Eclipse 4.x release
- Java 21
- Eclipse Platform and JDT features

Install or update from the unchanged public update-site URL:

<https://codingphil.github.io/go-to-project-eclipse-plugin/updatesite>

The 2.0.0 feature and plug-in JARs are intentionally unsigned. Eclipse 2026-06 is expected to show a trust prompt for unsigned content during installation or update; review and accept that prompt to continue. The retained 1.x artifacts are also unsigned.

## Notable fixes

- Project Explorer and Package Explorer access is null-safe when a view has not been created or is only partially initialized.
- Selection and focus behavior is deterministic. Every visible explorer with a valid selection provider is updated, the already-active explorer keeps focus, and Project Explorer is opened and focused when neither explorer is visible.
- Closed projects remain in the chooser and are opened before selection. Cancelling or failing to open a project stops without applying an invalid selection.

## Release status

The 2.0.0 source and release automation are prepared, but the immutable `v2.0.0` tag and public artifact verification are release-time gates. Source tag, Pages publication commit, workflow run, qualified artifact versions, checksums, and fresh-install/update smoke-test evidence will be recorded here after publication and verification.
