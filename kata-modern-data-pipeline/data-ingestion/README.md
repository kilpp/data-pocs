# Data Ingestion Layer

This is the first layer of the modern data pipeline. It ingests sales transaction data from two sources into Apache Kafka using Kafka Connect.

## How It Works

```
[Data Generator]  --> [PostgreSQL] --> [Kafka Connect JDBC]     --> [sales-sales_transactions]
[File Generator]  --> [CSV Files]  --> [Kafka Connect SpoolDir] --> [sales-csv]
```

### Source 1: PostgreSQL (JDBC Connector)

1. **Data Generator** (`data-generator/`): Scala app that continuously inserts randomized sales transactions into PostgreSQL.
2. **Kafka Connect JDBC Source**: Polls PostgreSQL every 5s for new rows (using `timestamp+incrementing` mode) and publishes them to the `sales-sales_transactions` topic.

### Source 2: CSV Files (SpoolDir Connector)

1. **File Generator** (`file-generator/`): Scala app that writes CSV files with sales data into the `spool/input/` directory.
2. **Kafka Connect SpoolDir**: Watches `spool/input/` for new `.csv` files, reads them, publishes each row to the `sales-csv` topic, then moves the file to `spool/finished/`.

## Prerequisites

- Podman (or Docker) with Compose
- JDK 11+ and sbt

## Step by Step

### 1. Start the infrastructure

```bash
cd data-ingestion
podman-compose up -d
```

This starts four containers + an init container that auto-registers both connectors:

| Container | Port | Purpose |
|---|---|---|
| sales-postgres | 5432 | PostgreSQL database |
| zookeeper | 2181 | Kafka coordination |
| kafka | 9092 | Message broker |
| kafka-connect | 8083 | JDBC + SpoolDir connectors |
| connector-init | - | Registers connectors on startup, then exits |

Wait ~60 seconds for Kafka Connect to install plugins and start. The `connector-init` service will automatically register both connectors.

### 2. Verify connectors are running

```bash
curl http://localhost:8083/connectors | jq
```

Should return `["postgres-sales-source","csv-sales-source"]`. Check individual status:

```bash
curl http://localhost:8083/connectors/postgres-sales-source/status | jq
curl http://localhost:8083/connectors/csv-sales-source/status | jq
```

### 3. Start the data generators

**PostgreSQL source** (in one terminal):

```bash
cd data-ingestion/data-generator
sbt run
```

**CSV file source** (in another terminal):

```bash
cd data-ingestion/file-generator
sbt run
```

### 4. Verify the data flow

**Check PostgreSQL records:**

```bash
podman exec sales-postgres psql -U sales_user -d sales \
  -c "SELECT * FROM sales_transactions ORDER BY id DESC LIMIT 5;"
```

**Check CSV files being processed:**

```bash
ls spool/input/      # pending files
ls spool/finished/   # processed files
```

**Read from JDBC topic:**

```bash
podman exec kafka kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic sales-sales_transactions \
  --from-beginning --max-messages 3
```

**Read from CSV topic:**

```bash
podman exec kafka kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic sales-csv \
  --from-beginning --max-messages 3
```

**List all topics:**

```bash
podman exec kafka kafka-topics --bootstrap-server localhost:9092 --list
```

## Configuration

### Data Generator (environment variables)

| Variable | Default | Description |
|---|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/sales` | PostgreSQL connection URL |
| `DB_USER` | `sales_user` | Database user |
| `DB_PASSWORD` | `sales_pass` | Database password |
| `INTERVAL_MS` | `1000` | Milliseconds between batches |
| `BATCH_SIZE` | `50` | Records per batch |

### File Generator (environment variables)

| Variable | Default | Description |
|---|---|---|
| `OUTPUT_DIR` | `./spool/input` | Directory to write CSV files |
| `INTERVAL_MS` | `5000` | Milliseconds between files |
| `ROWS_PER_FILE` | `10` | Rows per CSV file |

### Kafka Connect Connectors

**JDBC Source** (`postgres-sales-source`):

| Setting | Value | Description |
|---|---|---|
| `mode` | `timestamp+incrementing` | Detects new rows by ID + timestamp |
| `poll.interval.ms` | `5000` | How often to poll PostgreSQL |
| `topic.prefix` | `sales-` | Topic = prefix + table name |
| `numeric.mapping` | `best_fit` | Decimals as numbers, not base64 |

**SpoolDir Source** (`csv-sales-source`):

| Setting | Value | Description |
|---|---|---|
| `input.path` | `/data/spool/input` | Directory to watch for CSV files |
| `finished.path` | `/data/spool/finished` | Where processed files go |
| `topic` | `sales-csv` | Target Kafka topic |
| `csv.first.row.as.header` | `true` | First row is column names |

## Teardown

```bash
podman-compose down        # stop containers, keep data
podman-compose down -v     # stop containers and delete volumes
rm -rf spool/              # clean up CSV spool directory
```

## Project Structure

```
data-ingestion/
├── docker-compose.yml          # Infrastructure (PostgreSQL, Kafka, Zookeeper, Kafka Connect)
├── init.sql                    # PostgreSQL schema
├── connector-config.json       # JDBC Source Connector config (standalone reference)
├── spool/                      # Shared directory between file-generator and Kafka Connect
│   ├── input/                  # New CSV files land here
│   ├── finished/               # Processed files moved here
│   └── error/                  # Failed files moved here
├── README.md
├── data-generator/             # PostgreSQL data generator
│   ├── build.sbt
│   ├── project/build.properties
│   └── src/main/scala/
│       └── SalesDataGenerator.scala
└── file-generator/             # CSV file generator
    ├── build.sbt
    ├── project/build.properties
    └── src/main/scala/
        └── SalesFileGenerator.scala
```
