//
// Built on Wed Jan 29 12:39:47 CET 2014 by logback-translator
// For more information on configuration files in Groovy
// please see http://logback.qos.ch/manual/groovy.html

// For assistance related to this tool or configuration files
// in general, please contact the logback user mailing list at
//    http://qos.ch/mailman/listinfo/logback-user

// For professional support please see
//   http://www.qos.ch/shop/products/professionalSupport

import ch.qos.logback.classic.encoder.PatternLayoutEncoder
import ch.qos.logback.core.ConsoleAppender
import ch.qos.logback.core.FileAppender
import ch.qos.logback.core.rolling.FixedWindowRollingPolicy
import ch.qos.logback.core.rolling.RollingFileAppender
import ch.qos.logback.core.rolling.SizeBasedTriggeringPolicy

import static ch.qos.logback.classic.Level.ERROR
import static ch.qos.logback.classic.Level.INFO

def USER_HOME = System.getProperty("user.home")

appender("STDOUT", ConsoleAppender) {
    encoder(PatternLayoutEncoder) {
        pattern = "%msg%n"
    }
}
appender("logging-file", FileAppender) {
    file = "${USER_HOME}/jenkins-monitor.log"
    encoder(PatternLayoutEncoder) {
        pattern = "%date;%msg%n"
    }
}
logger("net.brandstaetter", INFO)

root(ERROR, ["logging-file"])