# Modern Data Pipeline Implementation Guide

## Project Overview
Build a modern, production-grade data pipeline that ingests data from multiple sources, processes it using stream/batch processing, tracks lineage, provides observability, and serves results via API.

## Restrictions to Note
❌ **Cannot Use:**
- Python
- Amazon Redshift
- Hadoop

## Architecture Overview

```
[Data Sources] → [Ingestion Layer] → [Processing Layer] → [Storage Layer] → [API Layer]
                         ↓                    ↓                  ↓
                   [Lineage Tracking] [Observability Stack]
```

---

## 1. Data Ingestion Layer

### Objective
Ingest data from three heterogeneous sources:
- Relational Database (e.g., PostgreSQL, MySQL, Oracle)
- File System (CSV, JSON, Parquet files)
- Traditional WS-* SOAP Web Services

### Technology Choices

#### Option A: Apache Kafka Connect (Recommended)
**Pros:** Production-ready, scalable, fault-tolerant
**Language:** Java/Scala configuration

**Components:**
- **JDBC Source Connector** - For Relational DB
  - Supports change data capture (CDC)
  - Incremental loads with timestamp/ID tracking
  
- **FileStream/Spooldir Connector** - For File System
  - Monitors directories for new files
  - Supports multiple formats
  
- **Custom SOAP Connector** - For WS-* Services
  - Build using Kafka Connect framework in Java/Scala
  - Poll SOAP endpoints periodically

**Setup:**
```bash
# Kafka Connect in distributed mode
connect-distributed.properties
```

**Configuration Example (JDBC):**
```json
{
  "name": "postgres-source",
  "config": {
    "connector.class": "io.confluent.connect.jdbc.JdbcSourceConnector",
    "connection.url": "jdbc:postgresql://localhost:5432/sales",
    "mode": "incrementing",
    "incrementing.column.name": "id",
    "topic.prefix": "sales-"
  }
}
```

#### Option B: Apache NiFi
**Pros:** Visual interface, extensive processors, good for complex routing
**Language:** Java-based

**Processors:**
- ExecuteSQL/QueryDatabaseTable - For RDBMS
- GetFile/ListFile/FetchFile - For filesystem
- InvokeHTTP with SOAP templates - For web services

#### Option C: Custom Scala/Java Services
**Pros:** Full control, lightweight
**Language:** Scala or Java

**Stack:**
- **Scala with Akka Streams** - Reactive ingestion
- **Slick** - Database access
- **Akka HTTP** - SOAP client
- **FS2** - File streaming

---

## 2. Modern Processing Layer

### Objective
Process ingested data using modern stream/batch processing frameworks

### Technology Choices

#### Option A: Apache Spark (Scala/Java) ⭐ Recommended
**Language:** Scala (preferred) or Java
**Best for:** Batch and micro-batch processing

**Features:**
- Structured Streaming for near real-time
- Rich SQL API
- Strong aggregation capabilities

**Example Code (Scala):**
```scala
import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.functions._

object SalesAggregation {
  def main(args: Array[String]): Unit = {
    val spark = SparkSession.builder()
      .appName("Sales Pipeline")
      .getOrCreate()
    
    // Read from Kafka
    val salesStream = spark.readStream
      .format("kafka")
      .option("kafka.bootstrap.servers", "localhost:9092")
      .option("subscribe", "sales-transactions")
      .load()
    
    // Top Sales per City
    val topSalesPerCity = salesStream
      .selectExpr("CAST(value AS STRING) as json")
      .select(from_json($"json", salesSchema).as("data"))
      .select("data.*")
      .groupBy("city")
      .agg(
        sum("amount").as("total_sales"),
        count("*").as("transaction_count")
      )
      .orderBy(desc("total_sales"))
    
    // Write to sink
    topSalesPerCity.writeStream
      .format("jdbc")
      .option("url", "jdbc:postgresql://localhost/results")
      .option("dbtable", "top_sales_city")
      .start()
  }
}
```

#### Option B: Apache Flink (Scala/Java)
**Language:** Scala or Java
**Best for:** True event-time stream processing, complex event processing

