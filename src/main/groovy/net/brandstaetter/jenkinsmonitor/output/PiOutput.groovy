package net.brandstaetter.jenkinsmonitor.output

import com.pi4j.io.gpio.GpioController
import com.pi4j.io.gpio.GpioFactory
import com.pi4j.io.gpio.GpioPinDigitalOutput
import com.pi4j.io.gpio.PinState
import com.pi4j.io.gpio.RaspiPin
import net.brandstaetter.jenkinsmonitor.JenkinsBuild
import net.brandstaetter.jenkinsmonitor.Message
import org.apache.commons.configuration.Configuration

import java.util.concurrent.BlockingQueue
import java.util.concurrent.Future

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
    private Future<?> buildingFuture;
    private Future<?> statusFuture;

    PiOutput(BlockingQueue<Message<JenkinsBuild>> queue, Configuration configuration) {
        super(queue, configuration)
        GpioController gpio = GpioFactory.getInstance();
        statusPin = gpio.provisionDigitalOutputPin(RaspiPin.GPIO_04, PinState.LOW);
        buildingPin = gpio.provisionDigitalOutputPin(RaspiPin.GPIO_05, PinState.LOW);
    }

    @Override
    protected void printStartUp(String currentDay) {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    protected void printNewDay(String newDay) {
        // do nothing
    }

    @Override
    protected void printCurrentState(String currentTime, JenkinsBuild currentState) {
        //To change body of implemented methods use File | Settings | File Templates.
        switch (currentState) {
            case JenkinsBuild.green_anim:
                buildingFuture = doBlink(buildingPin)
            case JenkinsBuild.green:
                statusPin.high();
                break;
            case JenkinsBuild.yellow_anim:
                buildingFuture = doBlink(buildingPin)
            case JenkinsBuild.yellow:
                statusFuture = doBlink(statusPin)
                break;
            case JenkinsBuild.gray_anim:
            case JenkinsBuild.red_anim:

                buildingFuture = doBlink(buildingPin)
            default:
                statusPin.low();
                break;
        }
    }

    @Override
    protected void printSeparator() {
        //do nothing
    }

    @Override
    protected void printStateChange(JenkinsBuild lastState, JenkinsBuild currentState, double timeInMinutes) {
        switch(lastState) {
            case JenkinsBuild.yellow_anim:
                resetFuture(buildingFuture)
            case JenkinsBuild.yellow:
                resetFuture(statusFuture)
                break;
            default:
                resetFuture(buildingFuture)
        }
    }

    @Override
    protected void printTerminated() {
        resetFuture(buildingFuture)
        resetFuture(statusFuture)
    }

    private void resetFuture(Future<?> f) {
        if (f!=null) f.cancel(true)
    }

    private Future<?> doBlink(GpioPinDigitalOutput pin) {
        return pin.blink(1,1000)
    }
}

