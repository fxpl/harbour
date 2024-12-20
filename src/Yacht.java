/* Copyright (c) 2024 Uppsala University
 *
 * Parts of this code for loading Cassandra/YCSB
 * comes from DaCapo whose license requires
 * copyright and license notices to be presereved.
 *
 * Copyright (c) 2009-2020 The Australian National University.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Apache License v2.0.
 * You may obtain the license at
 *
 *    http://www.opensource.org/licenses/apache2.0.php
 */
import java.io.File;
import java.io.FileInputStream;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Paths;
import java.nio.file.Files;
import java.util.*;
import java.io.InputStream;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

import static java.util.Map.entry;

import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;

public class Yacht {
    static private URLClassLoader loader;
    private File dirCassandraConf;
    private File dirCassandraStorage;
    private File dirCassandraLog;
    private File ymlConf;
    private File xmlLogback;
    private Class<?> clsYCSBClient;
    private Method mtdYCSBClientMain;
    private String[] ycsbWorkloadArgs;

    final private static String PACKAGE_PATH = "./bundles";
    final private static String CASSANDRA_PATH = "apache-cassandra-5.0.1";
    final private static String YCSB_PATH = "ycsb-0.17.0";
    final private static List<String> JARS_TO_LOAD = Arrays.asList(
        PACKAGE_PATH + "/"+ CASSANDRA_PATH +"/lib",
        PACKAGE_PATH + "/" + YCSB_PATH + "/lib",
        PACKAGE_PATH + "/" + YCSB_PATH + "/cassandra-binding/lib");

    public Yacht() throws RuntimeException, Exception {
        dirCassandraConf = new File(Paths.get(PACKAGE_PATH, CASSANDRA_PATH).toString(), "conf");
        ymlConf = new File(dirCassandraConf, "cassandra.yaml");
        xmlLogback = new File(dirCassandraConf, "logback.xml");

        setupScratch();
        setupCassandra();
        loadClasses();
    }

