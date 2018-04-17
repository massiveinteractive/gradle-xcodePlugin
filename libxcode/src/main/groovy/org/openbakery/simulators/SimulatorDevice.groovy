package org.openbakery.simulators

import java.util.regex.Matcher
import java.util.regex.Pattern

class SimulatorDevice {

	public String name
	public String identifier
	public String state
	public boolean available = true

	private
	static Pattern regDevice = ~/(?i)^\s{4}(.+) \(([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})\)\s\(([^)]+)\)\s?(\(([^)]+)\))?/

	SimulatorDevice(String name,
					String identifier,
					String state,
					boolean available) {
		this.name = name
		this.identifier = identifier
		this.state = state
		this.available = available
	}

	@Override
	String toString() {
		return "SimulatorDevice{" +
				"name='" + name + '\'' +
				", identifier='" + identifier + '\'' +
				", state='" + state + '\'' +
				'}';
	}

	boolean equals(o) {
		if (this.is(o)) return true
		if (getClass() != o.class) return false

		SimulatorDevice that = (SimulatorDevice) o

		if (identifier != that.identifier) return false

		return true
	}

	int hashCode() {
		return identifier.hashCode()
	}

	static Optional<SimulatorDevice> fromString(String string = null) {
		if (string != null) {
			Matcher matcher = regDevice.matcher(string)
			if (matcher.matches()) {
				String optionalStatus = matcher.group(5)
				return Optional.ofNullable(new SimulatorDevice(matcher.group(1),
						matcher.group(2),
						matcher.group(3),
						optionalStatus == null || !optionalStatus.startsWith("unavailable")))
			}
		}
		return Optional.empty()
	}
}
