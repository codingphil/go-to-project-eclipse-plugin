# Developing Go to Project

This guide describes the supported Eclipse PDE and command-line build workflows
for Go to Project. The Maven Wrapper build is the authoritative verification
path; the Eclipse workspace is intended for editing, incremental compilation,
launching, and debugging.

## Prerequisites

- Eclipse IDE 2026-06 / Eclipse Platform 4.40
- A Java 21 JDK used to run Eclipse and the build
- Network access to `https://download.eclipse.org/releases/2026-06/` when the
  target platform is first resolved

The Eclipse IDE for Eclipse Committers package includes the required tooling.
When using another Eclipse package, install these 2026-06 features:

| Eclipse feature | Installable-unit ID |
| --- | --- |
| Eclipse Java Development Tools | `org.eclipse.jdt.feature.group` |
| Eclipse Plug-in Development Environment | `org.eclipse.pde.feature.group` |
| M2E - Maven Integration for Eclipse | `org.eclipse.m2e.feature.feature.group` |
| M2E - PDE Integration | `org.eclipse.m2e.pde.feature.feature.group` |

M2E and M2E-PDE provide IDE awareness for the Maven/Tycho build. A separate
system Maven installation is not required because the repository includes the
Maven Wrapper.

Confirm the Java version before building:

```powershell
java -version
```

The reported major version must be 21. In Eclipse, also check that
`JavaSE-21` is associated with the Java 21 JDK under **Preferences > Java >
Installed JREs > Execution Environments**.

## Create the Eclipse workspace

Use a fresh Eclipse 2026-06 workspace outside this source repository. Do not
reuse legacy workspace metadata from an older Eclipse installation, and do not
import the directory containing the repository checkouts or this source
repository root as an Eclipse project.

Import the checked-in PDE projects with **File > Import > General > Existing
Projects into Workspace**. Select this repository as the search root, clear
**Copy projects into workspace**, and import:

- `dev.lonsing.eclipse.plugins.gotoproject`
- `dev.lonsing.eclipse.plugins.gotoproject.tests`
- `dev.lonsing.eclipse.plugins.gotoproject.ui.tests`
- `dev.lonsing.eclipse.plugins.gotoproject.feature`

The remaining normal-reactor modules are:

- `target-platform`
- `repository`

They intentionally do not contain checked-in `.project` files. To show them in
Project Explorer without changing the source tree, create workspace-local
**General > Project** projects and add a linked folder named `module` pointing
to each directory. Do not let Eclipse generate `.project` files in these source
directories. The optional `releng/composite-repository` module is needed only
for publication verification and does not need to be in the normal development
workspace.

Do not import the `gh-pages` checkout or its historical update-site projects.
That checkout contains publication output, not development source.

## Activate the target platform

Open:

```text
target-platform/eclipse-2026-06.target
```

Wait for all installable units to resolve, then select **Set as Active Target
Platform** in the Target Definition editor. This definition pins Eclipse
Platform 4.40, JDT 2026-06, and JUnit from the versioned 2026-06 p2 repository
and uses `JavaSE-21`.

Do not develop against the running Eclipse installation or a local p2 pool.
The checked-in target definition is the dependency baseline.

Only one target can be active in an Eclipse workspace. If Go to Project and
Working Set Tools are developed together, activate the Working Set Tools target
definition; it is a compatible superset that also contains m2e.

## Build in Eclipse

With the target resolved and active:

1. Enable **Project > Build Automatically**, or use **Project > Clean** to
   request a full workspace build.
2. Wait for target resolution and PDE/JDT builders to finish.
3. Check the Problems view for Java, manifest, build-path, and extension errors.

Eclipse incremental builds may create project-local `bin` directories. They are
development output only and must not be committed or used as release artifacts.

## Authoritative build and tests

From this repository root, run:

```powershell
.\mvnw.cmd --batch-mode --no-transfer-progress clean verify
```

The wrapper uses Maven 3.9.16, Tycho 5.0.3, Java 21, and the checked-in target
definition. The build:

- compiles the production plug-in and both test fragments;
- runs the headless and UI workbench test suites;
- validates the target platform;
- assembles the feature and p2 repository; and
- verifies the generated repository metadata and artifacts.

All Maven-generated content is written below module `target` directories. The
verified p2 repository is staged at:

```text
repository/target/repository
```

The two test projects have different roles:

- `dev.lonsing.eclipse.plugins.gotoproject.tests` contains non-SWT plug-in
  tests that run with the headless test harness.
- `dev.lonsing.eclipse.plugins.gotoproject.ui.tests` contains workbench/UI
  tests that run on the UI thread.

Use the full `clean verify` command before submitting a change. A successful
incremental Eclipse build is useful feedback but does not verify tests, p2
assembly, or repository integrity.

## Troubleshooting

### Required bundles cannot be resolved

Confirm that `eclipse-2026-06.target`, rather than **Running Platform**, is the
active target. Reload the target definition and verify access to the 2026-06 p2
repository.

### JavaSE-21 is unresolved

Run Eclipse with a Java 21 JDK and map the `JavaSE-21` execution environment to
that JDK in Eclipse preferences. Also ensure the shell running `mvnw.cmd` uses
Java 21.

### UI tests behave differently from plain JUnit

The UI tests require Tycho's Eclipse workbench harness and UI thread. The
Maven Wrapper result is authoritative; do not treat a plain JUnit launch as an
equivalent verification run.

### The repository is dirty after an Eclipse build

Review generated `bin` directories separately from source changes. Maven
outputs belong only under `target`. Before committing, use `git status` from
this repository root and keep generated output out of the change.

## Release boundary

Normal development and pull-request builds do not publish. Do not edit or copy
generated p2 files into the `gh-pages` checkout. Publication is performed only
by the intentional tag/manual release workflow after the full build and
repository verification have passed.
