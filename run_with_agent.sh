#!/bin/bash

/Users/elias.jordan/code/alfred-jenkins/target/universal/stage/bin/alfred-jenkins \
  -java-home /Users/elias.jordan/graalvm-ce-java11-20.1.0/Contents/Home \
  -J-agentlib:native-image-agent=config-output-dir=/tmp/graal-config,config-write-period-secs=1,config-write-initial-delay-secs=1 \
  $@
