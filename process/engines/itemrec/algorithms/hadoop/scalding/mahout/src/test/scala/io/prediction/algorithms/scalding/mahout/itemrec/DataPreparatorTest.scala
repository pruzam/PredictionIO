package io.prediction.algorithms.scalding.mahout.itemrec

import org.specs2.mutable._

import com.twitter.scalding._

import io.prediction.commons.scalding.appdata.{ Users, Items, U2iActions }
import io.prediction.commons.filepath.DataFile

class DataPreparatorTest extends Specification with TupleConversions {

  val Rate = "rate"
  val Like = "like"
  val Dislike = "dislike"
  val View = "view"
  //val ViewDetails = "viewDetails"
  val Conversion = "conversion"

  val appid = 2

  def test(itypes: List[String], recommendationTime: Long, params: Map[String, String],
    items: List[(String, String, String, String, String, String)], // id, itypes, appid, starttime, ct, endtime
    users: List[Tuple1[String]],
    u2iActions: List[(String, String, String, String, String)],
    ratings: List[(String, String, String)],
    selectedItems: List[(String, String, String, String)], // id, itypes, starttime, endtime
    itemsIndexer: Map[String, String],
    usersIndexer: Map[String, String],
    recommendItems: Option[List[String]] = None,
    seenActions: List[String] = List(),
    seen: List[(String, String)] = List()) = {

    val userIds = users map (x => x._1)
    val selectedItemsTextLine = selectedItems map { x => (itemsIndexer(x._1), x.productIterator.mkString("\t")) }
    val usersTextLine = users map { x => (usersIndexer(x._1), x._1) }

    val itemsIndex = selectedItems map { x => (itemsIndexer(x._1), x._1, x._2, x._3, x._4) }
    val usersIndex = users map { x => (usersIndexer(x._1), x._1) }

    val ratingsIndexed = ratings map { x => (usersIndexer(x._1), itemsIndexer(x._2), x._3) }

    val recommendItemsIndexed = recommendItems.map { x => x.map(y => itemsIndexer(y)) }.getOrElse(List())

    val seenIndexed: List[(String, String)] = if (seen.isEmpty) {
      // if no seen is defined, using rating file as seen file to check
      ratingsIndexed.map(y => (y._1, y._2))
    } else {
      seen.map(y => (usersIndexer(y._1), itemsIndexer(y._2)))
    }
    val dbType = "file"
    val dbName = "testpath/"
    val dbHost = None
    val dbPort = None
    val hdfsRoot = "testroot/"

    val engineid = 4
    val algoid = 5
    val evalid = None

    if (recommendItems == None) {
      JobTest("io.prediction.algorithms.scalding.mahout.itemrec.DataCopy")
        .arg("dbType", dbType)
        .arg("dbName", dbName)
        .arg("hdfsRoot", hdfsRoot)
        .arg("appid", appid.toString)
        .arg("engineid", engineid.toString)
        .arg("algoid", algoid.toString)
        .arg("itypes", itypes)
        .arg("viewParam", params("viewParam"))
        .arg("likeParam", params("likeParam"))
        .arg("dislikeParam", params("dislikeParam"))
        .arg("conversionParam", params("conversionParam"))
        .arg("conflictParam", params("conflictParam"))
        .source(Items(appId = appid, itypes = Some(itypes), dbType = dbType, dbName = dbName, dbHost = dbHost, dbPort = dbPort).getSource, items)
        .source(Users(appId = appid, dbType = dbType, dbName = dbName, dbHost = dbHost, dbPort = dbPort).getSource, users)
        .sink[(String)](Tsv(DataFile(hdfsRoot, appid, engineid, algoid, evalid, "userIds.tsv"))) { outputBuffer =>
          "correctly write userIds.tsv" in {
            outputBuffer.toList must containTheSameElementsAs(userIds)
          }
        }
        .sink[(String, String, String, String)](Tsv(DataFile(hdfsRoot, appid, engineid, algoid, evalid, "selectedItems.tsv"))) { outputBuffer =>
          "correctly write selectedItems.tsv" in {
            outputBuffer.toList must containTheSameElementsAs(selectedItems)
          }
        }
        .run
        .finish
    } else {
      JobTest("io.prediction.algorithms.scalding.mahout.itemrec.DataCopy")
        .arg("dbType", dbType)
        .arg("dbName", dbName)
        .arg("hdfsRoot", hdfsRoot)
        .arg("appid", appid.toString)
        .arg("engineid", engineid.toString)
        .arg("algoid", algoid.toString)
        .arg("itypes", itypes)
        .arg("viewParam", params("viewParam"))
        .arg("likeParam", params("likeParam"))
        .arg("dislikeParam", params("dislikeParam"))
        .arg("conversionParam", params("conversionParam"))
        .arg("conflictParam", params("conflictParam"))
        .arg("recommendationTime", recommendationTime.toString)
        .source(Items(appId = appid, itypes = Some(itypes), dbType = dbType, dbName = dbName, dbHost = dbHost, dbPort = dbPort).getSource, items)
        .source(Users(appId = appid, dbType = dbType, dbName = dbName, dbHost = dbHost, dbPort = dbPort).getSource, users)
        .sink[(String)](Tsv(DataFile(hdfsRoot, appid, engineid, algoid, evalid, "userIds.tsv"))) { outputBuffer =>
          "correctly write userIds.tsv" in {
            outputBuffer.toList must containTheSameElementsAs(userIds)
          }
        }
        .sink[(String, String, String, String)](Tsv(DataFile(hdfsRoot, appid, engineid, algoid, evalid, "selectedItems.tsv"))) { outputBuffer =>
          "correctly write selectedItems.tsv" in {
            outputBuffer.toList must containTheSameElementsAs(selectedItems)
          }
        }
        .run
        .finish
    }

    if (recommendItems == None) {
      JobTest("io.prediction.algorithms.scalding.mahout.itemrec.DataPreparator")
        .arg("dbType", dbType)
        .arg("dbName", dbName)
        .arg("hdfsRoot", hdfsRoot)
        .arg("appid", appid.toString)
        .arg("engineid", engineid.toString)
        .arg("algoid", algoid.toString)
        .arg("itypes", itypes)
        .arg("viewParam", params("viewParam"))
        .arg("likeParam", params("likeParam"))
        .arg("dislikeParam", params("dislikeParam"))
        .arg("conversionParam", params("conversionParam"))
        .arg("conflictParam", params("conflictParam"))
        .source(U2iActions(appId = appid, dbType = dbType, dbName = dbName, dbHost = dbHost, dbPort = dbPort).getSource, u2iActions)
        .source(TextLine(DataFile(hdfsRoot, appid, engineid, algoid, evalid, "selectedItems.tsv")), selectedItemsTextLine)
        .source(TextLine(DataFile(hdfsRoot, appid, engineid, algoid, evalid, "userIds.tsv")), usersTextLine)
        .sink[(String, String, String, String, String)](Tsv(DataFile(hdfsRoot, appid, engineid, algoid, evalid, "itemsIndex.tsv"))) { outputBuffer =>
          // index, iid, itypes
          "correctly write itemsIndex.tsv" in {
            outputBuffer.toList must containTheSameElementsAs(itemsIndex)
          }
        }
        .sink[(String, String)](Tsv(DataFile(hdfsRoot, appid, engineid, algoid, evalid, "usersIndex.tsv"))) { outputBuffer =>
          // index, uid
          "correctly write usersIndex.tsv" in {
            outputBuffer.toList must containTheSameElementsAs(usersIndex)
          }
        }
        .sink[(String, String, String)](Csv(DataFile(hdfsRoot, appid, engineid, algoid, evalid, "ratings.csv"))) { outputBuffer =>
          "correctly process and write data to ratings.csv" in {
            outputBuffer.toList must containTheSameElementsAs(ratingsIndexed)
          }
        }
        .sink[(String, String)](Tsv(DataFile(hdfsRoot, appid, engineid, algoid, evalid, "seen.tsv"))) { outputBuffer =>
          "correctly write data to seen.tsv" in {
            outputBuffer.toList must containTheSameElementsAs(seenIndexed)
          }
        }
        .run
        .finish
    } else if (!seenActions.isEmpty) {
      JobTest("io.prediction.algorithms.scalding.mahout.itemrec.DataPreparator")
        .arg("dbType", dbType)
        .arg("dbName", dbName)
        .arg("hdfsRoot", hdfsRoot)
        .arg("appid", appid.toString)
        .arg("engineid", engineid.toString)
        .arg("algoid", algoid.toString)
        .arg("itypes", itypes)
        .arg("viewParam", params("viewParam"))
        .arg("likeParam", params("likeParam"))
        .arg("dislikeParam", params("dislikeParam"))
        .arg("conversionParam", params("conversionParam"))
        .arg("conflictParam", params("conflictParam"))
        .arg("recommendationTime", recommendationTime.toString)
        .arg("seenActions", seenActions)
        .source(U2iActions(appId = appid, dbType = dbType, dbName = dbName, dbHost = dbHost, dbPort = dbPort).getSource, u2iActions)
        .source(TextLine(DataFile(hdfsRoot, appid, engineid, algoid, evalid, "selectedItems.tsv")), selectedItemsTextLine)
        .source(TextLine(DataFile(hdfsRoot, appid, engineid, algoid, evalid, "userIds.tsv")), usersTextLine)
        .sink[(String, String, String, String, String)](Tsv(DataFile(hdfsRoot, appid, engineid, algoid, evalid, "itemsIndex.tsv"))) { outputBuffer =>
          // index, iid, itypes
          "correctly write itemsIndex.tsv" in {
            outputBuffer.toList must containTheSameElementsAs(itemsIndex)
          }
        }
        .sink[(String, String)](Tsv(DataFile(hdfsRoot, appid, engineid, algoid, evalid, "usersIndex.tsv"))) { outputBuffer =>
          // index, uid
          "correctly write usersIndex.tsv" in {
            outputBuffer.toList must containTheSameElementsAs(usersIndex)
          }
        }
        .sink[(String, String, String)](Csv(DataFile(hdfsRoot, appid, engineid, algoid, evalid, "ratings.csv"))) { outputBuffer =>
          "correctly process and write data to ratings.csv" in {
            outputBuffer.toList must containTheSameElementsAs(ratingsIndexed)
          }
        }
        .sink[(String)](Csv(DataFile(hdfsRoot, appid, engineid, algoid, evalid, "recommendItems.csv"))) { outputBuffer =>
          "correctly process and write data to recomomendItems.csv" in {
            outputBuffer.toList must containTheSameElementsAs(recommendItemsIndexed)
          }
        }
        .sink[(String, String)](Tsv(DataFile(hdfsRoot, appid, engineid, algoid, evalid, "seen.tsv"))) { outputBuffer =>
          "correctly write data to seen.tsv" in {
            outputBuffer.toList must containTheSameElementsAs(seenIndexed)
          }
        }
        .run
        .finish
    } else {
      JobTest("io.prediction.algorithms.scalding.mahout.itemrec.DataPreparator")
        .arg("dbType", dbType)
        .arg("dbName", dbName)
        .arg("hdfsRoot", hdfsRoot)
        .arg("appid", appid.toString)
        .arg("engineid", engineid.toString)
        .arg("algoid", algoid.toString)
        .arg("itypes", itypes)
        .arg("viewParam", params("viewParam"))
        .arg("likeParam", params("likeParam"))
        .arg("dislikeParam", params("dislikeParam"))
        .arg("conversionParam", params("conversionParam"))
        .arg("conflictParam", params("conflictParam"))
        .arg("recommendationTime", recommendationTime.toString)
        .source(U2iActions(appId = appid, dbType = dbType, dbName = dbName, dbHost = dbHost, dbPort = dbPort).getSource, u2iActions)
        .source(TextLine(DataFile(hdfsRoot, appid, engineid, algoid, evalid, "selectedItems.tsv")), selectedItemsTextLine)
        .source(TextLine(DataFile(hdfsRoot, appid, engineid, algoid, evalid, "userIds.tsv")), usersTextLine)
        .sink[(String, String, String, String, String)](Tsv(DataFile(hdfsRoot, appid, engineid, algoid, evalid, "itemsIndex.tsv"))) { outputBuffer =>
          // index, iid, itypes
          "correctly write itemsIndex.tsv" in {
            outputBuffer.toList must containTheSameElementsAs(itemsIndex)
          }
        }
        .sink[(String, String)](Tsv(DataFile(hdfsRoot, appid, engineid, algoid, evalid, "usersIndex.tsv"))) { outputBuffer =>
          // index, uid
          "correctly write usersIndex.tsv" in {
            outputBuffer.toList must containTheSameElementsAs(usersIndex)
          }
        }
        .sink[(String, String, String)](Csv(DataFile(hdfsRoot, appid, engineid, algoid, evalid, "ratings.csv"))) { outputBuffer =>
          "correctly process and write data to ratings.csv" in {
            outputBuffer.toList must containTheSameElementsAs(ratingsIndexed)
          }
        }
        .sink[(String)](Csv(DataFile(hdfsRoot, appid, engineid, algoid, evalid, "recommendItems.csv"))) { outputBuffer =>
          "correctly process and write data to recomomendItems.csv" in {
            outputBuffer.toList must containTheSameElementsAs(recommendItemsIndexed)
          }
        }
        .sink[(String, String)](Tsv(DataFile(hdfsRoot, appid, engineid, algoid, evalid, "seen.tsv"))) { outputBuffer =>
          "correctly write data to seen.tsv" in {
            outputBuffer.toList must containTheSameElementsAs(seenIndexed)
          }
        }
        .run
        .finish
    }
  }

