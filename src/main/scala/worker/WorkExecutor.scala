package worker

import akka.actor.Actor
import com.sun.syndication.io.{ SyndFeedInput, XmlReader }
import com.sun.syndication.feed.synd.{ SyndEntry, SyndEntryImpl }
import java.net.URL
import scala.collection.JavaConversions._
import com.sksamuel.elastic4s.{ ElasticClient, ElasticsearchClientUri }
import com.sksamuel.elastic4s.ElasticDsl._

class WorkExecutor extends Actor {

  val uri = ElasticsearchClientUri("elasticsearch://192.168.2.9:9300")
  val client = ElasticClient.remote(uri)
  client.execute {
    create index "news"
  }

  case class RSSEntry(urlLink: String, date: String, title: String, desc: String)

  def writeToES(rss: RSSEntry): Unit = {

    if (!rss.title.isEmpty()) {
      val doc = index into "news" / "articles" fields (
        "publish_date" -> rss.date,
        "title" -> rss.title,
        "link" -> rss.urlLink,
        "description" -> rss.desc)
      client.execute { doc }
    }

  }

  def receive = {
    case link: String =>

      try {
        val feedURL = new URL(link)
        val input = new SyndFeedInput
        val feed = input.build(new XmlReader(feedURL))
        val entries = feed.getEntries.toList
        val syndentries = entries.map { _.asInstanceOf[SyndEntry] }

        syndentries.foreach { syndEntry =>
          val urlLink = syndEntry.getLink
          val date = syndEntry.getPublishedDate.toString()
          val desc = syndEntry.getDescription.getValue.toString()
          val title = syndEntry.getTitle

          val rss = RSSEntry(urlLink, date, title, desc)
          writeToES(rss)
        }

        val entryLength = syndentries.length
        sender() ! Worker.WorkComplete(s"Entry length --> $entryLength")
      } catch {
        case ex: Exception =>
          println("Couldn't parse the link")
          sender() ! Worker.WorkComplete("0")
      }
  }

}