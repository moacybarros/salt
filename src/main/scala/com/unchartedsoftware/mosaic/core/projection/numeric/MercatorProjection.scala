package com.unchartedsoftware.mosaic.core.projection.numeric

import com.unchartedsoftware.mosaic.core.util.ValueExtractor
import org.apache.spark.sql.Row

/**
 * A projection into 2D mercator (lon,lat) space
 *
 * @param minZoom the minimum zoom level which will be passed into rowToCoords()
 * @param maxZoom the maximum zoom level which will be passed into rowToCoords()
 * @param min the minimum value of a data-space coordinate (minLon, minLat)
 * @param max the maximum value of a data-space coordinate (maxLon, maxLat)
 */
class MercatorProjection(
  minZoom: Int,
  maxZoom: Int,
  min: (Double, Double),
  max: (Double, Double)) extends NumericProjection[(Double, Double), (Int, Int, Int), (Int, Int)](minZoom, maxZoom, min, max) {

  val _internalMaxX = Math.min(max._1, 180);
  val _internalMinX = Math.max(min._1, -180);
  val _internalMaxY = Math.min(max._2, 85.05112878);
  val _internalMinY = Math.max(min._2, -85.05112878);

  //Precompute some stuff we'll use frequently
  val piOver180 = Math.PI / 180;

  //number of tiles at each zoom level
  val tileCounts = new Array[Int](maxZoom+1)
  for (i <- minZoom until maxZoom+1) {
    tileCounts(i) = 1 << i //Math.pow(2, i).toInt
  }

  override def getZoomLevel(c: (Int, Int, Int)): Int = {
    c._1
  }

  override def project (dCoords: Option[(Double, Double)], z: Int, maxBin: (Int, Int)): Option[((Int, Int, Int), (Int, Int))] = {
    if (z > maxZoom || z < minZoom) {
      throw new Exception("Requested zoom level is outside this projection's zoom bounds.")
    } else {
      //with help from https://developer.here.com/rest-apis/documentation/traffic/topics/mercator-projection.html

      if (!dCoords.isDefined) {
        None
      } else if (dCoords.get._1 >= max._1 || dCoords.get._1 <= min._1 || dCoords.get._2 >= max._2 || dCoords.get._2 <= min._2) {
        //make sure that we always stay INSIDE the range
        None
      } else {
        var lon = dCoords.get._1
        var lat = dCoords.get._2
        val latRad = (-lat) * piOver180;
        val n = tileCounts(z);
        val howFarX = n * ((lon + 180) / 360);
        val howFarY = n * (1-(Math.log(Math.tan(latRad) + 1/Math.cos(latRad)) / Math.PI)) / 2

        val x = howFarX.toInt
        val y = howFarY.toInt

        var xBin = ((howFarX - x)*maxBin._1).toInt
        var yBin = (maxBin._2-1) - ((howFarY - y)*maxBin._2).toInt

        Some(((z, x, y), (xBin, yBin)))
      }
    }
  }

  override def binTo1D(bin: (Int, Int), maxBin: (Int, Int)): Int = {
    bin._1 + bin._2*maxBin._1
  }
}