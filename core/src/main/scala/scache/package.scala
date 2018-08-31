import java.time.Instant

package object scache {

  implicit class EnrichJavaTimeInstant(val self: Instant) extends AnyVal {
    def <(that: Instant): Boolean = self.isBefore(that)
    def <=(that: Instant): Boolean = self.isBefore(that) || self == that
    def >(that: Instant): Boolean = self.isAfter(that)
    def >=(that: Instant): Boolean = self.isAfter(that) || self == that
  }

}