  val noEndtime = "PIO_NONE"
  /**
   * Test 1. basic. Rate actions only without conflicts
   */
  val test1AllItypes = List("t1", "t2", "t3", "t4")
  val test1ItemsMap = Map(
    // id, itypes, appid, starttime, ct, endtime
    "i0" -> ("i0", "t1,t2,t3", appid.toString, "12345", "12346", noEndtime),
    "i1" -> ("i1", "t2,t3", appid.toString, "12347", "12348", noEndtime),
    "i2" -> ("i2", "t4", appid.toString, "12349", "12350", noEndtime),
    "i3" -> ("i3", "t3,t4", appid.toString, "12351", "12352", noEndtime))

  val test1Items = List(
    test1ItemsMap("i0"),
    test1ItemsMap("i1"),
    test1ItemsMap("i2"),
    test1ItemsMap("i3"))

  val test1RecommendItems = Some(List("i0", "i1", "i2", "i3"))

  def genSelectedItems(items: List[(String, String, String, String, String, String)]) = {
    items map { x =>
      val (id, itypes, appid, starttime, ct, endtime) = x
      (id, itypes, starttime, endtime)
    }
  }

  val test1ItemsIndexer = Map("i0" -> "0", "i1" -> "4", "i2" -> "7", "i3" -> "8") // map iid to index

