#!/usr/bin/env groovy

/**
 * <p>
 * This is the script that will be run by Jenkins to deploy the Blue Button API
 * data/backend systems. This drives our project's continuous deployment. It
 * uses the Ansible plays in this project.
 * </p>
 * <p>
 * This script is run by Jenkins' Pipeline feature. A good intro tutorial for
 * working with Jenkins Pipelines can be found here:
 * <a href="https://jenkins.io/doc/book/pipeline/">Jenkins > Pipeline</a>.
 * </p>
 * <p>
 * The canonical Jenkins server job for this project is located here:
 * <a href="https://builds.ls.r53.cmsfhir.systems/jenkins/job/bluebutton-ansible-playbooks-data">bluebutton-ansible-playbooks-data</a>.
 * </p>
 */

properties([
	parameters([
		booleanParam(name: 'test_test', description: 'Whether to run the test against the test environment', defaultValue: true),
		booleanParam(name: 'test_dpr', description: 'Whether to run the test against the DPR environment', defaultValue: false),
		booleanParam(name: 'test_prod', description: 'Whether to run the test against the Prod environment', defaultValue: false),
		string(name: 'env_num_servers', description: 'Number of JMeter Servers to use in the test.', defaultValue: '1')
	]),
	buildDiscarder(logRotator(artifactDaysToKeepStr: '', artifactNumToKeepStr: '', daysToKeepStr: '', numToKeepStr: ''))
])

stage('Prepare') {
	node {
		// Grab the commit that triggered the build.
		checkout scm

		// Ensure the Ansible image is ready to go.
		insideAnsibleContainer {
			// Just some general "what does the container look like" debug
			// logging. Useful for if/when things go sideways.
			sh 'cat /etc/passwd'
			sh 'echo $USER && echo $UID && echo $HOME && whoami'
			sh 'pwd && ls -la'
			sh 'cd ansible && ansible --version'

			// Verify the play's syntax before we run it.
			sh "cd ansible && ansible-playbook fhir-stress-test-temp-testers.yml -e \"target_env=test num_servers=${params.env_num_servers}\" --syntax-check"
		}
	}
}

/**
 * Runs Maven with the specified arguments.
 *
 * @param args the arguments to pass to <code>mvn</code>
 */
def mvn(args) {
	// This tool must be setup and named correctly in the Jenkins config.
	def mvnHome = tool 'maven-3'

	// Run the build, using Maven, with the appropriate config.
	configFileProvider(
			[
				configFile(fileId: 'bluebutton:settings.xml', variable: 'MAVEN_SETTINGS'),
				configFile(fileId: 'bluebutton:toolchains.xml', variable: 'MAVEN_TOOLCHAINS')
			]
	) {
		sh "${mvnHome}/bin/mvn --settings $MAVEN_SETTINGS --toolchains $MAVEN_TOOLCHAINS ${args}"
	}
}

stage('Build') {
	node {
		milestone(label: 'stage_build_start')

		withCredentials([
				string(credentialsId: 'proxy-host', variable: 'proxyHost'),
				string(credentialsId: 'proxy-port', variable: 'proxyPort')
		]) {
			// Run our Maven build with the correct proxy server settings, as part of
			// build needs to use the proxy to download some things.
			// (Note: Exposing the proxy server details on the Maven command line isn't
			// a risk, as anyone with shell access will already be able to find it from
			// the build system's environment variables.)
			mvn "--update-snapshots -Dmaven.test.failure.ignore clean install -Dhttp.proxyHost=${proxyHost} -Dhttp.proxyPort=${proxyPort} -Dhttps.proxyHost=${proxyHost} -Dhttps.proxyPort=${proxyPort} -Dhttp.nonProxyHosts=localhost"
		}
	}
}

stage('Test the Test ENV') {
	if (params.test_test) {
		lock(resource: 'env_test', inversePrecendence: true) {
			milestone(label: 'stage_test_test_start')

			node {
				insideAnsibleContainer {
					sh "cd ansible && ansible-playbook fhir-stress-test-temp-testers.yml -e \"target_env=test num_servers=${params.env_num_servers}\" --connection=paramiko"
				}
			}
		}
	} else {
		org.jenkinsci.plugins.pipeline.modeldefinition.Utils.markStageSkippedForConditional('Test the Test ENV')
	}
}

