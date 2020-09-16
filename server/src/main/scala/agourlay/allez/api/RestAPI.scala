package agourlay.allez.api

import agourlay.allez.service.AllezService
import agourlay.allez.util.Logging
import cats.effect.{ Clock, Resource }
import cats.implicits._
import faunadb.errors._
import fs2.Stream
import fs2.text.utf8Encode
import io.circe.generic.auto._
import io.circe.syntax._
import io.circe.Decoder
import monix.eval.Task._
import monix.eval.Task
import monix.eval.Task.catsEffect
import monix.execution.Scheduler
import org.http4s._
import org.http4s.circe._
import org.http4s.dsl._
import org.http4s.headers.`Content-Type`
import org.http4s.implicits._
import org.http4s.metrics.prometheus.{ Prometheus, PrometheusExportService }
import org.http4s.server._
import org.http4s.server.blaze.BlazeServerBuilder
import org.http4s.server.middleware.authentication.BasicAuth
import org.http4s.server.middleware.authentication.BasicAuth.BasicAuthenticator
import org.http4s.server.middleware.{ GZip, Metrics }

class RestAPI(val service: AllezService)(implicit s: Scheduler) extends Http4sDsl[Task] with Logging {

  implicit val clock = Clock.create[Task]

  implicit val routeJsonDecoder = jsonOf[Task, Route]
  implicit val routeDraftJsonDecoder = jsonOf[Task, RouteDraft]

  implicit val gymJsonDecoder = jsonOf[Task, Gym]
  implicit val gymDraftJsonDecoder = jsonOf[Task, GymDraft]

  implicit val userDraftJsonDecoder = jsonOf[Task, UserDraft]
  implicit val userLoginJsonDecoder = jsonOf[Task, UserLogin]

  implicit val suggestedGradeDraftJsonDecoder = jsonOf[Task, SuggestedGradeDraft]
  implicit val suggestedGradeJsonDeconde = jsonOf[Task, SuggestedGrade]

  implicit def pageJsonDecoder[A: Decoder] = jsonOf[Task, Page[A]]

  // Pagination params
  object PageSizeQueryParamMatcher extends OptionalQueryParamDecoderMatcher[Int]("pageSize")
  object PageBeforeQueryParamMatcher extends OptionalQueryParamDecoderMatcher[String]("pageBefore")
  object PageAfterQueryParamMatcher extends OptionalQueryParamDecoderMatcher[String]("pageAfter")

  // Search params
  object SearchNameParamMatcher extends OptionalQueryParamDecoderMatcher[String]("name")
  object PartialMatchParamMatcher extends FlagQueryParamMatcher("partialMatch")
  object SearchClimbProfileParamMatcher extends OptionalQueryParamDecoderMatcher[ClimbProfile]("profile")
  object SearchClimbTypeParamMatcher extends OptionalQueryParamDecoderMatcher[ClimbingType]("type")
  object SearchClimbGradeParamMatcher extends OptionalQueryParamDecoderMatcher[String]("grade")

  def renderFaunaException(e: FaunaException): String =
    s"[fauna error] error codes: ${e.errors.map(_.code).mkString("[",", ", "]")}\nmessage: ${e.getMessage}"

  // Do not log all the 4xx on production ;)
  def serviceErrorHandler: ServiceErrorHandler[Task] = _ => {
    // 4xx
    case UserError(m) =>
      logger.info(s"[user error] $m")
      BadRequest(m)
    case e: BadRequestException =>
      logger.info(renderFaunaException(e))
      BadRequest(e.getMessage)
    case e: NotFoundException =>
      logger.info(renderFaunaException(e))
      NotFound(e.getMessage)
    case e: PermissionDeniedException =>
      logger.info(renderFaunaException(e))
      Forbidden(e.getMessage)
    case e: UnauthorizedException => // do not how to use the Status API here
      logger.info(renderFaunaException(e))
      Task.now(Response(
        Status.Unauthorized,
        body = Stream(e.getMessage).through(utf8Encode),
        headers = Headers(`Content-Type`(MediaType.text.plain, Charset.`UTF-8`) :: Nil)))
    // 5xx
    case ServerError(m) =>
      logger.error(s"[server error] $m")
      BadRequest(m)
    case e: UnavailableException =>
      logger.error(renderFaunaException(e))
      ServiceUnavailable("FaunaDB not available")
    case e: InternalException =>
      logger.error(renderFaunaException(e))
      InternalServerError("Something is broken, contact an admin")
    case e: UnknownException =>
      logger.error(renderFaunaException(e))
      InternalServerError("Something is broken, contact an admin")
  }