  val test1Users = List(Tuple1("u0"), Tuple1("u1"), Tuple1("u2"), Tuple1("u3"))
  val test1UsersIndexer = Map("u0" -> "0", "u1" -> "1", "u2" -> "2", "u3" -> "3") // map uid to index

  val test1U2i = List(
    (Rate, "u0", "i0", "123450", "3"),
    (Rate, "u0", "i1", "123457", "1"),
    (Rate, "u0", "i2", "123458", "4"),
    (Rate, "u0", "i3", "123459", "2"),
    (Rate, "u1", "i0", "123457", "5"),
    (Rate, "u1", "i1", "123458", "2"))

  val test1Ratings = List(
    ("u0", "i0", "3"),
    ("u0", "i1", "1"),
    ("u0", "i2", "4"),
    ("u0", "i3", "2"),
    ("u1", "i0", "5"),
    ("u1", "i1", "2"))

  val test1Params: Map[String, String] = Map("viewParam" -> "3", "likeParam" -> "4", "dislikeParam" -> "1", "conversionParam" -> "5",
    "conflictParam" -> "latest")

  "DataPreparator with only rate actions, all itypes, no conflict" should {
    test(test1AllItypes, 20000, test1Params, test1Items, test1Users, test1U2i, test1Ratings, genSelectedItems(test1Items), test1ItemsIndexer, test1UsersIndexer, test1RecommendItems)
  }

