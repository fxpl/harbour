#!/usr/bin/bash

echo "HDR_FILE ${HDR_FILE}"
export YCSB_ARGS="-s -P ./bundles/ycsb-0.17.0/workloads/workload-default-dacapo -threads 32 -p cassandra.username=cassandra -p cassandra.password=cassandra -p hosts=127.0.0.1 ${TARGET} -p measurement.interval=both ${HDR_FILE}"

export JVM_OPTS=${SINGLE_JVM_OPTS}
export JVM_OPTS=${CASSANDRA_JVM_OPTS}
export JAVA_OPTS=${YCSB_JVM_OPTS}

single_flag=false
multi_flag=false
compile_flag=false
# Parse arguments
for arg in "$@"; do
    case $arg in
        --single)
            single_flag=true
            ;;
        --multi)
            multi_flag=true
            ;;
        --compile)
            compile_flag=true
            ;;
    esac
done

if [[ "$compile_flag" == true ]]; then
    echo "Compiling the Yacht"
    #make compile
fi

function prepare_db() {
  rm -rf db/
  cp -r db_pre_inited db
}

function single_host() {
  prepare_db
  LD_PRELOAD=/usr/lib/x86_64-linux-gnu/libjemalloc.so.2 /usr/bin/time --verbose java ${JVM_OPTS} -Djava.security.manager=allow --enable-native-access=ALL-UNNAMED -Djdk.attach.allowAttachSelf=true --add-exports java.base/jdk.internal.misc=ALL-UNNAMED --add-exports java.base/jdk.internal.ref=ALL-UNNAMED --add-exports java.base/sun.nio.ch=ALL-UNNAMED --add-exports java.management.rmi/com.sun.jmx.remote.internal.rmi=ALL-UNNAMED --add-exports java.rmi/sun.rmi.registry=ALL-UNNAMED --add-exports java.rmi/sun.rmi.server=ALL-UNNAMED --add-exports java.rmi/sun.rmi.transport.tcp=ALL-UNNAMED --add-exports java.sql/java.sql=ALL-UNNAMED --add-exports java.base/java.lang.ref=ALL-UNNAMED --add-exports java.base/java.lang.reflect=ALL-UNNAMED --add-exports jdk.unsupported/sun.misc=ALL-UNNAMED --add-exports jdk.compiler/com.sun.tools.javac.file=ALL-UNNAMED --add-opens java.base/java.lang.module=ALL-UNNAMED --add-opens java.base/java.net=ALL-UNNAMED --add-opens java.base/jdk.internal.loader=ALL-UNNAMED --add-opens java.base/jdk.internal.ref=ALL-UNNAMED --add-opens java.base/jdk.internal.reflect=ALL-UNNAMED --add-opens java.base/jdk.internal.math=ALL-UNNAMED --add-opens java.base/jdk.internal.module=ALL-UNNAMED  --add-opens jdk.management/com.sun.management=ALL-UNNAMED --add-opens java.base/sun.nio.ch=ALL-UNNAMED --add-opens java.base/java.io=ALL-UNNAMED --add-opens java.base/java.nio=ALL-UNNAMED --add-opens java.base/java.util.concurrent=ALL-UNNAMED --add-opens java.base/java.util=ALL-UNNAMED --add-opens java.base/java.util.concurrent.atomic=ALL-UNNAMED --add-opens java.base/java.lang=ALL-UNNAMED --add-opens java.base/java.math=ALL-UNNAMED --add-opens java.base/java.lang.reflect=ALL-UNNAMED --add-opens java.base/java.net=ALL-UNNAMED --add-opens java.rmi/sun.rmi.transport.tcp=ALL-UNNAMED -Djava.security.manager=allow -cp classes:lib/argparse4j-0.9.0.jar -Dcassandra.libjemalloc=/usr/lib/x86_64-linux-gnu/libjemalloc.so.2 -javaagent:./bundles/apache-cassandra-5.0.1/lib/jamm-0.4.0.jar -Djava.library.path=./classes:./bundles/apache-cassandra-5.0.1/lib/sigar-bin -XX:CompileCommandFile=./bundles/apache-cassandra-5.0.1/conf/hotspot_compiler -ea -da:net.openhft... -XX:+UseThreadPriorities -XX:+HeapDumpOnOutOfMemoryError -Xss256k -XX:+AlwaysPreTouch -XX:+UseTLAB -XX:+ResizeTLAB -XX:+UseNUMA -XX:+PerfDisableSharedMem -Djava.net.preferIPv4Stack=true -Dchronicle.analytics.disable=true Yacht
}

