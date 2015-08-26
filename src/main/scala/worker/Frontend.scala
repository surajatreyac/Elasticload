package worker

import scala.concurrent.duration._
import akka.actor.Actor
import akka.pattern._
import akka.util.Timeout
import akka.contrib.pattern.ClusterSingletonProxy
import akka.cluster.ClusterEvent.MemberUp
import akka.actor.ActorLogging

object Frontend {
  case object Ok
  case object NotOk
}

class Frontend extends Actor with ActorLogging{
  import Frontend._
  import context.dispatcher
  val masterProxy = context.actorOf(ClusterSingletonProxy.props(
    singletonPath = "/user/master/active",
    role = Some("backend")),
    name = "masterProxy")

  def receive = {
    case work =>
      implicit val timeout = Timeout(5.seconds)
      (masterProxy ? work) map {
        case Master.Ack(_) => Ok
      } recover { case _ => NotOk } pipeTo sender()

  }

}