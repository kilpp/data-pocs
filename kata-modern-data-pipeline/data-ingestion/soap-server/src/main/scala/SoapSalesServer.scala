import com.sun.net.httpserver.{HttpExchange, HttpHandler, HttpServer}
import java.net.InetSocketAddress
import java.time.Instant
import scala.util.Random

object SoapSalesServer {

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
    val port = sys.env.getOrElse("PORT", "8089").toInt
    val batchSize = sys.env.getOrElse("BATCH_SIZE", "50").toInt

    val server = HttpServer.create(new InetSocketAddress(port), 0)
    server.createContext("/ws/sales", new SoapHandler(batchSize))
    server.setExecutor(null)
    server.start()

    println(s"SOAP Sales Server started on port $port")
    println(s"WSDL: http://localhost:$port/ws/sales?wsdl")
    println(s"Generating $batchSize transactions per request")

    Thread.currentThread().join()
  }

  class SoapHandler(batchSize: Int) extends HttpHandler {
    override def handle(exchange: HttpExchange): Unit = {
      val method = exchange.getRequestMethod
      val query = Option(exchange.getRequestURI.getQuery).getOrElse("")

      if (method == "GET" && query.contains("wsdl")) {
        sendResponse(exchange, 200, generateWsdl(), "text/xml")
      } else if (method == "POST") {
        val body = new String(exchange.getRequestBody.readAllBytes())
        val response = handleSoapRequest(body)
        sendResponse(exchange, 200, response, "text/xml")
      } else {
        sendResponse(exchange, 405, "Method not allowed", "text/plain")
      }
    }

    private def handleSoapRequest(body: String): String = {
      val transactions = (1 to batchSize).map { _ =>
        val firstName = firstNames(rand.nextInt(firstNames.size))
        val lastName = lastNames(rand.nextInt(lastNames.size))
        val salesmanId = s"SM-${(firstName.take(2) + lastName.take(3)).toUpperCase}-${100 + rand.nextInt(900)}"
        val salesmanName = s"$firstName $lastName"
        val city = cities(rand.nextInt(cities.size))
        val amount = BigDecimal(50 + rand.nextDouble() * 9950).setScale(2, BigDecimal.RoundingMode.HALF_UP)
        val email = s"${firstName.toLowerCase}.${lastName.toLowerCase}@example.com"
        val timestamp = Instant.now().toString

        s"""        <sales:transaction>
           |          <sales:salesmanId>$salesmanId</sales:salesmanId>
           |          <sales:salesmanName>$salesmanName</sales:salesmanName>
           |          <sales:city>$city</sales:city>
           |          <sales:amount>$amount</sales:amount>
           |          <sales:email>$email</sales:email>
           |          <sales:createdAt>$timestamp</sales:createdAt>
           |        </sales:transaction>""".stripMargin
      }.mkString("\n")

      s"""<?xml version="1.0" encoding="UTF-8"?>
         |<soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/"
         |                  xmlns:sales="http://sales.example.com/">
         |  <soapenv:Body>
         |    <sales:GetSalesTransactionsResponse>
         |      <sales:transactions>
         |$transactions
         |      </sales:transactions>
         |    </sales:GetSalesTransactionsResponse>
         |  </soapenv:Body>
         |</soapenv:Envelope>""".stripMargin
    }

    private def generateWsdl(): String = {
      """<?xml version="1.0" encoding="UTF-8"?>
        |<definitions xmlns="http://schemas.xmlsoap.org/wsdl/"
        |             xmlns:soap="http://schemas.xmlsoap.org/wsdl/soap/"
        |             xmlns:tns="http://sales.example.com/"
        |             xmlns:xsd="http://www.w3.org/2001/XMLSchema"
        |             name="SalesService"
        |             targetNamespace="http://sales.example.com/">
        |  <types>
        |    <xsd:schema targetNamespace="http://sales.example.com/">
        |      <xsd:element name="GetSalesTransactions">
        |        <xsd:complexType>
        |          <xsd:sequence>
        |            <xsd:element name="batchSize" type="xsd:int" minOccurs="0"/>
        |          </xsd:sequence>
        |        </xsd:complexType>
        |      </xsd:element>
        |      <xsd:element name="GetSalesTransactionsResponse">
        |        <xsd:complexType>
        |          <xsd:sequence>
        |            <xsd:element name="transactions">
        |              <xsd:complexType>
        |                <xsd:sequence>
        |                  <xsd:element name="transaction" maxOccurs="unbounded">
        |                    <xsd:complexType>
        |                      <xsd:sequence>
        |                        <xsd:element name="salesmanId" type="xsd:string"/>
        |                        <xsd:element name="salesmanName" type="xsd:string"/>
        |                        <xsd:element name="city" type="xsd:string"/>
        |                        <xsd:element name="amount" type="xsd:decimal"/>
        |                        <xsd:element name="email" type="xsd:string"/>
        |                        <xsd:element name="createdAt" type="xsd:dateTime"/>
        |                      </xsd:sequence>
        |                    </xsd:complexType>
        |                  </xsd:element>
        |                </xsd:sequence>
        |              </xsd:complexType>
        |            </xsd:element>
        |          </xsd:sequence>
        |        </xsd:complexType>
        |      </xsd:element>
        |    </xsd:schema>
        |  </types>
        |  <message name="GetSalesTransactionsRequest">
        |    <part name="parameters" element="tns:GetSalesTransactions"/>
        |  </message>
        |  <message name="GetSalesTransactionsResponse">
        |    <part name="parameters" element="tns:GetSalesTransactionsResponse"/>
        |  </message>
        |  <portType name="SalesPortType">
        |    <operation name="GetSalesTransactions">
        |      <input message="tns:GetSalesTransactionsRequest"/>
        |      <output message="tns:GetSalesTransactionsResponse"/>
        |    </operation>
        |  </portType>
        |  <binding name="SalesBinding" type="tns:SalesPortType">
        |    <soap:binding style="document" transport="http://schemas.xmlsoap.org/soap/http"/>
        |    <operation name="GetSalesTransactions">
        |      <soap:operation soapAction="GetSalesTransactions"/>
        |      <input><soap:body use="literal"/></input>
        |      <output><soap:body use="literal"/></output>
        |    </operation>
        |  </binding>
        |  <service name="SalesService">
        |    <port name="SalesPort" binding="tns:SalesBinding">
        |      <soap:address location="http://localhost:8089/ws/sales"/>
        |    </port>
        |  </service>
        |</definitions>""".stripMargin
    }

    private def sendResponse(exchange: HttpExchange, status: Int, body: String, contentType: String): Unit = {
      val bytes = body.getBytes("UTF-8")
      exchange.getResponseHeaders.set("Content-Type", s"$contentType; charset=utf-8")
      exchange.sendResponseHeaders(status, bytes.length)
      val os = exchange.getResponseBody
      os.write(bytes)
      os.close()
    }
  }
}
