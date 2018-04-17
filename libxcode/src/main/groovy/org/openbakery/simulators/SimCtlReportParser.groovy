package org.openbakery.simulators

abstract class SimCtlReportParser {

	ArrayList<SimulatorDevicePair> devicePairs
	ArrayList<SimulatorDeviceType> deviceTypes
	ArrayList<SimulatorRuntime> runtimes
	HashMap<SimulatorRuntime, List<SimulatorDevice>> devices
	HashMap<String, SimulatorDevice> identifierToDevice

	abstract String resolveCtlList()

	void parse() {
		runtimes = new ArrayList<>()
		devices = new HashMap<>()
		deviceTypes = new ArrayList<>()
		identifierToDevice = new HashMap<>()
		devicePairs = new ArrayList<>()


		Section section = null
		String simctlList = resolveCtlList()

		ArrayList<SimulatorDevice> simulatorDevices = null
		SimulatorDevicePair pair = null

		for (String line in simctlList.split("\n")) {

			Section isSection = Section.isSection(line)
			if (isSection != null) {
				section = isSection
				continue
			}

			switch (section) {
				case Section.DEVICE_TYPE:
					deviceTypes.add(new SimulatorDeviceType(line))
					break

				case Section.RUNTIMES:
					SimulatorRuntime runtime = new SimulatorRuntime(line)
					runtimes.add(runtime)
					break

				case Section.DEVICES:
					SimulatorRuntime isRuntime = parseDevicesRuntime(line)
					if (isRuntime != null) {
						simulatorDevices = new ArrayList<>()
						devices.put(isRuntime, simulatorDevices)
						continue
					}
					if (line.startsWith("--")) {
						// unknown runtime, so we are done
						simulatorDevices = null
					}

					if (simulatorDevices != null) {
						SimulatorDevice device = new SimulatorDevice(line)
						simulatorDevices.add(device)
						identifierToDevice[device.identifier] = device
					}

					break
				case Section.DEVICE_PAIRS:


					if (line ==~ /^\s+Watch.*/) {
						pair.watch = parseIdentifierFromDevicePairs(line)
					} else if (line ==~ /^\s+Phone.*/) {
						pair.phone = parseIdentifierFromDevicePairs(line)
					} else {
						// is new device pair
						pair = new SimulatorDevicePair(line)
						devicePairs.add(pair)
					}

					break


			}
		}
		Collections.sort(runtimes, new SimulatorRuntimeComparator())

	}

	List<SimulatorRuntime> getRuntimes() {
		if (runtimes == null) {
			parse()
		}
		return runtimes
	}


	SimulatorDevice parseIdentifierFromDevicePairs(String line) {
		def tokenizer = new StringTokenizer(line, "()");
		if (tokenizer.hasMoreTokens()) {
			// ignore first token
			tokenizer.nextToken()
		}
		if (tokenizer.hasMoreTokens()) {
			def identifier = tokenizer.nextToken().trim()
			return getDeviceWithIdentifier(identifier)
		}
		return null

	}

	SimulatorDevice getDeviceWithIdentifier(String identifier) {
		for (Map.Entry<SimulatorRuntime, List<SimulatorDevice>> entry in devices.entrySet()) {
			for (SimulatorDevice device in entry.value) {
				if (device.identifier == identifier) {
					return device
				}
			}
		}
		return null
	}

	SimulatorRuntime parseDevicesRuntime(String line) {
		for (SimulatorRuntime runtime in runtimes) {
			if (line.equals("-- " + runtime.name + " --")) {
				return runtime
			}
		}
		return null
	}

	HashMap<SimulatorRuntime, List<SimulatorDevice>> getDevices() {
		if (devices == null) {
			parse()
		}
		return devices
	}

	List<SimulatorDeviceType> getDeviceTypes() {
		if (deviceTypes == null) {
			parse()
		}
		return deviceTypes
	}

	List<SimulatorDevicePair> getDevicePairs() {
		if (devicePairs == null) {
			parse()
		}
		return devicePairs

	}

	enum Section {
		DEVICE_TYPE("== Device Types =="),
		RUNTIMES("== Runtimes =="),
		DEVICES("== Devices =="),
		DEVICE_PAIRS("== Device Pairs ==")

		private final String identifier

		Section(String identifier) {
			this.identifier = identifier
		}


		static Section isSection(String line) {
			for (Section section : Section.values()) {
				if (section.identifier.equals(line)) {
					return section;
				}
			}
			return null
		}
	}
}
