package net.brandstaetter.jenkinsmonitor

import groovy.json.JsonSlurper
import org.apache.commons.configuration.Configuration
import org.apache.commons.configuration.PropertiesConfiguration
import org.fusesource.jansi.AnsiConsole
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import thingm.blink1.Blink1

import java.awt.*
import java.text.SimpleDateFormat
import java.util.List
import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

import static org.fusesource.jansi.Ansi.Color.*
import static org.fusesource.jansi.Ansi.ansi

/**
 * Created by brandstaetter on 08.02.14.
 */
class ThreadedJenkinsMonitor {

    public static void main(String... args) {
        Configuration configuration = new PropertiesConfiguration("jenkins_monitor_settings.properties");

        final BlockingQueue<Message<JenkinsBuild>> queue = new LinkedBlockingQueue<Message<JenkinsBuild>>()

        Runtime.getRuntime().addShutdownHook(new Thread() {
            public void run() {
                // send poison pill
                queue.offer(new Message<JenkinsBuild>(JenkinsBuild.green, Message.MessageType.END))
            }
        });
        JenkinsMonitor checkThread = new JenkinsMonitor(queue, configuration)
        Broadcaster broadcaster = new Broadcaster(queue)

        List<Thread> threadsNeedingInterruptOnQuit = new ArrayList<>()

        System.out.println("Press q followed by Enter to terminate");

        // multiplexer
        broadcaster.start()
        // consumers
        if (configuration.getBoolean('runConsole', true)) {
            final BlockingQueue<Message<JenkinsBuild>> queueForConsole = new LinkedBlockingQueue<Message<JenkinsBuild>>()
            ConsoleOutput consoleOutputThread = new ConsoleOutput(queueForConsole, configuration)
            broadcaster.addConsumer(queueForConsole)
            consoleOutputThread.start()
        }
        if (configuration.getBoolean('runBlink', false)) {
            final BlockingQueue<Message<JenkinsBuild>> queueForBlink = new LinkedBlockingQueue<Message<JenkinsBuild>>()
            Blink1Output blinkOutputThread = new Blink1Output(queueForBlink, configuration)
            broadcaster.addConsumer(queueForBlink)
            threadsNeedingInterruptOnQuit.add(blinkOutputThread)
            blinkOutputThread.start()
        }
        if (configuration.getBoolean('runJansi', false)) {
            final BlockingQueue<Message<JenkinsBuild>> queueForJansi = new LinkedBlockingQueue<Message<JenkinsBuild>>()
            JansiOutput jansiOutputThread = new JansiOutput(queueForJansi, configuration)
            broadcaster.addConsumer(queueForJansi)
            jansiOutputThread.start()
        }

        // producer
        checkThread.start()

        def msg
        def reader = new BufferedReader(new InputStreamReader(System.in))
        while (true) {
            try {
                msg = reader.readLine();
            } catch (Exception e) {
                msg = "continue"
            }

            if (msg != null && msg.equalsIgnoreCase("q")) {
                // send poison pill
                checkThread.tellQuit()
                checkThread.interrupt()
                for (Thread thread : threadsNeedingInterruptOnQuit) {
                    thread.interrupt()
                }
                queue.offer(new Message<JenkinsBuild>(JenkinsBuild.green, Message.MessageType.END))
                break;
            }
        }
    }
}

/** Producer */
class JenkinsMonitor extends Thread {
    private static Logger log = LoggerFactory.getLogger(JenkinsMonitor.class)
    static List<String> abortedColors = ["aborted"]
    static List<String> abortedAnimColors = ["aborted_anime"]
    static List<String> failingColors = ["red"]
    static List<String> failingAnimColors = ["red_anime"]
    static List<String> unstableColors = ["yellow"]
    static List<String> unstableAnimColors = ["yellow_anime"]
    static List<String> successAnimColors = ["blue_anime"]

    private static String mainUrl
    private static String basicAuthentication
    private static String interestingBuildsList
    private static String animIfBuildingList

