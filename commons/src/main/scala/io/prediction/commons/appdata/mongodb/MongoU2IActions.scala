package io.prediction.commons.appdata.mongodb

import io.prediction.commons.MongoUtils.{ attributesToMongoDBObject, emptyObj, getAttributesFromDBObject, idWithAppid }
import io.prediction.commons.appdata.{ U2IAction, U2IActions }

import com.mongodb.casbah.Imports._
import com.mongodb.casbah.commons.conversions.scala._
import com.github.nscala_time.time.Imports._

/** MongoDB implementation of Items. */
class MongoU2IActions(db: MongoDB) extends U2IActions {
  private val u2iActionColl = db("u2iActions")

  RegisterJodaTimeConversionHelpers()

  def insert(u2iAction: U2IAction) = {
    val appid = MongoDBObject("appid" -> u2iAction.appid)
    val action = MongoDBObject("action" -> u2iAction.action)
    val uid = MongoDBObject("uid" -> idWithAppid(u2iAction.appid, u2iAction.uid))
    val iid = MongoDBObject("iid" -> idWithAppid(u2iAction.appid, u2iAction.iid))
    val t = MongoDBObject("t" -> u2iAction.t)
    val lnglat = u2iAction.latlng map { l => MongoDBObject("lnglat" -> MongoDBList(l._2, l._1)) } getOrElse emptyObj
    val v = u2iAction.v map { v => MongoDBObject("v" -> v) } getOrElse emptyObj
    val price = u2iAction.price map { p => MongoDBObject("price" -> p) } getOrElse emptyObj
    val attributes = u2iAction.attributes map { a => attributesToMongoDBObject(a) } getOrElse emptyObj
    u2iActionColl.insert(appid ++ action ++ uid ++ iid ++ t ++ lnglat ++ v ++ price ++ attributes)
  }

  def getAllByAppid(appid: Int) = new MongoU2IActionIterator(u2iActionColl.find(MongoDBObject("appid" -> appid)))

  def getAllByAppidAndUidAndIids(appid: Int, uid: String, iids: Seq[String]) = new MongoU2IActionIterator(
    u2iActionColl.find(MongoDBObject("appid" -> appid, "uid" -> idWithAppid(appid, uid), "iid" -> MongoDBObject("$in" -> iids.map(idWithAppid(appid, _)))))
  )

  def deleteByAppid(appid: Int): Unit = {
    u2iActionColl.remove(MongoDBObject("appid" -> appid))
  }

  def countByAppid(appid: Int): Long = u2iActionColl.count(MongoDBObject("appid" -> appid))

  private def dbObjToU2IAction(dbObj: DBObject) = {
    val appid = dbObj.as[Int]("appid")
    U2IAction(
      appid = appid,
      action = dbObj.as[String]("action"),
      uid = dbObj.as[String]("uid").drop(appid.toString.length + 1),
      iid = dbObj.as[String]("iid").drop(appid.toString.length + 1),
      t = dbObj.as[DateTime]("t"),
      latlng = dbObj.getAs[MongoDBList]("lnglat") map { lnglat => (lnglat(1).asInstanceOf[Double], lnglat(0).asInstanceOf[Double]) },
      v = dbObj.getAs[Int]("v"),
      price = dbObj.getAs[Double]("price"),
      attributes = Option(getAttributesFromDBObject(dbObj)).filter(!_.isEmpty)
    )
  }

  class MongoU2IActionIterator(it: MongoCursor) extends Iterator[U2IAction] {
    def next = dbObjToU2IAction(it.next)
    def hasNext = it.hasNext
  }
}
