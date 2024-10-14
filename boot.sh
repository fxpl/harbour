#!/usr/bin/bash
java -XX:+UseSerialGC -Xint -XX:+PerfDisableSharedMem -XX:+DisableAttachMechanism -XX:-UsePerfData -XX:-CreateCoredumpOnCrash -XX:-DumpReplayDataOnError -Xms64m -Xmx64m -cp classes:lib/argparse4j-0.9.0.jar:lib/snakeyaml-2.3.jar Boot $@
