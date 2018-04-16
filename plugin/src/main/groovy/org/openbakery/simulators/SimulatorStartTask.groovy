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

        Optional<SimulatorDevice> device = simulatorControl.getDevice(destination)
        if (device.present) {
            simulatorControl.killAll()
            simulatorControl.runDevice(device)
            simulatorControl.waitForDevice(device)
        }
    }
}