    private static long millisForNextCheck
    private final BlockingQueue<Message<JenkinsBuild>> queue
    private boolean quit;

    public JenkinsMonitor(final BlockingQueue<Message<JenkinsBuild>> queue, Configuration configuration) {
        this.queue = queue;
        this.quit = false

        if (!configuration.containsKey("mainUrl")
                || !configuration.containsKey("basicAuthentication")
                || !configuration.containsKey("interestingBuildsList")) {
            // die, consumers.
            queue.offer(new Message<JenkinsBuild>(JenkinsBuild.green, Message.MessageType.END))
            throw new RuntimeException("mainUrl, basicAuthentication and interestingBuildsList have to be configured in settings.properties!")
        }
        try {
            millisForNextCheck = configuration.getLong("millisForNextCheck", 60000L)
        } catch (NumberFormatException ignored) {
            millisForNextCheck = 60000 //60*1000ms = 1 minute
        }

        mainUrl = configuration.getString("mainUrl").trim()
        basicAuthentication = configuration.getString("basicAuthentication").trim()
        interestingBuildsList = configuration.getString("interestingBuildsList")
        animIfBuildingList = configuration.getString("animIfBuildingList", '[]')
    }

    @Override
    public void run() {
        JenkinsBuild currentState
        def closure = { quit = true }
        while (!quit) {

            try {
                currentState = statusReport()
                log.debug(currentState.toString())
            } catch (Exception e) {
                currentState = JenkinsBuild.exception
                log.error(currentState.toString(), e)
            }
            queue.offer(new Message<JenkinsBuild>(currentState))

            sleep(millisForNextCheck, closure) // need a closure here, default sleep ignores interrupts
        }
        queue.offer(new Message<JenkinsBuild>(JenkinsBuild.green, Message.MessageType.END))
        System.out.println("Jenkins Monitor terminated.")
    }

    public void tellQuit() {
        this.quit = true
    }

    static JenkinsBuild statusReport() {

        def url = (mainUrl + '/api/json?tree=jobs[name,color,url]').toURL()
        def json = url.getText requestProperties: ['Authorization': ('Basic ' + basicAuthentication)]
        def result = new JsonSlurper().parseText json

        def interestingBuilds = Eval.me(interestingBuildsList)
        def animIfBuilding = Eval.me(animIfBuildingList)
        def missingJobs = new ArrayList<>((Collection) interestingBuilds)

        def greenAnim = false
        for (def job : result.jobs) {
            if (job.name.trim().toLowerCase() in interestingBuilds) {
                missingJobs.remove(job.name.trim().toLowerCase())
                if (job.color in failingColors) {
                    return JenkinsBuild.red
                } else if (job.color in failingAnimColors) {
                    return JenkinsBuild.red_anim
                } else if (job.color in unstableColors) {
                    return JenkinsBuild.yellow
                } else if (job.color in unstableAnimColors) {
                    return JenkinsBuild.yellow_anim
                } else if (job.color in abortedColors) {
                    return JenkinsBuild.gray
                } else if (job.color in abortedAnimColors) {
                    return JenkinsBuild.gray_anim
                }
            }
            if (job.name.trim().toLowerCase() in animIfBuilding) {
                if (job.color in successAnimColors) {
                    greenAnim = true
                }
            }
        }
        if (missingJobs.isEmpty()) {
            if (greenAnim) {
                return JenkinsBuild.green_anim
            }
            return JenkinsBuild.green
        }
        return JenkinsBuild.missing
    }
}

/** Multiplexer */
class Broadcaster extends Thread {

    private final BlockingQueue<Message<JenkinsBuild>> queue
    private boolean quit;

    private final List<BlockingQueue<Message<JenkinsBuild>>> consumers

    public Broadcaster(final BlockingQueue<Message<JenkinsBuild>> queue) {
        this.queue = queue;
        this.quit = false;
        this.consumers = new ArrayList<BlockingQueue<Message<JenkinsBuild>>>()
    }