  "DataPreparator with only rate actions, no itypes specified, no conflict" should {
    test(List(), 20000, test1Params, test1Items, test1Users, test1U2i, test1Ratings, genSelectedItems(test1Items), test1ItemsIndexer, test1UsersIndexer, test1RecommendItems)
  }

  "DataPreparator with only rate actions, no itypes specified, no conflict, without recommendItems arg" should {
    test(List(), 20000, test1Params, test1Items, test1Users, test1U2i, test1Ratings, genSelectedItems(test1Items), test1ItemsIndexer, test1UsersIndexer, None)
  }

  /**
   * Test 2. rate actions only with conflicts
   */
  val test2AllItypes = List("t1", "t2", "t3", "t4")
  val test2ItemsMap = Map(
    // id, itypes, appid, starttime, ct, endtime
    "i0" -> ("i0", "t1,t2,t3", appid.toString, "12345", "12346", noEndtime),
    "i1" -> ("i1", "t2,t3", appid.toString, "12347", "12348", noEndtime),
    "i2" -> ("i2", "t4", appid.toString, "12349", "12350", noEndtime),
    "i3" -> ("i3", "t3,t4", appid.toString, "12351", "12352", noEndtime))

  val test2Items = List(
    test2ItemsMap("i0"),
    test2ItemsMap("i1"),
    test2ItemsMap("i2"),
    test2ItemsMap("i3"))

  val test2RecommendItems = Some(List("i0", "i1", "i2", "i3"))

  val test2ItemsIndexer = Map("i0" -> "0", "i1" -> "4", "i2" -> "7", "i3" -> "8") // map iid to index

  val test2Users = List(Tuple1("u0"), Tuple1("u1"), Tuple1("u2"), Tuple1("u3"))
  val test2UsersIndexer = Map("u0" -> "0", "u1" -> "1", "u2" -> "2", "u3" -> "3") // map uid to index

  val test2U2i = List(
    (Rate, "u0", "i0", "123448", "3"),
    (Rate, "u0", "i0", "123449", "4"), // highest
    (Rate, "u0", "i0", "123451", "2"), // latest 
    (Rate, "u0", "i0", "123450", "1"), // lowest

    (Rate, "u0", "i1", "123456", "1"), // lowest
    (Rate, "u0", "i1", "123457", "2"),
    (Rate, "u0", "i1", "123458", "3"), // latest, highest

    (Rate, "u0", "i2", "123461", "2"), // latest, lowest
    (Rate, "u0", "i2", "123459", "3"),
    (Rate, "u0", "i2", "123460", "5"), // highest

    (Rate, "u0", "i3", "123459", "2"),
    (Rate, "u1", "i0", "123457", "5"),

    (Rate, "u1", "i1", "123458", "3"), // lowest
    (Rate, "u1", "i1", "123459", "4"), // highest
    (Rate, "u1", "i1", "123460", "3")) // latest, lowest

  val test2RatingsLatest = List(
    ("u0", "i0", "2"),
    ("u0", "i1", "3"),
    ("u0", "i2", "2"),
    ("u0", "i3", "2"),
    ("u1", "i0", "5"),
    ("u1", "i1", "3"))

