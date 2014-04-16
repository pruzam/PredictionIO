package io.prediction.algorithms.mahout.itemrec.svdsgd

import scala.collection.JavaConversions._

import io.prediction.algorithms.mahout.itemrec.MahoutJob
import io.prediction.algorithms.mahout.itemrec.AllPreferredItemsNeighborhoodCandidateItemsStrategy

import org.apache.mahout.cf.taste.model.DataModel
import org.apache.mahout.cf.taste.recommender.Recommender
import org.apache.mahout.cf.taste.impl.recommender.svd.SVDRecommender
import org.apache.mahout.cf.taste.impl.recommender.svd.RatingSGDFactorizer
import org.apache.mahout.cf.taste.impl.recommender.svd.Factorizer

class SVDSGDJob extends MahoutJob {

  override def buildRecommender(dataModel: DataModel, seenDataModel: DataModel, args: Map[String, String]): Recommender = {

    val numFeatures: Int = getArg(args, "numFeatures").toInt
    val learningRate: Double = getArg(args, "learningRate").toDouble
    val preventOverfitting: Double = getArg(args, "preventOverfitting").toDouble
    val randomNoise: Double = getArg(args, "randomNoise").toDouble
    val numIterations: Int = getArg(args, "numIterations").toInt
    val learningRateDecay: Double = getArg(args, "learningRateDecay").toDouble
    val unseenOnly: Boolean = getArgOpt(args, "unseenOnly", "false").toBoolean

    val factorizer: Factorizer = new RatingSGDFactorizer(dataModel, numFeatures, learningRate, preventOverfitting,
      randomNoise, numIterations, learningRateDecay)

    val candidateItemsStrategy = if (unseenOnly)
      new AllPreferredItemsNeighborhoodCandidateItemsStrategy(seenDataModel)
    else
      new AllPreferredItemsNeighborhoodCandidateItemsStrategy()

    val recommender: Recommender = new SVDRecommender(dataModel, factorizer, candidateItemsStrategy)

    recommender
  }
}
