/**
It's hard to test this script without constantly open/closing PRs.
Set to true to test with triggering only a build and using mock values.
*/
TEST_MODE = false

// Used to avoid known_hosts addition, which would require each machine to have GitHub added in advance (maybe should do?)
GIT_SSH_COMMAND = 'GIT_SSH_COMMAND="ssh -o UserKnownHostsFile=/dev/null -o StrictHostKeyChecking=no"'

// Globals
enum VersionIncrement { MAJOR, MINOR, PATCH }

def debug(String str) {
    echo "[DEBUG] ${str}"
}

// Returns Map of Maps containing the parsed JSON from the pull request event
def getPullRequestEvent() {
    def prEvents = null

    if(TEST_MODE) {
        prEvents = readJSON file: "${env.WORKSPACE}/pull_request_event.json"
        prEvents = prEvents.pull_request // Jenkins is configured to just grab the $.pull_request section -- this models that
    }
    else
        prEvents = readJSON text: env.pull_request_event

    return prEvents
}

// Takes a Map of Maps containing the parsed JSON from the pull request event
// Returns a list of label strings
def getLabels(prEvent) {
    debug("getLabels( prEvent: ${prEvent} )")

    def labels = []
    prEvent.labels.each{ labels << it.name }

    return labels
}
def notifyBuild(String buildStatus, Exception e) {
    buildStatus = buildStatus ?: 'SUCCESSFUL'

    // Default values
    def colorName = 'RED'
    def colorCode = '#FF0000'
    def subject = "${buildStatus}: Job '${env.JOB_NAME} [${env.BUILD_NUMBER}]'"
    def summary = """*${buildStatus}*: Job '${env.JOB_NAME} [${env.BUILD_NUMBER}]':\nMore detail in console output at <${env.BUILD_URL}|${env.BUILD_URL}>"""
    def details = """${buildStatus}: Job '${env.JOB_NAME} [${env.BUILD_NUMBER}]':\n
    Check console output at ${env.BUILD_URL} """
    // Override default values based on build status
    if (buildStatus == 'STARTED') {
        color = 'YELLOW'
        colorCode = '#FFFF00'
    } else if (buildStatus == 'SUCCESSFUL') {
        color = 'GREEN'
        colorCode = '#00FF00'
    } else {
        color = 'RED'
        colorCode = '#FF0000'
        details += "<p>Error message ${e.message}, stacktrace: ${e}</p>"
        summary += "\nError message ${e.message}, stacktrace: ${e}"
    }

    // Send notifications

    slackSend channel: "#cals-api", baseUrl: 'https://hooks.slack.com/services/', tokenCredentialId: 'slackmessagetpt2', color: colorCode, message: summary
    emailext(
            subject: subject,
            body: details,
            attachLog: true,
            recipientProviders: [[$class: 'DevelopersRecipientProvider']],
            to: "Leonid.Marushevskiy@osi.ca.gov, Alex.Kuznetsov@osi.ca.gov"
    )
}
// Takes an array of strings (labels)
// Returns a VersionIncrement object
def getVersionIncrement(labels) {
    debug("getVersionIncrement( labels: ${labels} )")

    def versionIncrement = null
    def versionIncrementsFound = 0
    for(label in labels){
        switch(label) {
            case "major":
                versionIncrement = VersionIncrement.MAJOR
                versionIncrementsFound++
                break
            case "minor":
                versionIncrement = VersionIncrement.MINOR
                versionIncrementsFound++
                break
            case "patch":
                versionIncrement = VersionIncrement.PATCH
                versionIncrementsFound++
                break
        }
    }

    if(versionIncrementsFound > 1)
        throw new Exception("More than one version increment label found. Please label PR with only one of 'major', 'minor', or 'patch'")

    return versionIncrement
}

// Compares two SemVer tags
// Returns -1 if tag1 is younger, 0 if equal, 1 if tag1 is newer
def compareTags(String tag1, String tag2) {
    debug("compareTags( tag1: ${tag1}, tag2: ${tag2} )")

    def tag1Split = tag1.tokenize('.')
    def tag2Split = tag2.tokenize('.')

    for(def index in (0..2)) {
        def result = tag1Split[index].compareTo(tag2Split[index])
        if(result != 0) {
            return result
        }
    }

    return 0
}

// Gets all the tags that match SemVer format
// Returns a list of strings (version number tags)
def getTags() {
    def gitTagOutput = sh(script: "git tag", returnStdout: true)
    debug("getTags(): git tag Output: ${gitTagOutput}")

    def tags = gitTagOutput.split("\n").findAll{ it =~ /^\d+\.\d+\.\d+$/ }
    return tags
}

