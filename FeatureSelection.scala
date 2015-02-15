package ml

/**
 * Created by Diego on 12/31/14.

 * Feature selection
 * Currently supported algorithms:
 * Greedy based on information gain and Mimimum-Redundancy Maximum-Relevancy
 */

import scala.collection.mutable.MutableList

import org.apache.spark.rdd.RDD
import org.apache.spark.mllib.regression.{LabeledPoint}
import util.BinarySearch

// buckets: ordered values of doubles specifying how values are bucketed; each value must fall into one of the buckets (meaning that the first value must be below the minimum value and the
// last values above the maximum value; these conditions are not checked)

class FeatureSelection(rdd:RDD[LabeledPoint],labelBuckets:Array[Double],featuresBuckets:Array[Array[Double]],noRecords:Long) {

  private val noFeatures = featuresBuckets.size
  // features selected: false if feature not selected; true if selected
  private val selectedFeatures = Array.fill[Boolean](noFeatures)(false)
  // informationGainsBeetweenFeatures[(feature index 1, feature index 2)] = information gain of these two features
  // we use lazy evaluation
  private val informationGainsBetweenFeatures = scala.collection.mutable.Map[(Int,Int),Double]()

  // relevancy - redundacy values in the order of selection to the feature's set: value,feature index
  private val internalRelevancyRedundacyValues:MutableList[(Double,Int)] = MutableList()
  lazy val relevancyRedundacyValues:List[(Double,Int)] = internalRelevancyRedundacyValues.toList

  /*
   * mrmr algorithm
   * it returns the indices of selected features
   */

  def minimumRedundancyMaximumRelevancy(desiredNumberOfFeatures:Int): Array[Int] = {
    // information gains of (feature,label); all of them are used and thus precomputed
    val indices = 0 until noFeatures toArray
    val extractLabel = (record:LabeledPoint) => {
      record.label
    }
    val informationGainsWithLabels = computeInformationGains(indices,labelBuckets,extractLabel)

    // select the initial feature
    val (v,f) = informationGainsWithLabels.view.zipWithIndex.maxBy(_._1)
    selectedFeatures(f) = true
    internalRelevancyRedundacyValues += ((v,f))

    //main loop to select the desired features
    selectNextFeature(f,desiredNumberOfFeatures-2,informationGainsWithLabels)

    selectedFeatures.zipWithIndex.flatMap{ case(value,index) => {
      if(value) List(index) else List()
    }}
  }

  /*
  main loop to select the desired features
   */

  private def selectNextFeature(lastSelectedFeature:Int,iterIndex:Int,informationGainsWithLabels:Array[Double]): Unit = {
    if(iterIndex < 0) return

    // compute new information gain values
    // features not yet selected
    val notYetSelectedFeatures = selectedFeatures.zipWithIndex.flatMap{ case(value,index) => {
      if(!value) List(index) else List()
    }}
    val extractValue = (record:LabeledPoint) => {
      record.features(lastSelectedFeature)
    }
    // info gain between last feature and not yet selected features
    val newInfoGains = computeInformationGains(notYetSelectedFeatures,featuresBuckets(lastSelectedFeature),extractValue)

    // select the best new feature
    val noSelected = noFeatures - notYetSelectedFeatures.size
    val (featureToIncludeValue,featureToInclude) = notYetSelectedFeatures.zipWithIndex.map{ case(index,indexLocal) => {
      (informationGainsWithLabels(index) - (newInfoGains(indexLocal) +
        informationGainsBetweenFeatures.filterKeys{ case(i,j) =>
          (i == index && selectedFeatures(j)) || (j == index && selectedFeatures(i))
        }.foldLeft(0.0)(_+_._2))/noSelected,index)
    }}.maxBy(_._1)

    selectedFeatures(featureToInclude) = true
    internalRelevancyRedundacyValues += ((featureToIncludeValue,featureToInclude))

    // update the map of information gains already computed
    newInfoGains.view.zipWithIndex.foreach{ case(v,i) => {
      informationGainsBetweenFeatures((i,lastSelectedFeature)) = v
      true
    }}

    // call to select the next new feature
    selectNextFeature(featureToInclude,iterIndex-1,informationGainsWithLabels)
  }

