# Releasing

1. Write the release notes in `notes/{version-number}.md`
2. Create a git tag for the version
   `git tag v{version-number} && git push --tags`
3. The release process will be run using github actions. See [`release.yaml`](./.github/workflows/release.yaml)
