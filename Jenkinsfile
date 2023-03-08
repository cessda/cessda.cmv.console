/**
# Copyright CESSDA ERIC 2017-2019
#
# Licensed under the Apache License, Version 2.0 (the "License"); you may not
# use this file except in compliance with the License.
# You may obtain a copy of the License at
# http://www.apache.org/licenses/LICENSE-2.0

# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
*/
pipeline {
	options {
		buildDiscarder logRotator(artifactNumToKeepStr: '5', numToKeepStr: '20')
        timeout(time: 1, unit: 'HOURS')
	}

	environment {
		productName = "cmv"
		componentName = "console"
		imageTag = "${docker_repo}/${productName}-${componentName}:${env.BRANCH_NAME}-${env.BUILD_NUMBER}"
	}

    agent {
        label 'jnlp-himem'
    }

	stages {
		// Building on main
		stage('Pull SDK Docker Image') {
		    agent {
		        docker {
                    image 'openjdk:17-jdk'
                    reuseNode true
                }
            }
		    stages {
                stage('Build Project') {
                    steps {
                        withMaven {
                            sh "./mvnw clean verify -DbuildNumber=${env.BUILD_NUMBER}"
                        }
                    }
                }
                stage('Record Issues') {
                    steps {
                        recordIssues aggregatingResults: true, tools: [errorProne(), java()]
                    }
                }
                stage('Run Sonar Scan') {
                    steps {
                        withSonarQubeEnv('cessda-sonar') {
                            withMaven {
                                sh "./mvnw sonar:sonar -DbuildNumber=${env.BUILD_NUMBER}"
                            }
                        }
						timeout(time: 1, unit: 'HOURS') {
							waitForQualityGate abortPipeline: false
						}
                    }
                    when { branch 'main' }
                }
            }
        }
		stage('Build and Push Docker image') {
            steps {
                sh 'gcloud auth configure-docker'
                withMaven {
                    sh "./mvnw jib:build -Dimage=${imageTag}"
                }
                sh "gcloud container images add-tag ${imageTag} ${docker_repo}/${productName}-${componentName}:${env.BRANCH_NAME}-latest"
            }
            when { branch 'main' }
		}
		stage('Check Requirements and Deployments') {
			steps {
				build job: 'cessda.cdc.aggregator.deploy/main', parameters: [string(name: 'cmv', value: "${env.BRANCH_NAME}-${env.BUILD_NUMBER}")], wait: false
			}
            when { branch 'main' }
		}
	}
}