    public addConsumer(BlockingQueue<Message<JenkinsBuild>> consumer) {
        if (consumer != null) {
            consumers.add consumer
        }
    }

    public void tellQuit() {
        this.quit = true
    }

    public void run() {
        while (!quit) {
            Message<JenkinsBuild> consumed = null
            while (!interrupted() && consumed == null) {
                try {
                    consumed = queue.poll(2L, TimeUnit.SECONDS);
                } catch (InterruptedException ignored) {
                    consumed = null
                }
            }

            for (BlockingQueue<Message<JenkinsBuild>> consumer : consumers) {
                consumer.offer(consumed)
            }

            // "poison pill"
            if (consumed.type == Message.MessageType.END) {
                queue.offer(consumed); // leave it for the other consumers
                tellQuit()
                break;
            }
        }
        System.out.println("Broadcaster terminated.")
    }

}

/** Consumer */
abstract class AbstractConsoleOutput extends Thread {
    private static int buildsPerRow = 10
    private static int buildsPerSeparator = 5
    private static JenkinsBuild lastState
    private static JenkinsBuild currentState

    private final BlockingQueue<Message<JenkinsBuild>> queue
    private boolean quit;

    public AbstractConsoleOutput(final BlockingQueue<Message<JenkinsBuild>> queue, Configuration configuration) {
        this.queue = queue;
        this.quit = false;

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
            Message<JenkinsBuild> consumed = null
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
            if (currentDay.getDay() != currentTime.getDay()) {
                currentDay = currentTime
                counter = 1
                printNewDay(sdfDay.format(currentDay))
            }
            printCurrentState(sdfHour.format(currentTime), currentState)
            if ((counter % buildsPerRow) == 0) System.out.println();
            if ((counter % buildsPerSeparator) == 0 && (counter % buildsPerRow) != 0) printSeparator()
            counter++
            if (!lastState.equals(currentState)) {
                printStateChange(lastState, currentState, (currentTime.getTime() - lastStateChangeDate.getTime()) / (1000 * 60))
                lastState = currentState;
                lastStateChangeDate = currentTime
            }
        }
        printTerminated()
    }

    protected abstract void printStartUp(String currentDay)

    protected abstract void printNewDay(String newDay)

    protected abstract void printCurrentState(String currentTime, JenkinsBuild currentState)

    protected abstract void printSeparator()

    protected abstract void printStateChange(JenkinsBuild lastState, JenkinsBuild currentState, double timeInMinutes)

    protected abstract void printTerminated()

}

class ConsoleOutput extends AbstractConsoleOutput {

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

/** Consumer */
class JansiOutput extends AbstractConsoleOutput {
    private static Logger log = LoggerFactory.getLogger(JansiOutput.class)

    public JansiOutput(BlockingQueue<Message<JenkinsBuild>> queue, Configuration configuration) {
        super(queue, configuration)
        AnsiConsole.systemInstall();
    }

    @Override
    protected void printStartUp(String currentDay) {
        System.out.println(ansi().bg(BLACK).fg(WHITE).a(currentDay))
    }

    @Override
    protected void printNewDay(String newDay) {
        System.out.println(ansi().bg(BLACK).fg(WHITE).a("\n\n" + sdfDay.format(currentDay)).reset())
    }

    @Override
    protected void printCurrentState(String currentTime, JenkinsBuild currentState) {
        def translated;
        switch (currentState.c) {
            case Color.GREEN:
                translated = GREEN;
                break;
            case Color.YELLOW:
                translated = YELLOW;
                break;
            case Color.RED:
                translated = RED;
                break;
            case Color.WHITE:
                translated = WHITE;
                break;
            case Color.CYAN:
                translated = CYAN;
                break;
            case Color.BLUE:
                translated = BLUE;
                break;
            default:
                translated = DEFAULT
        }
        def fg = BLACK;
        if (translated == BLUE) {
            fg = WHITE
        }

        System.out.print(ansi().bg(translated).fg(fg).a(currentTime).reset())
    }

