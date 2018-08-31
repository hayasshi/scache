package scache

import java.time.Instant

import akka.util.ByteString

sealed trait Entry {
  def key: ByteString
  def value: ByteString
  def timestamp: Instant
  def expiredAt: Instant
}

case object Empty extends Entry {
  val key: ByteString = ByteString.empty
  val value: ByteString = ByteString.empty
  val timestamp: Instant = Instant.MIN
  val expiredAt: Instant = Instant.MIN
}

case class Content(
  key: ByteString,
  value: ByteString,
  timestamp: Instant,
  expiredAt: Instant,
) extends Entry

object Entry {

  def empty: Entry = Empty

  def apply(
    key: ByteString,
    value: ByteString,
    timestamp: Instant,
    expiredAt: Instant,
  ): Entry = Content(key, value, timestamp, expiredAt)

}