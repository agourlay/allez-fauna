package agourlay.allez.service

import agourlay.allez.api.{ ClimbProfile, ClimbingType, Gym, GymDraft, Page, PaginationOptions, Route, RouteDraft, SuggestedGrade, SuggestedGradeDraft, User, UserDraft, UserLogin }
import agourlay.allez.persistence.{ FaunaDbAdmin, GymRepository, RouteRepository, SuggestedGradeRepository, UserRepository }
import agourlay.allez.util.Logging
import monix.eval.Task
import scala.concurrent.{ ExecutionContext, Future }

// TODO split service per entity
class AllezService(
  val db: FaunaDbAdmin,
  val gymRepo: GymRepository,
  val routeRepo: RouteRepository,
  val suggestedGradeRepo: SuggestedGradeRepository,
  val userRepo: UserRepository)(implicit ec: ExecutionContext) extends Logging {

  def createDatabase(name: String): Task[String] =
    Task.fromFuture(db.createDatabase(name))

  // Gyms
  def createGym(draft: GymDraft): Task[Gym] = {
    val result =
      for {
        id <- gymRepo.nextId()
        gym <- gymRepo.saveDocument(Gym.fromDraft(id, draft))
      } yield gym

    Task.fromFuture(result)
  }

  // TODO do this completely in FQL?
  def updateGym(gymId: String, draft: GymDraft): Task[Option[Gym]] = {
    val result = gymRepo.find(gymId).flatMap {
      case None =>
        Future.successful(None)
      case Some(gymRead) =>
        val tmp = Gym.fromDraft(gymId, draft).copy(createdAt = gymRead.createdAt)
        gymRepo.saveDocument(tmp).map(Some(_))
    }

    Task.fromFuture(result)
  }

  def deleteAllGyms(): Task[Unit] = {
    Task.fromFuture(gymRepo.truncateCollection())
  }

  def deleteGym(id: String): Task[Option[Gym]] = {
    Task.fromFuture(gymRepo.deleteGym(id))
  }

  def retrieveGyms(po: PaginationOptions): Task[Page[Gym]] = {
    Task.fromFuture(gymRepo.findAll(po))
  }

  def retrieveGymsByName(name: String)(po: PaginationOptions): Task[Page[Gym]] = {
    Task.fromFuture(gymRepo.findByName(name)(po))
  }

  def searchGymsByName(name: String): Task[Page[Gym]] = {
    Task.fromFuture(gymRepo.searchByFieldContains("name", name))
  }

  def retrieveGymById(id: String): Task[Option[Gym]] = {
    Task.fromFuture(gymRepo.find(id))
  }

  // Route
  def createRoute(draft: RouteDraft): Task[Route] = {
    val result =
      for {
        id <- routeRepo.nextId()
        gym <- routeRepo.saveRoute(Route.fromDraft(id, draft))
      } yield gym

    Task.fromFuture(result)
  }

  // TODO do this completely in FQL?
  def updateRoute(routeId: String, draft: RouteDraft): Task[Option[Route]] = {
    val result = routeRepo.find(routeId).flatMap {
      case None =>
        Future.successful(None)
      case Some(routeRead) =>
        val tmp = Route.fromDraft(routeId, draft).copy(createdAt = routeRead.createdAt)
        routeRepo.saveDocument(tmp).map(Some(_))
    }

    Task.fromFuture(result)
  }

  def deleteRoute(id: String): Task[Option[Route]] = {
    Task.fromFuture(routeRepo.removeDocument(id))
  }

  def deleteAllRoute(): Task[Unit] = {
    Task.fromFuture(routeRepo.truncateCollection())
  }

  def retrieveRoutes(po: PaginationOptions): Task[Page[Route]] = {
    Task.fromFuture(routeRepo.findAll(po))
  }

  def retrieveRoutesByName(name: String)(po: PaginationOptions): Task[Page[Route]] = {
    Task.fromFuture(routeRepo.findByName(name)(po))
  }

  def retrieveRouteById(id: String): Task[Option[Route]] = {
    Task.fromFuture(routeRepo.find(id))
  }

  def retrieveRoutesByGymId(gymId: String)(po: PaginationOptions): Task[Page[Route]] = {
    Task.fromFuture(routeRepo.findByGymId(gymId)(po))
  }

  def retrieveRoutesByGymIdAndFields(gymId: String, profile: Option[ClimbProfile], climbType: Option[ClimbingType], gradeLabel: Option[String])(po: PaginationOptions): Task[Page[Route]] = {
    Task.fromFuture(routeRepo.findByGymIdAndFields(gymId, profile, climbType, gradeLabel)(po))
  }

  def searchRoutesByName(name: String): Task[Page[Route]] = {
    Task.fromFuture(routeRepo.searchByFieldContains("name", name))
  }

  // User
  def createUser(draft: UserDraft): Task[User] = {
    val result =
      for {
        id <- userRepo.nextId()
        user <- userRepo.createUser(id, draft)
      } yield user

    Task.fromFuture(result)
  }

  def userLogin(userLogin: UserLogin): Task[String] = {
    Task.fromFuture(userRepo.userLogin(userLogin.email, userLogin.password))
  }

  def retrieveUserById(id: String): Task[Option[User]] = {
    Task.fromFuture(userRepo.find(id))
  }

  def deleteUser(id: String): Task[Option[User]] = {
    Task.fromFuture(userRepo.removeDocument(id))
  }

  def deleteAllUsers(): Task[Unit] = {
    Task.fromFuture(userRepo.truncateCollection())
  }

  def verifyUserSecret(secret: String)(implicit ec: ExecutionContext): Task[Option[String]] = {
    Task.fromFuture(userRepo.verifyUserSecret(secret))
  }

  // SuggestedRating
  def createSuggestedGrade(userId: String, draft: SuggestedGradeDraft): Task[SuggestedGrade] = {
    val result =
      for {
        id <- suggestedGradeRepo.nextId()
        user <- suggestedGradeRepo.saveSuggestedRating(SuggestedGrade.fromDraft(id, userId, draft))
      } yield user

    Task.fromFuture(result)
  }

  def retrieveSuggestedGradeById(id: String): Task[Option[SuggestedGrade]] = {
    Task.fromFuture(suggestedGradeRepo.find(id))
  }

  def retrieveSuggestedGradeByUserId(id: String)(po: PaginationOptions): Task[Page[SuggestedGrade]] = {
    Task.fromFuture(suggestedGradeRepo.findByUserId(id)(po))
  }

  def retrieveSuggestedGradeByRouteId(id: String)(po: PaginationOptions): Task[Page[SuggestedGrade]] = {
    Task.fromFuture(suggestedGradeRepo.findByRouteId(id)(po))
  }

  def deleteSuggestedGrade(id: String): Task[Option[SuggestedGrade]] = {
    Task.fromFuture(suggestedGradeRepo.removeDocument(id))
  }

  def deleteAllSuggestedGrade(): Task[Unit] = {
    Task.fromFuture(suggestedGradeRepo.truncateCollection())
  }

}
