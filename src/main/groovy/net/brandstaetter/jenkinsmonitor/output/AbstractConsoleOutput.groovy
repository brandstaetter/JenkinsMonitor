package net.brandstaetter.jenkinsmonitor.output

import net.brandstaetter.jenkinsmonitor.JenkinsBuild
import net.brandstaetter.jenkinsmonitor.Message
import net.brandstaetter.jenkinsmonitor.ThreadedJenkinsMonitor
import org.apache.commons.configuration.Configuration

import java.text.SimpleDateFormat
import java.util.concurrent.BlockingQueue
import java.util.concurrent.TimeUnit

/** Consumer */
abstract class AbstractConsoleOutput extends Thread {
    private static int buildsPerRow = 10
    private static int buildsPerSeparator = 5
    private static JenkinsBuild lastState
    private static Map<String, JenkinsBuild> currentState

    private final BlockingQueue<Message<Map<String, JenkinsBuild>>> queue
    protected final Configuration configuration
    private boolean quit;

    public AbstractConsoleOutput(
            final BlockingQueue<Message<Map<String, JenkinsBuild>>> queue, Configuration configuration) {
        this.queue = queue;
        this.quit = false;
        this.configuration = configuration

        try {
            buildsPerRow = configuration.getInt("buildsPerRow", 10)
        } catch (NumberFormatException ignored) {
            buildsPerRow = 10
        }
        try {
            buildsPerSeparator = configuration.getInt("buildsPerSeparator", 5)
        } catch (NumberFormatException ignored) {
            buildsPerSeparator = 5
        }
    }

    public void tellQuit() {
        this.quit = true
    }

    @Override
    public void run() {
        lastState = JenkinsBuild.green
        SimpleDateFormat sdfHour = new SimpleDateFormat(" HH:mm ")
        SimpleDateFormat sdfDay = new SimpleDateFormat(" yyyy.MM.dd:")
        Date currentDay = new Date()
        long counter = 1
        Date lastStateChangeDate = new Date();

        printStartUp(sdfDay.format(currentDay));

        while (!quit) {
            Message<Map<String, JenkinsBuild>> consumed = null
            while (!interrupted() && consumed == null) {
                try {
                    consumed = queue.poll(2L, TimeUnit.SECONDS);
                } catch (InterruptedException ignored) {
                    consumed = null
                }
            }

            // "poison pill"
            if (consumed.type == Message.MessageType.END) {
                queue.offer(consumed); // leave it for the other consumers
                tellQuit()
                break;
            }
            currentState = consumed.message
            Date currentTime = new Date()
            //noinspection GrDeprecatedAPIUsage
            if (currentDay.getDay() != currentTime.getDay()) {
                currentDay = currentTime
                counter = 1
                printNewDay(sdfDay.format(currentDay))
            }
            def currentSimpleState = ThreadedJenkinsMonitor.getSimpleResult(currentState, configuration)
            if (!lastState.equals(currentSimpleState)) {
                printStatesChange(lastState, currentState, (currentTime.getTime() - lastStateChangeDate.getTime()) / (1000 * 60))
                lastState = currentSimpleState
                lastStateChangeDate = currentTime
            }
            printCurrentStates(sdfHour.format(currentTime), currentState)
            if ((counter % buildsPerRow) == 0) System.out.println();
            if ((counter % buildsPerSeparator) == 0 && (counter % buildsPerRow) != 0) printSeparator()
            counter++

        }
        printTerminated()
    }

    protected abstract void printStartUp(String currentDay)

    protected abstract void printNewDay(String newDay)

    protected abstract void printCurrentStates(String currentTime, Map<String, JenkinsBuild> currentStates)

    protected abstract void printSeparator()

    protected
    abstract void printStatesChange(JenkinsBuild lastState, Map<String, JenkinsBuild> currentStates, double timeInMinutes)

    protected abstract void printTerminated()

}
