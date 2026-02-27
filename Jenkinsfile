/**
 *  Build pipeline for mail parser library
 */

pipeline {
  agent any

  options {
    buildDiscarder(logRotator(daysToKeepStr: '30', numToKeepStr: '8'))
    timestamps()
    timeout(time: 30, unit: 'MINUTES')
  }

  tools {
    maven 'Maven 3.9'
    jdk 'Java 11'
  }

  stages {
    stage ('Checkout') {
      steps {
        checkout scm
        lastChanges()
      }
    }

    stage ('Build') {
      steps {
        sh "mvn clean install"
      }
    }
  }
}
