import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
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

public class Boot {
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
            processBuilder.start().waitFor();
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
        parser.addArgument("--debug")
            .setDefault(false)
            .action(Arguments.storeTrue())
            .help("Don't send stderr/stdout to file on invoke");
        parser.addArgument("--dry")
            .setDefault(false)
            .action(Arguments.storeTrue())
            .help("Dry run");
        // Override YCSB parameters
        parser.addArgument("--operationcount")
            .help("Override operationcount");
        parser.addArgument("--warmup.operationcount")
            .help("Override warmup.operationcount");
        parser.addArgument("--warmup.iterations")
            .help("Override warmup.iterations");
        parser.addArgument("--target")
            .help("Override target");

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
        String wrapper = config.getOrDefault("wrapper", "");
        String cassandra_java = config.get("cassandra_jdk_path") + "/bin/java";
        String cassandra_gc_short_name = config.get("cassandra_gc_short_name");
        String cassandra_gc_options = config.get("cassandra_gc_options");
        String cassandra_gc_log_level = config.get("cassandra_gc_log_level");
        String cassandra_gc_heap = config.get("cassandra_gc_heap");
        String cassandra_vm_options = config.get("cassandra_vm_options");
        String jvm_mitigation = config.get("jvm_mitigation");

        String ycsb_java = config.get("cassandra_jdk_path");
        String ycsb_gc_short_name = config.get("ycsb_gc_short_name");
        String ycsb_gc_options = config.get("ycsb_gc_options");
        String ycsb_gc_log_level = config.get("ycsb_gc_log_level");
        String ycsb_gc_heap = config.get("ycsb_gc_heap");
        String ycsb_vm_options = config.get("ycsb_vm_options");

        Map<String, String> workload = getMap(ns.getString("workload"), "workload");
        String ycsb_workload_path = workload.get("ycsb_workload_path");
        String ycsb_workload = workload.get("ycsb_workload");
        String ycsb_target = ns.getString("target") != null ? ns.getString("target") : workload.get("ycsb_target");
        String ycsb_threads = workload.get("ycsb_threads");
        String ycsb_hosts = workload.get("ycsb_hosts");
        String ycsb_cassandra_username = workload.get("ycsb_cassandra_username");
        String ycsb_cassandra_password = workload.get("ycsb_cassandra_password");

        String YCSB_BASE_INVOKE = String.join(" ", wrapper, "./bundles/ycsb-0.17.0/bin/ycsb.sh");
        String YCSB_ARGS = String.join(" ", "-s", "-P", String.join("", ycsb_workload_path, ycsb_workload), "-threads", ycsb_threads, "-p", String.join("", "cassandra.username=", ycsb_cassandra_username), "-p", String.join("", "cassandra.password=", ycsb_cassandra_password), "-p", String.join("", "hosts=", ycsb_hosts));

        // ycsb_target is optional
        String logStrTarget = "";
        if (ycsb_target != null) {
            logStrTarget = "_target"+ycsb_target;
        }

        // Calculate log location
        final String logPath = "log/"+name+"/"+type+"/"+ycsb_workload+"";
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

        YCSB_ARGS = String.join(" ", YCSB_ARGS, "-p", "hdrhistogram.output.path="+logFull+".hdr");
        String YCSB_JAVA_OPTS = String.join(" ", ycsb_gc_options, "-Xms"+ycsb_gc_heap, "-Xmx"+ycsb_gc_heap, ycsb_gc_log_str, ycsb_vm_options);

        for (String argOverride : Arrays.asList("operationcount", "warmup.operationcount", "warmup.iterations")) {
            if (ns.getString(argOverride) != null) {
                YCSB_ARGS = String.join(" ", YCSB_ARGS, "-p " + argOverride+ "=" + ns.getString(argOverride));
            }
        }

        // If target exists it must be last!
        if (ycsb_target != null) {
            YCSB_ARGS = String.join(" ", YCSB_ARGS, "-p", "measurement.interval=both", "-target", ycsb_target);
        } else {
            YCSB_ARGS = String.join(" ", YCSB_ARGS, "-p", "measurement.interval=op");
        }

        Map<String, String> environment = new HashMap<>(Map.ofEntries(
            entry("YCSB_BASE_INVOKE", YCSB_BASE_INVOKE),
            entry("YCSB_JAVA_HOME", ycsb_java),
            entry("YCSB_JAVA_OPTS", YCSB_JAVA_OPTS),
            entry("YCSB_ARGS", YCSB_ARGS)
        ));

        String yachtInvoke = String.join(" ", wrapper, cassandra_java,
            cassandra_gc_options, cassandra_gc_log_str, cassandra_vm_options,
            jvm_mitigation, classpath, "Yacht", type == Type.MULTI ? "--multi" : "");

        invoke("rm -rf ./db", null);
        String dbName = "db_"+ycsb_workload;

        // Before invoke a real run we should either
        // copy pre_inited db or init on first use
        final boolean needs_init = !Files.isDirectory(Paths.get("./" + dbName));
        if (needs_init) {
            System.out.println("Looks like this is the first time you run this workload");
            String prev_ycsb_args = environment.get("YCSB_ARGS");
            String ycsb_args_no_target = prev_ycsb_args.substring(0, prev_ycsb_args.indexOf("-target"));
            System.out.println(dbName + " does not exists, creating can take a while...");
            environment.put("YCSB_ARGS", ycsb_args_no_target);
            invoke("mkdir -p db", null);
            System.out.println("Creating the prepopulated db...");
            String redirect = ns.getBoolean("debug") ? "" : "&> /dev/null";
            invoke(yachtInvoke + " --init " + redirect, environment);
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
