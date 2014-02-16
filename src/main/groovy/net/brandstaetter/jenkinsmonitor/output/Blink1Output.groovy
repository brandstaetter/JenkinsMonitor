package net.brandstaetter.jenkinsmonitor.output

import net.brandstaetter.jenkinsmonitor.JenkinsBuild
import net.brandstaetter.jenkinsmonitor.Message
import net.brandstaetter.jenkinsmonitor.ThreadedJenkinsMonitor
import org.apache.commons.configuration.Configuration
import thingm.blink1.Blink1

import java.awt.*
import java.util.concurrent.BlockingQueue
import java.util.concurrent.TimeUnit

/** Consumer */
class Blink1Output extends Thread {
    private static int blinkFadeTimeMillis = 1000
    private static JenkinsBuild lastState
    private static JenkinsBuild currentState

    private final BlockingQueue<Message<Map<String, JenkinsBuild>>> queue
    private final Configuration configuration
    private boolean quit;

    public Blink1Output(final BlockingQueue<Message<Map<String, JenkinsBuild>>> queue, Configuration configuration) {
        this.queue = queue;
        this.configuration = configuration
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

        Message<Map<String, JenkinsBuild>> consumed
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
            currentState = consumed != null ? ThreadedJenkinsMonitor.getSimpleResult(consumed.message, configuration) : currentState
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