  /*
  compute: information gain (feature,buckets) in one shot; here buckets can be either buckets pertaining to the labels or a different feature
  arguments: featureIndices - array of feature indices; targetBuckets - buckets of the other random variable which can be a feature or the labels;
  extractValue is the function that returns the value from LabeledPoint corresponding to target buckets
  The function compute information gain (featureIndices[i],target) for all i
   */

  private def computeInformationGains(featureIndices:Array[Int],targetBuckets:Array[Double],extractValue: LabeledPoint => Double): Array[Double] = {
    // for each feature we must count how many entries fall into (targeted bucket, bucket of feature)
    val allInitBuckets = new Array[Array[Array[Long]]](featureIndices.size) // [i,j,k] = count; feature obtained as featureIndices[i], j = bucket index of target, k = bucket index of the value of featureIndices[i]
    // initialize with zeros
    def initBuckets(i:Int): Unit = {
      def initBucketFeature(j:Int): Unit = {
        if(j < 0) return
        allInitBuckets(i)(j) = Array.fill[Long](featuresBuckets(featureIndices(i)).size)(0)
        initBucketFeature(j-1)
      }

      if(i < 0) return
      allInitBuckets(i) = new Array[Array[Long]](targetBuckets.size)
      initBucketFeature(targetBuckets.size-1)
      initBuckets(i-1)
    }
    initBuckets(featureIndices.size-1)

    val broadcastFeaturesBuckets = featuresBuckets
    // compute the counts for all buckets
    val allBucketValues = rdd.aggregate(allInitBuckets)(
      (values,record) => {
        def forEachFeature(i:Int): Unit = {
          if(i < 0) return
          // by using binary search find the indexes of the two values
          val targetValueIndex = BinarySearch.binarySearch(targetBuckets,extractValue(record))()
          val featureValueIndex = BinarySearch.binarySearch(broadcastFeaturesBuckets(featureIndices(i)),record.features(featureIndices(i)))()
          values(i)(targetValueIndex)(featureValueIndex) += 1

          forEachFeature(i-1)
        }

        forEachFeature(featureIndices.size-1)
        values
      },
      (v1,v2) => {
        // component-wise sum of two objects of type allBuckets
        v1.zip(v2).map{ case(feature1Buckets,feature2Buckets) => {
          feature1Buckets.zip(feature2Buckets).map{ case(values1,values2) => {
            values1.zip(values2).map{ case(a,b) =>
                a+b
            }
          }}
        }}
      }
    )

    // compute the counts for each target
    val targetBucketValues = allBucketValues(0).map{ targetBucket => {
      targetBucket.sum
    }}
    // compute the counts for each feature-bucket
    val featureBucketValues = allBucketValues.map{ t => {
      val s = Array.fill[Long](t(0).size)(0L)
      def compute(i:Int): Unit = {
        if(i < 0) return
        def computeOverLabels(j:Int) : Long = {
          if(j < 0) 0
          else computeOverLabels(j-1) + t(j)(i)
        }

        s(i) = computeOverLabels(t.size-1)
        compute(i-1)
      }
      compute(t(0).size-1)
      s
    }}

    // compute the information gain for each feature
    val result = Array.fill[Double](featureIndices.size)(Double.NaN)
    // formula for feature i: sum(j)(k) allBucketValues(i)(j)(k)/noRecords * log(allBucketValues(i)(j)(k) * noRecords / (targetBucketValues(j)*featureBucketValues(k))
    def infoGain(i:Int): Unit = {
      if(i < 0) return
      val featureValues = allBucketValues(i)
      // compute the sum
      def forj(j: Int): Double = {
        if (j < 0) 0
        else {
          def fork(k: Int): Double = {
            if (k < 0) 0
            else {
              val v = if(featureValues(j)(k) > 0)
                        (featureValues(j)(k) * math.log(featureValues(j)(k).toDouble * noRecords / (targetBucketValues(j) * featureBucketValues(i)(k))))/noRecords
                      else 0
              fork(k-1) + v
            }
          }
          forj(j - 1) + fork(featureValues(j).size - 1)
        }
      }
      result(i) = forj(featureValues.size - 1)
      infoGain(i-1)
    }

    infoGain(allBucketValues.size-1)
    result
  }
}
