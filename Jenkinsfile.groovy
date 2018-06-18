buildCount = env.DEFAULT_HISTORY_COUNT ?: "5"

pipeline {
    agent {
        node {
            label 'java'
        }
    }

    options {
        buildDiscarder(logRotator(numToKeepStr: buildCount))
        disableConcurrentBuilds()
    }

    environment {
        JFROGCLI = "${tool 'JfrogCLI'}"
    }

    stages {
        stage('Select') {
            parallel {
                stage('Checkout') {
                    when {
                        expression {
                            notIntegration()
                        }
                    }

                    steps {
                        checkout scm
                        seedReset()
                    }
                }

                stage('Integrate') {
                    when {
                        expression {
                            isIntegration()
                        }
                    }

                    steps {
                        echo "Execute integration"
                        stopUnless(isStartedBy("upstream"))
                    }
                }
            }
        }


        stage("Build") {
            when {
                expression {
                    notIntegration() && notFeatureBranch()
                }
            }

            parallel {
                stage("Snapshot") {
                    when {
                        expression {
                            notRelease()
                        }
                    }

                    steps {
                        updateVersion()
                        sh './gradlew clean worksGeneratePublication'
                    }
                }

                stage("Release") {
                    when {
                        expression {
                            isRelease()
                        }
                    }

                    steps {
                        sh """./gradlew clean test worksGeneratePublication"""
                    }
                }
            }
        }

        stage("Compare") {
            when {
                expression {
                    notIntegration() && notFeatureBranch()
                }
            }


            parallel {
                stage("Snapshot") {
                    when {
                        expression {
                            notRelease()
                        }
                    }

                    steps {
                        echo "Compare snapshot"
                        compareArtifact("snapshot", "integrate/snapshot")
                    }
                }

                stage("Release") {
                    when {
                        expression {
                            isRelease()
                        }
                    }

                    steps {
                        echo "Compare release"
                        compareArtifact("release", "integrate/release")
                    }
                }
            }
        }

        stage("Publish") {
            when {
                expression {
                    doPublish()
                }
            }

            parallel {
                stage("Snapshot") {
                    when {
                        expression {
                            notIntegration() && notRelease()
                        }
                    }

                    steps {
                        echo "Publishing snapshot"
                        publish("snapshot")
                    }
                }

                stage("Release") {
                    when {
                        expression {
                            notIntegration() && isRelease()
                        }
                    }

                    steps {
                        echo "Publishing release"
                        publish("release")
                    }
                }
            }
        }
    }
}

def updateVersion() {
    bintrayDownloadMatches repository: "mobilesolutionworks/snapshot",
            packageInfo: readYaml(file: 'plugin/module.yaml'),
            credential: "mobilesolutionworks.jfrog.org"

    def properties = readYaml(file: 'plugin/module.yaml')
    def incremented = versionIncrementQualifier()
    if (incremented != null) {
        properties.version = incremented
    } else {
        properties.version = properties.version + "-BUILD-1"
    }

    sh "rm plugin/module.yaml"
    writeYaml file: 'plugin/module.yaml', data: properties
}

def compareArtifact(String repo, String job) {
    bintrayDownloadMatches repository: "mobilesolutionworks/${repo}",
            packageInfo: readYaml(file: 'plugin/module.yaml'),
            credential: "mobilesolutionworks.jfrog.org"

    def same = bintrayCompare repository: "mobilesolutionworks/${repo}",
            packageInfo: readYaml(file: 'plugin/module.yaml'),
            credential: "mobilesolutionworks.jfrog.org",
            path: "plugin/build/libs"

    if (fileExists(".jenkins/notify")) {
        sh "rm .jenkins/notify"
    }

    if (same) {
        echo "Artifact output is identical, no integration needed"
    } else {
        writeFile file: ".jenkins/notify", text: job
    }
}

def doPublish() {
    return fileExists(".jenkins/notify")
}

def publish(String repo) {
    bintrayPublish([
            credential: "mobilesolutionworks.jfrog.org",
            pkg       : readYaml(file: 'plugin/module.yaml'),
            repo      : "mobilesolutionworks/${repo}",
            src       : "plugin/build/libs"
    ])
}