  val test2RatingsHighest = List(
    ("u0", "i0", "4"),
    ("u0", "i1", "3"),
    ("u0", "i2", "5"),
    ("u0", "i3", "2"),
    ("u1", "i0", "5"),
    ("u1", "i1", "4"))

  val test2RatingsLowest = List(
    ("u0", "i0", "1"),
    ("u0", "i1", "1"),
    ("u0", "i2", "2"),
    ("u0", "i3", "2"),
    ("u1", "i0", "5"),
    ("u1", "i1", "3"))

  val test2Itypes_t1t4 = List("t1", "t4")
  val test2Items_t1t4 = List(
    test2ItemsMap("i0"),
    test2ItemsMap("i2"),
    test2ItemsMap("i3"))

  val test2RecommendItems_t1t4 = Some(List("i0", "i2", "i3"))

  val test2RatingsHighest_t1t4 = List(
    ("u0", "i0", "4"),
    ("u0", "i2", "5"),
    ("u0", "i3", "2"),
    ("u1", "i0", "5"))

  val test2Params: Map[String, String] = Map("viewParam" -> "3", "likeParam" -> "4", "dislikeParam" -> "1", "conversionParam" -> "5",
    "conflictParam" -> "latest")
  val test2ParamsHighest = test2Params + ("conflictParam" -> "highest")
  val test2ParamsLowest = test2Params + ("conflictParam" -> "lowest")

  "DataPreparator with only rate actions, all itypes, conflict=latest" should {
    test(test2AllItypes, 20000, test2Params, test2Items, test2Users, test2U2i, test2RatingsLatest, genSelectedItems(test2Items), test2ItemsIndexer, test2UsersIndexer, test2RecommendItems)
  }

  "DataPreparator with only rate actions, all itypes, conflict=highest" should {
    test(test2AllItypes, 20000, test2ParamsHighest, test2Items, test2Users, test2U2i, test2RatingsHighest, genSelectedItems(test2Items), test2ItemsIndexer, test2UsersIndexer, test2RecommendItems)
  }

  "DataPreparator with only rate actions, all itypes, conflict=lowest" should {
    test(test2AllItypes, 20000, test2ParamsLowest, test2Items, test2Users, test2U2i, test2RatingsLowest, genSelectedItems(test2Items), test2ItemsIndexer, test2UsersIndexer, test2RecommendItems)
  }

  "DataPreparator with only rate actions, some itypes, conflict=highest" should {
    test(test2Itypes_t1t4, 20000, test2ParamsHighest, test2Items, test2Users, test2U2i, test2RatingsHighest_t1t4, genSelectedItems(test2Items_t1t4), test2ItemsIndexer, test2UsersIndexer, test2RecommendItems_t1t4)
  }

  /**
   * Test 3. Different Actions without conflicts and endtime
   */
  val test3AllItypes = List("t1", "t2", "t3", "t4")
  val test3ItemsMap = Map(
    // id, itypes, appid, starttime, ct, endtime
    "i0" -> ("i0", "t1,t2,t3", appid.toString, "12345", "12346", "56789"),
    "i1" -> ("i1", "t2,t3", appid.toString, "12347", "12348", noEndtime),
    "i2" -> ("i2", "t4", appid.toString, "12349", "12350", "56790"),
    "i3" -> ("i3", "t3,t4", appid.toString, "12351", "12352", noEndtime))

  val test3Items = List(
    test3ItemsMap("i0"),
    test3ItemsMap("i1"),
    test3ItemsMap("i2"),
    test3ItemsMap("i3"))

  val test3RecommendItems = Some(List("i0", "i1", "i2", "i3"))

  val test3ItemsIndexer = Map("i0" -> "0", "i1" -> "4", "i2" -> "7", "i3" -> "8") // map iid to index

  val test3Users = List(Tuple1("u0"), Tuple1("u1"), Tuple1("u2"), Tuple1("u3"))
  val test3UsersIndexer = Map("u0" -> "0", "u1" -> "1", "u2" -> "2", "u3" -> "3") // map uid to index

  val test3U2i = List(
    (Rate, "u0", "i0", "123450", "4"),
    (Like, "u0", "i1", "123457", "PIO_NONE"),
    (Dislike, "u0", "i2", "123458", "PIO_NONE"),
    (View, "u0", "i3", "123459", "PIO_NONE"), // NOTE: assume v field won't be missing
    (Rate, "u1", "i0", "123457", "2"),
    (Conversion, "u1", "i1", "123458", "PIO_NONE"))

  val test3Ratings = List(
    ("u0", "i0", "4"),
    ("u0", "i1", "4"),
    ("u0", "i2", "2"),
    ("u0", "i3", "1"),
    ("u1", "i0", "2"),
    ("u1", "i1", "5"))

  val test3Params: Map[String, String] = Map("viewParam" -> "1", "likeParam" -> "4", "dislikeParam" -> "2", "conversionParam" -> "5",
    "conflictParam" -> "latest")

