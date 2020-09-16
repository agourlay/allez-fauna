package agourlay.allez.api

trait AllezException extends Exception {
  val m: String
  override def getMessage = m
}

case class UserError(m: String) extends AllezException
case class ServerError(m: String) extends AllezException
