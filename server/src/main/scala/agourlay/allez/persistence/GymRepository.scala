package agourlay.allez.persistence

import agourlay.allez.api.{ Gym, Page, PaginationOptions }
import faunadb.FaunaClient
import faunadb.query.{ Map, _ }
import faunadb.values.{ Codec, StringV }

import scala.concurrent.{ ExecutionContext, Future }

class GymRepository(faunaClient: FaunaClient) extends AggregateRepository[Gym] {
  override protected val client: FaunaClient = faunaClient
  override protected val collectionName = "gyms"
  implicit override protected val faunaCodec: Codec[Gym] = Codec.Record[Gym]

  def findByName(name: String)(po: PaginationOptions)(implicit ec: ExecutionContext): Future[Page[Gym]] = {
    val beforeCursor = po.before.map(id => Before(Ref(Collection(collectionName), id)))
    val afterCursor = po.after.map(id => After(Ref(Collection(collectionName), id)))
    val cursor = beforeCursor.orElse(afterCursor).getOrElse(NoCursor)

    val result =
      client.query(
        Map(
          Paginate(Match(Index("gyms_by_name"), name), size = po.size, cursor = cursor),
          Lambda(nextRef => Select("data", Get(nextRef)))))

    result.decode[Page[Gym]]
  }

  def deleteGym(gymId: String)(implicit ec: ExecutionContext): Future[Option[Gym]] = {
    val result = client.query(
      If(
        IsNonEmpty(
          Paginate(Match(Index("routes_by_gymId"), gymId))),
        Abort(StringV(s"gymId $gymId can't be deleted as it has routes attached")),
        Select("data", Delete(Ref(Collection(collectionName), gymId)))))

    result.decode[Option[Gym]]
  }

}
