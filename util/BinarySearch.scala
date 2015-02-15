package util

/**
 * Created by diego on 1/5/15.
 * Binary search in an array
 * If target not in the array, we return the index i such that list(i) <= target < list(i+1)
 * Assumption: target falls in one of the subintervals, i.e. min list value < target and max list value > target
 */

object BinarySearch {

  def binarySearch(list: Array[Double], target: Double)
                           (start: Int=0, end: Int=list.length-1): Int = {
    if (start>end) return -1
    val mid = start + (end-start+1)/2
    if (list(mid) <= target && list(mid+1) > target)
      return mid
    else if (list(mid) > target)
      return binarySearch(list, target)(start, mid-1)
    else
      return binarySearch(list, target)(mid+1, end)
  }

}
