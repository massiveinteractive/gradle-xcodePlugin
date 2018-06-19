package org.openbakery

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.openbakery.signing.KeychainCreateTask
import spock.lang.Shared

import java.nio.file.Paths

class KeychainCreateTaskFunctionalTest extends FunctionalTestBase {

	@Shared
	File certificate

	def setup() {
		extractPluginClassPathResource()
		createMockBuildFile()
		copyTestProject()

		certificate = findResource("fake_distribution.p12")
		assert certificate.exists()
	}

	def "The task list should contain the task"() {
		when:
		def result = GradleRunner.create()
				.withProjectDir(testProjectDir.root)
				.withArguments('tasks')
				.withPluginClasspath(pluginClasspath)
				.build()

		then:
		result.output.contains(KeychainCreateTask.TASK_NAME
				+ " - "
				+ KeychainCreateTask.TASK_DESCRIPTION)
	}

	def "The task should be skipped if invalid configuration"() {
		when:
		BuildResult result = GradleRunner.create()
				.withProjectDir(testProjectDir.root)
				.withArguments(KeychainCreateTask.TASK_NAME)
				.withPluginClasspath(pluginClasspath)
				.build()

		then:
		result.output.contains("No signing certificate defined, will skip the keychain creation")
		result.output.contains("No signing certificate password defined, will skip the keychain creation")
		result.task(":" + KeychainCreateTask.TASK_NAME).outcome == TaskOutcome.SKIPPED
	}

	def "The task should be skipped if not certificate password is provided"() {
		setup:
		buildFile << """
			xcodebuild {
            	signing {
            		certificateURI = "$certificate.absolutePath"  
            	}
			}            
			"""

		when:
		BuildResult result = GradleRunner.create()
				.withProjectDir(testProjectDir.root)
				.withArguments(KeychainCreateTask.TASK_NAME)
				.withDebug(true)
				.withPluginClasspath(pluginClasspath)
				.build()

		then:
		result.task(":" + KeychainCreateTask.TASK_NAME).outcome == TaskOutcome.SKIPPED
	}

	def "The task should be executed if configuration is valid"() {
		setup:
		buildFile << """
			xcodebuild {
            	signing {
            		certificateURI = "${certificate.toURI().toString()}"
					certificatePassword = "p4ssword"
            	}
			}            
			"""

		when:
		BuildResult result = GradleRunner.create()
				.withProjectDir(testProjectDir.root)
				.withArguments(KeychainCreateTask.TASK_NAME)
				.withPluginClasspath(pluginClasspath)
				.withDebug(true)
				.build()

		then:
		result.task(":" + KeychainCreateTask.TASK_NAME).outcome == TaskOutcome.SUCCESS
	}

	def "The task should automatically delete the temporary keychain file"() {
		setup:
		buildFile << """
			xcodebuild {
            	signing {
            		certificateURI = "${certificate.toURI().toString()}"
					certificatePassword = "p4ssword"
            	}
			}            
			"""

		when:
		BuildResult result = GradleRunner.create()
				.withProjectDir(testProjectDir.root)
				.withArguments(KeychainCreateTask.TASK_NAME)
				.withPluginClasspath(pluginClasspath)
				.build()

		then:
		result.task(":" + KeychainCreateTask.TASK_NAME).outcome == TaskOutcome.SUCCESS

		and: "The temporary certificate provisioningFile1 should be deleted automatically"
		new File(testProjectDir.root, "build/codesign")
			.listFiles()
			.toList()
			.findAll {it.name.endsWith(".p12")}
			.empty

		and: "The temporary keychain provisioningFile1 should be deleted automatically"
		new File(testProjectDir.root, "build/codesign")
				.listFiles()
				.toList()
				.findAll {it.name.endsWith(".keychain")}
				.empty
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
