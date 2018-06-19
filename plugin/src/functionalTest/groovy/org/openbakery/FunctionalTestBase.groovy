package org.openbakery

import org.apache.commons.io.FileUtils
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Specification

class FunctionalTestBase extends Specification {

	@Rule
	final TemporaryFolder testProjectDir = new TemporaryFolder()

	List<File> pluginClasspath

	File buildFile

	void genericSetup() {
		extractPluginClassPathResource()
		createMockBuildFile()
		copyTestProject()
	}

	void extractPluginClassPathResource() {
		URL pluginClasspathResource = getClass().classLoader
				.findResource("plugin-classpath.txt")

		if (pluginClasspathResource == null) {
			throw new IllegalStateException("Did not find plugin classpath resource, "
					+ "run `testClasses` build task.")
		}

		pluginClasspath = pluginClasspathResource.readLines()
				.collect { new File(it) }
	}

	void createMockBuildFile() {
		buildFile = testProjectDir.newFile('build.gradle')
		buildFile << """
            plugins {
                id 'org.openbakery.xcode-plugin'
            }
        """
	}

	void copyTestProject() {
		URL folder = getClass().classLoader
				.findResource("TestProject")


		File file = new File(folder.getFile())
		assert file.exists()

		FileUtils.copyDirectory(file, testProjectDir.root)
	}
}
