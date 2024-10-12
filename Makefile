.PHONY: run clean compile run_debug

compile:
	javac --release 11 -cp classes:lib/argparse4j-0.9.0.jar:lib/snakeyaml-2.3.jar src/Yacht.java src/Boot.java -d classes

run: compile
	java -XX:+UseSerialGC -Xint -Xms100m -Xmx100m -cp classes:lib/argparse4j-0.9.0.jar:lib/snakeyaml-2.3.jar Boot --config configs/multi-vm/config.yaml --workload configs/workload/write_intense.yaml

run_single: compile
	java -XX:+UseSerialGC -Xint -Xms100m -Xmx100m -cp classes:lib/argparse4j-0.9.0.jar:lib/snakeyaml-2.3.jar Boot --config configs/single-vm/config.yaml --workload configs/workload/write_intense.yaml

run_debug: compile
	java -XX:+UseSerialGC -Xint -Xms100m -Xmx100m -cp classes:lib/argparse4j-0.9.0.jar:lib/snakeyaml-2.3.jar Boot --config configs/multi-vm/config.yaml --workload configs/workload/write_intense.yaml --debug

run_single_debug: compile
	java -XX:+UseSerialGC -Xint -Xms100m -Xmx100m -cp classes:lib/argparse4j-0.9.0.jar:lib/snakeyaml-2.3.jar Boot --config configs/single-vm/config.yaml --workload configs/workload/write_intense.yaml --debug