**Features:**
- Lower latency than Spark
- Advanced windowing
- Exactly-once semantics
- State management

**Example Code (Scala):**
```scala
import org.apache.flink.streaming.api.scala._
import org.apache.flink.streaming.api.windowing.time.Time

object FlinkSalesPipeline {
  def main(args: Array[String]): Unit = {
    val env = StreamExecutionEnvironment.getExecutionEnvironment
    
    val salesStream = env
      .addSource(new FlinkKafkaConsumer[Sale]("sales", new SaleSchema(), props))
    
    // Top Sales per City with tumbling window
    val topSalesPerCity = salesStream
      .keyBy(_.city)
      .timeWindow(Time.hours(1))
      .aggregate(new SalesAggregator)
    
    topSalesPerCity.addSink(new JdbcSink[CityAggregation](...))
    
    env.execute("Sales Pipeline")
  }
}
```

#### Option C: Kafka Streams (Scala/Java)
**Language:** Scala or Java
**Best for:** Lightweight stream processing, tight Kafka integration

**Features:**
- Library (not framework)
- Exactly-once semantics
- Local state stores
- Simple deployment

**Example Code (Scala):**
```scala
import org.apache.kafka.streams.scala._
import org.apache.kafka.streams.scala.kstream._

object KafkaStreamsPipeline {
  def main(args: Array[String]): Unit = {
    val builder = new StreamsBuilder()
    
    val salesStream: KStream[String, Sale] = 
      builder.stream[String, Sale]("sales-transactions")
    
    // Top Sales per City
    val salesByCity: KTable[String, Double] = salesStream
      .groupBy((_, sale) => sale.city)
      .aggregate(0.0)(
        (city, sale, agg) => agg + sale.amount
      )
    
    salesByCity.toStream.to("top-sales-by-city")
    
    val streams = new KafkaStreams(builder.build(), config)
    streams.start()
  }
}
```

### Recommended Choice
**Apache Spark with Scala**:
- Pipelines are aggregation-heavy (perfect for Spark SQL)
- Simpler operational model than Flink
- Better for batch and micro-batch than Kafka Streams
- Rich ecosystem and tooling

---

## 3. Data Lineage

### Objective
Track data transformations from source to destination

### Technology Choices

#### Option A: Apache Atlas ⭐ Recommended
**Integration:** Works with Spark, Kafka, Hive
**Features:**
- Automatic lineage capture
- Web UI for visualization
- REST API
- Metadata management

**Setup with Spark:**
```scala
// Add Spark-Atlas connector
"com.hortonworks.spark" % "spark-atlas-connector" % "0.1.0"

// Configure in spark-defaults.conf
spark.extraListeners=com.hortonworks.spark.atlas.SparkAtlasEventTracker
spark.sql.queryExecutionListeners=com.hortonworks.spark.atlas.SparkAtlasEventTracker
```

#### Option B: OpenLineage
**Modern Standard:** CNCF project
**Integration:** Spark, Flink, Airflow

**Implementation:**
```scala
// Add OpenLineage dependency
"io.openlineage" % "openlineage-spark" % "0.21.0"

// Configure listener
spark.extraListeners=io.openlineage.spark.agent.OpenLineageSparkListener
```

**Backends:**
- Marquez (OpenLineage reference implementation)
- Egeria
- Custom implementation

#### Option C: Amundsen + Custom Hooks
**Components:**
- Metadata service
- Search service
- Frontend

**Custom Lineage Capture:**
```scala
trait LineageTracker {
  def trackTransformation(
    source: Dataset, 
    target: Dataset, 
    operation: String
  ): Unit
}

// Implement custom hooks in Spark jobs
class SparkLineageTracker extends LineageTracker {
  def trackTransformation(...) = {
    // POST to lineage service
    lineageClient.recordLineage(LineageEvent(
      sourceUri = source.uri,
      targetUri = target.uri,
      transformationType = operation,
      timestamp = Instant.now()
    ))
  }
}
```

### Recommended Choice
**OpenLineage + Marquez** - Modern, standard, active community

---

## 4. Observability

### Objective
Monitor pipeline health, performance, and data quality

### Technology Stack

