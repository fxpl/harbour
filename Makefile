.PHONY: run clean compile

compile:
	javac -cp classes:lib/argparse4j-0.9.0.jar src/Yacht.java -d classes
	gcc -fPIC -I${JAVA_HOME}/include -I${JAVA_HOME}/include/linux -shared -o classes/librun_ycsb.so src/run_ycsb.c
