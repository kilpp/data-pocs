import java.io.{BufferedReader, InputStreamReader, OutputStream}
import java.net.{HttpURLConnection, URL}
import java.util.Properties
import org.apache.kafka.clients.producer.{KafkaProducer, ProducerConfig, ProducerRecord}
import org.apache.kafka.common.serialization.StringSerializer
import scala.xml.XML

object SoapSalesConnector {

  def main(args: Array[String]): Unit = {
    val soapUrl = sys.env.getOrElse("SOAP_URL", "http://localhost:8089/ws/sales")
    val bootstrapServers = sys.env.getOrElse("KAFKA_BOOTSTRAP_SERVERS", "localhost:9092")
    val topic = sys.env.getOrElse("TOPIC", "sales-sales_transactions")
    val pollIntervalMs = sys.env.getOrElse("POLL_INTERVAL_MS", "5000").toLong

    println(s"SOAP Sales Connector starting...")
    println(s"  SOAP URL: $soapUrl")
    println(s"  Kafka: $bootstrapServers")
    println(s"  Topic: $topic")
    println(s"  Poll interval: ${pollIntervalMs}ms")

    waitForSoapServer(soapUrl)

    val producer = createKafkaProducer(bootstrapServers)
    println("Kafka producer created. Polling SOAP service...")

    var totalProduced = 0L

    try {
      while (true) {
        try {
          val soapRequest = buildSoapRequest()
          val soapResponse = callSoapService(soapUrl, soapRequest)
          val transactions = parseSoapResponse(soapResponse)

          transactions.foreach { json =>
            val key = extractKey(json)
            val record = new ProducerRecord[String, String](topic, key, json)
            producer.send(record)
          }
          producer.flush()
          totalProduced += transactions.size
          println(s"Produced ${transactions.size} records from SOAP (total: $totalProduced)")
        } catch {
          case e: Exception =>
            println(s"Error polling SOAP: ${e.getMessage}. Retrying...")
        }

        Thread.sleep(pollIntervalMs)
      }
    } finally {
      producer.close()
    }
  }

  def waitForSoapServer(soapUrl: String): Unit = {
    val wsdlUrl = soapUrl + "?wsdl"
    println(s"Waiting for SOAP server at $wsdlUrl ...")
    var ready = false
    while (!ready) {
      try {
        val conn = new URL(wsdlUrl).openConnection().asInstanceOf[HttpURLConnection]
        conn.setConnectTimeout(5000)
        conn.setReadTimeout(5000)
        conn.setRequestMethod("GET")
        conn.connect()
        if (conn.getResponseCode == 200) ready = true
        conn.disconnect()
      } catch {
        case _: Exception =>
          println("  SOAP server not ready, retrying in 5s...")
          Thread.sleep(5000)
      }
    }
    println("SOAP server is ready.")
  }

  def createKafkaProducer(bootstrapServers: String): KafkaProducer[String, String] = {
    val props = new Properties()
    props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers)
    props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, classOf[StringSerializer].getName)
    props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, classOf[StringSerializer].getName)

    println(s"Waiting for Kafka at $bootstrapServers ...")
    var producer: KafkaProducer[String, String] = null
    while (producer == null) {
      try {
        producer = new KafkaProducer[String, String](props)
      } catch {
        case _: Exception =>
          println("  Kafka not ready, retrying in 5s...")
          Thread.sleep(5000)
      }
    }
    println("Kafka is ready.")
    producer
  }

  def buildSoapRequest(): String = {
    """<?xml version="1.0" encoding="UTF-8"?>
      |<soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/"
      |                  xmlns:sales="http://sales.example.com/">
      |  <soapenv:Body>
      |    <sales:GetSalesTransactions/>
      |  </soapenv:Body>
      |</soapenv:Envelope>""".stripMargin
  }

  def callSoapService(url: String, soapRequest: String): String = {
    val conn = new URL(url).openConnection().asInstanceOf[HttpURLConnection]
    conn.setRequestMethod("POST")
    conn.setRequestProperty("Content-Type", "text/xml; charset=utf-8")
    conn.setRequestProperty("SOAPAction", "GetSalesTransactions")
    conn.setDoOutput(true)
    conn.setConnectTimeout(10000)
    conn.setReadTimeout(30000)

    val os: OutputStream = conn.getOutputStream
    os.write(soapRequest.getBytes("UTF-8"))
    os.flush()
    os.close()

    val reader = new BufferedReader(new InputStreamReader(conn.getInputStream))
    val response = new StringBuilder
    var line: String = reader.readLine()
    while (line != null) {
      response.append(line)
      line = reader.readLine()
    }
    reader.close()
    conn.disconnect()

    response.toString()
  }

  def parseSoapResponse(xml: String): Seq[String] = {
    val doc = XML.loadString(xml)

    (doc \\ "transaction").map { tx =>
      val salesmanId = (tx \ "salesmanId").text
      val salesmanName = (tx \ "salesmanName").text
      val city = (tx \ "city").text
      val amount = (tx \ "amount").text
      val email = (tx \ "email").text
      val createdAt = (tx \ "createdAt").text

      s"""{"salesman_id":"$salesmanId","salesman_name":"$salesmanName","city":"$city","amount":$amount,"email":"$email","created_at":"$createdAt"}"""
    }
  }

  def extractKey(json: String): String = {
    val pattern = """"salesman_id":"([^"]+)"""".r
    pattern.findFirstMatchIn(json).map(_.group(1)).getOrElse("unknown")
  }
}