  "DataPreparator with only all actions, all itypes, no conflict" should {
    test(test3AllItypes, 20000, test3Params, test3Items, test3Users, test3U2i, test3Ratings, genSelectedItems(test3Items), test3ItemsIndexer, test3UsersIndexer, test3RecommendItems)
  }

  /**
   * test 4. Different Actions with conflicts and endtime
   */
  val test4Params: Map[String, String] = Map("viewParam" -> "2", "likeParam" -> "5", "dislikeParam" -> "1", "conversionParam" -> "4",
    "conflictParam" -> "latest")

  val test4AllItypes = List("t1", "t2", "t3", "t4")
  val test4ItemsMap = Map(
    // id, itypes, appid, starttime, ct, endtime
    "i0" -> ("i0", "t1,t2,t3", appid.toString, "12345", "12346", "56789"),
    "i1" -> ("i1", "t2,t3", appid.toString, "12347", "12348", noEndtime),
    "i2" -> ("i2", "t4", appid.toString, "12349", "12350", "56790"),
    "i3" -> ("i3", "t3,t4", appid.toString, "12351", "12352", noEndtime))

  val test4Items = List(
    test4ItemsMap("i0"),
    test4ItemsMap("i1"),
    test4ItemsMap("i2"),
    test4ItemsMap("i3"))

  val test4RecommendItems = Some(List("i0", "i1", "i2", "i3"))

  val test4ItemsIndexer = Map("i0" -> "0", "i1" -> "4", "i2" -> "7", "i3" -> "8") // map iid to index

  val test4Users = List(Tuple1("u0"), Tuple1("u1"), Tuple1("u2"), Tuple1("u3"))
  val test4UsersIndexer = Map("u0" -> "0", "u1" -> "1", "u2" -> "2", "u3" -> "3") // map uid to index

  val test4U2i = List(
    (Rate, "u0", "i0", "123448", "3"),
    (View, "u0", "i0", "123449", "PIO_NONE"), // lowest (2)
    (Like, "u0", "i0", "123451", "PIO_NONE"), // latest, highest (5)
    (Conversion, "u0", "i0", "123450", "PIO_NONE"),

    (Rate, "u0", "i1", "123456", "1"), // lowest
    (Rate, "u0", "i1", "123457", "4"), // highest
    (View, "u0", "i1", "123458", "PIO_NONE"), // latest (2)

    (Conversion, "u0", "i2", "123461", "PIO_NONE"), // latest, highest  (4)
    (Rate, "u0", "i2", "123459", "3"),
    (View, "u0", "i2", "123460", "PIO_NONE"), // lowest

    (Rate, "u0", "i3", "123459", "2"),
    (View, "u1", "i0", "123457", "PIO_NONE"), // (2)

    (Rate, "u1", "i1", "123458", "5"), // highest
    (Conversion, "u1", "i1", "123459", "PIO_NONE"), // (4)
    (Dislike, "u1", "i1", "123460", "PIO_NONE")) // latest, lowest (1)

  val test4RatingsLatest = List(
    ("u0", "i0", "5"),
    ("u0", "i1", "2"),
    ("u0", "i2", "4"),
    ("u0", "i3", "2"),
    ("u1", "i0", "2"),
    ("u1", "i1", "1"))

  "DataPreparator with all actions, all itypes, and conflicts=latest" should {
    test(test4AllItypes, 20000, test4Params, test4Items, test4Users, test4U2i, test4RatingsLatest, genSelectedItems(test4Items), test4ItemsIndexer, test4UsersIndexer, test4RecommendItems)
  }

  val test4ParamsIgnoreView = test4Params + ("viewParam" -> "ignore")

  val test4RatingsIgnoreViewLatest = List(
    ("u0", "i0", "5"),
    ("u0", "i1", "4"),
    ("u0", "i2", "4"),
    ("u0", "i3", "2"),
    ("u1", "i1", "1"))

  // seen won't be affacted by the ignore setting
  val test4Seen = List(
    ("u0", "i0"),
    ("u0", "i1"),
    ("u0", "i2"),
    ("u0", "i3"),
    ("u1", "i0"),
    ("u1", "i1"))

  "DataPreparator with all actions, all itypes, ignore View actions and conflicts=latest" should {
    test(test4AllItypes, 20000, test4ParamsIgnoreView, test4Items, test4Users, test4U2i, test4RatingsIgnoreViewLatest, genSelectedItems(test4Items), test4ItemsIndexer, test4UsersIndexer, test4RecommendItems, seen = test4Seen)
  }

  // note: currently rate action can't be ignored
  val test4ParamsIgnoreAllExceptView = test4Params + ("viewParam" -> "1", "likeParam" -> "ignore", "dislikeParam" -> "ignore", "conversionParam" -> "ignore")

  val test4RatingsIgnoreAllExceptViewLatest = List(
    ("u0", "i0", "1"),
    ("u0", "i1", "1"),
    ("u0", "i2", "1"),
    ("u0", "i3", "2"),
    ("u1", "i0", "1"),
    ("u1", "i1", "5"))

