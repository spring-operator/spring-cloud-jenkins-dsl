package io.springframework.cloud.e2e

import io.springframework.cloud.common.SpringCloudJobs
import io.springframework.cloud.common.SpringCloudNotification
import io.springframework.common.*
import javaposse.jobdsl.dsl.DslFactory

/**
 * @author Marcin Grzejszczak
 */
class EndToEndBuildMaker implements SpringCloudNotification, TestPublisher,
		JdkConfig, BreweryDefaults, Label, Cron, SpringCloudJobs {

	private final DslFactory dsl
	private final String organization

	EndToEndBuildMaker(DslFactory dsl) {
		this.dsl = dsl
		this.organization = "spring-cloud"
	}

	EndToEndBuildMaker(DslFactory dsl, String organization) {
		this.dsl = dsl
		this.organization = organization
	}

	void build(String projectName, String cronExpr) {
		build(projectName, "scripts/runAcceptanceTests.sh", cronExpr)
	}

	void build(String projectName, String scriptName, String cronExpr, boolean withTests = true) {
		build(projectName, projectName, scriptName, cronExpr, withTests)
	}

	void buildWithoutTests(String projectName, String scriptName, String cronExpr, String postBuildScripts) {
		build(projectName, projectName, scriptName, cronExpr, false, postBuildScripts)
	}

	void buildWithoutTests(String projectName, String repoName, String scriptName, String cronExpr, String postBuildScripts) {
		build(projectName, repoName, scriptName, cronExpr, false, postBuildScripts)
	}

	protected void build(String projectName, String repoName, String scriptName, String cronExpr,
						 boolean withTests = true, String postBuildScripts = "") {
		String organization = this.organization
		dsl.job("${prefixJob(projectName)}-e2e") {
			triggers {
				cron cronExpr
			}
			jdk jdk8()
			wrappers {
				label aws()
				environmentVariables([
						RETRIES: 70,
						(jdk8HomeEnvVar()): jdk8DefaultPath()
				])
			}
			scm {
				git {
					remote {
						url "https://github.com/${organization}/$repoName"
						branch 'master'
					}
					extensions {
						wipeOutWorkspace()
					}
				}
			}
			steps {
				shell(cleanup())
				shell("""
						sh -e ${scriptName}
					""")
				if (postBuildScripts) {
					shell("""
						sh -e ${postBuildScripts}
					""")
				}
			}
			configure {
				slackNotificationForSpringCloud(it as Node)
			}
			publishers {
				if (withTests) {
					archiveJunit gradleJUnitResults()
					archiveArtifacts acceptanceTestReports()
					archiveArtifacts acceptanceTestSpockReports()
				}
			}
		}
	}

}
