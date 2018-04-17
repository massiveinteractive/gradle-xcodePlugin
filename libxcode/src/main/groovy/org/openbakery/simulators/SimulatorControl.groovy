package org.openbakery.simulators

import groovy.transform.CompileStatic
import org.openbakery.CommandRunner
import org.openbakery.CommandRunnerException
import org.openbakery.xcode.Destination
import org.openbakery.xcode.Type
import org.openbakery.xcode.Version
import org.openbakery.xcode.Xcode
import org.slf4j.Logger
import org.slf4j.LoggerFactory

@CompileStatic
class SimulatorControl extends SimCtlReportParser {


	private static Logger logger = LoggerFactory.getLogger(SimulatorControl.class)

	CommandRunner commandRunner
	Xcode xcode

	public SimulatorControl(CommandRunner commandRunner, Xcode xcode) {
		this.commandRunner = commandRunner
		this.xcode = xcode
	}

	@Override
	String resolveCtlList() {
		return simctl("list")
	}

	public void waitForDevice(SimulatorDevice device, int timeoutMS = 10000) {
		def start = System.currentTimeMillis()
		while ((System.currentTimeMillis() - start) < timeoutMS) {
			parse()
			def polledDevice = identifierToDevice[device.identifier]
			if (polledDevice != null && polledDevice.state == "Booted")
				return
			sleep(500)
		}
		throw new Exception("Timeout waiting for " + device)
	}

	SimulatorRuntime getMostRecentRuntime(Type type) {
		List<SimulatorRuntime> runtimes = getRuntimes(type);
		if (runtimes.size() > 0) {
			return runtimes.get(0)
		}
		return null;
	}

	List<SimulatorRuntime> getRuntimes(String name) {
		List<SimulatorRuntime> result = []
		for (SimulatorRuntime runtime in getRuntimes()) {
			if (runtime.available && runtime.getName().startsWith(name)) {
				result << runtime
			}
		}
		Collections.sort(result, new SimulatorRuntimeComparator())
		return result
	}


	List<SimulatorRuntime> getRuntimes(Type type) {
		ArrayList<SimulatorRuntime> result = new ArrayList<>()

		for (SimulatorRuntime runtime in getRuntimes()) {
			if (runtime.type == type) {
				result.add(runtime);
			}
		}
		Collections.sort(result, new SimulatorRuntimeComparator())
		return result;
	}

	SimulatorDevice getDevice(SimulatorRuntime simulatorRuntime, String name) {
		for (SimulatorDevice device in getDevices(simulatorRuntime)) {
			if (device.name.equalsIgnoreCase(name)) {
				return device
			}
		}
		null
	}


	Optional<SimulatorRuntime> getRuntime(Destination destination) {
		return Optional.ofNullable(getRuntimes()
				.findAll { it.type == destination.targetType }
				.findAll { it.version?.equals(new Version(destination.os)) }
				.find())
	}

	Optional<SimulatorDevice> getDevice(final Destination destination) {
		return getRuntime(destination)
				.map { runtime -> getDevices(runtime as SimulatorRuntime) }
				.map { list -> list.findAll() { device ->
				((SimulatorDevice) device).name.equalsIgnoreCase(destination.name)
			}.find()
		}
	}

	List<SimulatorDevice> getDevices(SimulatorRuntime runtime) {
		return getDevices()
				.get(runtime)
	}

	String simctl(String... commands) {
		ArrayList<String> parameters = new ArrayList<>()
		parameters.add(xcode.getSimctl())
		parameters.addAll(commands)
		return commandRunner.runWithResult(parameters)
	}


	void deleteAll() {

		for (Map.Entry<SimulatorRuntime, List<SimulatorDevice>> entry : getDevices().entrySet()) {
			for (SimulatorDevice device in entry.getValue()) {
				if (device.available) {
					println "Delete simulator: '" + device.name + "' " + device.identifier
					simctl("delete", device.identifier)
				}
			}
		}
	}


