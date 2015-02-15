# spark-ml
Machine learning enhancements to Spark MlLib

FeatureSelection based on Maximum-Relevance Minimum-Redundancy (importance of a feature measured by information gain)

Usage:
val noRecords = vectorModelRDD.count
val labelBuckets:Array[Double] = .... // note: the first and last element must be below the minimum value and above the maximum value, respectively; For example, for a binary case, we need to specify [-1,0,1,2]
val featuresBuckets: Array[Array[Double]] = ....

val featureSelection = new MrMrFeatureSelection(vectorModelRDD, labelBuckets, featuresBuckets, noRecords)
val noDesiredFeatures = 30 // the number of desired features
val fs:Array[Int] = featureSelection.minimumRedundancyMaximumRelevancy(noDesiredFeatures) // this function does all the computations; fs contains the indices selected
val fsv:List[(Double,Int)] = featureSelection.relevancyRedundacyValues // fsv contains pairs of (objective function value, index of feature); the objective function value is info_gain(feature,labels) - sum(all previously selected features j) info_gain(j,feature)/(number of features previously selected)
