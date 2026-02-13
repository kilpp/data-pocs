import java.io.{File, PrintWriter}
import java.time.Instant
import scala.util.Random

object SalesFileGenerator {

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
    val outputDir = sys.env.getOrElse("OUTPUT_DIR", "../spool/input")
    val intervalMs = sys.env.getOrElse("INTERVAL_MS", "5000").toLong
    val rowsPerFile = sys.env.getOrElse("ROWS_PER_FILE", "50").toInt

    val dir = new File(outputDir)
    dir.mkdirs()

    println(s"Writing CSV files to $outputDir every ${intervalMs}ms ($rowsPerFile rows each)")

    var fileCount = 0L
    while (true) {
      fileCount += 1
      val fileName = s"sales_${System.currentTimeMillis()}_$fileCount.csv"
      val file = new File(dir, fileName)
      val writer = new PrintWriter(file)

      try {
        writer.println("salesman_id,salesman_name,city,amount,email,created_at")
        for (_ <- 1 to rowsPerFile) {
          val firstName = firstNames(rand.nextInt(firstNames.size))
          val lastName = lastNames(rand.nextInt(lastNames.size))
          val salesmanId = s"SM-${(firstName.take(2) + lastName.take(3)).toUpperCase}-${100 + rand.nextInt(900)}"
          val salesmanName = s"$firstName $lastName"
          val city = cities(rand.nextInt(cities.size))
          val amount = BigDecimal(50 + rand.nextDouble() * 9950).setScale(2, BigDecimal.RoundingMode.HALF_UP)
          val email = s"${firstName.toLowerCase}.${lastName.toLowerCase}@example.com"
          val timestamp = Instant.now().toString

          writer.println(s"$salesmanId,$salesmanName,$city,$amount,$email,$timestamp")
        }
        println(s"Wrote $fileName ($rowsPerFile rows)")
      } finally {
        writer.close()
      }

      Thread.sleep(intervalMs)
    }
  }
}