	void createAll() {
		for (SimulatorRuntime runtime in getRuntimes()) {

			if (runtime.available) {

				for (SimulatorDeviceType deviceType in getDeviceTypes()) {

					if (deviceType.canCreateWithRuntime(runtime)) {
						logger.debug("create '" + deviceType.name + "' '" + deviceType.identifier + "' '" + runtime.identifier + "'")
						try {
							simctl("create", deviceType.name, deviceType.identifier, runtime.identifier)
							println "Create simulator: '" + deviceType.name + "' for " + runtime.version
						} catch (CommandRunnerException ex) {
							println "Unable to create simulator: '" + deviceType.name + "' for " + runtime.version
						}
					}
				}
			}
		}
		pair()
	}

	void pair() {
		parse() // read the created ids again

		List<SimulatorRuntime> watchRuntimes = getRuntimes("watchOS")
		List<SimulatorRuntime> iOS9Runtimes = getRuntimes("iOS 9")


		for (SimulatorRuntime iOS9Runtime in iOS9Runtimes) {
			for (SimulatorRuntime watchRuntime in watchRuntimes) {

				SimulatorDevice iPhone6 = getDevice(iOS9Runtime, "iPhone 6")
				SimulatorDevice watch38 = getDevice(watchRuntime, "Apple Watch - 38mm")
				simctl("pair", iPhone6.identifier, watch38.identifier)


				SimulatorDevice iPhone6Plus = getDevice(iOS9Runtime, "iPhone 6 Plus")
				SimulatorDevice watch42 = getDevice(watchRuntime, "Apple Watch - 42mm")
				simctl("pair", iPhone6Plus.identifier, watch42.identifier)


			}
		}

	}


	void eraseAll() {
		for (Map.Entry<SimulatorRuntime, List<SimulatorDevice>> entry : getDevices().entrySet()) {
			for (SimulatorDevice device in entry.getValue()) {
				if (device.available) {
					println "Erase simulator: '" + device.name + "' " + device.identifier
					simctl("erase", device.identifier)
				}
			}
		}
	}

	public void killAll() {
		// kill a running simulator
		logger.info("Killing old simulators")
		try {
			commandRunner.run("killall", "iOS Simulator")
		} catch (CommandRunnerException ex) {
			// ignore, this exception means that no simulator was running
		}
		try {
			commandRunner.run("killall", "Simulator") // for xcode 7
		} catch (CommandRunnerException ex) {
			// ignore, this exception means that no simulator was running
		}
	}

	public void runDevice(SimulatorDevice device) {
		SimulatorRuntime runtime = getRuntime(device)
		if (runtime == null) {
			throw new IllegalArgumentException("cannot find runtime for device: " + device)
		}

		try {
			commandRunner.run([xcode.getPath() + "/Contents/Developer/usr/bin/instruments", "-w", device.identifier])
		} catch (CommandRunnerException ex) {
			// ignore, because the result of this command is a failure, but the simulator should be launched
		}
	}

	SimulatorRuntime getRuntime(SimulatorDevice simulatorDevice) {

		for (Map.Entry<SimulatorRuntime, List<SimulatorDevice>> runtime : devices) {
			for (SimulatorDevice device : runtime.value) {
				if (device.equals(simulatorDevice)) {
					return runtime.key
				}
			}
		}

		return null
	}

	List<Destination> getAllDestinations(Type type) {
		return getRuntimes(type)
				.collect { runtime -> getAllDestinations(type, runtime) }
				.flatten() as List<Destination>
	}

	List<Destination> getAllDestinations(Type type, SimulatorRuntime runtime) {
		return getDevices(runtime)
				.collect { device ->
			Destination destination = new Destination()
			destination.platform = type.value + ' Simulator'
			destination.name = device.name
			destination.os = runtime.version.toString()
			destination.id = device.identifier
			return destination
		}
	}

}
