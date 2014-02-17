package net.brandstaetter.jenkinsmonitor.output;

import net.brandstaetter.jenkinsmonitor.JenkinsBuild
import net.brandstaetter.jenkinsmonitor.Message
import net.brandstaetter.jenkinsmonitor.ThreadedJenkinsMonitor
import org.apache.commons.configuration.Configuration

import java.util.concurrent.BlockingQueue

public class ConsoleOutput extends AbstractConsoleOutput {

    public ConsoleOutput(BlockingQueue<Message<JenkinsBuild>> queue, Configuration configuration) {
        super(queue, configuration)
    }

    @Override
    protected void printStartUp(String currentDay) {
        System.out.println(currentDay)
    }

    @Override
    protected void printNewDay(String newDay) {
        System.out.println("\n\n" + newDay);
    }

    @Override
    protected void printCurrentStates(String currentTime, Map<String, JenkinsBuild> currentStates) {
        JenkinsBuild currentSimpleState = ThreadedJenkinsMonitor.getSimpleResult(currentStates, configuration)
        System.out.print(currentTime + currentSimpleState.s + " ")
    }

    @Override
    protected void printStatesChange(JenkinsBuild lastState, Map<String, JenkinsBuild> currentStates, double timeInMinutes) {
        JenkinsBuild currentSimpleState = ThreadedJenkinsMonitor.getSimpleResult(currentStates, configuration)
        System.out.println()
        System.out.println("state change: $lastState -> $currentSimpleState, $lastState lasted for " + timeInMinutes + " minutes");
    }

    @Override
    protected void printSeparator() {
        System.out.print(" ")
    }

    @Override
    protected void printTerminated() {
        System.out.println("Console Output terminated.")
    }
}
