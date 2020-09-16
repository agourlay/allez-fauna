package agourlay.allez.persistence

import agourlay.allez.api.{ User, UserDraft, UserError }
import faunadb.FaunaClient
import faunadb.errors.BadRequestException
import faunadb.query._
import faunadb.values.{ ArrayV, Codec }
import scala.concurrent.{ ExecutionContext, Future }

class UserRepository(faunaClient: FaunaClient) extends AggregateRepository[User] {
  override protected val client: FaunaClient = faunaClient
  override protected val collectionName = "users"
  implicit override protected val faunaCodec: Codec[User] = Codec.Record[User]

  def createUser(id: String, draft: UserDraft)(implicit ec: ExecutionContext): Future[User] = {
    val user = User.fromDraft(id, draft)
    val payload = Obj(
      "data" -> user,
      "credentials" -> Obj("password" -> draft.password)
    )
    val result = client.query(
      Select(
        "data",
        If(
          Exists(Ref(Collection(collectionName), id)),
          Replace(Ref(Collection(collectionName), id), payload),
          Create(Ref(Collection(collectionName), id), payload)))
    )
    result.decode[User]
      .recoverWith {
        case e: BadRequestException if e.errors.exists(_.code == "instance not unique") =>
          Future.failed(UserError("A user already exists with this email"))
      }
  }

  def userLogin(email: String, inputPassword: String)(implicit ec: ExecutionContext): Future[String] = {
    val result = client.query(
      Select("secret",
        Login(
          Match(Index("users_by_email"), email),
          Obj("password" -> inputPassword)
        )
      )
    )
    result.decode[String]
  }

  // Returns Option[userId]
  def verifyUserSecret(secret: String)(implicit ec: ExecutionContext): Future[Option[String]] = {
    val result = client.query(
      Select(ArrayV("instance", "id"), KeyFromSecret(secret))
    )
    result.optDecode[String]
  }

}
