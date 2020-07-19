# Alfred Jenkins

This is a WIP alfred workflow for interacting with Jenkins

# Releasing

1. Write the release notes in `notes/{version-number}.md`
2. Create a git tag for the version
   `git tag v{version-number && git push --tags`
3. Run the build and release process
   `sbt githubRelease`

# Development Notes

* Graal native-image build
    * `graalvm-native-image:packageBin`

* `/Users/elias.jordan/code/alfred-jenkins/target/universal/stage/bin/alfred-jenkins`

* `/Users/elias.jordan/code/alfred-jenkins/target/graalvm-native-image/alfred-jenkins`

* `/Users/elias.jordan/code/alfred-jenkins/run_with_agent.sh`
