package org.openbakery

import groovy.transform.CompileStatic
import org.gradle.api.DefaultTask
import org.gradle.api.Transformer
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.*
import org.openbakery.codesign.ProvisioningProfileReader
import org.openbakery.signing.KeychainCreateTask
import org.openbakery.signing.ProvisioningInstallTask
import org.openbakery.util.PathHelper
import org.openbakery.util.PlistHelper

@CompileStatic
class PrepareXcodeArchivingTask extends DefaultTask {

	@InputFile
	@Optional
	final Provider<RegularFile> entitlementsFile = newInputFile()

	@OutputFile
	final Property<RegularFile> outputFile = newOutputFile()

	@InputDirectory
	final Provider<RegularFile> projectFile = newInputFile()

	final Provider<String> scheme = project.objects.property(String)
	final Provider<String> buildConfiguration = project.objects.property(String)
	final Provider<String> target = project.objects.property(String)

	final ListProperty<File> registeredProvisioningFiles = project.objects.listProperty(File)
	final Property<CommandRunner> commandRunnerProperty = project.objects.property(CommandRunner)
	final Property<File> provisioningForConfiguration = project.objects.property(File)
	final Property<PlistHelper> plistHelperProperty = project.objects.property(PlistHelper)
	final Property<String> certificateFriendlyName = project.objects.property(String)
	final Property<String> configurationBundleIdentifier = project.objects.property(String)
	final Property<String> entitlementsFilePath = project.objects.property(String)

	@Internal
	final Property<ProvisioningProfileReader> provisioningReader = project.objects.property(ProvisioningProfileReader)

	public static final String DESCRIPTION = "Prepare the archive configuration file"
	public static final String NAME = "prepareArchiving"

	static final String KEY_BUNDLE_IDENTIFIER = "PRODUCT_BUNDLE_IDENTIFIER"
	static final String KEY_CODE_SIGN_IDENTITY = "CODE_SIGN_IDENTITY"
	static final String KEY_CODE_SIGN_ENTITLEMENTS = "CODE_SIGN_ENTITLEMENTS"
	static final String KEY_DEVELOPMENT_TEAM = "DEVELOPMENT_TEAM"
	static final String KEY_PROVISIONING_PROFILE_ID = "PROVISIONING_PROFILE"
	static final String KEY_PROVISIONING_PROFILE_SPEC = "PROVISIONING_PROFILE_SPECIFIER"

	PrepareXcodeArchivingTask() {
		super()

		dependsOn(XcodePlugin.INFOPLIST_MODIFY_TASK_NAME)
		dependsOn(XcodePlugin.XCODE_CONFIG_TASK_NAME)
		dependsOn(KeychainCreateTask.TASK_NAME)
		dependsOn(ProvisioningInstallTask.TASK_NAME)

		this.description = DESCRIPTION

		this.entitlementsFilePath.set(entitlementsFile.map(new Transformer<String, RegularFile>() {
			@Override
			String transform(RegularFile regularFile) {
				return regularFile.asFile.absolutePath
			}
		}))

		this.provisioningForConfiguration.set(configurationBundleIdentifier.map(new Transformer<File, String>() {
			@Override
			File transform(String bundleIdentifier) {
				return ProvisioningProfileReader.getProvisionFileForIdentifier(bundleIdentifier,
						registeredProvisioningFiles.getOrNull() as List<File>,
						commandRunnerProperty.get(),
						plistHelperProperty.get())
			}
		}))

		this.provisioningReader.set(provisioningForConfiguration.map(new Transformer<ProvisioningProfileReader, File>() {
			@Override
			ProvisioningProfileReader transform(File file) {
				return new ProvisioningProfileReader(file,
						commandRunnerProperty.get())
			}
		}))

		this.outputFile.set(project.layout
				.buildDirectory
				.file(PathHelper.FOLDER_ARCHIVE + "/" + PathHelper.GENERATED_XCARCHIVE_FILE_NAME))

		this.onlyIf {
			return certificateFriendlyName.present &&
					configurationBundleIdentifier.present &&
					provisioningForConfiguration.present
		}
	}

	private File getPbxProjFile() {
		return new File(projectFile.get().asFile, "project.pbxproj")
	}

	private Serializable getValueFromPbxProjFile(String value) {
		return plistHelperProperty.get()
				.getValueFromPlist(toXml(getPbxProjFile()), value) as Serializable
	}

	private Serializable getObjectValueFromPbxProjFile(String value) {
		return getValueFromPbxProjFile("objects:${value}")
	}

	private String findProductId(String rootKey) {
		return getValueFromPbxProjFile("objects:${rootKey}:targets")
				.find { it -> getObjectValueFromPbxProjFile("${it}:productName") == target.get() }
	}

	private String getBuildConfigurationIdentifier(String targetId) {
		String configurationId = getObjectValueFromPbxProjFile("$targetId:buildConfigurationList")
		List<String> list = getObjectValueFromPbxProjFile("${configurationId}:buildConfigurations") as List<String>
		return list.find {
			getValueFromPbxProjFile("objects:${it}:name") == buildConfiguration.get()
		}
	}

	private void setBuildConfigurationBuildSetting(String bcId,
												   String key,
												   String value) {
		String completeKey = "objects:${bcId}:buildSettings:${key}"
		Object property = plistHelperProperty.get().getValueFromPlist(getPbxProjFile(), completeKey)

		if (property == null) {
			plistHelperProperty.get()
					.addValueForPlist(getPbxProjFile(), completeKey, value)
		} else {

			plistHelperProperty.get()
					.setValueForPlist(getPbxProjFile(), completeKey, value)
		}
	}

	@TaskAction
	void generate() {
		String rootKey = getValueFromPbxProjFile("rootObject")

		String buildConfigurationId = getBuildConfigurationIdentifier(findProductId(rootKey))

		HashMap<String, String> map = new HashMap<>()

		map.put("CODE_SIGN_STYLE", "Manual")

		map.put(KEY_CODE_SIGN_IDENTITY, certificateFriendlyName.get())
		map.put("CODE_SIGN_IDENTITY[sdk=iphoneos*]", certificateFriendlyName.get())
		map.put(KEY_BUNDLE_IDENTIFIER, configurationBundleIdentifier.get())
		map.put("PRODUCT_NAME", configurationBundleIdentifier.get().replace(".", "_"))

		if (provisioningReader.present) {
			ProvisioningProfileReader reader = provisioningReader.get()
			map.put(KEY_DEVELOPMENT_TEAM, reader.getTeamIdentifierPrefix())
			map.put(KEY_PROVISIONING_PROFILE_ID, reader.getUUID())
			map.put(KEY_PROVISIONING_PROFILE_SPEC, reader.getName())
		}

		if (entitlementsFile.present) {
			map.put("CODE_SIGN_ENTITLEMENTS", entitlementsFile.get().asFile.absolutePath)
		}

		map.each { k, v ->
			setBuildConfigurationBuildSetting(buildConfigurationId, k, v)
		}
	}

	private File toXml(File source) {
		File file = File.createTempFile("project.plist", "")
		commandRunnerProperty.get()
				.run(["plutil",
					  "-convert",
					  "xml1",
					  source.absolutePath,
					  "-o", file.absolutePath])

		return file
	}
}
