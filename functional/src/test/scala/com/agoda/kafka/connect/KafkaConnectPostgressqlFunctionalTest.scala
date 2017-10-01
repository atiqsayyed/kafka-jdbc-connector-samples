package com.agoda.kafka.connect

import java.sql.{Connection, DriverManager, Timestamp}
import java.util.{Calendar, GregorianCalendar, TimeZone}

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Sink, Source}
import com.softwaremill.react.kafka.{ConsumerProperties, ReactiveKafka}
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.serialization.StringDeserializer
import org.reactivestreams.Publisher
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpec}

import scala.collection.mutable.ListBuffer
import scala.concurrent.Await
import scala.concurrent.duration.{Duration, DurationDouble}
import scala.language.postfixOps
import scala.util.Try
import scalaj.http.Http

class KafkaConnectPostgressqlFunctionalTest extends WordSpec with Matchers with BeforeAndAfterAll {
  var db: Connection = _
  var publisherTimestamp: Publisher[ConsumerRecord[String, String]] = _
  var publisherIncrementing: Publisher[ConsumerRecord[String, String]] = _
  var publisherTimestampIncrementing: Publisher[ConsumerRecord[String, String]] = _

  implicit val system = ActorSystem("functional-test")
  implicit val materializer = ActorMaterializer()

  "Kafka Connect in Timestamp mode" should {
    "should stream data into kafka" in {
      val url = "http://localhost:8083/connectors"
      val data = """
                   |{
                   |	"name" : "timestamp_postgresql",
                   |	"config" : {
                   |		"tasks.max": "1",
                   |		"connector.class": "com.agoda.kafka.connector.jdbc.JdbcSourceConnector",
                   |		"connection.url" : "jdbc:postgresql://postgresql:5432/tempdb?user=tempdb&password=example",
                   |		"batch.max.rows.variable.name" : "batch",
                   |		"batch.max.rows" : "2",
                   |		"mode" : "timestamp",
                   |		"timestamp.variable.name" : "time",
                   |		"timestamp.field.name" : "change_timestamp",
                   |		"timestamp.offset" : "2017-03-13 05:33:57.000",
                   |		"stored-procedure.name" : "sp_cdc_timestamp",
                   |		"topic" : "test-timestamp-postgresql",
                   |		"key.field.name" : "id"
                   |	}
                   |}""".stripMargin

      Http(url).postData(data).header("content-type", "application/json").asString

      val messages = Await.result(
        Source.fromPublisher(publisherTimestamp)
          .map(m => s"${m.key()} => ${m.value()}")
          .take(5)
          .takeWithin(1.minute)
          .runWith(Sink.seq[String]),
        Duration.Inf
      )

      messages shouldEqual ListBuffer(
        """6 => {"id":6,"change_timestamp":1489383241000}""",
        """7 => {"id":7,"change_timestamp":1489383242000}""",
        """8 => {"id":8,"change_timestamp":1489383244000}""",
        """9 => {"id":9,"change_timestamp":1489383246000}""",
        """10 => {"id":10,"change_timestamp":1489383247000}"""
      )
    }
  }

  "Kafka Connect in Incrementing mode" should {
    "should stream data into kafka" in {
      val url = "http://localhost:8083/connectors"
      val data = """
                   |{
                   |	"name" : "incrementing_postgresql",
                   |	"config" : {
                   |		"tasks.max": "1",
                   |		"connector.class": "com.agoda.kafka.connector.jdbc.JdbcSourceConnector",
                   |		"connection.url" : "jdbc:postgresql://postgresql:5432/tempdb?user=tempdb&password=example",
                   |		"batch.max.rows.variable.name" : "batch",
                   |		"batch.max.rows" : "2",
                   |		"mode" : "incrementing",
                   |		"incrementing.variable.name" : "id",
                   |		"incrementing.field.name" : "id",
                   |		"incrementing.offset" : "7",
                   |		"stored-procedure.name" : "sp_cdc_incrementing",
                   |		"topic" : "test-incrementing-postgresql",
                   |		"key.field.name" : "id"
                   |	}
                   |}""".stripMargin

      Http(url).postData(data).header("content-type", "application/json").asString

      val messages = Await.result(
        Source.fromPublisher(publisherIncrementing)
          .map(m => s"${m.key()} => ${m.value()}")
          .take(3)
          .takeWithin(1.minute)
          .runWith(Sink.seq[String]),
        Duration.Inf
      )

      messages shouldEqual ListBuffer(
        """8 => {"id":8,"change_timestamp":1489383244000}""",
        """9 => {"id":9,"change_timestamp":1489383246000}""",
        """10 => {"id":10,"change_timestamp":1489383247000}"""
      )
    }
  }

