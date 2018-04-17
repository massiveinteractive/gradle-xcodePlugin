package org.openbakery.simulators

import spock.lang.*

class SimulatorDeviceTest extends Specification {
	def "Test resolution from string"() {
		setup:
		Optional<SimulatorDevice> device = SimulatorDevice.fromString(ctlLine)

		expect:
		device.present == valid
		if (device.present) {
			device.get().name == name
			device.get().identifier == identifier
			device.get().state == state
			device.get().available == available
		}

		where:
		valid | name                   | state      | identifier                             | available | ctlLine
		true  | "iPhone 4s"            | "Shutdown" | "73C126C8-FD53-44EA-80A3-84F5F19508C0" | true      | "    iPhone 4s (73C126C8-FD53-44EA-80A3-84F5F19508C0) (Shutdown)"
		true  | "iPad Pro (12.9 inch)" | "Shutdown" | "C538D7F8-E581-44FF-9B17-5391F84642FB" | true      | "    iPad Pro (12.9 inch) (C538D7F8-E581-44FF-9B17-5391F84642FB) (Shutdown)"
		true  | "iPad Pro (12.9 inch)" | "Booted"   | "C538D7F8-E581-44FF-9B17-5391F84642FB" | true      | "    iPad Pro (12.9 inch) (C538D7F8-E581-44FF-9B17-5391F84642FB) (Booted)"
		true  | "Resizable iPad"       | "Shutdown" | "B33E6523-6E44-42EA-A8B6-AEFB6873E9E8" | false     | "    Resizable iPad (B33E6523-6E44-42EA-A8B6-AEFB6873E9E8) (Shutdown) (unavailable, device type profile not found)"
		false | null                   | _          | _                                      | _         | "invalid"
		false | null                   | _          | _                                      | _         | null
	}
}
