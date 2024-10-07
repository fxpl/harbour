mkdir -p log_default_dacapo/

LOG="log_default_dacapo/multi"
TARGET_LEVEL="80000"

mkdir -p ${LOG}
for i in {1..5}; do
  for gcClient in "G1"; do
    for gcServer in "G1"; do
      for heapClient in "32g"; do
        for heapServer in "32g"; do
          LOG_PREFIX="server${gcServer}_${heapServer}_client${gcClient}_${heapClient}_target${TARGET_LEVEL}.${i}"
          CASSANDRA_GC_LOG="-Xlog:gc:file=${LOG}/${LOG_PREFIX}.server.gc::filesize=0"
          YCSB_GC_LOG="-Xlog:gc:file=${LOG}/${LOG_PREFIX}.client.gc::filesize=0"
          CASSANDRA_JVM_OPTS="-XX:+Use${gcServer}GC -Xms${heapServer} -Xmx${heapServer} ${CASSANDRA_GC_LOG}" YCSB_JVM_OPTS="-XX:+Use${gcClient}GC -Xms${heapClient} -Xmx${heapClient} ${YCSB_GC_LOG}" TARGET="-target ${TARGET_LEVEL}" HDR_FILE="-p hdrhistogram.output.path=${LOG}/${LOG_PREFIX}.hdr" ./run.sh --multi &> ${LOG}/${LOG_PREFIX}.log
        done
      done
    done
  done
done
exit 0
LOG="log/single"
mkdir -p ${LOG}
for i in {1..5}; do
  for gc in "Z" "G1"; do
    for heap in "32g"; do
      LOG_PREFIX="${gc}_${heap}_target${TARGET_LEVEL}.${i}"
      SINGLE_GC_LOG="-Xlog:gc:file=${LOG}/${LOG_PREFIX}.gc::filesize=0"
      SINGLE_JVM_OPTS="-XX:+Use${gc}GC -Xms${heap} -Xmx${heap} ${SINGLE_GC_LOG}" TARGET="-target ${TARGET_LEVEL}" HDR_FILE="-p hdrhistogram.output.path=$LOG/${gc}_${heap}_target${TARGET_LEVEL}.${i}.hdr" ./run.sh --single &> ${LOG}/${LOG_PREFIX}.log
    done
  done
done
