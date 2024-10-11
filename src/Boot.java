import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Map;

import org.yaml.snakeyaml.Yaml;

import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;

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
            return (Map<String, String>)((Map<String, Object>)yaml.load(input)).get(base);
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

    static void invoke(String bash) {
        try {
            ProcessBuilder processBuilder = new ProcessBuilder("bash", "-c", bash);
            processBuilder.environment().put("MY_VAR", "RINNI");
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
        String wrapper = config.get("wrapper");
        String cassandra_java = config.get("cassandra_jdk_path") + "/bin/java";
        String cassandra_gc_options = config.get("cassandra_gc_options");
        String cassandra_vm_options = config.get("cassandra_vm_options");
        String jvm_mitigation = config.get("jvm_mitigation");

        String ycsb_jdk = config.get("cassandra_jdk_path") + "/bin/java";
        String ycsb_gc_options = config.get("ycsb_gc_options");

        Map<String, String> workload = getMap(ns.getString("workload"), "workload");
        String ycsb_path = workload.get("ycsb_path");
        String ycsb_workload = workload.get("ycsb_workload");
        String ycsb_target = workload.get("ycsb_target");
        String ycsb_threads = workload.get("ycsb_threads");
        String ycsb_hosts = workload.get("ycsb_hosts");
        String ycsb_cassandra_username = workload.get("ycsb_cassandra_username");
        String ycsb_cassandra_password = workload.get("ycsb_cassandra_password");

        // 1. Generate command to invoke
        String yachtInvoke = String.join(" ", wrapper, cassandra_java, cassandra_gc_options, cassandra_vm_options, jvm_mitigation, classpath, "Yacht");
        System.out.println(yachtInvoke);
        // 2. Generate environment variables to be set

        // 3. Invoke process and exit boot
    }
}
