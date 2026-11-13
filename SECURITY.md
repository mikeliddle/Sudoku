# Security and release policy

## Public source and private credentials

This repository contains no signing key, signing passwords, app accounts,
API keys, or GitHub credentials. APKs are published as GitHub Release assets,
not committed to Git. Build outputs, SDK paths, environment files, signing
properties, and common private-key formats are ignored.

The publisher's Android signing key has a private local backup outside the
checkout and an approval-gated copy in the `android-release` environment's
secrets. Never upload it as a release asset or put it in repository or
organization Actions secrets. Base64 encodes the keystore; it does not encrypt it.

## Owner-approved release automation

`.github/workflows/android-release.yml` runs only by manual dispatch on current,
protected `main`. Its default `verify` mode builds/tests an unsigned release
without release credentials and cannot sign or publish. There are no pull-request
or tag triggers. `release` mode may be requested only by `mikeliddle`.

The fresh signing job and separate publishing job both require the
`android-release` environment. Its sole required reviewer is the **mikeliddle
user**, admin bypass is disabled, and only the exact `main` branch is allowed.
Self-review is allowed so the sole owner can approve a release they initiated.
Never approve an environment deployment on the user's behalf. Review the
workflow, exact commit and build results before approving; GitHub may ask for
approval again for the later publishing job.

Only the signing step receives the environment's four Android signing secrets.
It does not check out source, run Gradle/project scripts, or use caches.
Android build-tools 36.0.0 are downloaded from Google's HTTPS repository and
SHA-256 verified before secrets are provided. The temporary keystore is
access-restricted and removed in `finally` and an unconditional cleanup step.
The APK must have the expected application ID/version, no permissions or
debuggable flag, valid alignment and the existing public-release certificate.
Neither keys nor passwords are uploaded as artifacts.

The build and signing tokens are read-only; only the separately gated
publishing job has `contents: write`, using the temporary `GITHUB_TOKEN`, not a
PAT. Immutable artifact IDs and SHA-256 archive/file digests bind the handoffs.
The publisher refuses an existing release tag, uploads assets to a draft, then
publishes it. A failure can leave a draft: inspect it rather than blindly retry.
Actions are full-commit pinned and repository-allowlisted; workflow tokens
default to read-only and cannot approve pull requests.

Main updates (including workflows) are restricted to repository administrators;
the sole administrator/collaborator at setup is `mikeliddle`. Main force-pushes
and deletion are disabled, including for admins. `v*` release tags cannot be
updated or deleted by any actor under the active ruleset. GitHub's personal-repo
API rejected a GitHub Actions bypass for a tag-creation rule, so no claim is made
that tag creation itself is exclusively restricted to this workflow. The only
current human writer is the owner, and this workflow's write-capable job is gated.

**Trust boundary:** the repository owner can edit environments, secrets,
rulesets and workflows. These controls do not prevent the owner or a compromised
owner account from changing the controls. Adding administrators/collaborators or
other write-token workflows changes that boundary and requires a fresh review.
Hosted runners, pinned actions, Java and Android tooling are also trusted;
this is not a guarantee against malicious build tools or account compromise.

Do not enable `pull_request_target` workflows that check out or execute pull
request code. Do not run contributed code or build scripts with write tokens,
cloud/signing credentials, or the publisher's signing directory available. Review a
contribution in an isolated environment without secrets; debug builds do not
need release credentials. Repository collaborators and account access must
remain trusted.

## Build supply chain

The Gradle 8.13 distribution is pinned to its official SHA-256 checksum.
The wrapper JAR was checked against the official checksum:

```text
81a82aaea5abcc8ff68b3dfcb58b3c3c429378efd98e7433460610fecd7ae45f
```

The Android Gradle Plugin version is fixed. Gradle dependency verification
metadata pins the resolved build tools' Maven artifacts to SHA-256 checksums.
With that metadata present, verification defaults to strict mode: a changed or
unlisted artifact fails verification. Do not use `--dependency-verification off`
or blindly regenerate metadata to make an unexpected failure disappear.

Checksums detect changes relative to the reviewed baseline; they do not prove
that a dependency is harmless. Review dependency updates, repositories,
wrapper changes, and verification-metadata changes together. Bootstrap/update
checksums only from trusted upstream artifacts in a credential-free environment.
The Android SDK and Java installation are separate trusted build prerequisites.

## Publishing

Build from reviewed source, verify the APK signature, confirm the release is
not debuggable and requests no permissions, and publish a SHA-256 checksum.
Keep the same private signing key for future releases and increase versionCode.
GitHub Release source archives are generated by GitHub; the APK is a separate
asset. A debug APK or another publisher's certificate cannot update the official
release.

## Reporting an issue

Use GitHub's private vulnerability reporting feature for sensitive issues when
available. Do not post passwords, keys, device identifiers, or private user data
in a public issue. This policy reduces exposure; it is not a guarantee against
all malicious dependencies or account compromise.
