package org.openbakery.signing

import org.apache.commons.io.FilenameUtils
import org.openbakery.xcode.Type

class ProvisioningFile implements Serializable {

	private File file
	private String applicationIdentifier
	private String uuid
	private String teamIdentifier
	private String teamName
	private String name
	private List<String> platforms

	public static final String PROVISIONING_NAME_BASE = "gradle-"

	ProvisioningFile(File file,
					 String applicationIdentifier,
					 String uuid,
					 String teamIdentifier,
					 String teamName,
					 String name,
					 List<String> platforms) {
		this.applicationIdentifier = applicationIdentifier
		this.file = file
		this.uuid = uuid
		this.teamIdentifier = teamIdentifier
		this.teamName = teamName
		this.name = name
		this.platforms = platforms
	}

	String getApplicationIdentifier() {
		return applicationIdentifier
	}

	File getFile() {
		return file
	}

	String getUuid() {
		return uuid
	}

	String getTeamIdentifier() {
		return teamIdentifier
	}

	String getTeamName() {
		return teamName
	}

	String getName() {
		return name
	}

	String getFormattedName() {
		return formattedName(uuid, file)
	}

	List<String> getPlatforms() {
		return platforms
	}

	boolean supportBuildType(Type type) {
		return platforms.contains(type.value)
	}

	static String formattedName(String uuid, File file) {
		return PROVISIONING_NAME_BASE + uuid + "." + FilenameUtils.getExtension(file.getName())
	}
}
