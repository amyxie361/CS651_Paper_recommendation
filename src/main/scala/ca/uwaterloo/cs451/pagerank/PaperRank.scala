package ca.uwaterloo.cs451.pagerank

import org.apache.log4j._
import org.apache.hadoop.fs._
import org.apache.spark._
import org.apache.spark.graphx._
import org.apache.spark.rdd.RDD
import org.rogach.scallop._

class PaperRankConf(args: Seq[String]) extends ScallopConf(args) {
  mainOptions = Seq(graph, index, keywords, output)
  val graph = opt[String](descr = "input edge list path", required = true)
  val index = opt[String](descr = "term2papers index path", required = true)
  val keywords = opt[String](descr = "keywords (comma-separated)", default = Some(""))
  val output = opt[String](descr = "output path", required = true)
  verify()
}

object PaperRank {
  val log = Logger.getLogger(getClass().getName())

  def main(argv: Array[String]) {
    val args = new PaperRankConf(argv)

    log.info("Graph Input: " + args.graph())
    log.info("Index: " + args.index())
    log.info("Keywords: " + args.keywords())
    log.info("Output: " + args.output())

    val conf = new SparkConf().setAppName("PaperRank")
    val sc = new SparkContext(conf)

    val outputDir = new Path(args.output())
    FileSystem.get(sc.hadoopConfiguration).delete(outputDir, true)

    var graph = GraphLoader.edgeListFile(sc, args.graph())

    if (args.keywords() != "") {
      val term2papers = sc.textFile(args.index())
        .map(line => {
          val termAndPaperIds = line.split("###")
          val term = termAndPaperIds(0)
          val paperIds = termAndPaperIds(1).split(",").map(id => id.toLong).toList
          (term, paperIds)
        })

      val keywords = sc.broadcast(args.keywords().split(",").toSet)
      val vIds = term2papers
        .filter(tuple => keywords.value.contains(tuple._1))
        .map(tuple => ("*", tuple._2))
        .reduceByKey(_ ::: _)
        .map(_._2)
        .collect()
      val paperSet = sc.broadcast(vIds(0).toSet)

      graph = graph.subgraph(vpred = (id, attr) => paperSet.value.contains(id))
    }

    val initialGraph: Graph[Double, Double] = graph
      .outerJoinVertices(graph.outDegrees) {
        (vid, vdata, deg) => deg.getOrElse(0)
      }
      .mapTriplets(e => 1.0 / e.srcAttr)
      .mapVertices((id, attr) => 1.0)

    val numIteration = 50
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

    val paperrankGraph = Pregel(initialGraph, initialMessage, numIteration)(
      vertexProgram, sendMessage, messageCombiner
    )

    val rec = paperrankGraph.vertices
      .sortBy(_._2, false)

    rec.saveAsTextFile(args.output())

    /*
    val ranks = graph.pageRank(0.0001).vertices.sortBy(_._2, false)
    ranks.saveAsTextFile("ranks")
    */
  }
}
