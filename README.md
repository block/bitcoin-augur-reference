# Augur Reference Implementation

This project serves as a reference implementation of the [augur](https://github.com/block/bitcoin-augur) Bitcoin fee estimation library. It demonstrates how to:

- Connect to a Bitcoin Core instance and persist mempool snapshots
- Calculate fee estimates using Augur's prediction model
- Expose fee estimates via a REST API

## Features

- **Mempool Data Collection**: Connects to Bitcoin Core via RPC and collects mempool data at regular intervals
- **Persistence**: Stores mempool snapshots for later analysis and fee estimation
- **Fee Estimation**: Uses the Augur library to calculate fee estimates based on historical mempool data
- **REST API**: Exposes fee estimates through a simple HTTP endpoint

## Getting Started

### Prerequisites

- Java 17 or higher
- Access to a Bitcoin Node with RPC access (specifically, requires the `getrawmempool` and `getblockchaininfo` RPC methods)

### Configuration

The application uses YAML for configuration with support for environment variable overrides.

#### Configuration Methods (in order of precedence)

1. **Environment Variables**: Highest priority
2. **External Config File**: Specified by `AUGUR_CONFIG_FILE` environment variable
3. **Default Config**: From `config.yaml` in resources

#### Configuration Options

| Setting | Environment Variable | Default | Description |
|---------|---------------------|---------|-------------|
| `server.host` | `AUGUR_SERVER_HOST` | `0.0.0.0` | HTTP server host |
| `server.port` | `AUGUR_SERVER_PORT` | `8080` | HTTP server port |
| `bitcoinRpc.url` | `BITCOIN_RPC_URL` | `http://localhost:8332` | Bitcoin Core RPC URL |
| `bitcoinRpc.username` | `BITCOIN_RPC_USERNAME` | _(empty)_ | Bitcoin Core RPC username |
| `bitcoinRpc.password` | `BITCOIN_RPC_PASSWORD` | _(empty)_ | Bitcoin Core RPC password |
| `persistence.dataDirectory` | `AUGUR_DATA_DIR` | `mempool_data` | Directory for storing mempool snapshots |

#### Sample YAML Configuration

```yaml
server:
  host: "0.0.0.0"
  port: 8080

bitcoinRpc:
  url: "http://localhost:8332"
  username: "rpcuser"
  password: "rpcpassword"

persistence:
  dataDirectory: "mempool_data"
```

### Building

This project uses CashApp's [Hermit](https://cashapp.github.io/hermit/). Hermit ensures that your team, your contributors,
and your CI have the same consistent tooling. Here are the [installation instructions](https://cashapp.github.io/hermit/usage/get-started/#installing-hermit).

[Activate Hermit](https://cashapp.github.io/hermit/usage/get-started/#activating-an-environment) either
by [enabling the shell hooks](https://cashapp.github.io/hermit/usage/shell/) (one-time only, recommended) or manually
sourcing the env with `. ./bin/activate-hermit`.

Use gradle to run all tests:

```shell
bin/gradle build
```

### Running the Application

#### Using Default Configuration

```bash
bin/gradle run
```

#### Using Environment Variables

```bash
BITCOIN_RPC_USERNAME=myuser BITCOIN_RPC_PASSWORD=mypassword bin/gradle run
```

#### Using External Config File

```bash
AUGUR_CONFIG_FILE=/path/to/my-config.yaml bin/gradle run
```

#### Using Docker
Alternatively, you can use Docker by first defining the `bitcoinRpc` username and password in the `config.yaml` file,
and then using:
```bash
docker build -t bitcoin-augur-reference .
docker run -p 8080:8080 bitcoin-augur-reference
```

## API Usage

Once running, the application exposes fee estimates at:

```
GET /fees
```

Example response:

```json
{
  "mempool_update_time": "2025-03-03T19:13:50.043Z",
  "estimates": {
    "3": {
      "probabilities": {
        "0.05": {
          "fee_rate": 2.0916
        },
        "0.20": {
          "fee_rate": 3.0931
        },
        "0.50": {
          "fee_rate": 3.4846
        },
        "0.80": {
          "fee_rate": 4.0535
        },
        "0.95": {
          "fee_rate": 5.0531
        }
      }
    },
    "6": {
      "probabilities": {
        "0.05": {
          "fee_rate": 1.9631
        },
        "0.20": {
          "fee_rate": 2.9441
        },
        "0.50": {
          "fee_rate": 3.1191
        },
        "0.80": {
          "fee_rate": 3.4903
        },
        "0.95": {
          "fee_rate": 4.8708
        }
      }
    },
    "9": {
      "probabilities": {
        "0.05": {
          "fee_rate": 1.8778
        },
        "0.20": {
          "fee_rate": 2.8979
        },
        "0.50": {
          "fee_rate": 3.1154
        },
        "0.80": {
          "fee_rate": 3.4903
        },
        "0.95": {
          "fee_rate": 4.2041
        }
      }
    }
  }
}
```

## Local Development
If you'd like to use a local version of [augur](https://github.com/block/bitcoin-augur) within your reference implementation:
- Within augur, run `bin/gradle shadowJar` to build a fat jar of augur.
- Copy the file `lib/build/libs/augur.jar` into this reference implementation `app/libs` directory.
- Change
  `implementation(libs.augur)` to `implementation(files("libs/augur.jar"))` in the `app/build.gradle.kts` [file](https://github.com/block/bitcoin-augur-reference/blob/main/app/build.gradle.kts).
- Within the reference implementation, run `bin/gradle build` to build the project with the local version of augur.

## Project Structure

- `config`: Configuration classes
- `bitcoin`: Bitcoin Core RPC client
- `persistence`: Mempool snapshot persistence
- `service`: Core application services
- `api`: API endpoints
- `server`: HTTP server setup

## License

This project is licensed under the Apache License 2.0 - see the [LICENSE](LICENSE) file for details.
