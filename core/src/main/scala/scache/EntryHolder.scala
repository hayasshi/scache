package scache

import java.time.Instant

import akka.actor.{Actor, ActorLogging, Props}
import akka.util.ByteString

class EntryHolder extends Actor with ActorLogging {
  import EntryHolder.Protocol._

  var entry: Entry = Entry.empty

  override def receive: Receive = {
    case req: GetRequest => entry match {
      case Empty => sender() ! GetNotFound(req)
      case c: Content => sender() ! GetSuccess(req, c)
    }

    case req: SetRequest if req.entry.expiredAt < Instant.now() =>
      sender() ! SetEntryAlreadyExpiredError(req)

    case req: SetRequest => entry match {
      case c: Content if req.entry.timestamp < c.timestamp =>
        sender() ! SetEntryIsOldThanCurrentError(req)
      case _ =>
        entry = req.entry
        sender() ! SetSuccess(req)
    }

  }

}

object EntryHolder {

  def props(): Props = Props(new EntryHolder)

  object Protocol {
    case class GetRequest(key: ByteString)
    case class SetRequest(entry: Entry)

    sealed trait GetResponse {
      def request: GetRequest
    }
    case class GetSuccess(request: GetRequest, entry: Entry) extends GetResponse
    case class GetFailure(request: GetRequest, reason: String) extends GetResponse
    case class GetError(request: GetRequest, reason: String) extends GetResponse
    case class GetNotFound(request: GetRequest) extends GetResponse

    sealed trait SetResponse {
      def request: SetRequest
    }
    case class SetSuccess(request: SetRequest) extends SetResponse
    case class SetFailure(request: SetRequest, reason: String) extends SetResponse
    sealed trait SetError extends SetResponse
    case class SetEntryAlreadyExpiredError(request: SetRequest) extends SetError
    case class SetEntryIsOldThanCurrentError(request: SetRequest) extends SetError
  }

}
