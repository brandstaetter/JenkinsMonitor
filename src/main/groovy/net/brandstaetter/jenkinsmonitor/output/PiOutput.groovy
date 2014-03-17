package net.brandstaetter.jenkinsmonitor.output

import com.pi4j.io.gpio.*
import net.brandstaetter.jenkinsmonitor.JenkinsBuild
import net.brandstaetter.jenkinsmonitor.Message
import net.brandstaetter.jenkinsmonitor.ThreadedJenkinsMonitor
import org.apache.commons.configuration.Configuration

import java.util.concurrent.BlockingQueue

/**
 * Created with IntelliJ IDEA.
 * User: brosenberger
 * Date: 13.02.14
 * Time: 07:39
 * To change this template use File | Settings | File Templates.
 */
class PiOutput extends AbstractConsoleOutput {

    private GpioPinDigitalOutput statusPin;
    private GpioPinDigitalOutput buildingPin;

    PiOutput(BlockingQueue<Message<JenkinsBuild>> queue, Configuration configuration) {
        super(queue, configuration)
        GpioController gpio = GpioFactory.getInstance();
        statusPin = gpio.provisionDigitalOutputPin(RaspiPin.GPIO_04, PinState.LOW);
        buildingPin = gpio.provisionDigitalOutputPin(RaspiPin.GPIO_05, PinState.LOW);
    }

    @Override
    protected void printStartUp(String currentDay) {
        // do nothing
    }

    @Override
    protected void printNewDay(String newDay) {
        // do nothing
    }

    @SuppressWarnings("GroovyFallthrough")
    @Override
    protected void printCurrentStates(String currentTime, Map<String, JenkinsBuild> currentStates) {
        JenkinsBuild currentSimpleState = ThreadedJenkinsMonitor.getSimpleResult(currentStates, configuration)
        switch (currentSimpleState) {
            case JenkinsBuild.green_anim:
                buildingPin.high();
                statusPin.low();
                break;
            case JenkinsBuild.green:
                statusPin.low();
                buildingPin.low();
                break;
            case JenkinsBuild.yellow_anim:
                buildingPin.high()
                statusPin.high();
                break;
            case JenkinsBuild.yellow:
                statusPin.high();
                buildingPin.low();
                break;
            case JenkinsBuild.gray_anim:
            case JenkinsBuild.red_anim:
                buildingPin.high();
                statusPin.high();
                break;
            default:
                buildingPin.low();
                statusPin.high();
                break;
        }
    }

    @Override
    protected void printSeparator() {
    }

    @Override
    protected void printStatesChange(JenkinsBuild lastState, Map<String, JenkinsBuild> currentStates, double timeInMinutes) {
        // nothing todo
    }

    @Override
    protected void printTerminated() {
        //shutdown gpio
        buildingPin.low();
        statusPin.low();
        GpioFactory.getInstance().shutdown()
        System.out.println("Pi Output terminated.")
    }
}