  "Kafka Connect in Timestamp+Incrementing mode" should {
    "should stream data into kafka" in {
      val url = "http://localhost:8083/connectors"
      val data = """
                   |{
                   |	"name" : "timestamp_incrementing_postgresql",
                   |	"config" : {
                   |		"tasks.max": "1",
                   |		"connector.class": "com.agoda.kafka.connector.jdbc.JdbcSourceConnector",
                   |		"connection.url" : "jdbc:postgresql://postgresql:5432/tempdb?user=tempdb&password=example",
                   |		"batch.max.rows.variable.name" : "batch",
                   |		"batch.max.rows" : "2",
                   |		"mode" : "timestamp+incrementing",
                   |    "timestamp.variable.name" : "time",
                   |    "timestamp.field.name" : "change_timestamp",
                   |    "timestamp.offset" : "2017-03-13 05:33:51.000",
                   |		"incrementing.variable.name" : "id",
                   |		"incrementing.field.name" : "id",
                   |		"incrementing.offset" : "3",
                   |		"stored-procedure.name" : "sp_cdc_timestamp_incrementing",
                   |		"topic" : "test-timestamp-incrementing-postgresql",
                   |		"key.field.name" : "id"
                   |	}
                   |}""".stripMargin

      Http(url).postData(data).header("content-type", "application/json").asString.code

      val messages = Await.result(
        Source.fromPublisher(publisherTimestampIncrementing)
          .map(m => s"${m.key()} => ${m.value()}")
          .take(7)
          .takeWithin(1.minute)
          .runWith(Sink.seq[String]),
        Duration.Inf
      )

      messages shouldEqual ListBuffer(
        """4 => {"id":4,"change_timestamp":1489383231000}""",
        """5 => {"id":5,"change_timestamp":1489383237000}""",
        """6 => {"id":6,"change_timestamp":1489383241000}""",
        """7 => {"id":7,"change_timestamp":1489383242000}""",
        """8 => {"id":8,"change_timestamp":1489383244000}""",
        """9 => {"id":9,"change_timestamp":1489383246000}""",
        """10 => {"id":10,"change_timestamp":1489383247000}"""
      )
    }
  }

