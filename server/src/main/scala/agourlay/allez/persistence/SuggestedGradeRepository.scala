package agourlay.allez.persistence

import agourlay.allez.api.{ Page, PaginationOptions, SuggestedGrade, UserError }
import faunadb.FaunaClient
import faunadb.errors.BadRequestException
import faunadb.query.{ Abort, Collection, Exists, Get, If, Index, Lambda, Map, Match, Paginate, Ref, Select }
import faunadb.values.{ Codec, StringV }
import scala.concurrent.{ ExecutionContext, Future }

class SuggestedGradeRepository(faunaClient: FaunaClient) extends AggregateRepository[SuggestedGrade] {
  override protected val client: FaunaClient = faunaClient
  override protected val collectionName = "suggested_grades"
  implicit override protected val faunaCodec: Codec[SuggestedGrade] = Codec.Record[SuggestedGrade]

  // the userId has been validated during the authentication already
  def saveSuggestedRating(suggestedRating: SuggestedGrade)(implicit ec: ExecutionContext): Future[SuggestedGrade] = {
    // TODO check that the suggestion as the same scale as the base grade?
    val result = client.query(
      If(
        Exists(Ref(Collection("routes"), suggestedRating.routeId)),
        saveQuery(suggestedRating.id, suggestedRating),
        Abort(StringV(s"routeId ${suggestedRating.routeId} does not exist"))))

    result.decode[SuggestedGrade]
      .recoverWith {
        case e: BadRequestException if e.errors.exists(_.code == "instance not unique") =>
          Future.failed(UserError("The user already suggested a rating for this route"))
      }
  }

  def findByUserId(userId: String)(po: PaginationOptions)(implicit ec: ExecutionContext): Future[Page[SuggestedGrade]] = {
    val cursor = paginationOptionsToCursor(po)

    val result =
      client.query(
        If(
          Exists(Ref(Collection("users"), userId)),
          Map(
            Paginate(Match(Index("suggested_grades_by_user_id"), userId), size = po.size, cursor = cursor),
            Lambda(nextRef => Select("data", Get(nextRef)))),
          Abort(StringV(s"user $userId does not exist"))))

    result.decode[Page[SuggestedGrade]]
  }

  def findByRouteId(routeId: String)(po: PaginationOptions)(implicit ec: ExecutionContext): Future[Page[SuggestedGrade]] = {
    val cursor = paginationOptionsToCursor(po)

    val result =
      client.query(
        If(
          Exists(Ref(Collection("routes"), routeId)),
          Map(
            Paginate(Match(Index("suggested_grades_by_route_id"), routeId), size = po.size, cursor = cursor),
            Lambda(nextRef => Select("data", Get(nextRef)))),
          Abort(StringV(s"route $routeId does not exist"))))

    result.decode[Page[SuggestedGrade]]
  }
}