#### Metrics Collection
**Prometheus** (Pull-based metrics)
- Scrapes JMX metrics from Spark/Flink/Kafka
- Time-series database
- PromQL query language

**Setup for Spark:**
```scala
// spark-defaults.conf
spark.metrics.conf.*.sink.prometheus.class=org.apache.spark.metrics.sink.PrometheusSink
spark.metrics.conf.*.sink.prometheus.port=9091
```

#### Visualization
**Grafana** (Dashboards)
- Connect to Prometheus
- Pre-built dashboards for Spark/Kafka/Flink
- Alerting capabilities

**Key Dashboards:**
- Pipeline throughput (records/sec)
- Processing latency (P50, P95, P99)
- Error rates
- Resource utilization (CPU, memory, disk)

#### Distributed Tracing
**Jaeger or Zipkin**
- Trace requests through pipeline stages
- Identify bottlenecks

**Implementation (Scala with OpenTelemetry):**
```scala
import io.opentelemetry.api.trace.Tracer

class TracedProcessor(tracer: Tracer) {
  def processRecord(record: Sale): Unit = {
    val span = tracer.spanBuilder("process-sale").startSpan()
    try {
      // Processing logic
      validateSale(record)
      enrichSale(record)
      aggregateSale(record)
    } finally {
      span.end()
    }
  }
}
```

#### Logging
**ELK Stack** (Elasticsearch, Logstash, Kibana) or **Loki**

**Structured Logging (Scala with Logback):**
```scala
import org.slf4j.LoggerFactory
import net.logstash.logback.marker.Markers._

val logger = LoggerFactory.getLogger(getClass)

logger.info(
  append("event", "record_processed")
    .and(append("city", sale.city))
    .and(append("amount", sale.amount)),
  "Processed sale record"
)
```

#### Data Quality Monitoring
**Great Expectations** (Port to JVM) or **Deequ** (Amazon)

**Deequ Example (Scala):**
```scala
import com.amazon.deequ.checks.{Check, CheckLevel}
import com.amazon.deequ.{VerificationSuite, VerificationResult}

val verificationResult = VerificationSuite()
  .onData(salesDF)
  .addCheck(
    Check(CheckLevel.Error, "Sales Quality Checks")
      .hasSize(_ > 0)
      .isComplete("amount")
      .isNonNegative("amount")
      .isComplete("city")
      .hasPattern("email", """\S+@\S+\.\S+""".r)
  )
  .run()

if (verificationResult.status != CheckStatus.Success) {
  logger.error("Data quality check failed!")
  // Alert or stop pipeline
}
```

### Observability Stack Summary
```
Metrics:     Prometheus + Grafana
Tracing:     Jaeger/Zipkin + OpenTelemetry
Logging:     ELK/Loki
Data Quality: Deequ
Alerting:    AlertManager + PagerDuty/Slack
```

---

## 5. Pipeline Implementations

### Pipeline A: Top Sales per City

**Requirements:**
- Aggregate sales by city
- Rank cities by total sales
- Update continuously/regularly

**Implementation (Spark Scala):**
```scala
object TopSalesByCity {
  def run(spark: SparkSession): Unit = {
    import spark.implicits._
    
    val salesStream = spark.readStream
      .format("kafka")
      .option("kafka.bootstrap.servers", "localhost:9092")
      .option("subscribe", "sales-events")
      .load()
      .select(from_json($"value".cast("string"), salesSchema).as("sale"))
      .select("sale.*")
    
    val topSalesByCity = salesStream
      .withWatermark("timestamp", "1 hour")
      .groupBy(
        window($"timestamp", "1 day"),
        $"city"
      )
      .agg(
        sum("amount").as("total_sales"),
        count("*").as("transaction_count"),
        avg("amount").as("avg_sale"),
        max("amount").as("max_sale")
      )
      .select(
        $"window.start".as("date"),
        $"city",
        $"total_sales",
        $"transaction_count",
        $"avg_sale",
        $"max_sale"
      )
    
    // Write to PostgreSQL
    topSalesByCity.writeStream
      .foreachBatch { (batchDF: DataFrame, batchId: Long) =>
        batchDF.write
          .mode("append")
          .jdbc(
            url = "jdbc:postgresql://localhost/analytics",
            table = "top_sales_by_city",
            connectionProperties = new Properties()
          )
      }
      .option("checkpointLocation", "/checkpoints/top-sales-city")
      .start()
  }
}
```