  lazy private val dropTable   = db.prepareStatement("DROP TABLE IF EXISTS TEST CASCADE")
  lazy private val createTable = db.prepareStatement("CREATE TABLE TEST (id INT, change_timestamp TIMESTAMP)")
  lazy private val insertData  = db.prepareStatement(
    "INSERT INTO TEST (id, change_timestamp) VALUES(1,  ?),(2,  ?),(3,  ?),(4,  ?),(5,  ?),(6,  ?),(7,  ?),(8,  ?),(9,  ?),(10, ?);"
  )
  lazy private val dropTimestampProcedure = db.prepareStatement("DROP FUNCTION IF EXISTS sp_cdc_timestamp(TIMESTAMP, INT) CASCADE")
  lazy private val createTimestampProcedure = db.prepareStatement(
    """
      |CREATE OR REPLACE FUNCTION sp_cdc_timestamp (IN _time TIMESTAMP, IN _batch INT)
      |RETURNS SETOF TEST AS
      |$BODY$
      |	SELECT *
      |	FROM TEST
      |	WHERE change_timestamp > _time
      |	ORDER BY change_timestamp ASC
      | LIMIT _batch;
      |$BODY$
      |LANGUAGE sql;
    """.stripMargin
  )
  lazy private val dropIncrementingProcedure = db.prepareStatement("DROP FUNCTION IF EXISTS sp_cdc_incrementing(BIGINT, INT) CASCADE")
  lazy private val createIncrementingProcedure = db.prepareStatement(
    """
      |CREATE OR REPLACE FUNCTION sp_cdc_incrementing (IN _id BIGINT, IN _batch INT)
      |RETURNS SETOF TEST AS
      |$BODY$
      |	SELECT *
      |	FROM TEST
      |	WHERE id > _id
      |	ORDER BY id ASC
      | LIMIT _batch;
      |$BODY$
      |LANGUAGE sql;
    """.stripMargin
  )
  lazy private val dropTimestampIncrementingProcedure = db.prepareStatement("DROP FUNCTION IF EXISTS sp_cdc_timestamp_incrementing(TIMESTAMP, BIGINT, INT) CASCADE")
  lazy private val createTimestampIncrementingProcedure = db.prepareStatement(
    """
      |CREATE OR REPLACE FUNCTION sp_cdc_timestamp_incrementing(_time TIMESTAMP, _id BIGINT, _batch INT)
      |RETURNS SETOF TEST AS
      |$BODY$
      |	SELECT *
      |	FROM TEST
      |	WHERE
      |		(change_timestamp > _time)
      |		OR
      |		(change_timestamp = _time AND id > _id)
      |	ORDER BY change_timestamp, id ASC
      |   LIMIT _batch;
      |$BODY$
      |LANGUAGE sql;
    """.stripMargin
  )
  lazy private val kafka        = new ReactiveKafka()
  lazy private val deserializer = new StringDeserializer()

  override def beforeAll(): Unit = {
    val dbUrl = "jdbc:postgresql://localhost:5432/tempdb?user=tempdb&password=example"
    Class.forName("org.postgresql.Driver").newInstance()
    db = DriverManager.getConnection(dbUrl)
    val UTC_Calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
    insertData.setTimestamp(1,  new Timestamp(1489383225000L), UTC_Calendar)
    insertData.setTimestamp(2,  new Timestamp(1489383229000L), UTC_Calendar)
    insertData.setTimestamp(3,  new Timestamp(1489383231000L), UTC_Calendar)
    insertData.setTimestamp(4,  new Timestamp(1489383231000L), UTC_Calendar)
    insertData.setTimestamp(5,  new Timestamp(1489383237000L), UTC_Calendar)
    insertData.setTimestamp(6,  new Timestamp(1489383241000L), UTC_Calendar)
    insertData.setTimestamp(7,  new Timestamp(1489383242000L), UTC_Calendar)
    insertData.setTimestamp(8,  new Timestamp(1489383244000L), UTC_Calendar)
    insertData.setTimestamp(9,  new Timestamp(1489383246000L), UTC_Calendar)
    insertData.setTimestamp(10, new Timestamp(1489383247000L), UTC_Calendar)
    dropTable.execute()
    createTable.execute()
    insertData.execute()
    dropTimestampProcedure.execute()
    createTimestampProcedure.execute()
    dropIncrementingProcedure.execute()
    createIncrementingProcedure.execute()
    dropTimestampIncrementingProcedure.execute()
    createTimestampIncrementingProcedure.execute()

    publisherTimestamp             = kafka.consume(ConsumerProperties("kafka:9092", "test-timestamp-postgresql", "functional-test-postgresql", deserializer, deserializer))
    publisherIncrementing          = kafka.consume(ConsumerProperties("kafka:9092", "test-incrementing-postgresql", "functional-test-postgresql", deserializer, deserializer))
    publisherTimestampIncrementing = kafka.consume(ConsumerProperties("kafka:9092", "test-timestamp-incrementing-postgresql", "functional-test-postgresql", deserializer, deserializer))  }

  override def afterAll(): Unit = db.close()
}
