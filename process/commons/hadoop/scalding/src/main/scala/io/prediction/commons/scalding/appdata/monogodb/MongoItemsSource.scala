package io.prediction.commons.scalding.appdata.mongodb

import com.twitter.scalding._

import cascading.pipe.Pipe
import cascading.flow.FlowDef
import cascading.tuple.Tuple

import java.util.ArrayList
import java.util.HashMap

//import org.scala_tools.time.Imports._
//import com.github.nscala_time.time.Imports._
import org.joda.time.DateTime

import com.mongodb.BasicDBList
import com.mongodb.casbah.Imports._
//import com.mongodb.casbah.commons.conversions.scala.RegisterJodaTimeConversionHelpers

import io.prediction.commons.scalding.MongoSource
import io.prediction.commons.scalding.appdata.ItemsSource
import io.prediction.commons.scalding.appdata.ItemsSource.FIELD_SYMBOLS
import io.prediction.commons.appdata.{ Item }

class MongoItemsSource(db: String, hosts: Seq[String], ports: Seq[Int], appid: Int, itypes: Option[List[String]]) extends MongoSource(
  db = db,
  coll = "items",
  cols = {
    val itemsCols = new ArrayList[String]()

    itemsCols.add("_id") // 0
    itemsCols.add("itypes") // 1
    itemsCols.add("appid") // 2
    itemsCols.add("starttime") // 3
    itemsCols.add("ct") // 4
    itemsCols.add("endtime") // 5 optional
    itemsCols.add("inactive") // 6 optional

    itemsCols
  },
  mappings = {
    val itemsMappings = new HashMap[String, String]()

    itemsMappings.put("_id", FIELD_SYMBOLS("id").name)
    itemsMappings.put("itypes", FIELD_SYMBOLS("itypes").name)
    itemsMappings.put("appid", FIELD_SYMBOLS("appid").name)
    itemsMappings.put("starttime", FIELD_SYMBOLS("starttime").name)
    itemsMappings.put("ct", FIELD_SYMBOLS("ct").name)
    itemsMappings.put("endtime", FIELD_SYMBOLS("endtime").name) // optional
    itemsMappings.put("inactive", FIELD_SYMBOLS("inactive").name) // optional

    itemsMappings
  },
  query = { // read query
    val itemsQuery = MongoDBObject("appid" -> appid)

    itemsQuery
  },
  hosts = hosts, // String
  ports = ports // Int
) with ItemsSource {

  val activeItypes: List[String] = itypes.getOrElse(List())

  import com.twitter.scalding.Dsl._ // get all the fancy implicit conversions that define the DSL

  override def getSource: Source = this

  override def readData(iidField: Symbol, itypesField: Symbol)(implicit fd: FlowDef): Pipe = {
    val items = this.read
      .mapTo((0, 1) -> (iidField, itypesField)) { fields: (String, BasicDBList) =>
        // NOTE: convert itypes form BasicDBList to scala List.
        (fields._1, fields._2.toList)
      }

    items
  }

  override def readStartEndtime(iidField: Symbol, itypesField: Symbol, starttimeField: Symbol, endtimeField: Symbol)(implicit fd: FlowDef): Pipe = {
    val items = this.read
      .mapTo((0, 1, 3, 5) -> (iidField, itypesField, starttimeField, endtimeField)) { fields: (String, BasicDBList, java.util.Date, java.util.Date) =>

        //val dt = new DateTime(fields._3)
        val starttime: Long = fields._3.getTime()
        val endtimeOpt: Option[Long] = Option(fields._4).map(_.getTime()) // NOTE: become None if fields._4 is null

        // NOTE: convert itypes form BasicDBList to scala List.
        (fields._1, fields._2.toList, starttime, endtimeOpt)
      }

    items
  }

  override def readObj(objField: Symbol)(implicit fd: FlowDef): Pipe = {
    val items = this.read
      .mapTo((0, 1, 2, 3, 4, 5, 6) -> (objField)) {
        fields: (String, BasicDBList, Int, java.util.Date, java.util.Date, java.util.Date, Any) =>
          val (id, itypes, appid, starttime, ct, endtime, inactiveAny) = fields

          val inactive: Option[Boolean] = inactiveAny match {
            case null => None
            case x: Boolean => Some(x)
            case _ => None
          }

          val _itypes: List[String] = itypes.toList.map(x => x.toString)
          val _inactive: Option[Boolean] = Some(inactive.getOrElse(false) || (!activeItypes.isEmpty && activeItypes.intersect(_itypes).isEmpty))
          Item(
            id = id,
            appid = appid,
            ct = new DateTime(ct),
            itypes = _itypes,
            starttime = Some(new DateTime(starttime)),
            endtime = Option(endtime).map(x => new DateTime(x)),
            price = None,
            profit = None,
            latlng = None,
            inactive = _inactive,
            attributes = None
          )

      }
    items
  }

  override def writeData(iidField: Symbol, itypesField: Symbol, appid: Int)(p: Pipe)(implicit fd: FlowDef): Pipe = {
    val writtenData = p.mapTo((iidField, itypesField) ->
      (FIELD_SYMBOLS("id"), FIELD_SYMBOLS("itypes"), FIELD_SYMBOLS("appid"))) {
      fields: (String, List[String]) =>
        val (iid, itypes) = fields

        val itypesTuple = new Tuple()

        for (x <- itypes) {
          itypesTuple.add(x)
        }

        (iid, itypesTuple, appid)
    }.write(this)

    writtenData
  }

  override def writeObj(objField: Symbol)(p: Pipe)(implicit fd: FlowDef): Pipe = {
    val writtenData = p.mapTo(objField ->
      (FIELD_SYMBOLS("id"), FIELD_SYMBOLS("itypes"), FIELD_SYMBOLS("appid"),
        FIELD_SYMBOLS("starttime"), FIELD_SYMBOLS("ct"),
        FIELD_SYMBOLS("endtime"), FIELD_SYMBOLS("inactive"))) { obj: Item =>

      val itypesTuple = new Tuple()

      for (x <- obj.itypes) {
        itypesTuple.add(x)
      }

      val starttime: java.util.Date = obj.starttime.get.toDate()
      val ct: java.util.Date = obj.ct.toDate()
      val endtime: java.util.Date = obj.endtime.map(_.toDate()).getOrElse(null)
      val inactive: Any = obj.inactive.getOrElse(null)

      (obj.id, itypesTuple, obj.appid, starttime, ct, endtime, inactive)
    }.write(this)

    writtenData
  }

}
