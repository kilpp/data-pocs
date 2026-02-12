# Data Ingestion Layer

This is the first layer of the modern data pipeline. It generates sales transaction data, stores it in PostgreSQL, and streams it into Apache Kafka using Kafka Connect.

## How It Works

```
[Scala Data Generator] --> [PostgreSQL] --> [Kafka Connect JDBC] --> [Kafka Topic]
```

1. **Data Generator** (`data-generator/`): A Scala application that continuously inserts randomized sales transactions into PostgreSQL. Each record contains a salesman ID, name, city, sale amount, email, and timestamp.

2. **PostgreSQL**: Stores the `sales_transactions` table. Initialized automatically via `init.sql` when the container starts.

3. **Kafka Connect**: Runs the Confluent JDBC Source Connector, which polls PostgreSQL every 5 seconds for new rows (using `timestamp+incrementing` mode on the `id` and `created_at` columns) and publishes them to the Kafka topic `sales-sales_transactions`.

4. **Kafka**: Receives the streamed records, making them available for downstream processing (Spark, Flink, etc.).

## Prerequisites

- Podman (or Docker) with Compose
- JDK 11+ and sbt (for the data generator)

## Step by Step

### 1. Start the infrastructure

```bash
cd data-ingestion
podman-compose up -d
```

This starts four containers:
| Container | Port | Purpose |
|---|---|---|
| sales-postgres | 5432 | PostgreSQL database |
| zookeeper | 2181 | Kafka coordination |
| kafka | 9092 | Message broker |
| kafka-connect | 8083 | JDBC Source Connector |

Wait ~30 seconds for Kafka Connect to fully start (it installs the JDBC connector plugin on first boot).

### 2. Register the Kafka Connect connector

```bash
curl -X POST http://localhost:8083/connectors \
  -H "Content-Type: application/json" \
  -d @connector-config.json
```

Verify it's running:

```bash
curl http://localhost:8083/connectors/postgres-sales-source/status | jq
```

Both `connector.state` and `tasks[0].state` should be `RUNNING`.

### 3. Start the data generator

```bash
cd data-generator
sbt run
```

You should see output like:

```
Connecting to jdbc:postgresql://localhost:5432/sales ...
Connected. Generating sales data...
Inserted batch of 5 records (total: 5)
Inserted batch of 5 records (total: 10)
...
```

### 4. Verify the data flow

**Check PostgreSQL:**

```bash
podman exec sales-postgres psql -U sales_user -d sales \
  -c "SELECT * FROM sales_transactions ORDER BY id DESC LIMIT 5;"
```

**Check Kafka topic:**

```bash
podman exec kafka kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic sales-sales_transactions \
  --from-beginning \
  --max-messages 3
```

You should see JSON messages with schema and payload for each sales transaction.

**List all Kafka topics:**

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
| `BATCH_SIZE` | `5` | Records per batch |

### Kafka Connect Connector (`connector-config.json`)

| Setting | Value | Description |
|---|---|---|
| `mode` | `timestamp+incrementing` | Detects new rows by ID + timestamp |
| `poll.interval.ms` | `5000` | How often to poll PostgreSQL |
| `topic.prefix` | `sales-` | Kafka topic = prefix + table name |
| `numeric.mapping` | `best_fit` | Decimals as numbers, not base64 bytes |

## Teardown

```bash
podman-compose down        # stop containers, keep data
podman-compose down -v     # stop containers and delete volumes
```

## Project Structure

```
data-ingestion/
├── docker-compose.yml          # Infrastructure (PostgreSQL, Kafka, Zookeeper, Kafka Connect)
├── init.sql                    # PostgreSQL schema
├── connector-config.json       # JDBC Source Connector configuration
├── README.md
└── data-generator/
    ├── build.sbt               # Scala project definition
    ├── project/
    │   └── build.properties    # sbt version
    └── src/main/scala/
        └── SalesDataGenerator.scala
```
