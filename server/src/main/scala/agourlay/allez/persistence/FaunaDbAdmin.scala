package agourlay.allez.persistence

import agourlay.allez.util.Logging
import faunadb.FaunaClient
import faunadb.query._

import scala.concurrent.{ ExecutionContext, Future }
import scala.concurrent.duration._
import scala.util.{ Failure, Success }

class FaunaDbAdmin(faunaClient: FaunaClient)(implicit ec: ExecutionContext) extends Logging {

  def testConnectivity(): Future[Boolean] =
    faunaClient.query(Exists(Ref(Collection("abc"), "123")), 3.seconds)
      .map(_ => true)
      .andThen {
        case Success(_) =>
          logger.info("Fauna connection up!")
          Future.successful(true)
        case Failure(e) =>
          logger.error("Fauna connectivity test failed!", e)
          Future.successful(false)
      }
}
