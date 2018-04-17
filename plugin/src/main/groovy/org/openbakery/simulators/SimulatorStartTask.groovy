package org.openbakery.simulators

import org.gradle.api.tasks.TaskAction
import org.openbakery.xcode.Destination

class SimulatorStartTask extends AbstractSimulatorTask {

	public SimulatorStartTask() {
		setDescription("Start iOS Simulators")
	}

	@TaskAction
	void run() {
		Destination destination = getDestination()
		println "device ::: " + simulatorControl.getDevice(destination)
		simulatorControl.getDevice(destination)
				.ifPresent { device ->
			println device
			runDevice(device)
		}
//			.ifPresent(new Consumer<SimulatorDevice>() {
//			@Override
//			void accept(SimulatorDevice simulatorDevice) {
//				runDevice(simulatorDevice)
//			}
//		})
//				.ifPresent { device -> runDevice(device) }

//		if (!device.present) {
//			throw new IllegalArgumentException("Missing destination")
//            simulatorControl.killAll()
//            simulatorControl.runDevice(device)
//            simulatorControl.waitForDevice(device)
//		}
	}

	void runDevice(SimulatorDevice device) {
		simulatorControl.resolveCtlList()
	}
}
