package net.brandstaetter.jenkinsmonitor

import groovy.json.JsonSlurper
import net.brandstaetter.jenkinsmonitor.output.*
import org.apache.commons.configuration.Configuration
import org.apache.commons.configuration.PropertiesConfiguration
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.awt.*
import java.util.List
import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

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
        if (configuration.getBoolean('runPi', false)) {
            final BlockingQueue<Message<JenkinsBuild>> queueForPi = new LinkedBlockingQueue<Message<JenkinsBuild>>();
            PiOutput piOutputThread = new PiOutput(queueForPi, configuration)
            broadcaster.addConsumer(queueForPi)
            piOutputThread.start()
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
