package scache

import java.time.Instant

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit}
import akka.util.ByteString
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import scache.EntryHolder.Protocol._

import scala.util.Random

class EntryHolderSpec
  extends TestKit(ActorSystem("EntryHolderSpec"))
  with ImplicitSender
  with WordSpecLike
  with Matchers
  with BeforeAndAfterAll {

  override def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
  }

  "Receive SetRequest" should {

    "reply set success" in {
      val holder = system.actorOf(EntryHolder.props())

      val entry = Entry(
        key = ByteString(Random.nextString(10)),
        value = ByteString(Random.nextString(100)),
        timestamp = Instant.now(),
        expiredAt = Instant.now.plusSeconds(10L)
      )

      holder ! SetRequest(entry)
      expectMsgType[SetSuccess]
    }

    "reply set error if request entry is already expired" in {
      val holder = system.actorOf(EntryHolder.props())

      val entry = Entry(
        key = ByteString(Random.nextString(10)),
        value = ByteString(Random.nextString(100)),
        timestamp = Instant.now(),
        expiredAt = Instant.now.minusSeconds(10L)
      )

      holder ! SetRequest(entry)
      expectMsgType[SetEntryAlreadyExpiredError]
    }

    "reply set error if request entry is old than current" in {
      val holder = system.actorOf(EntryHolder.props())

      val entry1 = Content(
        key = ByteString(Random.nextString(10)),
        value = ByteString(Random.nextString(100)),
        timestamp = Instant.now(),
        expiredAt = Instant.now.plusSeconds(10L)
      )
      holder ! SetRequest(entry1)
      expectMsgType[SetSuccess]

      val entry2 = entry1.copy(timestamp = entry1.timestamp.minusSeconds(1L))

      holder ! SetRequest(entry2)
      expectMsgType[SetEntryIsOldThanCurrentError]
    }

  }

  "Receive GetRequest" should {

    "reply not found message if entry is empty" in {
      val holder = system.actorOf(EntryHolder.props())
      holder ! GetRequest(ByteString.empty)
      expectMsgType[GetNotFound]
    }

    "reply entry" in {
      val holder = system.actorOf(EntryHolder.props())
      val entry = Entry(
        key = ByteString(Random.nextString(10)),
        value = ByteString(Random.nextString(100)),
        timestamp = Instant.now(),
        expiredAt = Instant.now.plusSeconds(10L)
      )
      holder ! SetRequest(entry)
      expectMsgType[SetSuccess]

      holder ! GetRequest(entry.key)
      val actual = expectMsgType[GetSuccess]
      actual.request.key shouldBe entry.key
      actual.entry shouldBe entry
    }

  }

}
