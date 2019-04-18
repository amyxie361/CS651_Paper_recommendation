package ca.uwaterloo.cs451.patternsearch

import org.apache.log4j._
import org.apache.hadoop.fs._
import org.apache.spark._
import org.apache.spark.graphx._
import org.apache.spark.rdd.RDD
import org.rogach.scallop._

class PatternSearchConf(args: Seq[String]) extends ScallopConf(args) {
  mainOptions = Seq(graph, iter, paperlist, output)
  val graph = opt[String](descr = "input edge list path", required = true)
  val iter = opt[Int](descr = "iterations", required = true)
  val paperlist = opt[List[Long]](descr = "papler list from history reading (white-space-separated)")
  val output = opt[String](descr = "output path", required = true)
  verify()
}

object PatternSearch {
  val log = Logger.getLogger(getClass().getName())

  def main(argv: Array[String]) {
    val args = new PatternSearchConf(argv)

    log.info("Graph Input: " + args.graph())
    log.info("iter: " + args.iter())
    log.info("Paper List: " + args.paperlist())
    log.info("Output: " + args.output())

    val conf = new SparkConf().setAppName("PatternSearch")
    val sc = new SparkContext(conf)

    val outputDir = new Path(args.output())
    FileSystem.get(sc.hadoopConfiguration).delete(outputDir, true)

    var graph = GraphLoader.edgeListFile(sc, args.graph())
    val paperlist = sc.broadcast(args.paperlist().toSet)

    val initialGraph = graph.mapVertices((id, _) => if (paperlist.value.contains(id)) 2.0 else 0.0)
    val initialMessage = 0.0
    val vprog1 = (id: VertexId, attr: Double, msgSum: Double) => {
      attr + msgSum
    }
    val sendMessage1 = (edge: EdgeTriplet[Double, Int]) => {
      if (edge.srcAttr > 1.5) Iterator((edge.dstId, edge.srcAttr + 1.0)) else Iterator((edge.dstId, 0.0))
    }
    val messageCombiner1 = (a: Double, b: Double) => {
      math.max(a, b)
    }
    val onestepGraph = Pregel(initialGraph, initialMessage, 1)(
      vprog1, sendMessage1, messageCombiner1
    )
    val onefilteredGraph = Graph(
      onestepGraph.vertices
//        .filter{ case (id, attr) => attr > 2.0 }
        .map{ case (id, attr) => {
          if (attr > 2.0) (id, (1.0, 0.0))
          else (id, (0.0, 0.0))
        }}, onestepGraph.edges)

    val vprog2 = (id: VertexId, attr: (Double, Double), msgSum: Double) => {
      if (attr._1 > 0.0) (attr._1, 3.0)
      else if (msgSum > 1.0) (attr._1, 2.0)
      else if (msgSum > 0.0) (attr._1, 1.0)
      else (attr._1, 0.0)
    }
    val sendMessage2 = (edge: EdgeTriplet[(Double, Double), Int]) => {
      if (edge.srcAttr._1 > 0.5) Iterator((edge.dstId, 1.0)) else Iterator((edge.dstId, 0.0))
    }

    val messageCombiner2 = (a: Double, b: Double) => {
      a + b
    }

    val twostepGraph = Pregel(onefilteredGraph, 0.0, 1)(
      vprog2, sendMessage2, messageCombiner2
    )

    val rec = twostepGraph.vertices.sortBy(_._2._2, false)
//      .filter(_._2._2 > 0.0).sortBy(_._2._2, false)

    rec.saveAsTextFile(args.output())

  }
}
