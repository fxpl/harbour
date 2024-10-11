import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

import org.yaml.snakeyaml.Yaml;

import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;
import static java.util.Map.entry;
enum Type {
    MULTI("multi"),
    SINGLE("single");
    private final String val;

    private Type(String val) {
        this.val = val;
    }

    @Override
    public String toString() {
        return this.val;
    }
}

public class Boot {
    final static private Yaml yaml = new Yaml();
    final static private String classpath = "-cp classes:lib/argparse4j-0.9.0.jar";

    @SuppressWarnings("unchecked")
    static Map<String, String> getMap(String file, String base) {
        try (InputStream input = new FileInputStream(file)) {
            return (Map<String, String>) ((Map<String, Object>) yaml.load(input)).get(base);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    static Type parseType(String str) {
        System.out.println(str);
        if (str.equals(Type.MULTI.toString())) {
            return Type.MULTI;
        } else if (str.equals(Type.SINGLE.toString())) {
            return Type.SINGLE;
        }
        throw new RuntimeException();
    }

    static void invoke(String bash, Map<String, String> environment) {
        try {
            ProcessBuilder processBuilder = new ProcessBuilder("bash", "-c", bash);
            Map<String, String> processEnvironment = processBuilder.environment();
            environment.forEach(processEnvironment::put);
            processBuilder.inheritIO();
            processBuilder.start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        ArgumentParser parser = ArgumentParsers.newFor("Boot").build()
                .defaultHelp(true)
                .description("Boot Cassandra and YCSB");
        parser.addArgument("--config")
                .required(true)
                .help("System configuration");
        parser.addArgument("--workload")
                .required(true)
                .help("Workload to run");
        parser.addArgument("--i")
                .required(true)
                .help("What number should be assigned to log outputs?");

        Namespace ns = null;
        try {
            ns = parser.parseArgs(args);
        } catch (ArgumentParserException e) {
            parser.handleError(e);
            System.exit(1);
        }

        Map<String, String> config = getMap(ns.getString("config"), "harbour");
        Type type = parseType(config.get("type"));
        String name = config.get("name");
        String wrapper = config.getOrDefault("wrapper", "");
        String cassandra_java = config.get("cassandra_jdk_path") + "/bin/java";
        String cassandra_gc_options = config.get("cassandra_gc_options");
        String cassandra_vm_options = config.get("cassandra_vm_options");
        String jvm_mitigation = config.get("jvm_mitigation");

        String ycsb_java = config.get("cassandra_jdk_path") + "/bin/java";
        String ycsb_gc_options = config.get("ycsb_gc_options");

        Map<String, String> workload = getMap(ns.getString("workload"), "workload");
        String ycsb_workload_path = workload.get("ycsb_workload_path");
        String ycsb_workload = workload.get("ycsb_workload");
        String ycsb_target = workload.get("ycsb_target");
        String ycsb_threads = workload.get("ycsb_threads");
        String ycsb_hosts = workload.get("ycsb_hosts");
        String ycsb_cassandra_username = workload.get("ycsb_cassandra_username");
        String ycsb_cassandra_password = workload.get("ycsb_cassandra_password");

        // 1. Generate command to invoke
        String yachtInvoke = String.join(" ", wrapper, cassandra_java,
                cassandra_gc_options, cassandra_vm_options, jvm_mitigation, classpath, "Yacht");

        // ./bundles/ycsb-0.17.0/bin/ycsb.sh run cassandra-cql " + System.getenv("YCSB_ARGS")
        String YCSB_BASE_INVOKE = String.join(" ", wrapper, "./bundles/ycsb-0.17.0/bin/ycsb.sh");
        String YCSB_ARGS = String.join(" ", "-s", "-P", String.join("", ycsb_workload_path, ycsb_workload), "-threads", ycsb_threads, "-p", String.join("", "cassandra.username=", ycsb_cassandra_username), "-p", String.join("", "cassandra.password=", ycsb_cassandra_password), "-p", String.join("", "hosts=", ycsb_hosts));
        // ycsb_target is optional
        if (ycsb_target != null) {
            YCSB_ARGS = String.join(" ", YCSB_ARGS, "-target", ycsb_target, "-p", "measurement.interval=both");
        } else {
            YCSB_ARGS = String.join(" ", YCSB_ARGS, "-p", "measurement.interval=op");
        }
        // Should also add HDR_FILE info HDR_FILE="-p hdrhistogram.output.path=$LOG/${gc}_${heap}_target${TARGET_LEVEL}.${i}.hdr"
        String YCSB_HDR_FILE =  "";

        // 2. Generate environment variables to be set

        Map<String, String> environment = Map.ofEntries(
                entry("YCSB_BASE_INVOKE", YCSB_BASE_INVOKE),
                entry("YCSB_ARGS", YCSB_ARGS+" -p operationcount=10000"),
                entry("YCSB_HDR_FILE", YCSB_HDR_FILE)
        );

        // Calculate log location
        /*
        LOG_PREFIX="server${gcServer}_${heapServer}_client${gcClient}_${heapClient}_target${TARGET_LEVEL}.${i}"
        CASSANDRA_GC_LOG="-Xlog:gc:file=${LOG}/${LOG_PREFIX}.server.gc::filesize=0"
        YCSB_GC_LOG="-Xlog:gc:file=${LOG}/${LOG_PREFIX}.client.gc::filesize=0"
        CASSANDRA_JVM_OPTS="-XX:+Use${gcServer}GC -Xms${heapServer} -Xmx${heapServer} ${CASSANDRA_GC_LOG}"
            YCSB_JVM_OPTS="-XX:+Use${gcClient}GC -Xms${heapClient} -Xmx${heapClient} ${YCSB_GC_LOG}"
            TARGET="-target ${TARGET_LEVEL}" HDR_FILE="-p hdrhistogram.output.path=${LOG}/${LOG_PREFIX}.hdr"
        ./run.sh --multi &> ${LOG}/${LOG_PREFIX}.log
         */
        // 3. Invoke process and exit boot
        System.out.println(yachtInvoke);
        invoke(yachtInvoke, environment);
    }
}
