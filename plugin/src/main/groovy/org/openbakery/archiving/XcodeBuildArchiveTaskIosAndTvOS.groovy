package org.openbakery.archiving

import groovy.transform.CompileStatic
import org.gradle.api.DefaultTask
import org.gradle.api.Task
import org.gradle.api.Transformer
import org.gradle.api.file.Directory
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.specs.Spec
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.openbakery.CommandRunner
import org.openbakery.PrepareXcodeArchivingTask
import org.openbakery.XcodeService
import org.openbakery.signing.ProvisioningInstallTask
import org.openbakery.xcode.Type
import org.openbakery.xcode.Xcode
import org.openbakery.xcode.Xcodebuild

@CompileStatic
class XcodeBuildArchiveTaskIosAndTvOS extends DefaultTask {

	@Input
	final Provider<String> xcodeVersion = project.objects.property(String)

	@Input
	final Provider<String> scheme = project.objects.property(String)

	@Input
	final Provider<Type> buildType = project.objects.property(Type)

	final Provider<String> buildConfiguration = project.objects.property(String)

	@OutputDirectory
	final Provider<Directory> outputArchiveFile = newOutputDirectory()

	final Property<XcodeService> xcodeServiceProperty = project.objects.property(XcodeService)
	final Property<Xcode> xcode = project.objects.property(Xcode)
	final Property<CommandRunner> commandRunnerProperty = project.objects.property(CommandRunner)

	public static final String NAME = "archiveXcodeBuild"

	XcodeBuildArchiveTaskIosAndTvOS() {
		super()

		dependsOn(ProvisioningInstallTask.TASK_NAME)
		dependsOn(PrepareXcodeArchivingTask.NAME)

		this.description = "Use the XcodeBuild archive command line to create the project archive"

		onlyIf(new Spec<Task>() {
			@Override
			boolean isSatisfiedBy(Task task) {
				return buildType.get() == Type.iOS || buildType.get() == Type.tvOS
			}
		})
	}

	@TaskAction
	void archive() {
		assert scheme.present: "No target scheme configured"
		assert outputArchiveFile.present: "No output file folder configured"

		outputArchiveFile.get().asFile.parentFile.mkdirs()

		logger.lifecycle("Archive project with configuration: " +
				"\n\tScheme : ${scheme.get()} " +
				"\n\tXcode version : ${xcodeVersion.getOrElse("System default")}")

		Xcodebuild.archive(commandRunnerProperty.get(),
				scheme.get(),
				outputArchiveFile.get().asFile,
				buildConfiguration.get(),
				getXcodeAppForConfiguration().getOrNull())
	}

	private Provider<File> getXcodeAppForConfiguration() {

		Provider<File> xcodeApp
		if (xcodeVersion.present) {
			xcodeApp = xcodeServiceProperty.map(new Transformer<File, XcodeService>() {
				@Override
				File transform(XcodeService xcodeService) {
					XcodeService.XcodeApp app = xcodeService.getInstallationForVersion(xcodeVersion.get())
					return app.contentDeveloperFile
				}
			})
		} else {
			xcodeApp = project.objects.property(File)
		}
		return xcodeApp
	}
}