    @Override
    protected void printSeparator() {
        System.out.print(ansi().bg(BLACK).a(" ").reset())
    }

    @Override
    protected void printStateChange(JenkinsBuild lastState, JenkinsBuild currentState, double timeInMinutes) {
        log.info("state change: $lastState -> $currentState, $lastState lasted for " + timeInMinutes + " minutes");
    }

    @Override
    protected void printTerminated() {
        AnsiConsole.systemUninstall();
        System.out.println("Jansi Console terminated.")
    }
}

/** Consumer */
class Blink1Output extends Thread {
    private static int blinkFadeTimeMillis = 1000
    private static JenkinsBuild lastState
    private static JenkinsBuild currentState

    private final BlockingQueue<Message<JenkinsBuild>> queue
    private boolean quit;

    public Blink1Output(final BlockingQueue<Message<JenkinsBuild>> queue, Configuration configuration) {
        this.queue = queue;
        this.quit = false;

        try {
            blinkFadeTimeMillis = configuration.getInt("blinkFadeTimeMillis", 1000)
        } catch (NumberFormatException ignored) {
            blinkFadeTimeMillis = 1000
        }
    }

    public void tellQuit() {
        this.quit = true
    }

    @Override
    public void run() {
        lastState = JenkinsBuild.green
        currentState = JenkinsBuild.green

        Blink1 blink1 = new Blink1();

        Message<JenkinsBuild> consumed
        def closure = { quit = true }
        while (!quit) {
            try {
                consumed = queue.poll(2L, TimeUnit.SECONDS);
            } catch (InterruptedException ignored) {
                consumed = null
            }

            // "poison pill"
            if (consumed != null && consumed.type == Message.MessageType.END) {
                queue.offer(consumed); // leave it for the other consumers
                tellQuit()
                break;
            }
            currentState = consumed != null ? consumed.message : currentState
            Color col = currentState.c
            if (col == Color.yellow) {
                col = Color.orange
            }
            blink1.enumerate();

            if (blink1.getCount() > 0) {
                if (currentState.blinking) {
                    blink1.open();
                    blink1.fadeToRGB(blinkFadeTimeMillis, Color.black);
                    blink1.close();
                    sleep(blinkFadeTimeMillis * 3, closure)
                    if (quit) {
                        break
                    }
                    blink1.open();
                    blink1.fadeToRGB(blinkFadeTimeMillis, col);
                    blink1.close();
                    sleep(blinkFadeTimeMillis * 3, closure)
                    if (quit) {
                        break
                    }
                } else {
                    blink1.open();
                    blink1.fadeToRGB(blinkFadeTimeMillis, col);
                    blink1.close();
                }
            }

        }
        if (blink1.count > 0) {
            blink1.open()
            blink1.setRGB(Color.black)
            blink1.close()
        }
        System.out.println("blink(1) Output terminated.")
    }
}

enum JenkinsBuild {
    green(Color.GREEN, "G", false),
    green_anim(Color.GREEN, "G", true),
    yellow(Color.YELLOW, "Y", false),
    yellow_anim(Color.YELLOW, "Y", true),
    red(Color.RED, "R", false),
    red_anim(Color.RED, "R", true),
    gray(Color.WHITE, "A", false),
    gray_anim(Color.WHITE, "A", true),
    exception(Color.BLUE, "E", false),
    missing(Color.CYAN, "M", false)

    public Color c;
    public String s;
    public boolean blinking

    private JenkinsBuild(Color c, String s, boolean blinking) {
        this.c = c;
        this.s = s;
        this.blinking = blinking
    }
}

public class Message<E> {
    enum MessageType {
        STANDARD, END
    }

    private final MessageType type;
    private final E message;

    public Message(final E message) {
        this(message, MessageType.STANDARD);
    }

    public Message(final E message, final MessageType type) {
        this.message = message;
        this.type = type;
    }

    public MessageType getType() {
        return type;
    }

    public E getMessage() {
        return message;
    }
}
