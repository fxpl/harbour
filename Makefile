.PHONY: run clean compile

compile:
	javac --release 21 -cp classes:lib/argparse4j-0.9.0.jar:lib/snakeyaml-2.3.jar src/Yacht.java src/Boot.java -d classes

run: compile
	java -XX:+UseSerialGC -Xms100m -Xmx100m -cp classes:lib/argparse4j-0.9.0.jar:lib/snakeyaml-2.3.jar Boot --config configs/multi-vm/config.yaml --workload configs/workload/write_intense.yaml --i 1