package agourlay.allez.persistence

import agourlay.allez.api.{ Aggregate, Page, PaginationOptions }
import agourlay.allez.util.Logging
import faunadb.FaunaClient
import faunadb.errors.NotFoundException
import faunadb.query._
import faunadb.values._

import scala.concurrent.{ ExecutionContext, Future }

trait AggregateRepository[A <: Aggregate] extends AggregateRepository.Implicits with Logging {
  protected val client: FaunaClient
  protected val collectionName: String
  implicit protected val faunaCodec: Codec[A]

  def paginationOptionsToCursor(po: PaginationOptions): Cursor = {
    val beforeCursor = po.before.map(id => Before(Ref(Collection(collectionName), id)))
    val afterCursor = po.after.map(id => After(Ref(Collection(collectionName), id)))
    beforeCursor.orElse(afterCursor).getOrElse(NoCursor)
  }

  /**
   * It returns a unique valid Id leveraging Fauna's NewId function.
   *
   * The produced Id is guaranteed to be unique across the entire
   * cluster and once generated will never be generated a second time.
   *
   * @return a unique valid Id
   * @see [[https://docs.fauna.com/fauna/current/reference/queryapi/misc/newid NewId]]
   */
  def nextId()(implicit ec: ExecutionContext): Future[String] = {
    val result = client.query(
      NewId())
    result.decode[String]
  }

  // TODO should generate the id in a single db call instead of expecting the id to be there
  def saveDocument(entity: A)(implicit ec: ExecutionContext): Future[A] = {
    val result = client.query(
      saveQuery(entity.id, entity))
    result.decode[A]
  }

  def removeDocument(id: String)(implicit ec: ExecutionContext): Future[Option[A]] = {
    val result = client.query(
      Select(
        "data",
        Delete(Ref(Collection(collectionName), id))))

    result.optDecode[A]
  }

  def truncateCollection(pageSize: Int = 100)(implicit ec: ExecutionContext): Future[Unit] = {
    // Returns a page of deleted References (which are not used for now)
    def deleteNextPage(afterId: Option[String]): Future[Page[RefV]] = {
      val res = client.query(
        Map(
          Paginate(
            resource = Documents(Collection(collectionName)),
            size = pageSize,
            cursor = afterId.fold[Cursor](NoCursor)(i => After(Ref(Collection(collectionName), i)))),
          Lambda(
            "aggRef",
            Let(
              bindings = "aggDeleteDoc" -> Delete(Var("aggRef")) :: Nil,
              in = Var("aggRef")))))
      res.decode[Page[RefV]]
    }

    def loopDelete(currentPage: Option[Page[RefV]]): Future[Unit] = {
      currentPage match {
        case Some(p) if p.after.isEmpty => // reached end of cursor
          Future.unit
        case Some(p) =>
          deleteNextPage(p.after).flatMap(p => loopDelete(Some(p))) // delete next page
        case None =>
          deleteNextPage(None).flatMap(p => loopDelete(Some(p))) // delete first page
      }
    }

    loopDelete(None)
  }

  def find(id: String)(implicit ec: ExecutionContext): Future[Option[A]] = {
    val result = client.query(
      Select("data", Get(Ref(Collection(collectionName), id))))

    result.optDecode[A]
  }

  def findAll(po: PaginationOptions)(implicit ec: ExecutionContext): Future[Page[A]] = {
    val cursor = paginationOptionsToCursor(po)
    val result = client.query(
      Map(
        Paginate(Documents(Collection(collectionName)), size = po.size, cursor = cursor),
        Lambda(nextRef => Select("data", Get(nextRef)))))

    result.decode[Page[A]]
  }

  def searchByFieldContains(fieldName: String, searchInput: String)(implicit ec: ExecutionContext): Future[Page[A]] = {
    val result = client.query(
      Map(
        Filter(
          Paginate(Documents(Collection(collectionName))),
          Lambda(aggRef =>
            ContainsStr(
              LowerCase(Select(ArrayV("data", fieldName), Get(aggRef))),
              searchInput))),
        Lambda(aggRef => Select("data", Get(aggRef)))))

    result.decode[Page[A]]
  }

  protected def saveQuery(id: Expr, data: Expr): Expr =
    Select(
      "data",
      If(
        Exists(Ref(Collection(collectionName), id)),
        Replace(Ref(Collection(collectionName), id), Obj("data" -> data)),
        Create(Ref(Collection(collectionName), id), Obj("data" -> data))))

}

object AggregateRepository {

  trait Implicits {

    implicit def pageDecoder[A](implicit decoder: Decoder[A]): Decoder[Page[A]] = new Decoder[Page[A]] {
      def decode(v: Value, path: FieldPath): Result[Page[A]] = {
        /*
         * Note that below code for extracting the data within the "after"
         * and the "before" cursors directly depends on the definition of
         * the Index from which the Page is being derived. For this particular
         * case, the Index return values should only contain the Ref field
         * from the Instances being covered by the Index. If the Index return
         * values should contain more fields, update below code accordingly.
         */
        val before = v("before").to[Seq[RefV]].map(_.head.id).toOpt
        val after = v("after").to[Seq[RefV]].map(_.head.id).toOpt

        val result: Result[Page[A]] =
          v("data").to[Seq[A]].map { data =>
            Page(data, before, after)
          }

        result
      }
    }

    // TODO don't rely so much on Exception
    implicit class ExtendedFutureValue(value: Future[Value]) {

      def decode[A: Decoder](implicit ec: ExecutionContext): Future[A] = value.map(_.to[A].get)

      def optDecode[A: Decoder](implicit ec: ExecutionContext): Future[Option[A]] =
        value
          .decode[A]
          .map(Some(_))
          .recover {
            case _: NotFoundException => None
          }
    }
  }

  object Implicits extends Implicits
}