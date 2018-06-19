package org.openbakery.signing

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.openbakery.FunctionalTestBase
import spock.lang.Unroll

import java.nio.file.Paths

class ProvisioningInstallTaskFunctionalTest extends FunctionalTestBase {

	File provisioningFile1

	def setup() {
		genericSetup()
		provisioningFile1 = findResource("test1.mobileprovision")
		assert provisioningFile1.exists()
	}

	def "The task list should contain the task"() {
		when:
		def result = GradleRunner.create()
				.withProjectDir(testProjectDir.root)
				.withArguments('tasks', '--s')
				.withPluginClasspath(pluginClasspath)
				.withDebug(true)
				.build()

		then:
		result.output.contains(ProvisioningInstallTask.TASK_NAME
				+ " - "
				+ ProvisioningInstallTask.TASK_DESCRIPTION)
	}

	def "If no provisioning is defined, then the task should be skipped"() {
		when:
		def result = GradleRunner.create()
				.withProjectDir(testProjectDir.root)
				.withArguments(ProvisioningInstallTask.TASK_NAME)
				.withPluginClasspath(pluginClasspath)
				.build()

		then:
		result.task(":" + ProvisioningInstallTask.TASK_NAME)
				.outcome == TaskOutcome.SKIPPED
	}

	def "If provisioning list is empty, then the task should be skipped"() {
		setup:
		buildFile << """
			xcodebuild {
				signing {
					mobileProvisionList = []
				}
			}
		"""

		when:
		def result = GradleRunner.create()
				.withProjectDir(testProjectDir.root)
				.withArguments(ProvisioningInstallTask.TASK_NAME)
				.withPluginClasspath(pluginClasspath)
				.build()

		then:
		result.task(":" + ProvisioningInstallTask.TASK_NAME)
				.outcome == TaskOutcome.SKIPPED
	}

	def "The provisioning can be configured via the mobileProvisionList list"() {
		setup:
		buildFile << """
			xcodebuild {
				signing {
					mobileProvisionList = ["${provisioningFile1.toURI().toString()}"]
				}
			}
		"""

		when:
		def result = GradleRunner.create()
				.withProjectDir(testProjectDir.root)
				.withArguments(ProvisioningInstallTask.TASK_NAME, "--s")
				.withPluginClasspath(pluginClasspath)
				.withDebug(true)
				.build()

		then:
		result.task(":" + ProvisioningInstallTask.TASK_NAME)
				.outcome == TaskOutcome.SUCCESS
	}

	@Unroll
	def "With gradle version : #gradleVersion If provisioning list is present, then the task should be skipped"() {
		setup:
		buildFile << """
			xcodebuild {
				signing {
					mobileProvisionURI = "${provisioningFile1.toURI().toString()}"
				}
			}
		"""

		when:
		def result = GradleRunner.create()
				.withProjectDir(testProjectDir.root)
				.withArguments(ProvisioningInstallTask.TASK_NAME)
				.withPluginClasspath(pluginClasspath)
				.withGradleVersion(gradleVersion)
				.build()

		then:
		result.task(":" + ProvisioningInstallTask.TASK_NAME)
				.outcome == TaskOutcome.SUCCESS

		and: "The temporary provisioning provisioningFile1 should be deleted"
		new File(testProjectDir.root, "build/provision")
				.listFiles().size() == 0

		where:
		gradleVersion | _
		"4.7"         | _
		"4.8"         | _
	}

	private File findResource(String name) {
		ClassLoader classLoader = getClass().getClassLoader()
		return (File) Optional.ofNullable(classLoader.getResource(name))
				.map { URL url -> url.toURI() }
				.map { URI uri -> Paths.get(uri).toFile() }
				.filter { File file -> file.exists() }
				.orElseThrow { new Exception("Resource $name cannot be found") }
	}
}
