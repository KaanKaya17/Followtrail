# Code signing policy

[Türkçe](CODE-SIGNING.md)

**Status (22 November 2026):** Windows releases are not Authenticode-signed. The application to [SignPath Foundation](https://signpath.org) submitted on 20 November 2026 was not accepted. The signing step in the release pipeline stays in place but does not run because it is not configured; releases are published unsigned and Windows SmartScreen may warn on first launch. That is why you can [verify a download with its SHA-256 and build provenance](#verify-a-download).

## How releases are built

- Every Windows release is built from a `windows-vX.Y.Z` tag on a clean GitHub Actions Windows runner: [`.github/workflows/release-windows.yml`](../.github/workflows/release-windows.yml). Files built on a developer's machine are never published.
- Python and Node dependencies are installed from lock files (`requirements.lock.txt`, `build-requirements.lock.txt`, `package-lock.json`). Every GitHub Action is pinned to a commit SHA, and Dependabot proposes updates as reviewed pull requests.
- The package is tested on the same runner before it is published: backend tests, source checks, 46 functional checks on the packaged EXE and the ZIP review check.
- Only the two programs built from this repository are signed: `Followtrail.exe` and `resources/app/worker/tracker-worker.exe`. Both carry the product name **Followtrail** and the release version. Electron and Python runtime files keep their upstream signatures. [Signing configuration](../packaging/signpath/artifact-configuration.xml).
- The workflow publishes a GitHub [build provenance attestation](https://docs.github.com/actions/security-for-github-actions/using-artifact-attestations) that records which commit and workflow produced the ZIP.
- Releases are created as **drafts**. A maintainer reviews the notes and files and publishes them by hand. Every signing request is approved manually.

## Roles

| Role | Members | Responsibility |
| --- | --- | --- |
| Committer and reviewer | [Kaan Kaya](https://github.com/KaanKaya17) | May change the repository directly. Reviews every outside contribution (pull request) before merging; workflows and build scripts are reviewed like source code. |
| Approver | [Kaan Kaya](https://github.com/KaanKaya17) | Approves the signing request of every release. |

All team members use multi-factor authentication on GitHub and SignPath.

## Privacy

This program will not transfer any information to other networked systems unless specifically requested by the user or the person installing or operating it.

- **ZIP mode** makes no network requests; the file never leaves the device.
- **Account mode** sends requests directly to Instagram only after the user connects their own account and starts a check. The app has no server, telemetry or analytics SDK.
- An error report is shared only when the user chooses **Report on GitHub** or **Copy text**.

Details: [Privacy guide](wiki/EN-Privacy.md) · [Technical privacy notes (Turkish)](PRIVACY.md).

## Verify a download

```powershell
Get-FileHash .\Followtrail-Desktop-X.Y.Z.zip -Algorithm SHA256
```

Compare the value with `SHA256SUMS.txt` on the release page. For releases built on GitHub Actions you can also check the build provenance (requires the [GitHub CLI](https://cli.github.com)):

```powershell
gh attestation verify .\Followtrail-Desktop-X.Y.Z.zip -R KaanKaya17/Followtrail
```

For signed releases, right-click the EXE and open **Properties → Digital Signatures**.

## Text added after approval

When SignPath Foundation approves the project, this page, the "Code signing policy" link in the README and the release pages will carry:

> Free code signing provided by [SignPath.io](https://about.signpath.io), certificate by [SignPath Foundation](https://signpath.org).

This sentence is not published before approval.