// Gets a string indicating what the new tag should be in SemVer format
// Takes a list of strings in sem
// Returns a string with the new version tag
def getNewTag(List tags, VersionIncrement increment) {
    debug("getNewTag( tags: {$tags}, increment: ${increment} )")

    tags.sort{ x, y -> compareTags(x, y)}
    def mostRecentTag = tags.last()
    def mostRecentTagParts = mostRecentTag.tokenize('.')

    def newTagMajor = mostRecentTagParts[0].toInteger()
    def newTagMinor = mostRecentTagParts[1].toInteger()
    def newTagPatch = mostRecentTagParts[2].toInteger()

    switch(increment) {
        case VersionIncrement.MAJOR:
            newTagMajor++
            newTagMinor = 0
            newTagPatch = 0
            break
        case VersionIncrement.MINOR:
            newTagMinor++
            newTagPatch = 0
            break
        case VersionIncrement.PATCH:
            newTagPatch++
            break
    }

    def newTag = "${newTagMajor}.${newTagMinor}.${newTagPatch}"
    return newTag
}

// Updates any build files that contain a version tag
def updateFiles(String newTag) {
    debug("updateFiles( newTag: ${newTag} )")

    // TODO - Implement for updating a file
    debug("updateFiles: TODO Implement")
}

def copyAndReplaceText(source, dest, Closure replaceText){
    dest.write(replaceText(source.text))
}

def updateFiles(newTag) {
    debug("updateFiles( newTag: ${newTag} )")

    def source = new File('build.gradle.old')
    def dest = new File('build.gradle')
    source << dest.text

    copyAndReplaceText(source, dest) {
        it.replaceAll('projectVersion = (isRelease ? projectReleaseVersion : projectSnapshotVersion )', 'projectVersion = '+newTag)
    }

}
// Tags the repo

def tagRepo(String newTag) {
    debug("tagRepo( newTag: ${newTag} )")

    def tagStatus = sh(script: "git tag ${newTag}", returnStatus: true)
    if( tagStatus != 0) {
        throw new Exception("Unable to tag the repository with tag '${newTag}'")
    }

    def pushStatus = sh(
        script: "${GIT_SSH_COMMAND} git push origin ${newTag}",
        returnStatus: true)
    if( pushStatus != 0) {
        throw new Exception("Unable to push the tag '${newTag}'")
    }
}