### Pipeline B: Top Salesman in the Country

**Requirements:**
- Aggregate sales by salesperson
- Rank salespeople nationally
- Update continuously/regularly

**Implementation (Spark Scala):**
```scala
object TopSalesmen {
  def run(spark: SparkSession): Unit = {
    import spark.implicits._
    
    val salesStream = spark.readStream
      .format("kafka")
      .option("kafka.bootstrap.servers", "localhost:9092")
      .option("subscribe", "sales-events")
      .load()
      .select(from_json($"value".cast("string"), salesSchema).as("sale"))
      .select("sale.*")
    
    val topSalesmen = salesStream
      .withWatermark("timestamp", "1 hour")
      .groupBy(
        window($"timestamp", "1 day"),
        $"salesman_id",
        $"salesman_name"
      )
      .agg(
        sum("amount").as("total_sales"),
        count("*").as("deals_closed"),
        avg("amount").as("avg_deal_size"),
        countDistinct("city").as("cities_covered")
      )
      .select(
        $"window.start".as("date"),
        $"salesman_id",
        $"salesman_name",
        $"total_sales",
        $"deals_closed",
        $"avg_deal_size",
        $"cities_covered"
      )
      .withColumn(
        "rank",
        rank().over(
          Window
            .partitionBy($"date")
            .orderBy($"total_sales".desc)
        )
      )
      .filter($"rank" <= 100) // Top 100 salesmen
    
    topSalesmen.writeStream
      .foreachBatch { (batchDF: DataFrame, batchId: Long) =>
        batchDF.write
          .mode("append")
          .jdbc(
            url = "jdbc:postgresql://localhost/analytics",
            table = "top_salesmen",
            connectionProperties = new Properties()
          )
      }
      .option("checkpointLocation", "/checkpoints/top-salesmen")
      .start()
  }
}
```

---

## 6. Storage Layer (Dedicated DB)

### Objective
Store aggregated results for API consumption

### Technology Choices

#### Option A: PostgreSQL ⭐ Recommended
**Pros:**
- ACID compliant
- Excellent JSON support
- Mature ecosystem
- Great for analytical queries

**Schema:**
```sql
-- Top Sales by City
CREATE TABLE top_sales_by_city (
    id SERIAL PRIMARY KEY,
    date DATE NOT NULL,
    city VARCHAR(100) NOT NULL,
    total_sales DECIMAL(15, 2) NOT NULL,
    transaction_count INTEGER NOT NULL,
    avg_sale DECIMAL(15, 2),
    max_sale DECIMAL(15, 2),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(date, city)
);

CREATE INDEX idx_city_date ON top_sales_by_city(city, date);
CREATE INDEX idx_total_sales ON top_sales_by_city(total_sales DESC);

-- Top Salesmen
CREATE TABLE top_salesmen (
    id SERIAL PRIMARY KEY,
    date DATE NOT NULL,
    salesman_id VARCHAR(50) NOT NULL,
    salesman_name VARCHAR(200) NOT NULL,
    total_sales DECIMAL(15, 2) NOT NULL,
    deals_closed INTEGER NOT NULL,
    avg_deal_size DECIMAL(15, 2),
    cities_covered INTEGER,
    rank INTEGER,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(date, salesman_id)
);

CREATE INDEX idx_salesman_date ON top_salesmen(salesman_id, date);
CREATE INDEX idx_rank ON top_salesmen(rank);
```

#### Option B: Apache Cassandra
**Best for:** Very high write throughput, multi-region
**Pros:** Horizontal scalability, high availability
**Cons:** Learning curve, eventual consistency

#### Option C: ClickHouse
**Best for:** OLAP queries, real-time analytics
**Pros:** Extremely fast aggregations, columnar storage
**Cons:** Limited update support

---

## 7. API Layer

### Objective
Expose aggregated results via REST API

### Technology Choices

