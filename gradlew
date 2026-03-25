#!/bin/sh
APP_HOME=$( cd "${0%[/\\]*}" > /dev/null && pwd -P ) || exit
CLASSPATH=$APP_HOME/gradle/wrapper/gradle-wrapper.jar

# Determine the Java command to use to start the JVM.
if [ -n "$JAVA_HOME" ] ; then
    JAVACMD=$JAVA_HOME/bin/java
else
    JAVACMD=$( which java ) || { echo "ERROR: JAVA_HOME is not set and no 'java' command found"; exit 1; }
fi

exec "$JAVACMD" \
    -Xmx64m -Xms64m \
    -classpath "$CLASSPATH" \
    org.gradle.wrapper.GradleWrapperMain \
    "$@"
