SHELL := /bin/sh

.PHONY: help run test build clean package install-desktop-entry

help:
	@printf '%s\n' \
		'mindgraph targets:' \
		'  make run    - run the desktop application' \
		'  make test   - run the test suite' \
		'  make build  - assemble the application' \
		'  make clean  - remove build output' \
		'' \
		'linux packaging:' \
		'  make package              - build a native installer for this OS' \
		'  make install-desktop-entry - add the launcher entry (after installing the package)'

run:
	cd desktop && ./gradlew run

test:
	cd desktop && ./gradlew test

build:
	cd desktop && ./gradlew build

clean:
	cd desktop && ./gradlew clean

package:
	cd desktop && ./gradlew packageDistributionForCurrentOS

# jpackage's own entry is installed by xdg-desktop-menu from the package's post-install
# scriptlet, which runs as root and lands it in root's home rather than a shared directory.
# It also cannot declare StartupWMClass, without which the desktop cannot match the running
# window to the entry and shows a generic icon. Installing this one fixes both.
install-desktop-entry:
	@test -x /opt/mindgraph/bin/MindGraph || \
		{ echo "MindGraph is not installed in /opt/mindgraph - install the package first."; exit 1; }
	install -Dm644 packaging/mindgraph.desktop \
		"$${XDG_DATA_HOME:-$$HOME/.local/share}/applications/mindgraph.desktop"
	-update-desktop-database "$${XDG_DATA_HOME:-$$HOME/.local/share}/applications"
	@echo "Installed. Restart MindGraph so the desktop can match the window to the entry."