#### Option A: Scala with http4s ⭐ Recommended
**Pros:** Type-safe, functional, excellent performance

**Implementation:**
```scala
import cats.effect._
import org.http4s._
import org.http4s.dsl.io._
import org.http4s.circe.CirceEntityCodec._
import io.circe.generic.auto._
import doobie._
import doobie.implicits._

case class CitySales(
  date: String,
  city: String,
  totalSales: Double,
  transactionCount: Int
)

case class Salesman(
  date: String,
  salesmanId: String,
  salesmanName: String,
  totalSales: Double,
  dealsCount: Int,
  rank: Int
)

object SalesAPI extends IOApp {
  
  val xa = Transactor.fromDriverManager[IO](
    "org.postgresql.Driver",
    "jdbc:postgresql://localhost/analytics",
    "user", "password"
  )
  
  val routes = HttpRoutes.of[IO] {
    
    // GET /api/v1/sales/city?date=2026-01-23&limit=10
    case GET -> Root / "api" / "v1" / "sales" / "city" :? 
      DateParam(date) +& LimitParam(limit) =>
      
      val query = sql"""
        SELECT date, city, total_sales, transaction_count
        FROM top_sales_by_city
        WHERE date = $date
        ORDER BY total_sales DESC
        LIMIT $limit
      """.query[CitySales].to[List]
      
      query.transact(xa).flatMap(sales => Ok(sales))
    
    // GET /api/v1/salesmen/top?date=2026-01-23&limit=10
    case GET -> Root / "api" / "v1" / "salesmen" / "top" :?
      DateParam(date) +& LimitParam(limit) =>
      
      val query = sql"""
        SELECT date, salesman_id, salesman_name, 
               total_sales, deals_closed, rank
        FROM top_salesmen
        WHERE date = $date AND rank <= $limit
        ORDER BY rank
      """.query[Salesman].to[List]
      
      query.transact(xa).flatMap(salesmen => Ok(salesmen))
    
    // GET /api/v1/salesmen/:id/history
    case GET -> Root / "api" / "v1" / "salesmen" / salesmanId / "history" =>
      
      val query = sql"""
        SELECT date, salesman_id, salesman_name,
               total_sales, deals_closed, rank
        FROM top_salesmen
        WHERE salesman_id = $salesmanId
        ORDER BY date DESC
        LIMIT 30
      """.query[Salesman].to[List]
      
      query.transact(xa).flatMap(history => Ok(history))
  }
  
  def run(args: List[String]): IO[ExitCode] = {
    BlazeServerBuilder[IO]
      .bindHttp(8080, "0.0.0.0")
      .withHttpApp(routes.orNotFound)
      .serve
      .compile
      .drain
      .as(ExitCode.Success)
  }
}
```

#### Option B: Java with Spring Boot
**Pros:** Mature ecosystem, lots of tooling

**Implementation:**
```java
@RestController
@RequestMapping("/api/v1")
public class SalesController {
    
    @Autowired
    private SalesRepository salesRepository;
    
    @GetMapping("/sales/city")
    public List<CitySales> getTopCities(
        @RequestParam String date,
        @RequestParam(defaultValue = "10") int limit
    ) {
        return salesRepository.findTopCitiesByDate(
            LocalDate.parse(date), 
            limit
        );
    }
    
    @GetMapping("/salesmen/top")
    public List<Salesman> getTopSalesmen(
        @RequestParam String date,
        @RequestParam(defaultValue = "10") int limit
    ) {
        return salesRepository.findTopSalesmenByDate(
            LocalDate.parse(date),
            limit
        );
    }
}
```

#### Option C: Go with Gin
**Pros:** Excellent performance, simple deployment, small footprint

**Implementation:**
```go
package main

import (
    "github.com/gin-gonic/gin"
    "gorm.io/gorm"
)

type CitySales struct {
    Date             string  `json:"date"`
    City             string  `json:"city"`
    TotalSales       float64 `json:"total_sales"`
    TransactionCount int     `json:"transaction_count"`
}

func main() {
    router := gin.Default()
    
    router.GET("/api/v1/sales/city", func(c *gin.Context) {
        date := c.Query("date")
        limit := c.DefaultQuery("limit", "10")
        
        var sales []CitySales
        db.Where("date = ?", date).
           Order("total_sales DESC").
           Limit(limit).
           Find(&sales)
        
        c.JSON(200, sales)
    })
    
    router.Run(":8080")
}
```