  "DataPreparator with all actions, all itypes, ignore all actions except View (and Rate) and conflicts=latest" should {
    test(test4AllItypes, 20000, test4ParamsIgnoreAllExceptView, test4Items, test4Users, test4U2i, test4RatingsIgnoreAllExceptViewLatest, genSelectedItems(test4Items), test4ItemsIndexer, test4UsersIndexer, test4RecommendItems, seen = test4Seen)
  }

  // note: meaning rate action only
  val test4ParamsIgnoreAll = test4Params + ("viewParam" -> "ignore", "likeParam" -> "ignore", "dislikeParam" -> "ignore", "conversionParam" -> "ignore")

  val test4RatingsIgnoreAllLatest = List(
    ("u0", "i0", "3"),
    ("u0", "i1", "4"),
    ("u0", "i2", "3"),
    ("u0", "i3", "2"),
    ("u1", "i1", "5"))

  "DataPreparator with all actions, all itypes, ignore all actions (except Rate) and conflicts=latest" should {
    test(test4AllItypes, 20000, test4ParamsIgnoreAll, test4Items, test4Users, test4U2i, test4RatingsIgnoreAllLatest, genSelectedItems(test4Items), test4ItemsIndexer, test4UsersIndexer, test4RecommendItems, seen = test4Seen)
  }

  val test4ParamsLowest: Map[String, String] = test4Params + ("conflictParam" -> "lowest")

  val test4Itypes_t3 = List("t3")
  val test4Items_t3 = List(
    test4ItemsMap("i0"),
    test4ItemsMap("i1"),
    test4ItemsMap("i3"))

  val test4RecommendItems_t3 = Some(List("i0", "i1", "i3"))

  val test4RatingsLowest_t3 = List(
    ("u0", "i0", "2"),
    ("u0", "i1", "1"),
    ("u0", "i3", "2"),
    ("u1", "i0", "2"),
    ("u1", "i1", "1"))

  "DataPreparator with only all actions, some itypes, and conflicts=lowest" should {
    test(test4Itypes_t3, 20000, test4ParamsLowest, test4Items, test4Users, test4U2i, test4RatingsLowest_t3, genSelectedItems(test4Items_t3), test4ItemsIndexer, test4UsersIndexer, test4RecommendItems_t3)
  }

  /* test5: test starttime and endtime */

  // starttime, endtime
  // i0  A |---------|
  // i1    B |---------|E
  // i2       C|---------|
  // i3           |---------|
  //               D        F G  

  val tA = 123122
  val tB = 123123
  val tC = 123457
  val tD = 123679
  val tE = 543322
  val tF = 543654
  val tG = 543655

  val test5AllItypes = List("t1", "t2", "t3", "t4")
  val test5ItemsMap = Map(
    // id, itypes, appid, starttime, ct, endtime
    "i0" -> ("i0", "t1,t2,t3", appid.toString, "123123", "12345", "543210"),
    "i1" -> ("i1", "t1,t2", appid.toString, "123456", "12345", "543321"),
    "i2" -> ("i2", "t2,t3", appid.toString, "123567", "12345", "543432"),
    "i3" -> ("i3", "t2", appid.toString, "123678", "12345", "543654")
  )

  val test5Items = List(
    test5ItemsMap("i0"),
    test5ItemsMap("i1"),
    test5ItemsMap("i2"),
    test5ItemsMap("i3"))

  val test5ItemsIndexer = Map("i0" -> "0", "i1" -> "4", "i2" -> "7", "i3" -> "8") // map iid to index

  val test5Users = List(Tuple1("u0"), Tuple1("u1"), Tuple1("u2"), Tuple1("u3"))
  val test5UsersIndexer = Map("u0" -> "0", "u1" -> "1", "u2" -> "2", "u3" -> "3") // map uid to index

  val test5U2i = List(
    (Rate, "u0", "i0", "123450", "4"),
    (Like, "u0", "i1", "123457", "PIO_NONE"),
    (Dislike, "u0", "i2", "123458", "PIO_NONE"),
    (View, "u0", "i3", "123459", "PIO_NONE"), // NOTE: assume v field won't be missing
    (Rate, "u1", "i0", "123457", "2"),
    (Conversion, "u1", "i1", "123458", "PIO_NONE"))

  val test5Ratings = List(
    ("u0", "i0", "4"),
    ("u0", "i1", "4"),
    ("u0", "i2", "2"),
    ("u0", "i3", "1"),
    ("u1", "i0", "2"),
    ("u1", "i1", "5"))

  val test5RecommendItems = Some(List("i0", "i1", "i2", "i3"))
  val test5RecommendItemsEmpty = Some(List())
  val test5RecommendItemsi0 = Some(List("i0"))
  val test5RecommendItemsi0i1 = Some(List("i0", "i1"))
  val test5RecommendItemsi2i3 = Some(List("i2", "i3"))

  val test5Params: Map[String, String] = Map("viewParam" -> "1", "likeParam" -> "4", "dislikeParam" -> "2", "conversionParam" -> "5",
    "conflictParam" -> "latest")

