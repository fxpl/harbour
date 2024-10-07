/* Parts of this code comes from DaCapo whose license requires
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
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;

public class Yacht {
    public native int runYcsbNative(String cmd);

    static {
        System.loadLibrary("run_ycsb");
    }
    static private URLClassLoader loader;
    private File dirCassandraConf;
    private File dirCassandraStorage;
    private File dirCassandraLog;
    private File dirYCSBWorkloads;
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
        dirYCSBWorkloads = new File(Paths.get(PACKAGE_PATH, YCSB_PATH).toString(), "/workloads");
        ymlConf = new File(dirCassandraConf, "cassandra.yaml");
        xmlLogback = new File(dirCassandraConf, "logback.xml");

        setupScratch();
        setupCassandra();
        loadClasses();
    }

    public void loadClasses() throws RuntimeException, Exception {
        ArrayList<URL> jarUrlsList = new ArrayList<>();
        for (String path : JARS_TO_LOAD) {
            for (File file : new File(path).listFiles((dir, name) -> name.endsWith(".jar"))) {
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

        Namespace ns = null;
        try {
            ns = parser.parseArgs(args);
        } catch (ArgumentParserException e) {
            parser.handleError(e);
            System.exit(1);
        }
        Yacht yacht = new Yacht();

        Class<?> EmbeddedCassandraServiceClass = Class.forName(
                "org.apache.cassandra.service.EmbeddedCassandraService",
                true,
                loader
        );

        Object res = EmbeddedCassandraServiceClass.getMethod("start")
          .invoke(EmbeddedCassandraServiceClass.getConstructor().newInstance());

        System.out.println("Cassandra service started.");
        yacht.prepareYCSBArgs();
        // yacht.prepareYCSBCQL();
        if (ns.getBoolean("multi")) {
            int exitCode = yacht.runYcsbNative("/usr/bin/time --verbose bash ./bundles/ycsb-0.17.0/bin/ycsb.sh run cassandra-cql " + System.getenv("YCSB_ARGS"));
            System.out.println("YCSB process exited with code: " + exitCode);
        } else {
            yacht.clsYCSBClient = loader.loadClass("site.ycsb.Client");
            yacht.mtdYCSBClientMain = yacht.clsYCSBClient.getMethod("main", String[].class);
            yacht.mtdYCSBClientMain.invoke(null, (Object) yacht.ycsbWorkloadArgs);
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
           //e.printStackTrace();
        }
    }

    private void prepareYCSBArgs() {
        String envArgs = System.getenv("YCSB_ARGS");
        ArrayList<String> baseArgs = new ArrayList<>();
        if (envArgs != null && !envArgs.trim().isEmpty()) {
            baseArgs.addAll(Arrays.asList(envArgs.split(" ")));
        }
        baseArgs.add(baseArgs.size(), "-t"); // same as run
        baseArgs.add(baseArgs.size(), "-db");
        baseArgs.add(baseArgs.size(), "site.ycsb.db.CassandraCQLClient");

        ycsbWorkloadArgs = baseArgs.toArray(new String[0]);
    }

    private void setupScratch() {
        dirCassandraStorage = new File(PACKAGE_PATH +"/../db", "cassandra-storage");
        dirCassandraLog = new File(PACKAGE_PATH + "/../db", "cassandra-log");
        // dirCassandraStorage.mkdir();
        // dirCassandraLog.mkdir();
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
            System.getProperties().forEach((k, v) -> System.out.println(k + "=" + v));
        } catch (Exception e) {
            System.err.println("Exception during initialization: " + e.toString());
        }
    }
}