### API Documentation
Use **OpenAPI/Swagger** for documentation:

```yaml
openapi: 3.0.0
info:
  title: Sales Analytics API
  version: 1.0.0
paths:
  /api/v1/sales/city:
    get:
      summary: Get top sales by city
      parameters:
        - name: date
          in: query
          required: true
          schema:
            type: string
            format: date
        - name: limit
          in: query
          schema:
            type: integer
            default: 10
      responses:
        '200':
          description: Successful response
          content:
            application/json:
              schema:
                type: array
                items:
                  $ref: '#/components/schemas/CitySales'
```

---

## Complete Technology Stack Recommendation

### Recommended Stack
```
┌─────────────────────────────────────────────────────────┐
│                     INGESTION LAYER                      │
├─────────────────────────────────────────────────────────┤
│  • Kafka Connect (JDBC, File, Custom SOAP connector)    │
│  • Apache Kafka (Message Broker)                        │
└─────────────────────────────────────────────────────────┘
                           ↓
┌─────────────────────────────────────────────────────────┐
│                   PROCESSING LAYER                       │
├─────────────────────────────────────────────────────────┤
│  • Apache Spark 3.5+ with Scala 2.13                   │
│  • Spark Structured Streaming                           │
└─────────────────────────────────────────────────────────┘
                           ↓
┌─────────────────────────────────────────────────────────┐
│                    STORAGE LAYER                         │
├─────────────────────────────────────────────────────────┤
│  • PostgreSQL 15+ (Aggregated Results)                  │
│  • Optional: MinIO/S3 (Raw Data Lake)                   │
└─────────────────────────────────────────────────────────┘
                           ↓
┌─────────────────────────────────────────────────────────┐
│                      API LAYER                           │
├─────────────────────────────────────────────────────────┤
│  • Scala + http4s + Doobie                              │
│  • Alternative: Spring Boot (Java) or Gin (Go)          │
└─────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────┐
│                  CROSS-CUTTING CONCERNS                  │
├─────────────────────────────────────────────────────────┤
│  Lineage:       OpenLineage + Marquez                   │
│  Observability: Prometheus + Grafana + Jaeger + ELK     │
│  Orchestration: Apache Airflow                          │
│  Data Quality:  Deequ                                    │
└─────────────────────────────────────────────────────────┘
```

---

## Key Considerations

### Scalability
- Kafka: Partition topics by city or region for parallel processing
- Spark: Configure executors based on data volume (start with 4 executors, 4GB each)
- PostgreSQL: Use connection pooling (HikariCP), read replicas for API queries

### Fault Tolerance
- Kafka: Replication factor = 3
- Spark: Checkpointing enabled for Structured Streaming
- API: Deploy multiple instances behind load balancer

### Security
- TLS/SSL for all inter-service communication
- API authentication (JWT or OAuth2)
- Encrypt sensitive data at rest
- Network segmentation (VPC/private subnets)

### Cost Optimization
- Use spot instances for Spark workers (if cloud)
- Implement data retention policies
- Archive old data to cheaper storage (S3 Glacier)

---

## Next Steps

1. **Review and Approve Architecture** - Discuss with team/stakeholders
2. **Set Up Development Environment** - Install tools, configure IDEs
3. **Create Sample Data** - Generate realistic test data for development
4. **Start with Phase 1** - Infrastructure setup
5. **Iterate and Refine** - Build incrementally, test continuously

## Resources

### Documentation
- [Apache Spark Documentation](https://spark.apache.org/docs/latest/)
- [Kafka Connect Guide](https://docs.confluent.io/platform/current/connect/index.html)
- [http4s Documentation](https://http4s.org/)
- [OpenLineage Docs](https://openlineage.io/)

### Books
- "Designing Data-Intensive Applications" by Martin Kleppmann
- "Streaming Systems" by Tyler Akidau
- "High Performance Spark" by Holden Karau
