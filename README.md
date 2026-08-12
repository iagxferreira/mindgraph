# agni

Agni is a Redis-like in-memory cache server written in Rust.

## Workspace

- `agni/` core library for store, protocol, and command logic
- `agni-server/` TCP server binary
- `agni-client/` CLI client binary
- `agni-bench/` benchmark binary

## Getting Started

```bash
cargo run -p agni-server -- --config config.example.yml
cargo run -p agni-client -- PING
```

## Docker

```bash
docker build -t agni .
docker run -p 6379:6379 agni
```

For custom config, mount a file at `/etc/agni/config.yml`. Inside the container, `host` must be `0.0.0.0`.

## Documentation

- [AGENTS.md](AGENTS.md) contributor guide and repo conventions
- [BENCHMARK.md](BENCHMARK.md) performance methodology and results

## Library Use

```toml
[dependencies]
agni = "0.1"
```

```rust
use agni::store::Store;

let store = Store::new();
store.set("key".to_string(), b"value".to_vec());
```

## Roadmap

- [x] Core commands: `PING`, `GET`, `SET`
- [ ] Remaining commands: `DEL`, `EXISTS`, `EXPIRE`, `TTL`
- [ ] TTL and background expiry cleanup
- [ ] Persistence
- [ ] Additional data types: lists, hashes, sets