node('dora-slave') {
    def serverArti = Artifactory.server 'CWDS_DEV'
    def rtGradle = Artifactory.newGradleBuild()
    newTag = '';
    properties([buildDiscarder(logRotator(artifactDaysToKeepStr: '', artifactNumToKeepStr: '', daysToKeepStr: '3', numToKeepStr: '15')), disableConcurrentBuilds(), [$class: 'RebuildSettings', autoRebuild: false, rebuildDisabled: false],
    parameters([
        booleanParam(defaultValue: true, description: '', name: 'USE_NEWRELIC'),
        string(defaultValue: 'inventories/tpt2dev/hosts.yml', description: '', name: 'inventory')
    ]), pipelineTriggers([githubPush()])])
    def errorcode = null;
    def buildInfo = '';

    try {
        stage('Preparation') {
            cleanWs()
            git branch: '$pull_request_event_base_ref', url: 'https://github.com/ca-cwds/dora.git'
            rtGradle.tool = "Gradle_35"
            rtGradle.resolver repo: 'repo', server: serverArti
        }
        stage("Increment Tag") {
            try {
                def prEvent = getPullRequestEvent()
                debug("Increment Tag: prEvent: ${prEvent}")

                def labels = getLabels(prEvent)
                debug("Increment Tag: labels: ${labels}")

                VersionIncrement increment = getVersionIncrement(labels)
                debug("Increment Tag: increment: ${increment}")
                if(increment != null ) {
                    def tags = getTags()
                    debug("Increment Tag: tags: ${tags}")

                    newTag = getNewTag(tags, increment)
                    debug("Increment Tag: newTag: ${newTag}")

                    updateFiles(newTag)
                }
            }
            catch(Exception ex) {
                error "[ERROR] ${ex}"
            }
        }
        stage('Build') {
            buildInfo = rtGradle.run buildFile: 'build.gradle', tasks: 'jar'
        }
        stage('Unit Tests') {
            buildInfo = rtGradle.run buildFile: 'build.gradle', tasks: 'test jacocoTestReport'
            publishHTML([allowMissing: true, alwaysLinkToLastBuild: true, keepAll: true, reportDir: 'dora-api/build/reports/tests/test/', reportFiles: 'index.html', reportName: 'JUnit Reports', reportTitles: 'JUnit tests summary'])
        }
        stage('License Report') {
            buildInfo = rtGradle.run buildFile: 'build.gradle', tasks: 'downloadLicenses'
        }
        stage('SonarQube analysis') {
            withSonarQubeEnv('Core-SonarQube') {
                buildInfo = rtGradle.run buildFile: 'build.gradle', switches: '--info', tasks: 'sonarqube'
            }
        }
        stage('Push to Artifactory') {
            rtGradle.deployer.deployArtifacts = true
            buildInfo = rtGradle.run buildFile: 'build.gradle', tasks: 'publish'
            rtGradle.deployer.deployArtifacts = false
        }
        stage('Build Docker') {
            withEnv(['ELASTIC_HOST=127.0.0.1']) {
                buildInfo = rtGradle.run buildFile: 'build.gradle', tasks: 'printConfig'
                buildInfo = rtGradle.run buildFile: './docker-dora/build.gradle', tasks: 'dockerCreateImage'
                withDockerRegistry([credentialsId: '6ba8d05c-ca13-4818-8329-15d41a089ec0']) {
                    buildInfo = rtGradle.run buildFile: './docker-dora/build.gradle', tasks: 'dockerDoraPublish'
                }
            }
        }
        stage ('Build Tests Docker'){
            buildInfo = rtGradle.run buildFile: './dora-api/docker-tests/build.gradle', switches: '--stacktrace',  tasks: 'dockerTestsCreateImage'
           withDockerRegistry([credentialsId: '6ba8d05c-ca13-4818-8329-15d41a089ec0']) {
                buildInfo = rtGradle.run buildFile: './dora-api/docker-tests/build.gradle', switches: '--stacktrace',  tasks: 'dockerTestsPublish'
           }
        }
        stage('Archive Artifacts') {
            archiveArtifacts artifacts: '**/dora*.jar,readme.txt', fingerprint: true
            cleanWs()
        }
        stage('Deploy Application') {
 	        withDockerRegistry([credentialsId: '6ba8d05c-ca13-4818-8329-15d41a089ec0']) {
	           sh "cd /opt/dora-es; docker-compose pull ; docker-compose up -d"
	        }
            git changelog: false, credentialsId: '433ac100-b3c2-4519-b4d6-207c029a103b', poll: false, url: 'git@github.com:ca-cwds/de-ansible.git'
            sh 'ansible-playbook -e NEW_RELIC_AGENT=$USE_NEWRELIC -e DORA_API_VERSION=$newTag -i $inventory deploy-dora.yml --vault-password-file ~/.ssh/vault.txt -vv'
            cleanWs()
        }
        stage('Smoke Tests') {
            git branch: '$pull_request_event_base_ref', url: 'https://github.com/ca-cwds/dora.git'
            sleep 30
            sh "curl http://dora.dev.cwds.io:8083/system-information"
            buildInfo = rtGradle.run buildFile: './dora-api/build.gradle', tasks: 'smokeTest --stacktrace'
            tagRepo(newTag)
        }
        stage('Clean WorkSpace') {
          buildInfo = rtGradle.run buildFile: './docker-dora/build.gradle', tasks: 'dockerCleanUpTagged'
        }
    } catch (Exception e) {
        errorcode = e;
        currentBuild.result = "FAIL"
        notifyBuild(currentBuild.result, errorcode)
        throw e;

    } finally {
        publishHTML([allowMissing: true, alwaysLinkToLastBuild: true, keepAll: true, reportDir: 'build/reports/license/', reportFiles: 'license-dependency.html', reportName: 'License Report', reportTitles: 'License summary'])
        publishHTML([allowMissing: true, alwaysLinkToLastBuild: true, keepAll: true, reportDir: 'dora-api/build/reports/tests/test/', reportFiles: 'index.html', reportName: 'JUnit Reports', reportTitles: 'JUnit tests summary'])
        publishHTML([allowMissing: true, alwaysLinkToLastBuild: true, keepAll: true, reportDir: 'dora-api/build/reports/tests/smokeTest', reportFiles: 'index.html', reportName: 'Smoke Tests Reports', reportTitles: 'Smoke tests summary'])
    }
}
