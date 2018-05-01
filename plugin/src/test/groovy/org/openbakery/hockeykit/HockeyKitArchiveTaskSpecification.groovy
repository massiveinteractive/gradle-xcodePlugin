package org.openbakery.hockeykit

import org.apache.commons.io.FileUtils
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import org.openbakery.CommandRunner
import org.openbakery.util.PathHelper
import org.openbakery.util.PlistHelper
import spock.lang.Specification
/**
 * User: rene
 * Date: 11/11/14
 */
class HockeyKitArchiveTaskSpecification extends Specification {

	Project project
	HockeyKitArchiveTask hockeyKitArchiveTask;

	CommandRunner commandRunner = Mock(CommandRunner)

	File infoPlist

	def setup() {

		File projectDir = new File(System.getProperty("java.io.tmpdir"), "gradle-xcodebuild")

		project = ProjectBuilder.builder().withProjectDir(projectDir).build()
		project.buildDir = new File(projectDir, 'build').absoluteFile
		project.apply plugin: org.openbakery.XcodePlugin
		project.xcodebuild.productName = 'Test'
		project.xcodebuild.infoPlist = 'Info.plist'

		hockeyKitArchiveTask = project.getTasks().getByPath('hockeykitArchive')
		hockeyKitArchiveTask.plistHelper = new PlistHelper(commandRunner)
		hockeyKitArchiveTask.commandRunner = commandRunner

		File ipaBundle = new File(project.getBuildDir(), "package/Test.ipa")
		FileUtils.writeStringToFile(ipaBundle, "dummy")

		File archiveDirectory = new File(PathHelper.resolveArchiveFolder(project), "Test.xcarchive")
		archiveDirectory.mkdirs()

		infoPlist = new File(archiveDirectory, "Products/Applications/Test.app/Info.plist");
		infoPlist.parentFile.mkdirs();
		FileUtils.writeStringToFile(infoPlist, "dummy")
	}


	def cleanup() {
		FileUtils.deleteDirectory(project.projectDir)
	}


	def "archive"() {
		given:
		project.hockeykit.versionDirectoryName = "123"

		def commandList = ["/usr/libexec/PlistBuddy", infoPlist.absolutePath, "-c", "Print :CFBundleIdentifier"]
		commandRunner.runWithResult(commandList) >> "com.example.Test"

		when:
		hockeyKitArchiveTask.archive()

		File expectedIpa = new File(project.buildDir, "hockeykit/com.example.test/123/Test.ipa")

		then:
		expectedIpa.exists()
	}

	def "archive with BundleSuffix"() {
		given:
		project.xcodebuild.bundleNameSuffix = '-SUFFIX'
		project.hockeykit.versionDirectoryName = "123"

		def commandList = ["/usr/libexec/PlistBuddy", infoPlist.absolutePath, "-c", "Print :CFBundleIdentifier"]
		commandRunner.runWithResult(commandList) >> "com.example.Test"

		when:
		hockeyKitArchiveTask.archive()

		File expectedIpa = new File(project.buildDir, "hockeykit/com.example.test/123/Test-SUFFIX.ipa")

		then:
		expectedIpa.exists()
	}
}
