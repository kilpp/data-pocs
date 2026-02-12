import java.sql.{Connection, DriverManager, Timestamp}
import java.time.Instant
import scala.util.Random

object SalesDataGenerator {

  val cities = List(
    "New York", "Los Angeles", "Chicago", "Houston", "Phoenix",
    "Philadelphia", "San Antonio", "San Diego", "Dallas", "San Jose",
    "Austin", "Jacksonville", "Fort Worth", "Columbus", "Charlotte",
    "Indianapolis", "San Francisco", "Seattle", "Denver", "Nashville"
  )

  val firstNames = List(
    "James", "Mary", "Robert", "Patricia", "John", "Jennifer", "Michael",
    "Linda", "David", "Elizabeth", "William", "Barbara", "Richard", "Susan",
    "Joseph", "Jessica", "Thomas", "Sarah", "Christopher", "Karen"
  )

  val lastNames = List(
    "Smith", "Johnson", "Williams", "Brown", "Jones", "Garcia", "Miller",
    "Davis", "Rodriguez", "Martinez", "Hernandez", "Lopez", "Gonzalez",
    "Wilson", "Anderson", "Thomas", "Taylor", "Moore", "Jackson", "Martin"
  )

  val rand = new Random()

  def main(args: Array[String]): Unit = {
    val url = sys.env.getOrElse("DB_URL", "jdbc:postgresql://localhost:5432/sales")
    val user = sys.env.getOrElse("DB_USER", "sales_user")
    val password = sys.env.getOrElse("DB_PASSWORD", "sales_pass")
    val intervalMs = sys.env.getOrElse("INTERVAL_MS", "1000").toLong
    val batchSize = sys.env.getOrElse("BATCH_SIZE", "5").toInt

    println(s"Connecting to $url ...")
    val conn = DriverManager.getConnection(url, user, password)
    println("Connected. Generating sales data...")

    try {
      generateForever(conn, intervalMs, batchSize)
    } finally {
      conn.close()
    }
  }

  def generateForever(conn: Connection, intervalMs: Long, batchSize: Int): Unit = {
    val sql =
      """INSERT INTO sales_transactions (salesman_id, salesman_name, city, amount, email, created_at)
        |VALUES (?, ?, ?, ?, ?, ?)""".stripMargin

    var totalInserted = 0L

    while (true) {
      val stmt = conn.prepareStatement(sql)
      try {
        for (_ <- 1 to batchSize) {
          val firstName = firstNames(rand.nextInt(firstNames.size))
          val lastName = lastNames(rand.nextInt(lastNames.size))
          val salesmanId = s"SM-${(firstName.take(2) + lastName.take(3)).toUpperCase}-${100 + rand.nextInt(900)}"
          val salesmanName = s"$firstName $lastName"
          val city = cities(rand.nextInt(cities.size))
          val amount = BigDecimal(50 + rand.nextDouble() * 9950).setScale(2, BigDecimal.RoundingMode.HALF_UP)
          val email = s"${firstName.toLowerCase}.${lastName.toLowerCase}@example.com"
          val timestamp = Timestamp.from(Instant.now())

          stmt.setString(1, salesmanId)
          stmt.setString(2, salesmanName)
          stmt.setString(3, city)
          stmt.setBigDecimal(4, amount.bigDecimal)
          stmt.setString(5, email)
          stmt.setTimestamp(6, timestamp)
          stmt.addBatch()
        }
        stmt.executeBatch()
        totalInserted += batchSize
        println(s"Inserted batch of $batchSize records (total: $totalInserted)")
      } finally {
        stmt.close()
      }

      Thread.sleep(intervalMs)
    }
  }
}
