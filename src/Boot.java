/* Copyright (c) 2024 Uppsala University
 */
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.io.IOException;
import org.yaml.snakeyaml.Yaml;

import net.sourceforge.argparse4j.impl.Arguments;
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

enum LatencyMeasure {
    SEPARATE("separate"),
    ALL("all");
    private final String val;

    private LatencyMeasure(String val) {
        this.val = val;
    }

    @Override
    public String toString() {
        return this.val;
    }
}

public class Boot {
    private static List<Process> processes = new ArrayList<>();

    final static private Yaml yaml = new Yaml();
    static private boolean dry = false;
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
        if (str.equals(Type.MULTI.toString())) {
            return Type.MULTI;
        } else if (str.equals(Type.SINGLE.toString())) {
            return Type.SINGLE;
        }
        throw new RuntimeException();
    }

    static void invoke(String bash, Map<String, String> environment) {
        if (dry) return;
        try {
            ProcessBuilder processBuilder = new ProcessBuilder("bash", "-c", bash);
            Map<String, String> processEnvironment = processBuilder.environment();
            if (environment != null) environment.forEach(processEnvironment::put);
            processBuilder.inheritIO();
            Process process = processBuilder.start();
            processes.add(process);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    static String getNextI(String logFull) {
        for (int i = 1; i < Integer.MAX_VALUE; i++) {
            String check = logFull+Integer.toString(i)+".log";
            if (Files.notExists(Paths.get(check))) {
                return Integer.toString(i);
            }
        }
        throw new RuntimeException("Could not determine i");
    }

    static public String ensureValidHeapString(String str) {
        if (str.length() < 2) {
            throw new RuntimeException("Invalid heap");
        }
        String numeric = str.substring(0, str.length()-1);
        try {
            Long.parseUnsignedLong(numeric);
        } catch (NumberFormatException e) {
            throw new RuntimeException("Invalid heap");
        }
        char lastChar = Character.toUpperCase(str.charAt(str.length()-1));
        if (lastChar == 'K' || lastChar == 'M' || lastChar == 'G') {
            return numeric+lastChar;
        }
        throw new RuntimeException("Invalid heap");
    }

    public static void main(String[] args) {
        ArgumentParser parser = ArgumentParsers.newFor("Boot").build()
            .defaultHelp(true)
            .description("Boot Cassandra and YCSB");
        parser.addArgument("--config")
            .required(true)
            .help("System configuration");
        parser.addArgument("--latencyMeasure")
            .required(false)
            .help("All operations in one metric or separate");
        parser.addArgument("--workload")
            .required(true)
            .help("Workload to run");
        parser.addArgument("--debug")
            .setDefault(false)
            .action(Arguments.storeTrue())
            .help("Don't send stderr/stdout to file on invoke");
        parser.addArgument("--dry")
            .setDefault(false)
            .action(Arguments.storeTrue())
            .help("Dry run");
        parser.addArgument("--cassandraHeap")
            .help("Override Cassandra heap");
        parser.addArgument("--ycsbHeap")
            .help("Override YCSB heap");
        parser.addArgument("--threads")
            .help("Override YCSB threads");

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Shutdown hook triggered. Terminating child processes...");
            processes.forEach(Process::destroy);
            for (Process process : processes) {
                try {
                    process.waitFor();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }));

        List<String> overrideParamsCassandra = Arrays.asList("concurrent_writes", "concurrent_counter_writes", "concurrent_materialized_view_writes", "concurrent_compactors");
        for (String argOverride : overrideParamsCassandra) {
            parser.addArgument("--"+argOverride)
                .help("Override" + argOverride);
        }

        // Override YCSB parameters
        List<String> overrideParams = Arrays.asList("operationcount", "warmup.operationcount", "warmup.iterations", "warmup.gc", "warmup.ignore");
        for (String argOverride : overrideParams) {
            parser.addArgument("--"+argOverride)
                .help("Override " + argOverride);
        }

        Namespace ns = null;
        try {
            ns = parser.parseArgs(args);
        } catch (ArgumentParserException e) {
            parser.handleError(e);
            System.exit(1);
        }
        dry = ns.getBoolean("dry");

        Map<String, String> config = getMap(ns.getString("config"), "harbour");
        Type type = parseType(config.get("type"));
        String name = config.get("name");
        String cassandra_wrapper = config.getOrDefault("cassandra_wrapper", "");
        String cassandra_java = config.get("cassandra_jdk_path") + "/bin/java";
        String cassandra_gc_short_name = config.get("cassandra_gc_short_name");
        String cassandra_gc_options = config.get("cassandra_gc_options");
        String cassandra_gc_log_level = config.get("cassandra_gc_log_level");
        String cassandra_gc_heap = ensureValidHeapString(ns.getString("cassandraHeap") != null ? ns.getString("cassandraHeap") : config.get("cassandra_gc_heap"));
        String cassandra_vm_options = config.get("cassandra_vm_options");
        String jvm_mitigation = config.get("jvm_mitigation");

        String ycsb_wrapper = config.getOrDefault("ycsb_wrapper", "");
        String ycsb_java = config.get("cassandra_jdk_path");
        String ycsb_gc_short_name = config.get("ycsb_gc_short_name");
        String ycsb_gc_options = config.get("ycsb_gc_options");
        String ycsb_gc_log_level = config.get("ycsb_gc_log_level");
        String ycsb_gc_heap = ensureValidHeapString(ns.getString("ycsbHeap") != null ? ns.getString("ycsbHeap") : config.get("ycsb_gc_heap"));
        String ycsb_vm_options = config.get("ycsb_vm_options");

        Map<String, String> workload = getMap(ns.getString("workload"), "workload");
        String target = ns.getString("target") != null ? ns.getString("target") : workload.get("target");
        String threads = ns.getString("threads") != null ? ns.getString("threads") : workload.get("threads");

        String latencyMeasureString = ns.getString("latencyMeasure") != null ? ns.getString("latencyMeasure") : workload.getOrDefault("latencyMeasure", LatencyMeasure.ALL.toString());
        LatencyMeasure latencyMeasure = null;
        if (latencyMeasureString.equals(LatencyMeasure.SEPARATE.toString())) {
            latencyMeasure = LatencyMeasure.SEPARATE;
        } else if (latencyMeasureString.equals(LatencyMeasure.ALL.toString())) {
            latencyMeasure = LatencyMeasure.ALL;
        } else {
            throw new RuntimeException("Invalid latencyMeasure");
        }

        String YCSB_BASE_INVOKE = String.join(" ", ycsb_wrapper, "./bundles/ycsb-0.17.0/bin/ycsb.sh");
        String YCSB_ARGS = String.join(" ",
            "-s", "-P", workload.get("workload_path")+workload.get("workloadYCSB"),
            "-threads", threads, "-p", "cassandra.username="+workload.get("cassandra_username"),
            "-p", "cassandra.password="+workload.get("cassandra_password"),
            "-p", "hosts="+workload.get("hosts"),
            "-p", "latencyMeasure="+latencyMeasure.toString()
            );

        // target is optional
        String logStrTarget = "";
        if (target != null) {
            logStrTarget = "_target"+target;
        }

        // Calculate log location
        final String logPath = "log/"+name+"/"+type+"/"+workload.get("workloadYCSB")+"";
        final String logSubPrefix = "server"+cassandra_gc_short_name+"_"+cassandra_gc_heap+"_client"+ycsb_gc_short_name+"_"+ycsb_gc_heap+logStrTarget+".";
        final String logPrefix = logSubPrefix + getNextI(logPath + "/" + logSubPrefix);
        final String logFull = logPath + "/" + logPrefix;
        try {
            Files.createDirectories(Paths.get(logPath));
        } catch (IOException e) {
            e.printStackTrace();
        }

        String cassandra_gc_log_str = cassandra_gc_log_level != null ? "-Xlog:"+cassandra_gc_log_level+":file=" + logFull + ".server.gc::filesize=0" : "";
        String ycsb_gc_log_str = ycsb_gc_log_level != null ? "-Xlog:"+ycsb_gc_log_level+":file=" + logFull + ".client.gc::filesize=0" : "";

        YCSB_ARGS = String.join(" ", YCSB_ARGS, "-p", "hdrhistogram.output.path="+logFull);
        String YCSB_JAVA_OPTS = String.join(" ", ycsb_gc_options, "-Xms"+ycsb_gc_heap, "-Xmx"+ycsb_gc_heap, ycsb_gc_log_str, ycsb_vm_options);

        for (String argOverride : overrideParams) {
            if (ns.getString(argOverride) != null) {
                YCSB_ARGS = String.join(" ", YCSB_ARGS, "-p " + argOverride+ "=" + ns.getString(argOverride));
            } else if (workload.get(argOverride) != null) {
                YCSB_ARGS = String.join(" ", YCSB_ARGS, "-p " + argOverride+ "=" + workload.get(argOverride));
            }
        }

        // If target exists it must be last!
        if (target != null) {
            YCSB_ARGS = String.join(" ", YCSB_ARGS, "-p", "measurement.interval=both", "-target", target);
        } else {
            YCSB_ARGS = String.join(" ", YCSB_ARGS, "-p", "measurement.interval=op");
        }

        Map<String, String> environment = new HashMap<>(Map.ofEntries(
            entry("YCSB_BASE_INVOKE", YCSB_BASE_INVOKE),
            entry("YCSB_JAVA_HOME", ycsb_java),
            entry("YCSB_JAVA_OPTS", YCSB_JAVA_OPTS),
            entry("YCSB_ARGS", YCSB_ARGS)
        ));

        String yachtInvoke = String.join(" ", cassandra_wrapper, cassandra_java,
            cassandra_gc_options, "-Xms"+cassandra_gc_heap, "-Xmx"+cassandra_gc_heap, cassandra_gc_log_str, cassandra_vm_options,
            jvm_mitigation, classpath, "Yacht", type == Type.MULTI ? "--multi" : "");
        for (String argOverride : overrideParamsCassandra) {
            if (ns.getString(argOverride) != null) {
                yachtInvoke = String.join(" ", yachtInvoke, "--"+argOverride+"="+ns.getString(argOverride));
            } else if (workload.get(argOverride) != null) {
                yachtInvoke = String.join(" ", yachtInvoke, "--"+argOverride+"="+workload.get(argOverride));
            }
        }

        invoke("rm -rf ./db", null);
        String dbName = "db_"+workload.get("workloadYCSB");
        System.out.println(dbName);
        // Before invoke a real run we should either
        // copy pre_inited db or init on first use
        final boolean needs_init = !Files.isDirectory(Paths.get("./" + dbName));
        if (needs_init) {
            System.out.println("Looks like this is the first time you run this workload");
            System.out.println(dbName + " does not exists, creating can take a while...");
            String prev_ycsb_args = environment.get("YCSB_ARGS");
            if (prev_ycsb_args.contains("-target")) {
                String ycsb_args_no_target = prev_ycsb_args.substring(0, prev_ycsb_args.indexOf("-target"));
                environment.put("YCSB_ARGS", ycsb_args_no_target);
            }
            invoke("mkdir -p db", null);
            System.out.println("Creating the prepopulated db...");
            invoke(yachtInvoke + " --init ", environment);
            environment.put("YCSB_ARGS", prev_ycsb_args);

            System.out.println("Caching db for future runs");
            invoke("cp -r ./db ./" + dbName, null);
        } else {
            System.out.println("Using cached database");
            invoke("cp -r ./" + dbName + " ./db", null);
        }

        System.out.println("Invoking benchmark");
        if (!ns.getBoolean("debug")) {
            yachtInvoke = yachtInvoke +  "&> " + logFull+".log";
        }

        invoke(yachtInvoke, environment);
    }
}
