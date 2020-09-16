package agourlay.allez.persistence

import agourlay.allez.api.{ ClimbProfile, ClimbingType, Page, PaginationOptions, Route }
import agourlay.allez.util.AllezStringUtil._
import faunadb.FaunaClient
import faunadb.query.{ Map, _ }
import faunadb.values.{ ArrayV, Codec, StringV }

import scala.concurrent.{ ExecutionContext, Future }

class RouteRepository(faunaClient: FaunaClient) extends AggregateRepository[Route] {
  override protected val client: FaunaClient = faunaClient
  override protected val collectionName = "routes"
  implicit override protected val faunaCodec: Codec[Route] = Codec.Record[Route]

  def findByName(name: String)(po: PaginationOptions)(implicit ec: ExecutionContext): Future[Page[Route]] = {
    val cursor = paginationOptionsToCursor(po)
    val result =
      client.query(
        Map(
          Paginate(Match(Index("routes_by_name"), name), size = po.size, cursor = cursor),
          Lambda(nextRef => Select("data", Get(nextRef)))))

    result.decode[Page[Route]]
  }

  def findByGymId(gymId: String)(po: PaginationOptions)(implicit ec: ExecutionContext): Future[Page[Route]] = {
    val cursor = paginationOptionsToCursor(po)

    val result =
      client.query(
        If(
          Exists(Ref(Collection("gyms"), gymId)),
          Map(
            Paginate(Match(Index("routes_by_gymId"), gymId), size = po.size, cursor = cursor),
            Lambda(nextRef => Select("data", Get(nextRef)))),
          Abort(StringV(s"gymId $gymId does not exist"))))

    result.decode[Page[Route]]
  }

  def findByGymIdAndFields(gymId: String, profile: Option[ClimbProfile], climbingType: Option[ClimbingType], gradeLabel: Option[String])(po: PaginationOptions)(implicit ec: ExecutionContext): Future[Page[Route]] = {
    (profile, climbingType, gradeLabel) match {
      case (None, None, None) =>
        findByGymId(gymId)(po)
      case _ =>

        def filteringClause(route: Expr): Expr = {
          val profileClause: Expr = profile.map(classNameObject)
            .map(ContainsValue(_, Select("profile", route)))
            .getOrElse(true)

          val typeClause: Expr = climbingType.map(classNameObject)
            .map(Equals(_, Select("climbingType", route)))
            .getOrElse(true)

          val gradeLabelClause: Expr = gradeLabel
            .map(Equals(_, Select(ArrayV("grade", "label"), route)))
            .getOrElse(true)

          And(profileClause, typeClause, gradeLabelClause)
        }

        val result =
          client.query(
            If(
              Exists(Ref(Collection("gyms"), gymId)),
              Filter(
                Map(
                  Paginate(Match(Index("routes_by_gymId"), gymId), size = po.size, cursor = paginationOptionsToCursor(po)),
                  Lambda(nextRef => Select("data", Get(nextRef)))),
                Lambda(route => filteringClause(route))),
              Abort(StringV(s"gymId $gymId does not exist"))))

        result.decode[Page[Route]]
    }
  }

  def saveRoute(route: Route)(implicit ec: ExecutionContext): Future[Route] = {
    val result = client.query(
      If(
        Exists(Ref(Collection("gyms"), route.gymId)),
        saveQuery(route.id, route),
        Abort(StringV(s"gymId ${route.gymId} does not exist"))))
    result.decode[Route]
  }

}
