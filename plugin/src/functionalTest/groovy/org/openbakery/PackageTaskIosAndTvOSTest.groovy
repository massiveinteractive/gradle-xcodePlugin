package org.openbakery

import org.gradle.testkit.runner.GradleRunner
import org.openbakery.packaging.PackageTaskIosAndTvOS

class PackageTaskIosAndTvOSTest extends FunctionalTestBase {

	def setup() {
		genericSetup()
	}

	def "The task list should contain the task"() {
		when:
		def result = GradleRunner.create()
				.withProjectDir(testProjectDir.root)
				.withArguments('tasks')
				.withPluginClasspath(pluginClasspath)
				.build()

		then:
		result.output.contains(PackageTaskIosAndTvOS.NAME
				+ " - "
				+ PackageTaskIosAndTvOS.DESCRIPTION)
	}
}
