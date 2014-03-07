package net.brandstaetter.jenkinsmonitor.output

import net.brandstaetter.jenkinsmonitor.JenkinsBuild
import net.brandstaetter.jenkinsmonitor.Message
import net.brandstaetter.jenkinsmonitor.ThreadedJenkinsMonitor
import org.apache.commons.configuration.Configuration
import org.fusesource.jansi.Ansi
import org.fusesource.jansi.AnsiConsole
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.awt.*
import java.util.concurrent.BlockingQueue

/** Consumer */
class JansiOutput extends AbstractConsoleOutput {
    private static Logger log = LoggerFactory.getLogger(JansiOutput.class)

    public JansiOutput(BlockingQueue<Message<JenkinsBuild>> queue, Configuration configuration) {
        super(queue, configuration)
        AnsiConsole.systemInstall();
    }

    @Override
    protected void printStartUp(String currentDay) {
        System.out.println(Ansi.ansi().bg(Ansi.Color.BLACK).fg(Ansi.Color.WHITE).a(currentDay))
    }

    @Override
    protected void printNewDay(String newDay) {
        System.out.println(Ansi.ansi().bg(Ansi.Color.BLACK).fg(Ansi.Color.WHITE).a("\n\n" + newDay).reset())
    }

    @Override
    protected void printCurrentStates(String currentTime, Map<String, JenkinsBuild> currentStates) {
        JenkinsBuild currentSimpleState = ThreadedJenkinsMonitor.getSimpleResult(currentStates, configuration)
        def (Ansi.Color translated, Ansi.Color fg) = translate(currentSimpleState)

        System.out.print(Ansi.ansi().bg(translated).fg(fg).a(currentTime).reset())
    }

    private static java.util.List translate(JenkinsBuild state) {
        def translated;
        switch (state.c) {
            case Color.GREEN:
                translated = Ansi.Color.GREEN;
                break;
            case Color.YELLOW:
                translated = Ansi.Color.YELLOW;
                break;
            case Color.RED:
                translated = Ansi.Color.RED;
                break;
            case Color.WHITE:
                translated = Ansi.Color.WHITE;
                break;
            case Color.CYAN:
                translated = Ansi.Color.CYAN;
                break;
            case Color.BLUE:
                translated = Ansi.Color.BLUE;
                break;
            default:
                translated = Ansi.Color.DEFAULT
        }
        def fg = Ansi.Color.BLACK;
        if (translated == Ansi.Color.BLUE || state.blinking) {
            fg = Ansi.Color.WHITE
        }
        if (translated == Ansi.Color.WHITE && state.blinking) {
            fg = Ansi.Color.BLUE
        }
        return [translated, fg]
    }

    @Override
    protected void printSeparator() {
        System.out.print(Ansi.ansi().bg(Ansi.Color.BLACK).a(" ").reset())
    }

    @Override
    protected void printStatesChange(JenkinsBuild lastState, Map<String, JenkinsBuild> currentStates, double timeInMinutes) {
        JenkinsBuild currentSimpleState = ThreadedJenkinsMonitor.getSimpleResult(currentStates, configuration)
        log.info("state change: $lastState -> $currentSimpleState, $lastState lasted for " + timeInMinutes + " minutes");
    }

    @Override
    protected void printTerminated() {
        AnsiConsole.systemUninstall();
        System.out.println("Jansi Console terminated.")
    }
}