    static void invoke(String bash, Map<String, String> environment) {
        try {
            ProcessBuilder processBuilder = new ProcessBuilder("bash", "-c", bash);
            if (environment != null) {
                Map<String, String> processEnvironment = processBuilder.environment();
                environment.forEach(processEnvironment::put);
            }
            processBuilder.inheritIO();
            processBuilder.start().waitFor();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void loadClasses() throws RuntimeException, Exception {
        ArrayList<URL> jarUrlsList = new ArrayList<>();
        for (String path : JARS_TO_LOAD) {
            for (File file : Objects.requireNonNull(new File(path).listFiles((dir, name) -> name.endsWith(".jar")))) {
                if (file.getName().contains("slf4j-simple-1.7.25.jar")) {
                    // Skip to avoid conflict since both cassandra and ycsb have an implementation
                    continue;
                }
                jarUrlsList.add(file.toURI().toURL());
            }
        }

        URL[] jarUrls = new URL[jarUrlsList.size()];
        for (int i = 0; i < jarUrls.length; i++) {
            jarUrls[i] = jarUrlsList.get(i);
        }

        loader = new URLClassLoader(jarUrls);
    }

    public static void main(String[] args) throws RuntimeException, Exception {
        ArgumentParser parser = ArgumentParsers.newFor("Yacht").build()
            .defaultHelp(true)
            .description("Run Cassandra and YCSB");
        parser.addArgument("--multi")
            .action(net.sourceforge.argparse4j.impl.Arguments.storeTrue())
            .help("Run multi-JVM");
        parser.addArgument("--init")
            .setDefault(false)
            .action(net.sourceforge.argparse4j.impl.Arguments.storeTrue())
            .help("Run multi-JVM");

        List<String> overrideParamsCassandra = Arrays.asList("concurrent_writes", "concurrent_counter_writes", "concurrent_materialized_view_writes", "concurrent_compactors");
        for (String argOverride : overrideParamsCassandra) {
            parser.addArgument("--"+argOverride)
                .help("");
        }

        Namespace ns = null;
        try {
            ns = parser.parseArgs(args);
        } catch (ArgumentParserException e) {
            parser.handleError(e);
            System.exit(1);
        }
        Yacht yacht = new Yacht();
        Files.copy(Paths.get(yacht.dirCassandraConf.toString(), "cassandra.yaml.template"), Paths.get(yacht.dirCassandraConf.toString(), "cassandra.yaml"), StandardCopyOption.REPLACE_EXISTING);

        for (String argOverride : overrideParamsCassandra) {
            if (ns.getString(argOverride) == null) continue;
            Files.writeString(
                Paths.get(yacht.dirCassandraConf.toString(), "cassandra.yaml"),
                argOverride + ": "+ ns.getString(argOverride) + System.lineSeparator(),
                StandardOpenOption.APPEND
            );
        }

        Class<?> EmbeddedCassandraServiceClass = Class.forName(
            "org.apache.cassandra.service.EmbeddedCassandraService",
            true,
            loader
        );

        Object res = EmbeddedCassandraServiceClass.getMethod("start")
          .invoke(EmbeddedCassandraServiceClass.getConstructor().newInstance());

        System.err.println("Using Cassandra configuration with:");
        Class<?> DatabaseDescriptor = Class.forName("org.apache.cassandra.config.DatabaseDescriptor", true, loader);
        for (String str : Arrays.asList("getConcurrentReaders", "getConcurrentCounterWriters", "getConcurrentWriters", "getConcurrentViewWriters", "getConcurrentCompactors")) {
            System.err.println(str+ ": " + (Integer)DatabaseDescriptor.getMethod(str).invoke(null));
        }

        System.out.println("Cassandra service started.");
        yacht.prepareYCSBArgs();

        if (!ns.getBoolean("multi") || ns.getBoolean("init")) {
            try {
                yacht.clsYCSBClient = loader.loadClass("site.ycsb.Client");
                yacht.mtdYCSBClientMain = yacht.clsYCSBClient.getMethod("main", String[].class);
                if (ns.getBoolean("init")) {
                    yacht.prepareYCSBCQL();
                    yacht.ycsbWorkloadArgs[yacht.ycsbWorkloadArgs.length - 1] = "-load";
                }
                yacht.mtdYCSBClientMain.invoke(null, (Object) yacht.ycsbWorkloadArgs);
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else if (ns.getBoolean("multi")) {
            String YCSB_BASE_INVOKE = System.getenv("YCSB_BASE_INVOKE");
            String YCSB_ARGS = System.getenv("YCSB_ARGS");
            invoke(String.join(" ", YCSB_BASE_INVOKE, "run",
                "cassandra-cql", YCSB_ARGS), Map.ofEntries(
                entry("JAVA_HOME", System.getenv("YCSB_JAVA_HOME")),
                entry("JAVA_OPTS", System.getenv("YCSB_JAVA_OPTS") + " -Dorg.slf4j.simpleLogger.defaultLogLevel=OFF")
            ));
        }

        System.exit(0);
    }

    private void prepareYCSBCQL() {
        try {
            Object sess;
            Class<?> cluster = Class.forName("com.datastax.driver.core.Cluster", true, loader);
            Class<?> clusterBuilder = Class.forName("com.datastax.driver.core.Cluster$Builder", true, loader);
            Class<?> DatabaseDescriptor = Class.forName("org.apache.cassandra.config.DatabaseDescriptor", true, loader);
            Class<?> session = Class.forName("com.datastax.driver.core.Session", true, loader);

            Method getNativeTransportPort = DatabaseDescriptor.getMethod("getNativeTransportPort");
            Method builder = cluster.getMethod("builder");
            Method connect = cluster.getDeclaredMethod("connect");

            Method withPort = clusterBuilder.getDeclaredMethod("withPort", int.class);
            Method build = clusterBuilder.getDeclaredMethod("build");
            Method addContactPoint = clusterBuilder.getDeclaredMethod("addContactPoint", String.class);

            Method execute = session.getDeclaredMethod("execute", String.class);
            Method close = session.getDeclaredMethod("close");

            Object tr = builder.invoke(null);
            tr = addContactPoint.invoke(tr, "localhost");
            tr = withPort.invoke(tr, (Object) getNativeTransportPort.invoke(null));
            sess = connect.invoke(build.invoke(tr));

            execute.invoke(sess, (Object) "CREATE KEYSPACE ycsb WITH REPLICATION = {'class': 'SimpleStrategy', 'replication_factor': 1};");
            execute.invoke(sess, (Object) "USE ycsb;");
            execute.invoke(sess, (Object) "CREATE TABLE usertable ("
                    + "y_id varchar primary key,"
                    + "field0 varchar,"
                    + "field1 varchar,"
                    + "field2 varchar,"
                    + "field3 varchar,"
                    + "field4 varchar,"
                    + "field5 varchar,"
                    + "field6 varchar,"
                    + "field7 varchar,"
                    + "field8 varchar,"
                    + "field9 varchar);");
            close.invoke(sess);
        } catch (Exception e) {
           e.printStackTrace();
        }
    }

    private void prepareYCSBArgs() {
        String envArgs = System.getenv("YCSB_ARGS");
        ArrayList<String> baseArgs = new ArrayList<>();
        if (envArgs != null && !envArgs.trim().isEmpty()) {
            baseArgs.addAll(Arrays.asList(envArgs.split(" ")));
        }
        baseArgs.add(baseArgs.size(), "-db");
        baseArgs.add(baseArgs.size(), "site.ycsb.db.CassandraCQLClient");
        baseArgs.add(baseArgs.size(), "-t"); // same as run

        ycsbWorkloadArgs = baseArgs.toArray(new String[0]);
    }

    private void setupScratch() {
        dirCassandraStorage = new File(PACKAGE_PATH +"/../db", "cassandra-storage");
        dirCassandraLog = new File(PACKAGE_PATH + "/../db", "cassandra-log");
    }

    private void setupCassandra() {
        try {
            System.setProperty("java.library.path", PACKAGE_PATH+"/"+CASSANDRA_PATH+"/lib/sigar-bin");
            System.setProperty("cassandra.storagedir", dirCassandraStorage.toString());
            System.setProperty("cassandra.logdir", dirCassandraLog.toString());
            System.setProperty("cassandra.config", ymlConf.toPath().toUri().toString());
            System.setProperty("cassandra.logback.configurationFile", xmlLogback.toString());
            System.setProperty("logback.configurationFile", xmlLogback.toString());
            System.setProperty("cassandra-foreground", "yes");
            System.setProperty("java.security.manager", "allow");

            // System.getProperties().forEach((k, v) -> System.out.println(k + "=" + v));
        } catch (Exception e) {
            System.err.println("Exception during initialization: " + e.toString());
        }
    }
}
