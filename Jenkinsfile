/**
# Copyright CESSDA ERIC 2017-2024
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
		productName = "cdc"
		componentName = "validator"
		imageTag = "${DOCKER_ARTIFACT_REGISTRY}/${productName}-${componentName}:${env.BRANCH_NAME}-${env.BUILD_NUMBER}"
	}

    agent {
        label 'jnlp-himem'
    }

	stages {
		stage('Pull SDK Docker Image') {
		    agent {
		        docker {
                    image 'eclipse-temurin:25'
                    reuseNode true
                }
            }
            environment {
                HOME = "${WORKSPACE_TMP}"
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
                        recordIssues(tools: [java()])
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
							waitForQualityGate abortPipeline: true
						}
                    }
                    when { branch 'main' }
                }
            }
        }
        stage('Compile Native Image') {
            agent {
                docker {
                    image 'graalvm/native-image-community:25'
                    args '--entrypoint=\'\''
                    registryUrl 'https://ghcr.io/'
                    registryCredentialsId '699b8178-5d52-46a1-aaad-ddb5b0a4069f'
                    reuseNode true
                }
            }
            environment {
                HOME = "${WORKSPACE_TMP}"
            }
            steps {
                sh "./mvnw -Pnative native:compile-no-fork -DbuildNumber=${env.BUILD_NUMBER}"
            }
        }
		stage('Build and Push Docker image') {
            steps {
                sh "gcloud auth configure-docker ${ARTIFACT_REGISTRY_HOST}"
                withMaven {
                    sh "./mvnw jib:build -Dimage=${imageTag}"
                }
                sh "gcloud artifacts docker tags add ${imageTag} ${DOCKER_ARTIFACT_REGISTRY}/${productName}-${componentName}:${env.BRANCH_NAME}-latest"
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
