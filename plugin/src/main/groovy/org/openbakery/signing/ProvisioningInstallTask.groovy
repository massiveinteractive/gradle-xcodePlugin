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
package org.openbakery.signing

import org.apache.commons.io.FilenameUtils
import org.gradle.api.tasks.TaskAction
import org.openbakery.AbstractXcodeTask
import org.openbakery.XcodePlugin
import org.openbakery.codesign.ProvisioningProfileReader

class ProvisioningInstallTask extends AbstractXcodeTask {

	public final static PROVISIONING_NAME_BASE = "gradle-"



	ProvisioningInstallTask() {
		super()
		dependsOn(XcodePlugin.PROVISIONING_CLEAN_TASK_NAME)
		this.description = "Installs the given provisioning profile"
	}


	void linkToLibraray(File mobileProvisionFile) {
		File provisionPath = new File(System.getProperty("user.home") + "/Library/MobileDevice/Provisioning Profiles/");
		if (!provisionPath.exists()) {
			provisionPath.mkdirs()
		}

		File mobileProvisionFileLinkToLibrary =  new File(System.getProperty("user.home") + "/Library/MobileDevice/Provisioning Profiles/" + mobileProvisionFile.getName());
		if (mobileProvisionFileLinkToLibrary.exists()) {
			mobileProvisionFileLinkToLibrary.delete()
		}


		commandRunner.run(["/bin/ln", "-s", mobileProvisionFile.absolutePath, mobileProvisionFileLinkToLibrary.absolutePath])

	}

	@TaskAction
	def install() {


		if (project.xcodebuild.signing.mobileProvisionURI == null) {
			logger.lifecycle("No provisioning profile specifed so do nothing here")
			return
		}

		for (String mobileProvisionURI : project.xcodebuild.signing.mobileProvisionURI) {
			def mobileProvisionFile = download(project.xcodebuild.signing.mobileProvisionDestinationRoot, mobileProvisionURI)



			ProvisioningProfileReader provisioningProfileIdReader = new ProvisioningProfileReader(new File(mobileProvisionFile), this.commandRunner, this.plistHelper)

			String uuid = provisioningProfileIdReader.getUUID()


			String extension = FilenameUtils.getExtension(mobileProvisionFile)
			String mobileProvisionName
			mobileProvisionName = PROVISIONING_NAME_BASE + uuid + "." + extension


			File downloadedFile = new File(mobileProvisionFile)
			File renamedProvisionFile = new File(downloadedFile.getParentFile(), mobileProvisionName)
			downloadedFile.renameTo(renamedProvisionFile)

			project.xcodebuild.signing.addMobileProvisionFile(renamedProvisionFile)

			linkToLibraray(renamedProvisionFile)
		}

	}
}
