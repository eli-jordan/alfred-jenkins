<p align="center">
    <img height="128" src="alfred/icon.png" />
</p>

# Jenkins Builds for Alfred

![Alfred Jenkins CI](https://github.com/eli-jordan/alfred-jenkins/workflows/CI/badge.svg) 
[![Scala Steward badge](https://img.shields.io/badge/Scala_Steward-helping-blue.svg?style=flat&logo=data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAA4AAAAQCAMAAAARSr4IAAAAVFBMVEUAAACHjojlOy5NWlrKzcYRKjGFjIbp293YycuLa3pYY2LSqql4f3pCUFTgSjNodYRmcXUsPD/NTTbjRS+2jomhgnzNc223cGvZS0HaSD0XLjbaSjElhIr+AAAAAXRSTlMAQObYZgAAAHlJREFUCNdNyosOwyAIhWHAQS1Vt7a77/3fcxxdmv0xwmckutAR1nkm4ggbyEcg/wWmlGLDAA3oL50xi6fk5ffZ3E2E3QfZDCcCN2YtbEWZt+Drc6u6rlqv7Uk0LdKqqr5rk2UCRXOk0vmQKGfc94nOJyQjouF9H/wCc9gECEYfONoAAAAASUVORK5CYII=)](https://scala-steward.org)


Browse and search for jenkins builds right from Alfred.

# Install

* Download the `Jenkins.alfredworkflow` of the [latest release](https://github.com/eli-jorda/alfred-jenkins/releases/latest)
* Double-click the downloaded file to launch Alfred and install the workflow.

**MacOS Catalina**

If you are running catalina, you need to allow unsigned binaries to be run for this workflow to
work. Follow [this guide](https://github.com/deanishe/awgo/wiki/Catalina) to allow the `alfred-jenkins` binary to be run on your system.
 
# Usage

* `jenkins-login` &mdash; Authenticate with a Jenkins server 
   - `jenkins-login <url> <username> <password>`
     e.g. `jenkins-login https://jenkins.com/ eli-jordan password123`
   - When logging in, you will be prompted to allow access to the keychain. Alfred jenkins
     saves the Jenkins password in the current users keychain. Select "Always Allow" to avoid
     being prompted every time a request is made to Jenkins.
     
* `jenkins-browse` &mdash; Browse all Jenkins jobs on the configured server.
   - `↩` drill into the job to see its children, or build history
   - `⌘ ↩` open the current item in the browser
   
* `jenkins-search` &mdash; Search all Jenkins jobs that are not folders
   - `↩` drill into the job to see its children, or build history
   - `⌘ ↩` open the current item in the browser
   
# Compatibility

* This worklfow has been tested on MacOS 10.15.5 and Alfred 4. If you have problems
  when using different version please [open an issue](https://github.com/eli-jordan/alfred-jenkins/issues/new)




