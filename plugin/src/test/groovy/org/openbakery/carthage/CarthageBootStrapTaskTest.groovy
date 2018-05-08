package org.openbakery.carthage

import org.gradle.api.Project
import org.gradle.api.provider.Provider
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Rule
import org.junit.rules.ExpectedException
import org.openbakery.CommandRunner
import org.openbakery.output.ConsoleOutputAppender
import org.openbakery.xcode.Xcode
import spock.lang.Specification
import spock.lang.Unroll

import static org.openbakery.carthage.AbstractCarthageTaskBase.*
import static org.openbakery.xcode.Type.*

class CarthageBootStrapTaskTest extends Specification {

	CarthageBootStrapTask subject
	CommandRunner commandRunner = Mock(CommandRunner)
	Xcode mockXcode = Mock(Xcode)
	File projectDir
	File cartFile
	Project project

	@Rule
	public ExpectedException exception = ExpectedException.none()

	void setup() {
		projectDir = File.createTempDir()

		cartFile = new File(projectDir, "Cartfile")
		cartFile << 'github "Alamofire/Alamofire"'

		project = ProjectBuilder.builder()
				.withProjectDir(projectDir)
				.build()

		project.buildDir = new File(projectDir, 'build').absoluteFile
		project.apply plugin: org.openbakery.XcodePlugin

		subject = project.getTasks().getByPath('carthageBootstrap')
		assert subject != null

		subject.commandRunner = commandRunner
	}

	def "The carthage bootstrap task should be present"() {
		expect:
		subject instanceof CarthageBootStrapTask
	}

	@Unroll
	def "When bootstrap is executed should only update the platform: #platform"() {
		given:
		commandRunner.runWithResult("which", "carthage") >> "/usr/local/bin/carthage"
		project.xcodebuild.type = platform

		when:
		subject.update()

		then:
		1 * commandRunner.run(_,
				[CARTHAGE_USR_BIN_PATH,
				 ACTION_BOOTSTRAP,
				 ARG_PLATFORM,
				 carthagePlatform,
				 ARG_CACHE_BUILDS]
				, _
				, _) >> {
			args -> args[3] instanceof ConsoleOutputAppender
		}

		where:
		platform | carthagePlatform
		tvOS     | CARTHAGE_PLATFORM_TVOS
		macOS    | CARTHAGE_PLATFORM_MACOS
		watchOS  | CARTHAGE_PLATFORM_WATCHOS
		iOS      | CARTHAGE_PLATFORM_IOS
	}

	def "The task should not be executed if the 'Cartfile` file is missing"() {
		given:
		commandRunner.runWithResult("which", "carthage") >> "/usr/local/bin/carthage"
		project.xcodebuild.type = platform

		when:
		cartFile.delete()
		subject.update()

		then:
		0 * commandRunner.run(_,
				getCommandRunnerArgsForPlatform(carthagePlatform),
				_,
				_) >> {
			args -> args[3] instanceof ConsoleOutputAppender
		}

		where:
		platform | carthagePlatform
		tvOS     | CARTHAGE_PLATFORM_TVOS
		macOS    | CARTHAGE_PLATFORM_MACOS
		watchOS  | CARTHAGE_PLATFORM_WATCHOS
		iOS      | CARTHAGE_PLATFORM_IOS
	}

	def "The subject output directory should be platform dependant"() {
		when:
		subject.xcode.getXcodeSelectEnvValue(_) >> new HashMap<String, String>()
		project.xcodebuild.type = platform

		then:
		Provider<File> outputDirectory = subject.outputDirectory
		outputDirectory.isPresent()
		outputDirectory.get().name == carthagePlatform

		where:
		platform | carthagePlatform
		tvOS     | CARTHAGE_PLATFORM_TVOS
		macOS    | CARTHAGE_PLATFORM_MACOS
		watchOS  | CARTHAGE_PLATFORM_WATCHOS
		iOS      | CARTHAGE_PLATFORM_IOS
	}

	def "The xcode selection should be applied if a xcode version is defined"() {
		when:
		subject.xcode.getXcodeSelectEnvValue(_) >> new HashMap<String, String>()
		project.xcodebuild.type = iOS
		project.xcodebuild.version = version

		subject.xcode = mockXcode
		subject.xcode.setVersionFromString(_) >> _
		subject.update()

		then:
		1 * mockXcode.getXcodeSelectEnvValue(version)

		where:
		version | _
		"7.1.1" | _
	}

	private List<String> getCommandRunnerArgsForPlatform(String carthagePlatform) {
		return [CARTHAGE_USR_BIN_PATH,
				ACTION_UPDATE,
				ARG_PLATFORM,
				carthagePlatform,
				ARG_CACHE_BUILDS]
	}
}
