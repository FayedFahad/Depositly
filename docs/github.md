# GitHub releases

The `Build and publish APK` workflow publishes the signed APK under **Releases → latest → Assets** after a successful push to `main`. It also publishes `build-info.json` containing the source commit, Android version code, APK hash and signing certificate fingerprint. Old APK names are removed from this rolling release when the visible version changes. Other releases are not modified.

The repository should be private unless its owner decides to publish the source. Enable GitHub Actions in repository settings if necessary.

## Signing secrets

Add these **repository Actions secrets**:

| Name | Value |
| --- | --- |
| `DEPOSITLY_KEYSTORE_BASE64` | Base64 contents of the original `depositly.p12` signing file |
| `DEPOSITLY_KEYSTORE_PASSWORD` | The original signing password |

Do not commit either value. `private/`, keystores, passwords, APKs and archives are excluded by `.gitignore`. The original private signing files remain available locally under `private/signing/`; keep an independent safe copy before moving or removing this project.

The expected signing certificate SHA-256 is `655445e0b1a199ef9ae90efa6e795d0800d36ef0ca725f5f87835d396796224d`. The build refuses to publish an APK signed with a different certificate, because it would not update the installed Depositly app in place.

The workflow reconstructs signing files only in the temporary runner directory. The source checkout and release uploads do not include them. GitHub-hosted runners are discarded after the job.

## Version codes

CI selects a code at least as large as the configured base plus the workflow run number, and larger than the last published build's code. This maintains installable updates without requiring a manual version-code edit for every change. The visible version name remains controlled by `app/apktool.yml` and the interface labels.

The rolling `latest` tag points to the successfully built source commit. Builds are serialized per branch, and obsolete runs are canceled. A failed test/build does not run the publishing step.

## Troubleshooting

- Missing signing secrets: add the two secrets above and rerun the workflow.
- Version label test failure: match the Settings interface labels to `versionName` in `app/apktool.yml`.
- SDK preparation failure: inspect the Prepare Android tools step; it installs the pinned platform/build-tools as needed.
- Release permission failure: the publishing job needs `contents: write`, already configured in the workflow.

Official references: [GitHub release assets](https://cli.github.com/manual/gh_release_upload), [encrypted repository secrets](https://cli.github.com/manual/gh_secret_set), and [workflow permissions](https://docs.github.com/en/actions/writing-workflows/workflow-syntax-for-github-actions#permissions).

