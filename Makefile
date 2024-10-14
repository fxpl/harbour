.PHONY: run run_debug run_single run_single_debug clean compile zip

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

zip: compile
	rm -rf dist
	mkdir -p dist
	mkdir -p dist/harbour-0.0.1
	cp -r src dist/harbour-0.0.1
	cp -r classes dist/harbour-0.0.1
	cp -r lib dist/harbour-0.0.1
	cp -r configs dist/harbour-0.0.1
	cp -r boot.sh dist/harbour-0.0.1
	cp -r bundles/apache-cassandra-5.0.1 dist/harbour-0.0.1
	cp -r bundles/ycsb-0.17.0 dist/harbour-0.0.1
	zip -r dist/harbour.zip dist/harbour-0.0.1

