package org.openbakery.extension

import groovy.transform.CompileStatic

@CompileStatic
class TargetConfiguration implements Serializable {

	public File provisioningFile
	public File certificateFile
	public String certificatePassword
	public String bundleIdentifier
	public File entitlementsFile
	public String version
	public String shortVersion

	private final String name

	TargetConfiguration(String name) {
		this.name = name
	}

	String getName() {
		return name
	}
}
