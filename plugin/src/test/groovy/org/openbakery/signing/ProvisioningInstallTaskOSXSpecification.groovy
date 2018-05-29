package org.openbakery.signing

import org.apache.commons.io.FileUtils
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import org.openbakery.CommandRunner
import org.openbakery.codesign.ProvisioningProfileReader
import org.openbakery.xcode.Type
import spock.lang.Specification

class ProvisioningInstallTaskOSXSpecification extends Specification {

	Project project
	ProvisioningInstallTask provisioningInstallTask;

	CommandRunner commandRunner = Mock(CommandRunner)

	File provisionLibraryPath
	File projectDir


	def setup() {

		projectDir = new File(System.getProperty("java.io.tmpdir"), "gradle-xcodebuild")

		project = ProjectBuilder.builder().withProjectDir(projectDir).build()
		project.buildDir = new File(projectDir, 'build').absoluteFile
		project.apply plugin: org.openbakery.XcodePlugin

		project.xcodebuild.type = Type.macOS

		provisioningInstallTask = project.getTasks()
				.getByPath(ProvisioningInstallTask.TASK_NAME)

		provisioningInstallTask.commandRunnerProperty.set(commandRunner)

		provisionLibraryPath = new File(System.getProperty("user.home") + "/Library/MobileDevice/Provisioning Profiles/");

	}

	def cleanup() {
		FileUtils.deleteDirectory(projectDir)
	}


	def "single ProvisioningProfile"() {

		given:
		File testMobileprovision = new File("src/test/Resource/test-wildcard-mac.provisionprofile")
		project.xcodebuild.signing.mobileProvisionURI = testMobileprovision.toURI().toString()

		ProvisioningProfileReader reader = new ProvisioningProfileReader(testMobileprovision, commandRunner)
		String uuid = reader.getUUID()
		String name = "gradle-" + uuid + ".provisionprofile";

		File source = new File(projectDir, "build/provision/" + name)
		File destination = new File(provisionLibraryPath, name)

		when:
		provisioningInstallTask.download()

		File sourceFile = new File(projectDir, "build/provision/" + name)

		then:
		sourceFile.exists()
		1 * commandRunner.run(["/bin/ln", "-s", source.absolutePath, destination.absolutePath])
	}

	def "multiple ProvisioningProfiles"() {
		given:
		File firstMobileprovision = new File("src/test/Resource/test-wildcard-mac.provisionprofile")
		File secondMobileprovision = new File("src/test/Resource/openbakery-example.provisionprofile")
		project.xcodebuild.signing.mobileProvisionURI = [firstMobileprovision.toURI().toString(), secondMobileprovision.toURI().toString()]

		String firstName = "gradle-" + new ProvisioningProfileReader(firstMobileprovision, new CommandRunner()).getUUID() + ".provisionprofile";
		String secondName = "gradle-" + new ProvisioningProfileReader(secondMobileprovision, new CommandRunner()).getUUID() + ".provisionprofile";

		File firstFile = new File(projectDir, "build/provision/" + firstName)
		File secondFile = new File(projectDir, "build/provision/" + secondName)

		File firstSource = new File(projectDir, "build/provision/" + firstName)
		File firstDestination = new File(provisionLibraryPath, firstName)

		File secondSource = new File(projectDir, "build/provision/" + secondName)
		File secondDestination = new File(provisionLibraryPath, secondName)

		when:
		provisioningInstallTask.download()

		then:
		firstFile.exists()
		secondFile.exists()

		1 * commandRunner.run(["/bin/ln", "-s", firstSource.absolutePath, firstDestination.absolutePath])
		1 * commandRunner.run(["/bin/ln", "-s", secondSource.absolutePath, secondDestination.absolutePath])

	}

}