stage('Test the DPR ENV') {
	if (params.test_dpr) {
		lock(resource: 'env_dpr', inversePrecendence: true) {
			milestone(label: 'stage_test_dpr_start')

			node {
				insideAnsibleContainer {
					sh "cd ansible && ansible-playbook fhir-stress-test-temp-testers.yml -e \"target_env=dpr num_servers=${params.env_num_servers}\" --connection=paramiko"
				}
			}
		}
	} else {
		org.jenkinsci.plugins.pipeline.modeldefinition.Utils.markStageSkippedForConditional('Test the DPR ENV')
	}
}

stage('Test the Prod ENV') {
	if (params.test_prod) {
		lock(resource: 'env_prod', inversePrecendence: true) {
			milestone(label: 'stage_test_prod_start')

			node {
				insideAnsibleContainer {
					sh "cd ansible && ansible-playbook fhir-stress-test-temp-testers.yml -e \"target_env=prod num_servers=${params.env_num_servers}\" --connection=paramiko"
				}
			}
		}
	} else {
		org.jenkinsci.plugins.pipeline.modeldefinition.Utils.markStageSkippedForConditional('Test the Prod ENV')
	}
}


/**
 * @return the result returned from running the specified `Closure` inside the
 *         `ansible_runner` container
 */
public <V> V insideAnsibleContainer(Closure<V> body) {
	/*
	 * Prepare the Ansible execution environment. We use Docker for this, as the
	 * Jenkins system is in FIPS mode, which prevents virtualenv, pip, and
	 * Ansible from working (different symptoms, but generally they need MD5).
	 *
	 * Jenkins Docker references:
	 * * <https://builds.ls.r53.cmsfhir.systems/jenkins/job/bluebutton-ansible-playbooks-data/pipeline-syntax/globals#docker>
	 * * <https://github.com/jenkinsci/docker-workflow-plugin/blob/master/src/main/resources/org/jenkinsci/plugins/docker/workflow/Docker.groovy>
	 */

	// FIXME: Get the Docker image building in Jenkins/LSS. Until then, we're
	// using an image Karl built locally and then exported-imported to the
	// Jenkins system.
	//def ansibleRunner = docker.build('ansible_runner', './dockerfiles/ansible_runner')
	def ansibleRunner = docker.image('ansible_runner')

	// Ensure that the Ansible container can open the Ansible Vault files
	// for this project.
	withCredentials([file(credentialsId: 'bluebutton-data-server-perf-tests-ansible-vault-password', variable: 'vaultPasswordFile')]) {

		// Run the container as root, not as the random unbound user that
		// Jenkins defaults to (as that will cause Python/Ansible errors).
		def dockerArgs = '--user root:root'

		// Ensure that Ansible uses Jenkins' SSH config and keys.
		dockerArgs += ' --volume=/var/lib/jenkins/.ssh:/root/.ssh_jenkins:rw'
		dockerArgs += ' --volume=/etc/ssh:/etc/ssh:rw'

		// Bind mount the `vault.password` file where it's needed.
		dockerArgs += " --volume=${vaultPasswordFile}:${env.WORKSPACE}/ansible/vault.password:ro"

		// Ensure that Ansible uses Jenkins' Maven repo.
		dockerArgs += ' --volume=/u01/jenkins/.m2:/root/.m2:ro'

		// Prepend the specified closure with some needed in-container setup.
		def bodyWithSetup = {
			// Copy the SSH config and keys and fix permissions.
			sh 'cp --recursive --no-target-directory /root/.ssh_jenkins /root/.ssh'
			sh 'chmod -R u=rw,g=,o= /root/.ssh'

			// Link the project's Ansible roles to where they're expected.
			sh 'ln -s /etc/ansible/roles roles_external'
			body.call()
		}

		// Now start the container with the above args and run the specified
		// closure in it, returning the result from that.
		try {
			return ansibleRunner.inside(dockerArgs, bodyWithSetup)
		} finally {
			// We're running the containerized process as root, so it's going to leave
			// around files owned by root. That's not great anyways, but it gets
			// particularly annoying because Jenkins can't move/cleanup those files
			// when it needs to later. So: we fix the ownership here and problem solved.
			def jenkinsUid = sh(script: 'id --user', returnStdout: true).trim()
			def jenkinsGid = sh(script: 'id --group', returnStdout: true).trim()
			ansibleRunner.inside(dockerArgs, {
				sh "find . -writable -exec chown ${jenkinsUid}:${jenkinsGid} '{}' \\;"
			})
		}
	}
}