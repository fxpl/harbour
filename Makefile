.PHONY: run clean compile

compile:
	javac --release 21 -cp classes:lib/argparse4j-0.9.0.jar:lib/snakeyaml-2.3.jar src/Yacht.java src/Boot.java -d classes