  "recommendationTime < all item starttime" should {
    test(test5AllItypes, tA, test5Params, test5Items, test5Users, test5U2i, test5Ratings, genSelectedItems(test5Items), test5ItemsIndexer, test5UsersIndexer, test5RecommendItemsEmpty)
  }

  "recommendationTime == earliest starttime" should {
    test(test5AllItypes, tB, test5Params, test5Items, test5Users, test5U2i, test5Ratings, genSelectedItems(test5Items), test5ItemsIndexer, test5UsersIndexer, test5RecommendItemsi0)
  }

  "recommendationTime > some items starttime" should {
    test(test5AllItypes, tC, test5Params, test5Items, test5Users, test5U2i, test5Ratings, genSelectedItems(test5Items), test5ItemsIndexer, test5UsersIndexer, test5RecommendItemsi0i1)
  }

  "recommendationTime > all item starttime and < all item endtime" should {
    test(test5AllItypes, tD, test5Params, test5Items, test5Users, test5U2i, test5Ratings, genSelectedItems(test5Items), test5ItemsIndexer, test5UsersIndexer, test5RecommendItems)
  }

  "recommendationTime > some item endtime" should {
    test(test5AllItypes, tE, test5Params, test5Items, test5Users, test5U2i, test5Ratings, genSelectedItems(test5Items), test5ItemsIndexer, test5UsersIndexer, test5RecommendItemsi2i3)
  }

  "recommendationTime == last item endtime" should {
    test(test5AllItypes, tF, test5Params, test5Items, test5Users, test5U2i, test5Ratings, genSelectedItems(test5Items), test5ItemsIndexer, test5UsersIndexer, test5RecommendItemsEmpty)
  }

  "recommendationTime > last item endtime" should {
    test(test5AllItypes, tG, test5Params, test5Items, test5Users, test5U2i, test5Ratings, genSelectedItems(test5Items), test5ItemsIndexer, test5UsersIndexer, test5RecommendItemsEmpty)
  }

  /* test6: seen action */
  val test6AllItypes = List("t1", "t2", "t3", "t4")
  val test6ItemsMap = Map(
    // id, itypes, appid, starttime, ct, endtime
    "i0" -> ("i0", "t1,t2,t3", appid.toString, "12345", "12346", "56789"),
    "i1" -> ("i1", "t2,t3", appid.toString, "12347", "12348", noEndtime),
    "i2" -> ("i2", "t4", appid.toString, "12349", "12350", "56790"),
    "i3" -> ("i3", "t3,t4", appid.toString, "12351", "12352", noEndtime))

  val test6Items = List(
    test6ItemsMap("i0"),
    test6ItemsMap("i1"),
    test6ItemsMap("i2"),
    test6ItemsMap("i3"))

  val test6RecommendItems = Some(List("i0", "i1", "i2", "i3"))

  val test6ItemsIndexer = Map("i0" -> "0", "i1" -> "4", "i2" -> "7", "i3" -> "8") // map iid to index

  val test6Users = List(Tuple1("u0"), Tuple1("u1"), Tuple1("u2"), Tuple1("u3"))
  val test6UsersIndexer = Map("u0" -> "0", "u1" -> "1", "u2" -> "2", "u3" -> "3") // map uid to index

  val test6U2i = List(
    (Rate, "u0", "i0", "123450", "4"),
    (Like, "u0", "i1", "123457", "PIO_NONE"),
    (Dislike, "u0", "i2", "123458", "PIO_NONE"),
    (View, "u0", "i3", "123459", "PIO_NONE"), // NOTE: assume v field won't be missing
    (Rate, "u1", "i0", "123457", "2"),
    (Conversion, "u1", "i1", "123458", "PIO_NONE"),
    ("action1", "u0", "i2", "123459", "PIO_NONE"),
    ("action2", "u1", "i1", "123459", "PIO_NONE"),
    ("action1", "u2", "i0", "123459", "PIO_NONE"))

  val test6Ratings = List(
    ("u0", "i0", "4"),
    ("u0", "i1", "4"),
    ("u0", "i2", "2"),
    ("u0", "i3", "1"),
    ("u1", "i0", "2"),
    ("u1", "i1", "5"))

  val test6seenActions = List("action1", "action2")
  val test6Seen = List(
    ("u0", "i2"),
    ("u1", "i1"),
    ("u2", "i0"))

  val test6Params: Map[String, String] = Map("viewParam" -> "1", "likeParam" -> "4", "dislikeParam" -> "2", "conversionParam" -> "5",
    "conflictParam" -> "latest")

  "DataPreparator with only all actions, all itypes, no conflict, and seenActions" should {
    test(test6AllItypes, 20000, test6Params, test6Items, test6Users, test6U2i, test6Ratings, genSelectedItems(test6Items), test6ItemsIndexer, test6UsersIndexer, test6RecommendItems, test6seenActions, test6Seen)
  }

}