  // TODO
  // add route_count view field on gym
  // add suggested_grades_count view field on gym
  // add suggested_grades_count view field on route

  private val gymsEndpoint = HttpRoutes.of[Task] {
    case GET -> Root / "gyms" :? SearchNameParamMatcher(nameOption) :? PartialMatchParamMatcher(partialMatch) :? PageSizeQueryParamMatcher(pageSize) :? PageBeforeQueryParamMatcher(pageBefore) :? PageAfterQueryParamMatcher(pageAfter) =>
      val po = PaginationOptions(pageSize, pageBefore, pageAfter)
      val pagesT = nameOption match {
        case None => service.retrieveGyms(po)
        case Some(name) if partialMatch => service.searchGymsByName(name)
        case Some(name) if !partialMatch => service.retrieveGymsByName(name)(po)
      }
      pagesT.flatMap(pages => Ok(pages.asJson))

    case GET -> Root / "gyms" / id =>
      service.retrieveGymById(id).flatMap {
        case Some(g) => Ok(g.asJson)
        case None => NotFound(s"Gym $id not found")
      }

    case GET -> Root / "gyms" / id / "routes"
      :? PageSizeQueryParamMatcher(pageSize) :? PageBeforeQueryParamMatcher(pageBefore) :? PageAfterQueryParamMatcher(pageAfter)
      :? SearchClimbProfileParamMatcher(profile) :? SearchClimbTypeParamMatcher(climbType) :? SearchClimbGradeParamMatcher(gradeLabel) =>
      val po = PaginationOptions(pageSize, pageBefore, pageAfter)
      service.retrieveRoutesByGymIdAndFields(id, profile, climbType, gradeLabel)(po).flatMap(pages => Ok(pages.asJson))

    case DELETE -> Root / "gyms" =>
      service.deleteAllGyms().flatMap(_ => Ok("All gyms deleted"))

    case DELETE -> Root / "gyms" / id =>
      service.deleteGym(id).flatMap {
        case Some(_) => Ok(s"Gym $id deleted")
        case None => NotFound(s"Gym $id not found")
      }

    case req @ POST -> Root / "gyms" =>
      for {
        gd <- req.as[GymDraft]
        gym <- service.createGym(gd)
        response <- Created(gym.asJson)
      } yield response

    case req @ POST -> Root / "gyms" / id =>
      req.as[GymDraft]
        .flatMap(gd => service.updateGym(id, gd))
        .flatMap {
          case Some(g) => Ok(g.asJson)
          case None => NotFound(s"Gym $id not found")
        }
  }

  private val routesEndpoint = HttpRoutes.of[Task] {
    case GET -> Root / "routes" :? SearchNameParamMatcher(nameOption) :? PartialMatchParamMatcher(fuzzy) :? PageSizeQueryParamMatcher(pageSize) :? PageBeforeQueryParamMatcher(pageBefore) :? PageAfterQueryParamMatcher(pageAfter) =>
      val po = PaginationOptions(pageSize, pageBefore, pageAfter)
      val pagesT = nameOption match {
        case None => service.retrieveRoutes(po)
        case Some(name) if fuzzy => service.searchRoutesByName(name)
        case Some(name) if !fuzzy => service.retrieveRoutesByName(name)(po)
      }
      pagesT.flatMap(pages => Ok(pages.asJson))

    case GET -> Root / "routes" / id =>
      service.retrieveRouteById(id).flatMap {
        case Some(r) => Ok(r.asJson)
        case None => NotFound(s"Route $id not found")
      }

    case DELETE -> Root / "routes" =>
      service.deleteAllRoute().flatMap(_ => Ok("All routes deleted"))

    case DELETE -> Root / "routes" / id =>
      service.deleteRoute(id).flatMap {
        case Some(_) => Ok(s"Route $id deleted")
        case None => NotFound(s"Route $id not found deleted")
      }

    case req @ POST -> Root / "routes" =>
      for {
        gd <- req.as[RouteDraft]
        route <- service.createRoute(gd)
        response <- Created(route.asJson)
      } yield response

    case req @ POST -> Root / "routes" / id =>
      req.as[RouteDraft]
        .flatMap(gd => service.updateRoute(id, gd))
        .flatMap {
          case Some(g) => Ok(g.asJson)
          case None => NotFound(s"Route $id not found")
        }

    case GET -> Root / "routes" / id / "suggestedGrades" :? PageSizeQueryParamMatcher(pageSize) :? PageBeforeQueryParamMatcher(pageBefore) :? PageAfterQueryParamMatcher(pageAfter) =>
      val po = PaginationOptions(pageSize, pageBefore, pageAfter)
      service.retrieveSuggestedGradeByRouteId(id)(po).flatMap(pages => Ok(pages.asJson))
  }

