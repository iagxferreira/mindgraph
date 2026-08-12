SHELL := /bin/sh

.PHONY: help run check test fmt clippy coverage

help:
	@printf '%s\n' \
		'mindgraph targets:' \
		'  make run       - run the TUI application' \
		'  make check     - type-check the project' \
		'  make test      - run the test suite' \
		'  make fmt       - format the codebase' \
		'  make clippy    - run clippy on all targets' \
		'  make coverage  - run coverage reporting'

run:
	cargo run

check:
	cargo check

test:
	cargo test

fmt:
	cargo fmt

clippy:
	cargo clippy --all-targets --all-features

coverage:
	cargo llvm-cov --workspace --all-features
