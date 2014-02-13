JenkinsMonitor
==============

[![Build Status](https://travis-ci.org/brandstaetter/JenkinsMonitor.png?branch=master)](https://travis-ci.org/brandstaetter/JenkinsMonitor)

A modular threaded monitor application for jenkins jobs


How to run:

* clone this repo
* adapt `runme.bat` to point to your correct JDK path (32 bit if using blink(1), otherwise it does not matter)
* create a `jenkins_monitor_settings.properties` in your home directory where you set the required properties
 and can overwrite some default values (see `src/main/resources/jenkins_monitor_settings.properties`)

 ```
 mainUrl= #< your jenkins url >
 basicAuthentication= #< the basic auth string >
 interestingBuildsList=['build_name_1'\,'build_name_2'\,'build_name_3'] #the list of builds to watch, separated by "\,"
 animIfBuildingList=['build_name_1'\,'build_name_2'] #used e.g. for blink(1) to pulse LED for these jobs while building
 ```

 * Building a basic auth string:
 Take your credentials (<<user>>:<<passwort>>) and encode them with base64 (e.g. http://www.base64encode.org/).
 Example: "myuser:mypassword" -> bXl1c2VyOm15cGFzc3dvcmQ=

* execute `runme.bat` or `gradlew install` and `build/install/JenkinsMonitor/bin/JenkinsMonitor`


Notes:

If you want to use a [blink(1) USB Dongle](http://thingm.com/products/blink-1/), you need to use a 32 bit Java version.

If you want to run it on a RaspberryPi, you need to install the native library wiringPi (http://wiringpi.com/download-and-install/) and run the monitor with sudo. The both used Pins are GPIO 23/24 (numbering scheme from http://elinux.org/RPi_Low-level_peripherals).

If you have suggestions, feel free to file an issue or submit a pull request!
