package ca.uwaterloo.cs451.pagerank

import org.apache.log4j._
import org.apache.hadoop.fs._
import org.apache.spark._
import org.apache.spark.graphx._
import org.apache.spark.rdd.RDD
import org.rogach.scallop._

class PaperRankConf(args: Seq[String]) extends ScallopConf(args) {
  mainOptions = Seq(input)
  val input = opt[String](descr = "input edge list path", required = true)
  verify()
}

object PaperRank {
  val log = Logger.getLogger(getClass().getName())

  def main(argv: Array[String]) {
    val args = new PaperRankConf(argv)

    log.info("Input: " + args.input())

    val conf = new SparkConf().setAppName("PaperRank")
    val sc = new SparkContext(conf)

    val graph = GraphLoader.edgeListFile(sc, args.input())
    val initialGraph: Graph[Double, Double] = graph
      .outerJoinVertices(graph.outDegrees) {
        (vid, vdata, deg) => deg.getOrElse(0)
      }
      .mapTriplets(e => 1.0 / e.srcAttr)
      .mapVertices((id, attr) => 1.0)

    val numIteration = 100
    val resetProb = 0.15
    val initialMessage = 0.0
    val vertexProgram = (id: VertexId, attr: Double, msgSum: Double) => {
      resetProb + (1.0 - resetProb) * msgSum
    }
    val sendMessage = (edge: EdgeTriplet[Double, Double]) => {
      Iterator((edge.dstId, edge.srcAttr * edge.attr))
    }
    val messageCombiner = (a: Double, b: Double) => {
      a + b
    }

    val pagerankGraph = Pregel(initialGraph, initialMessage, numIteration)(
      vertexProgram, sendMessage, messageCombiner
    )
  }
}
