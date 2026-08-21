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

Go to Project 2.0.0 was published and verified on 2026-08-21:

- Source tag: annotated `v2.0.0`, peeling to source commit `30fa8da28e0f010264e3a971f1036f8af4bb408f`
- Release workflow: run `32495902889`, artifact `9451656054`
- Pages publication commit: `b325a7c3016f95641d105ec863963870c2fecc82`
- Qualified feature and bundle version: `2.0.0.202608192230`
- Feature SHA-256: `418d8b21ddd05d9c1d0eb0ce244e66b7da5e6c025b05b44a766dd059e7b71565`
- Plug-in SHA-256: `bee8d10882045102721bc0e48ed7e3fcbc181c8f0deff2b5d796167ba395fa12`
- Release artifact ZIP SHA-256: `6f215b8f5dbdbf6a264c25da190290da03e5d8331a6d40edfad78baad98cdd0a`

Fresh installation and update from the published 1.x feature were tested in isolated, unmodified Eclipse IDE for Java Developers 2026-06 profiles. The staged candidate, exact tag artifact, unchanged public update URL, both explorer views, the no-explorer state, closed projects, restart persistence, and the expected unsigned-content trust flow all passed without an unexpected Error Log entry. All 21 non-dot files served by GitHub Pages returned HTTP 200 with the recorded release hashes; the public repository exposes the retained 1.x versions and `2.0.0.202608192230`.
