package agourlay.allez

import agourlay.allez.api.RestAPI
import agourlay.allez.persistence.{ FaunaDbAdmin, GymRepository, RouteRepository, SuggestedGradeRepository, UserRepository }
import agourlay.allez.service.AllezService
import agourlay.allez.util.Logging
import cats.effect.ExitCode
import faunadb.FaunaClient
import monix.eval.{ Task, TaskApp }
import monix.execution.Scheduler

import scala.concurrent.Await
import scala.concurrent.duration._
import pureconfig._
import pureconfig.generic.auto._

object Main extends TaskApp with Logging {

  implicit val s: Scheduler = Scheduler.Implicits.global

  override def run(args: List[String]): Task[ExitCode] = {
    // Config
    val config = ConfigSource.default
      .at("allez-fauna").load[AllezConfig]
      .fold(e => throw new IllegalArgumentException(e.head.description), identity)
    logger.info(s"Starting AllezFauna API on port ${config.httpPort}")

    // Fauna client + connectivity check
    val faunaClient = FaunaClient(secret = config.faunaSecret, endpoint = config.faunaApiEndpoint)
    val db = new FaunaDbAdmin(faunaClient)
    Await.result(db.testConnectivity(), 5.seconds)

    // Repos
    val gymRepository = new GymRepository(faunaClient)
    val routeRepository = new RouteRepository(faunaClient)
    val suggestedGradeRepository = new SuggestedGradeRepository(faunaClient)
    val userRepository = new UserRepository(faunaClient)

    // Service
    val service = new AllezService(db, gymRepository, routeRepository, suggestedGradeRepository, userRepository)

    // HTTP API
    val api = new RestAPI(service)

    // Server
    val server = api.start(config.httpPort)
    server.use(_ => Task.never).as(ExitCode.Success)
  }

}

case class AllezConfig(httpPort: Int, faunaApiEndpoint: String, faunaSecret: String)