  private val usersEndpoint = HttpRoutes.of[Task] {
    case req @ POST -> Root / "users" =>
      for {
        ud <- req.as[UserDraft]
        user <- service.createUser(ud)
        response <- Created(user.asJson)
      } yield response

    case GET -> Root / "users" / id =>
      service.retrieveUserById(id).flatMap {
        case Some(r) => Ok(r.asJson)
        case None => NotFound(s"User $id not found")
      }

    case GET -> Root / "users" / id / "suggestedGrades" :? PageSizeQueryParamMatcher(pageSize) :? PageBeforeQueryParamMatcher(pageBefore) :? PageAfterQueryParamMatcher(pageAfter) =>
      val po = PaginationOptions(pageSize, pageBefore, pageAfter)
      service.retrieveSuggestedGradeByUserId(id)(po).flatMap(pages => Ok(pages.asJson))

    case DELETE -> Root / "users" / id =>
      service.deleteUser(id).flatMap {
        case Some(_) => Ok(s"User $id deleted")
        case None => NotFound(s"User $id not found deleted")
      }

    case req @ POST -> Root / "user-login" =>
      for {
        ul <- req.as[UserLogin]
        secret <- service.userLogin(ul)
        response <- Ok(secret)
      } yield response

    case DELETE -> Root / "users" =>
      service.deleteAllUsers().flatMap(_ => Ok("All users deleted"))
  }

  // HTTP Basic Auth wants credentials in the form "username:password".
  // Since we’re using a secret that represents both, we just add a colon (:) to the end of the secret.
  private val authStore: BasicAuthenticator[Task, String] = { (creds: BasicCredentials) =>
    service.verifyUserSecret(creds.username)
  }

  private val authMiddleware: AuthMiddleware[Task, String] = BasicAuth("secure site", authStore)

  private val secureSuggestedGradesEndpoint = authMiddleware {
    AuthedRoutes.of[String, Task] {
      case req @ POST -> Root / "suggestedGrades" as userId =>
        for {
          ud <- req.req.as[SuggestedGradeDraft]
          user <- service.createSuggestedGrade(userId, ud)
          response <- Created(user.asJson)
        } yield response
    }
  }

  private val suggestedGradesEndpoint = HttpRoutes.of[Task] {
    case GET -> Root / "suggestedGrades" / id =>
      service.retrieveSuggestedGradeById(id).flatMap {
        case Some(r) => Ok(r.asJson)
        case None => NotFound(s"SuggestedGrade $id not found")
      }

    case DELETE -> Root / "suggestedGrades" / id =>
      service.deleteSuggestedGrade(id).flatMap {
        case Some(_) => Ok(s"SuggestedGrade $id deleted")
        case None => NotFound(s"SuggestedGrade $id not found deleted")
      }

    case DELETE -> Root / "suggestedGrades" =>
      service.deleteAllSuggestedGrade().flatMap(_ => Ok("All suggestedGrades deleted"))
  }

  // TODO need prometheus metrics for fauna-driver?
  private val meteredRouterResource =
    for {
      metricsSvc <- PrometheusExportService.build[Task]
      metrics <- Prometheus.metricsOps[Task](metricsSvc.collectorRegistry, "server")
      router = Router[Task](
        "/api/" -> Metrics[Task](metrics)(
          gymsEndpoint <+> routesEndpoint <+> usersEndpoint <+> suggestedGradesEndpoint <+> secureSuggestedGradesEndpoint
        ),
        "/" -> metricsSvc.routes)
    } yield router

  def start(httpPort: Int): Resource[Task, Server[Task]] = {
    for {
      meteredRouter <- meteredRouterResource
      server <- BlazeServerBuilder[Task](executionContext = s)
        .bindHttp(httpPort, "localhost")
        .withoutBanner
        .withServiceErrorHandler(r => serviceErrorHandler(r).orElse(DefaultServiceErrorHandler(Task.catsEffect)(r)))
        .withNio2(true)
        .withHttpApp(GZip(meteredRouter.orNotFound))
        .resource
    } yield server
  }
}