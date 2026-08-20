SHELL := /bin/sh

.PHONY: help run test build clean

help:
	@printf '%s\n' \
		'mindgraph targets:' \
		'  make run    - run the desktop application' \
		'  make test   - run the test suite' \
		'  make build  - assemble the application' \
		'  make clean  - remove build output'

run:
	cd desktop && ./gradlew run

test:
	cd desktop && ./gradlew test

build:
	cd desktop && ./gradlew build

clean:
	cd desktop && ./gradlew clean
