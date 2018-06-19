/*
 * Copyright 2013 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openbakery

import org.apache.commons.io.filefilter.SuffixFileFilter
import org.apache.commons.lang.StringUtils
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Project
import org.gradle.api.Transformer
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.util.ConfigureUtil
import org.openbakery.extension.Signing
import org.openbakery.extension.TargetConfiguration
import org.openbakery.util.PathHelper
import org.openbakery.util.PlistHelper
import org.openbakery.util.VariableResolver
import org.openbakery.xcode.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class XcodeBuildPluginExtension {

	final Property<Boolean> bitcode = project.objects.property(Boolean)
	final Property<String> version = project.objects.property(String)
	final Property<String> configuration = project.objects.property(String)
	final Property<String> target = project.objects.property(String)
	final Property<Type> targetType = project.objects.property(Type)
	final Property<String> scheme = project.objects.property(String)
	final DirectoryProperty archiveDirectory = project.layout.directoryProperty()
	final DirectoryProperty schemeArchiveFile = project.layout.directoryProperty()
	final DirectoryProperty dstRoot = project.layout.directoryProperty()
	final DirectoryProperty objRoot = project.layout.directoryProperty()
	final DirectoryProperty symRoot = project.layout.directoryProperty()
	final DirectoryProperty sharedPrecompsDir = project.layout.directoryProperty()
	final DirectoryProperty derivedDataPath = project.layout.directoryProperty()
	final Property<XcodeService> xcodeServiceProperty = project.objects.property(XcodeService)
	final NamedDomainObjectContainer<TargetConfiguration> targetConfigurations

	final Signing signing

	XcodebuildParameters _parameters = new XcodebuildParameters()

	String infoPlist = null

	boolean simulator = true
	Type type = Type.iOS

	def additionalParameters = null
	String bundleNameSuffix = null
	List<String> arch = null
	String workspace = null

	Map<String, String> environment = null
	String productName = null
	String bundleName = null
	String productType = "app"
	String ipaFileName = null
	File projectFile

	boolean useXcodebuildArchive = false


	Devices devices = Devices.UNIVERSAL

	VariableResolver variableResolver
	PlistHelper plistHelper


	Xcode xcode

	HashMap<String, BuildTargetConfiguration> projectSettings = new HashMap<>()

	/**
	 * internal parameters
	 */
	private final Project project
	private final CommandRunner commandRunner

	private static final Logger logger = LoggerFactory.getLogger(XcodeBuildPluginExtension.class)

	XcodeBuildPluginExtension(Project project,
							  CommandRunner commandRunner) {
		this.project = project
		this.commandRunner = commandRunner

		plistHelper = new PlistHelper(commandRunner)

		configureServices()
		configurePaths()

		this.signing = project.objects.newInstance(Signing, project, commandRunner)
		this.variableResolver = new VariableResolver(project)

		this.dstRoot.set(project.layout.buildDirectory.dir("dst"))
		this.objRoot.set(project.layout.buildDirectory.dir("obj"))
		this.symRoot.set(project.layout.buildDirectory.dir("sym"))
		this.sharedPrecompsDir.set(project.layout.buildDirectory.dir("shared"))
		this.derivedDataPath.set(project.layout.buildDirectory.dir("derivedData"))
		this.targetType.set(Type.iOS)

		targetConfigurations = project.container(TargetConfiguration)
		project.extensions.targetConfigurations = targetConfigurations
	}

	private void configureServices() {
		XcodeService service = project.objects.newInstance(XcodeService,
				project)
		service.commandRunnerProperty.set(commandRunner)
		this.xcodeServiceProperty.set(service)
	}

	private void configurePaths() {
		this.archiveDirectory.set(project.layout
				.buildDirectory
				.dir(PathHelper.FOLDER_ARCHIVE))

		this.schemeArchiveFile.set(scheme.map(new Transformer<Directory, String>() {
			@Override
			Directory transform(String scheme) {
				return archiveDirectory.get()
						.dir(scheme + PathHelper.EXTENSION_XCARCHIVE)
			}
		}))
	}

	Optional<BuildConfiguration> getBuildTargetConfiguration(String schemeName,
															 String configuration) {
		return Optional.ofNullable(projectSettings.get(schemeName, null))
				.map { it -> it.buildSettings }
				.map { bs -> (BuildConfiguration) bs.get(configuration) }
	}

	String getWorkspace() {
		if (workspace != null) {
			return workspace
		}
		String[] fileList = project.projectDir.list(new SuffixFileFilter(".xcworkspace"))
		if (fileList != null && fileList.length) {
			return fileList[0]
		}
		return null
	}

	void signing(Closure closure) {
		ConfigureUtil.configure(closure, this.signing)
	}


	boolean isSimulatorBuildOf(Type expectedType) {
		if (type != expectedType) {
			logger.debug("is no simulator build")
			return false;
		}
		logger.debug("is simulator build {}", this.simulator)
		return this.simulator;
	}

	boolean isDeviceBuildOf(Type expectedType) {
		if (type != expectedType) {
			return false;
		}
		return !this.simulator
	}


	void destination(Closure closure) {
		Destination destination = new Destination()
		ConfigureUtil.configure(closure, destination)
		setDestination(destination)
	}

	void setDestination(def destination) {
		_parameters.setDestination(destination)
	}

	Set<Destination> getDestinations() {
		return _parameters.configuredDestinations
	}


	void setArch(Object arch) {
		if (arch instanceof List) {
			logger.debug("Arch is List: " + arch + " - " + arch.getClass().getName())
			this.arch = arch;
		} else {
			logger.debug("Arch is string: " + arch + " - " + arch.getClass().getName())
			this.arch = new ArrayList<String>();
			this.arch.add(arch.toString());
		}
	}

	void setEnvironment(Object environment) {
		if (environment == null) {
			return
		}

		if (environment instanceof Map) {
			logger.debug("environment is Map: " + environment + " - " + environment.getClass().getName())
			this.environment = environment;
		} else {
			logger.debug("environment is string: " + environment + " - " + environment.getClass().getName())
			this.environment = new HashMap<String, String>();

			String environmentString = environment.toString()
			int index = environmentString.indexOf("=")
			if (index == -1) {
				this.environment.put(environmentString, null)
			} else {
				this.environment.put(environmentString.substring(0, index), environmentString.substring(index + 1))
			}
		}
	}


	String getValueFromInfoPlist(key) {
		if (infoPlist != null) {
			File infoPlistFile = new File(project.projectDir, infoPlist)
			return plistHelper.getValueFromPlist(infoPlistFile, key)
		}
		/*
		try {
			logger.debug("project.projectDir {}", project.projectDir)
			File infoPlistFile = new File(project.projectDir, infoPlist)
			logger.debug("get value {} from plist file {}", key, infoPlistFile)
			return commandRunner.runWithResult([
							"/usr/libexec/PlistBuddy",
							infoPlistFile.absolutePath,
							"-c",
							"Print :" + key])
		} catch (IllegalStateException ex) {
			return null
		}
		*/
	}

	String getBundleName() {
		if (bundleName != null) {
			return bundleName
		}
		bundleName = getValueFromInfoPlist("CFBundleName")

		bundleName = variableResolver.resolve(bundleName)

		if (StringUtils.isEmpty(bundleName)) {
			bundleName = this.productName
		}
		return bundleName
	}

	// should be removed an replaced by the xcodebuildParameters.outputPath
	File getOutputPath() {
		String path = configuration.get()
		if (type != Type.macOS) {
			path += "-"
			if (type == Type.iOS) {
				if (simulator) {
					path += PathHelper.IPHONE_SIMULATOR
				} else {
					path += PathHelper.IPHONE_OS
				}
			} else if (type == Type.tvOS) {
				if (simulator) {
					path += PathHelper.APPLE_TV_SIMULATOR
				} else {
					path += PathHelper.APPLE_TV_OS
				}
			}
		}
		return new File(getSymRoot().asFile.get(), path)
	}


	BuildConfiguration getParent(BuildConfiguration buildSettings) {
		BuildConfiguration result = buildSettings
		File infoPlist = new File(project.projectDir, buildSettings.infoplist);
		String bundleIdentifier = plistHelper.getValueFromPlist(infoPlist, "WKCompanionAppBundleIdentifier")
		if (bundleIdentifier != null) {

			projectSettings.each { String key, BuildTargetConfiguration buildConfiguration ->

				BuildConfiguration settings = buildConfiguration.buildSettings[configuration.get()];
				if (settings != null && settings.bundleIdentifier.equalsIgnoreCase(bundleIdentifier)) {
					result = settings
					return
				}
			}
		}
		return result;

	}


	File getApplicationBundle() {

		BuildTargetConfiguration buildConfiguration = projectSettings[target.get()]
		if (buildConfiguration != null) {
			BuildConfiguration buildSettings = buildConfiguration.buildSettings[configuration.get()];
			if (buildSettings != null && buildSettings.sdkRoot.equalsIgnoreCase("watchos")) {
				BuildConfiguration parent = getParent(buildSettings)
				return new File(getOutputPath(), parent.productName + "." + this.productType)
			}
		}
		return new File(getOutputPath(), getBundleName() + "." + this.productType)
	}

	File getBinary() {
		logger.debug("getBinary")
		BuildTargetConfiguration buildConfiguration = projectSettings[target.get()]
		if (buildConfiguration != null) {
			BuildConfiguration buildSettings = buildConfiguration.buildSettings[configuration.get()];
			logger.debug("buildSettings: {}", buildSettings)
			if (type == Type.macOS) {
				return new File(getOutputPath(), buildSettings.productName + ".app/Contents/MacOS/" + buildSettings.productName)
			}
			return new File(getOutputPath(), buildSettings.productName + ".app/" + buildSettings.productName)
		}
		return null
	}


	BuildConfiguration getBuildConfiguration() {
		BuildTargetConfiguration buildTargetConfiguration = projectSettings[target.get()]
		if (buildTargetConfiguration != null) {
			return buildTargetConfiguration.buildSettings[configuration.get()];
		}
		throw new IllegalStateException("No build configuration found for + target '" + parameters.target.get() + "' and configuration '" + configuration.get() + "'")
	}

	BuildConfiguration getBuildConfiguration(String bundleIdentifier) {
		BuildConfiguration result = null
		projectSettings.each() { target, buildTargetConfiguration ->
			BuildConfiguration settings = buildTargetConfiguration.buildSettings[configuration.get()]

			if (settings != null) {

				if (settings.bundleIdentifier == null && settings.infoplist != null) {
					String identifier = plistHelper.getValueFromPlist(new File(settings.infoplist), "CFBundleIdentifier")
					if (identifier != null && identifier.equalsIgnoreCase(bundleIdentifier)) {
						result = settings
						return true
					}
				} else if (settings.bundleIdentifier != null && settings.bundleIdentifier.equalsIgnoreCase(bundleIdentifier)) {
					result = settings
					return true
				}
			}
		}
		return result
	}

	void setType(String type) {
		this.type = Type.typeFromString(type)
		this.targetType.set(this.type)
	}

	Type getType() {
		return type
	}

	boolean getSimulator() {
		if (type == Type.macOS) {
			return false
		}
		return this.simulator
	}

	void setSimulator(Object simulator) {
		if (simulator instanceof Boolean) {
			this.simulator = simulator
			return
		}
		this.simulator = simulator.toString().equalsIgnoreCase("true") || simulator.toString().equalsIgnoreCase("yes")
	}

	void setProjectFile(File projectFile) {
		assert projectFile.exists()
		this.projectFile = projectFile
	}

	void setProjectFile(String projectFile) {
		this.projectFile = new File(project.projectDir.absolutePath, projectFile)
	}

	File getProjectFile() {
		if (this.projectFile != null) {
			return this.projectFile
		}

		String[] projectFiles = project.rootProject
				.projectDir
				.list(new SuffixFileFilter(".xcodeproj"))

		if (!projectFiles || projectFiles.length < 1) {
			throw new FileNotFoundException("No Xcode project files were found in ${project.projectDir}")
		}

		return new File(project.rootProject.projectDir,
				projectFiles.first())
	}

	// should be remove in the future, so that every task has its own xcode object
	Xcode getXcode() {
		if (xcode == null) {
			xcode = new Xcode(commandRunner, version.get())
		}
		logger.debug("using xcode {}", xcode)
		return xcode
	}


	XcodebuildParameters getXcodebuildParameters() {
		def result = new XcodebuildParameters()
		result.scheme = this.scheme.getOrNull()
		result.target = this.target.get()
		result.simulator = this.simulator
		result.type = this.type
		result.workspace = getWorkspace()
		result.configuration = this.configuration.get()
		result.dstRoot = this.getDstRoot().asFile.getOrNull()
		result.objRoot = this.getObjRoot().asFile.getOrNull()
		result.symRoot = this.getSymRoot().asFile.getOrNull()
		result.sharedPrecompsDir = this.getSharedPrecompsDir().asFile.getOrNull()
		result.derivedDataPath = this.derivedDataPath.asFile.getOrNull()
		result.additionalParameters = this.additionalParameters
		result.devices = this.devices
		result.configuredDestinations = this.destinations
		result.bitcode = this.bitcode.getOrElse(true)
		result.applicationBundle = getApplicationBundle()

		if (this.arch != null) {
			result.arch = this.arch.clone()
		}

		return result
	}

}
