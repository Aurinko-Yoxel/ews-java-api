# Releasing ews-java-api

Releases use Git tags matching the version, for example `2.6.1` (no `v` prefix
or `-SNAPSHOT` suffix). GitHub Actions tests the tagged source, creates a GitHub
Release with JAR attachments, and requests the version from JitPack. JitPack
builds the tag independently and hosts its Maven artifacts.

## Prerequisites

- JDK 8 and Maven 3.6.3 or a newer Maven version compatible with JDK 8.
  Check `mvn -version`; Maven itself must run on JDK 8 for this legacy project.
- GitHub SSH access with permission to push commits and tags to
  `Aurinko-Yoxel/ews-java-api`.
- GitHub Actions enabled, with repository policy allowing the release job's
  `GITHUB_TOKEN` to write repository contents. No separate release secret is
  needed for this public repository.
- A clean checkout of the intended release branch, synchronized with its remote.
- All tests passing, including timezone tests on the installed JDK.

The workflow runs on pull requests to `main`/`master` and on version-tag pushes.
If the repository uses another integration branch, update the pull-request filter.
Tag builds are not restricted to a particular branch.

## Prepare a release

Commit the release configuration and desired library changes before starting.
The POM should contain the next development version, such as `2.6.1-SNAPSHOT`.
Choose a version whose tag does not already exist; do not move published tags.

Run the test suite:

```sh
mvn -B clean test
```

Then either let Maven choose the release and next development versions:

```sh
mvn -B release:prepare
```

Or supply them explicitly (example only; choose unused versions):

```sh
mvn -B release:prepare \
  -DreleaseVersion=2.6.1 \
  -DdevelopmentVersion=2.6.2-SNAPSHOT
```

`release:prepare` changes the POM to the release version, runs `clean test`,
creates a release commit and version tag, advances the POM to the next snapshot,
and **pushes the commits and tag**. It is not a local-only validation command.
An interactive `mvn release:prepare` is also available.

Do **not** run `release:perform` or `mvn deploy` for JitPack. They target Maven
deployment repositories; this project's legacy `distributionManagement` and
`deploy_snapshot.sh` are not used by the new release process.

## Verify publication

1. Open the tag's run under GitHub Actions. Tests must pass before the release job
   runs. Snapshot or malformed tags are rejected by release validation.
2. Check the GitHub Release and its binary, source, and Javadoc JAR attachments.
3. Check JitPack's build status and log:

   ```text
   https://jitpack.io/#Aurinko-Yoxel/ews-java-api
   https://jitpack.io/com/github/Aurinko-Yoxel/ews-java-api/2.6.1/build.log
   ```

The workflow requests the published POM to trigger JitPack. A GitHub Release alone
does not prove the JitPack build succeeded. JitPack skips tests and legacy
Checkstyle verification during artifact installation; GitHub CI and Maven release
preparation run the tests separately. Anyone requesting a tag directly from
JitPack can trigger its build independently of GitHub Actions.

If tests or packaging fail, fix the cause and prepare a new version rather than
rewriting a published tag. If only JitPack fails transiently, inspect its log and
retry the failed build through JitPack, then rerun the failed workflow job.

## Use the release

Maven:

```xml
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>

<dependency>
    <groupId>com.github.Aurinko-Yoxel</groupId>
    <artifactId>ews-java-api</artifactId>
    <version>2.6.1</version>
</dependency>
```

sbt:

```scala
resolvers += "jitpack" at "https://jitpack.io"
libraryDependencies += "com.github.Aurinko-Yoxel" % "ews-java-api" % "2.6.1"
```

Replace `2.6.1` with the actual published tag. JitPack coordinates come from the
GitHub repository and tag; the source POM's group ID does not need changing.
