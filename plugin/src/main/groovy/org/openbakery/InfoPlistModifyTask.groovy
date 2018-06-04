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

import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskAction

class InfoPlistModifyTask extends AbstractDistributeTask {

	File infoPlist
	Boolean modfied = false

	public final Provider<String> configurationBundleIdentifier = project.objects.property(String)

	public static final String KeyBundleIdentifier = "CFBundleIdentifier"

	public InfoPlistModifyTask() {
		dependsOn(XcodePlugin.XCODE_CONFIG_TASK_NAME)
	}

	@TaskAction
	def prepare() {
		if (!project.infoplist.hasValuesToModify()) {
			logger.debug("Nothing to modify")
			return;
		}


		if (project.xcodebuild.infoPlist == null) {
			throw new IllegalArgumentException("No Info.plist was found! Check you xcode project settings if the specified target has a Info.plist set.")
		}

		infoPlist = new File(project.rootProject.projectDir, project.xcodebuild.infoPlist)


		logger.debug("Try to updating {}", infoPlist)

		modifyBundleIdentifier()

		// Modify bundle bundleName
		if (project.infoplist.bundleName != null) {
			setValueForPlist("CFBundleName", project.infoplist.bundleName)
		}

		// Modify bundle bundleDisplayName
		if (project.infoplist.bundleDisplayName != null) {
			setValueForPlist("CFBundleDisplayName", project.infoplist.bundleDisplayName)
		}

		// add suffix to bundleDisplayName
		if (project.infoplist.bundleDisplayNameSuffix != null) {
			def bundleDisplayName = plistHelper.getValueFromPlist(infoPlist, "CFBundleDisplayName")
			setValueForPlist("CFBundleDisplayName", bundleDisplayName + project.infoplist.bundleDisplayNameSuffix)
		}

		logger.debug("project.infoplist.version: {}", project.infoplist.version)

		modifyVersion(infoPlist)
		modifyShortVersion(infoPlist)


		for (String command in project.infoplist.commands) {
			setValueForPlist(command)
		}

		if (modfied) {
			logger.lifecycle("{} was updated", infoPlist)
		} else {
			logger.debug("Nothing was modified!")
		}

		configurationBundleIdentifier.set(getBundleIdentifier())
	}

	private void modifyVersion(File infoPlist) {
		if (project.infoplist.version == null && project.infoplist.versionSuffix == null && project.infoplist.versionPrefix == null) {
			return
		}


		def version;
		if (project.infoplist.version != null) {
			version = project.infoplist.version
		} else {
			version = plistHelper.getValueFromPlist(infoPlist, "CFBundleVersion")
		}

		if (project.infoplist.versionSuffix) {
			version = version + project.infoplist.versionSuffix
		}

		if (project.infoplist.versionPrefix) {
			version = project.infoplist.versionPrefix + version
		}

		logger.debug("Modify CFBundleVersion to {}", version)
		setValueForPlist("CFBundleVersion", version)
	}


	private void modifyShortVersion(File infoPlist) {
		if (project.infoplist.shortVersionString == null && project.infoplist.shortVersionStringSuffix == null && project.infoplist.shortVersionStringPrefix == null) {
			return
		}

		def shortVersionString
		try {
			shortVersionString = plistHelper.getValueFromPlist(infoPlist, "CFBundleShortVersionString")
		} catch (IllegalStateException ex) {
			// no CFBundleShortVersionString exists so noting can be modified!
			return
		}

		if (project.infoplist.shortVersionString != null) {
			shortVersionString = project.infoplist.shortVersionString
		}
		if (project.infoplist.shortVersionStringSuffix) {
			shortVersionString = shortVersionString + project.infoplist.shortVersionStringSuffix
		}

		if (project.infoplist.shortVersionStringPrefix) {
			shortVersionString = project.infoplist.shortVersionStringPrefix + shortVersionString
		}

		logger.debug("Modify CFBundleShortVersionString to {}", shortVersionString)
		setValueForPlist("CFBundleShortVersionString", shortVersionString)
	}

	private void modifyBundleIdentifier() {
		// Resolve bundle identifier
		XcodeBuildPluginExtension xcodeExtension = getXcodeExtension()
		InfoPlistExtension extension = getInfoPlistExtension()

		if (extension.bundleIdentifier != null) {
			setValueForPlist(KeyBundleIdentifier, extension.bundleIdentifier)
		} else {
			xcodeExtension.getBuildTargetConfiguration(xcodeExtension.scheme.getOrNull(),
					xcodeExtension.configuration.get())
					.map { it -> it.bundleIdentifier }
					.ifPresent { it -> setValueForPlist(KeyBundleIdentifier, it) }
		}

		// Add suffix to bundleIdentifier if defined
		if (extension.bundleIdentifierSuffix != null) {
			String bundleIdentifier = plistHelper.getValueFromPlist(infoPlist, KeyBundleIdentifier)
			setValueForPlist(KeyBundleIdentifier, bundleIdentifier + extension.bundleIdentifierSuffix)
		}
	}

	void setValueForPlist(String key, String value) {
		modfied = true
		logger.lifecycle("Set {} to {}", key, value)
		plistHelper.setValueForPlist(infoPlist, key, value)
	}

	void setValueForPlist(String command) {
		modfied = true
		logger.lifecycle("Set {}", command)
		plistHelper.commandForPlist(infoPlist, command)

	}
}
