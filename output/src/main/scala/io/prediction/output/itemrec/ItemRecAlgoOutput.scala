package io.prediction.output.itemrec

import io.prediction.commons.Config
import io.prediction.commons.appdata.Items
import io.prediction.commons.modeldata.ItemRecScore
import io.prediction.commons.settings.{ Algo, App, Engine, OfflineEval }

import scala.util.Random

import com.github.nscala_time.time.Imports._

trait ItemRecAlgoOutput {
  /** output the Seq of iids */
  def output(uid: String, n: Int, itypes: Option[Seq[String]])(implicit app: App, algo: Algo, offlineEval: Option[OfflineEval]): Iterator[String]
}

object ItemRecAlgoOutput {
  val config = new Config

  def serendipityN(n: Int)(implicit engine: Engine) = {
    /** Serendipity settings. */
    val serendipity = engine.params.get("serendipity").map(_.asInstanceOf[Int])

    /**
     * Serendipity value (s) from 0-10 in engine settings.
     * Implemented as randomly picking items from top n*(s+1) results.
     */
    serendipity.map { s => n * (s + 1) }.getOrElse(n)
  }

  def serendipityOutput(output: Seq[String], n: Int)(implicit engine: Engine) = {
    val serendipity = engine.params.get("serendipity").map(_.asInstanceOf[Int])
    /** Serendipity output. */
    serendipity.map { s =>
      if (s > 0)
        Random.shuffle(output).take(n)
      else
        output
    } getOrElse output
  }

  def freshnessOutput(output: Seq[String], n: Int)(implicit app: App, engine: Engine, items: Items) = {
    val freshness = engine.params.get("freshness").map(_.asInstanceOf[Int])

    /** Freshness output. */
    freshness map { f =>
      if (f > 0) {
        val freshnessN = scala.math.round(n * f / 10)
        val otherN = n - freshnessN
        val freshnessOutput = items.getRecentByIds(app.id, output).map(_.id)
        val finalFreshnessOutput = freshnessOutput.take(freshnessN)
        val finalFreshnessOutputSet = finalFreshnessOutput.toSet
        finalFreshnessOutput ++ (output filterNot { finalFreshnessOutputSet(_) }).take(otherN)
      } else
        output
    } getOrElse output
  }

  /**
   * The ItemRec output does the following in sequence:
   *
   * - determine capabilities that needs to be handled by the engine
   * - compute the number of items for the engine to postprocess
   * - perform mandatory filtering (geo, time)
   * - perform postprocessing capabilities
   * - output items
   */
  def output(uid: String, n: Int, itypes: Option[Seq[String]],
    latlng: Option[Tuple2[Double, Double]], within: Option[Double],
    unit: Option[String])(implicit app: App, engine: Engine, algo: Algo,
      offlineEval: Option[OfflineEval] = None): Seq[String] = {
    val algoInfos = config.getSettingsAlgoInfos
    implicit val items = offlineEval map { _ =>
      config.getAppdataTrainingItems
    } getOrElse { config.getAppdataItems }

    /**
     * Determine capability of algo to see what this engine output layer needs
     * to handle.
     */
    val engineCapabilities = Seq("serendipity", "freshness")
    val algoCapabilities = algoInfos.get(algo.infoid).map(_.capabilities).
      getOrElse(Seq())
    val handledByEngine = engineCapabilities.filterNot(
      algoCapabilities.contains(_))

    // Determine the number of items to process in the filtering stage.
    val filterN = handledByEngine.foldLeft(n) { (n, cap) =>
      if (cap == "serendipity") serendipityN(n) else n
    }

    /**
     * At the moment, PredictionIO depends only on MongoDB for its model data
     * storage. Since we are still using the legacy longitude-latitude format,
     * the maximum number of documents that can be returned from a query with
     * geospatial constraint is 100. A "manual join" is still feasible with this
     * size.
     */
    val (iids, iidsCopy): (Iterator[String], Iterator[String]) = latlng.map { ll =>
      val geoItems = items.getByAppidAndLatlng(app.id, ll, within, unit).map(_.id).toSet
      // use n = 0 to return all available iids for now
      ItemRecCFAlgoOutput.output(uid, 0, itypes).filter { geoItems(_) }
    }.getOrElse {
      // use n = 0 to return all available iids for now
      ItemRecCFAlgoOutput.output(uid, 0, itypes)
    }.duplicate

    /** Start and end time filtering. */
    val itemsForTimeCheck = items.getByIds(app.id, iidsCopy.toSeq)
    val iidsWithValidTimeSet = (itemsForTimeCheck filter { item =>
      (item.starttime, item.endtime) match {
        case (Some(st), None) => DateTime.now >= st
        case (None, Some(et)) => DateTime.now <= et
        case (Some(st), Some(et)) => st <= DateTime.now && DateTime.now <= et
        case _ => true
      }
    } map { _.id }).toSet
    val iidsWithValidTime: Iterator[String] = iids.filter { iidsWithValidTimeSet(_) }

    /**
     * At this point "output" is guaranteed to have n*(s+1) items (seen or
     * unseen) unless model data is exhausted.
     */
    val output = iidsWithValidTime.take(filterN).toSeq

    val finalOutput = handledByEngine.foldLeft(output) { (output, cap) =>
      cap match {
        case "serendipity" => serendipityOutput(output, n)
        case "freshness" => freshnessOutput(output, n)
      }
    }

    finalOutput
  }
}
