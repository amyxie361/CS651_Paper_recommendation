package ca.uwaterloo.cs451.tfidf

import io.bespin.scala.util.Tokenizer

import org.apache.log4j._
import org.apache.hadoop.fs._
import org.apache.spark.Partitioner
import org.apache.spark.SparkContext
import org.apache.spark.SparkConf
import org.apache.spark.sql.SparkSession
import org.rogach.scallop._

class TFIDFConf(args: Seq[String]) extends ScallopConf(args) {
  mainOptions = Seq(input, output)
  val input = opt[String](descr = "input path", required = true)
  val output = opt[String](descr = "output path", required = true)
  verify()
}

object TFIDF extends Tokenizer {
  val log = Logger.getLogger(getClass().getName())

  def main(argv: Array[String]) {
    val args = new TFIDFConf(argv)

    log.info("Input: " + args.input())
    log.info("Output: " + args.output())

    val conf = new SparkConf().setAppName("TFIDF")
    val sc = new SparkContext(conf)

    val outputDir = new Path(args.output())
    FileSystem.get(sc.hadoopConfiguration).delete(outputDir, true)

    val csvFile = sc.textFile(args.input())

    // total number of papers with abstract in our collection
    val numTotalDocs = sc.broadcast(csvFile.map(_ => ("*", 1)).reduceByKey(_ + _).collect()(0)._2.toDouble)

    val paperIdAndTerms = csvFile
      .map(line => {
        val paper_and_abstract = line.split("###")
        (paper_and_abstract(0), tokenize(paper_and_abstract(1)))
      })

    // count number of papers with term i
    // i.e. (term, num_papers)
    val numDocsForTerm = paperIdAndTerms
      .map(paper_abstract => (paper_abstract._1, paper_abstract._2.distinct))
      .flatMap(paper_abstract => {
        paper_abstract._2.map(term => (term, 1)).toList
      })
      .reduceByKey(_ + _)

    val term2papers = paperIdAndTerms
      // count number of occurrences of term i in paper j
      .flatMap(paper_abstract => {
        paper_abstract._2.map(term => ((term, paper_abstract._1.toInt), 1)).toList
      })
      .reduceByKey(_ + _)
      // transform back to (term, (paper_id, tf))
      .map(tuple => (tuple._1._1, (tuple._1._2, tuple._2)))
      // result format of this join: (term, ((paper_id, tf), num_papers))
      .join(numDocsForTerm)
      .map(tuple => {
        val paperId = tuple._2._1._1
        val term = tuple._1
        val tf = tuple._2._1._2
        val numPapersWithThisTerm = tuple._2._2
        val tfidf = tf * Math.log10(numTotalDocs.value / numPapersWithThisTerm)

        (paperId, List((term, tfidf)))
      })
      .reduceByKey(_ ::: _)
      // result format of this map: (paper_id, list_of_top_5_terms_in_this_paper)
      .map(tuple => (tuple._1, tuple._2.sortWith(_._2 > _._2).take(5).map(_._1).toList))
      // finally, we reverse our index to be: term -> list_of_papers
      .flatMap(tuple => {
        val paperId = tuple._1
        tuple._2.map(term => (term, List(paperId))).toList
      })
      .reduceByKey(_ ::: _)

      term2papers.saveAsTextFile(args.output())
  }
}
