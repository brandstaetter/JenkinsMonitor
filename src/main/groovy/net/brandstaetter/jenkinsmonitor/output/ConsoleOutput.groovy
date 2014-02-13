package net.brandstaetter.jenkinsmonitor.output;

import net.brandstaetter.jenkinsmonitor.JenkinsBuild
import net.brandstaetter.jenkinsmonitor.Message
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
        System.out.println("\n\n" + sdfDay.format(currentDay));
    }

    @Override
    protected void printCurrentState(String currentTime, JenkinsBuild currentState) {
        System.out.print(currentTime + currentState.s + " ")
    }

    @Override
    protected void printStateChange(JenkinsBuild lastState, JenkinsBuild currentState, double timeInMinutes) {
        System.out.println()
        System.out.println("state change: $lastState -> $currentState, $lastState lasted for " + timeInMinutes + " minutes");
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
