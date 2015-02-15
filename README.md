# spark-ml
Machine learning enhancements to Spark MlLib

FeatureSelection based on Maximum-Relevance Minimum-Redundancy (importance of a feature measured by information gain)

Usage: 

MrMrFeatureSelection(vectorModelRDD, labelBuckets, featuresBuckets, noRecords)

vectorModelRDD = rdd of LabeledPoint where features are of type DenseVector
labelBuckets = type Array[Double] which corresponds to buckets of labels; the first element must be smaller than the minimum value of the label and the last element must be larger than the maximum value of the label
featuresBuckets = type Array[Array[Double]] featuresBuckets[i] corresponds to the array of buckets pertaining to feature i (index based on input rdd); same rule as for the labels regarding the first and last element

featureSelection.minimumRedundancyMaximumRelevancy(noDesiredFeatures) 

run the feature selection algorithm
returns the indices of the features selected

featureSelection.relevancyRedundacyValues
returns List[(Double,Int)] where each entry corresponds to a feature selected. Int is the index selected and Double the objective value of the feature upon selection. It is
info_gain(feature,labels) - sum(all previously selected features j)                                                                                 info_gain(j,feature)/(number of features previously selected)

Example:

val noRecords = vectorModelRDD.count
val labelBuckets:Array[Double] = .... // note: the first and last element must be below the minimum value and above the maximum value, respectively; For example, for a binary case, we need to specify [-1,0,1,2]
val featuresBuckets: Array[Array[Double]] = ....

// create the object
val featureSelection = new MrMrFeatureSelection(vectorModelRDD, labelBuckets, featuresBuckets, noRecords)
val noDesiredFeatures = 30 // the number of desired features

/*
 * this function does all the computations
 * fs contains the indices selected
 */

val fs:Array[Int] = featureSelection.minimumRedundancyMaximumRelevancy(noDesiredFeatures) 

/*
 * fsv contains pairs of (objective function value, index of feature)
 * the objective function value is info_gain(feature,labels) - sum(all previously selected features j)               
 *                                                              info_gain(j,feature)/(number of features previously selected)
 */
 
val fsv:List[(Double,Int)] = featureSelection.relevancyRedundacyValues