function mutli_host() {
  prepare_db
  export JVM_OPTS="${JVM_OPTS} -Dcassandra.storagedir=./db/cassandra-storage -Dcassandra.logdir=./db/cassandra-log"
  LD_PRELOAD=/usr/lib/x86_64-linux-gnu/libjemalloc.so.2 /usr/bin/time --verbose java ${JVM_OPTS} -Djava.security.manager=allow --enable-native-access=ALL-UNNAMED -Djdk.attach.allowAttachSelf=true --add-exports java.base/jdk.internal.misc=ALL-UNNAMED --add-exports java.base/jdk.internal.ref=ALL-UNNAMED --add-exports java.base/sun.nio.ch=ALL-UNNAMED --add-exports java.management.rmi/com.sun.jmx.remote.internal.rmi=ALL-UNNAMED --add-exports java.rmi/sun.rmi.registry=ALL-UNNAMED --add-exports java.rmi/sun.rmi.server=ALL-UNNAMED --add-exports java.rmi/sun.rmi.transport.tcp=ALL-UNNAMED --add-exports java.sql/java.sql=ALL-UNNAMED --add-exports java.base/java.lang.ref=ALL-UNNAMED --add-exports java.base/java.lang.reflect=ALL-UNNAMED --add-exports jdk.unsupported/sun.misc=ALL-UNNAMED --add-exports jdk.compiler/com.sun.tools.javac.file=ALL-UNNAMED --add-opens java.base/java.lang.module=ALL-UNNAMED --add-opens java.base/java.net=ALL-UNNAMED --add-opens java.base/jdk.internal.loader=ALL-UNNAMED --add-opens java.base/jdk.internal.ref=ALL-UNNAMED --add-opens java.base/jdk.internal.reflect=ALL-UNNAMED --add-opens java.base/jdk.internal.math=ALL-UNNAMED --add-opens java.base/jdk.internal.module=ALL-UNNAMED  --add-opens jdk.management/com.sun.management=ALL-UNNAMED --add-opens java.base/sun.nio.ch=ALL-UNNAMED --add-opens java.base/java.io=ALL-UNNAMED --add-opens java.base/java.nio=ALL-UNNAMED --add-opens java.base/java.util.concurrent=ALL-UNNAMED --add-opens java.base/java.util=ALL-UNNAMED --add-opens java.base/java.util.concurrent.atomic=ALL-UNNAMED --add-opens java.base/java.lang=ALL-UNNAMED --add-opens java.base/java.math=ALL-UNNAMED --add-opens java.base/java.lang.reflect=ALL-UNNAMED --add-opens java.base/java.net=ALL-UNNAMED --add-opens java.rmi/sun.rmi.transport.tcp=ALL-UNNAMED -Djava.security.manager=allow -cp classes:lib/argparse4j-0.9.0.jar -Dcassandra.libjemalloc=/usr/lib/x86_64-linux-gnu/libjemalloc.so.2 -javaagent:./bundles/apache-cassandra-5.0.1/lib/jamm-0.4.0.jar -Djava.library.path=./classes:./bundles/apache-cassandra-5.0.1/lib/sigar-bin -XX:CompileCommandFile=./bundles/apache-cassandra-5.0.1/conf/hotspot_compiler -ea -da:net.openhft... -XX:+UseThreadPriorities -XX:+HeapDumpOnOutOfMemoryError -Xss256k -XX:+AlwaysPreTouch -XX:+UseTLAB -XX:+ResizeTLAB -XX:+UseNUMA -XX:+PerfDisableSharedMem -Djava.net.preferIPv4Stack=true -Dchronicle.analytics.disable=true Yacht --multi
}

if [[ "$single_flag" == true && "$multi_flag" == true ]]; then
    echo "Please supply either --single or --multi"
    exit 1
elif [[ "$single_flag" == true ]]; then
    single_host
elif [[ "$multi_flag" == true ]]; then
    mutli_host
else
    echo "Please supply either --single or --multi"
    exit 1
fi

rm -rf db/