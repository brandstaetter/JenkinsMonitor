JenkinsMonitor
==============

A modular threaded monitor application for jenkins jobs


How to run:

* clone this repo
* adapt the `gradle.properties` and `runme.bat` to point to your correct JDK path
* create a `jenkins_monitor_settings.properties` where you set the required properties and can overwrite some default values (see `src/main/resources/jenkins_monitor_settings.properties`)
* execute `runme.bat`


Notes:

If you want to use a [blink(1) USB Dongle](http://thingm.com/products/blink-1/), you need to use a 32 bit Java version.

If you have suggestions, feel free to file an issue or submit a pull request!
