package worker

import java.util.UUID
import scala.concurrent.forkjoin.ThreadLocalRandom
import scala.concurrent.duration._
import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.ActorRef

object WorkProducer {
  case object Tick
}

class WorkProducer(frontend: ActorRef) extends Actor with ActorLogging {
  import WorkProducer._
  import context.dispatcher
  def scheduler = context.system.scheduler
  def rnd = ThreadLocalRandom.current
  var n = 0
  
  def nextWorkId(): String =  {n = n + 1; n.toString}//UUID.randomUUID().toString



  override def preStart(): Unit =
    scheduler.scheduleOnce(5.seconds, self, Tick)

  // override postRestart so we don't call preStart and schedule a new Tick
  override def postRestart(reason: Throwable): Unit = ()

  val links = List(
    "http://feeds.bbci.co.uk/news/rss.xml",
    "http://feeds.bbci.co.uk/news/world/rss.xml"
    ).toIterator

  def receive = {
    case Tick =>
      if (links.hasNext) {
        val link = links.next()
        log.info("Produced work: {}", link)
        val work = Work(nextWorkId(), link)
        frontend ! work
        context.become(waitAccepted(work), discardOld = false)
      }
  }

  def waitAccepted(work: Work): Actor.Receive = {
    case Frontend.Ok =>
      context.unbecome()
      //scheduler.scheduleOnce(rnd.nextInt(3, 10).seconds, self, Tick)
      self ! Tick
    case Frontend.NotOk =>
      log.info("Work not accepted, retry after a while")
      scheduler.scheduleOnce(3.seconds, frontend, work)
  }

}
