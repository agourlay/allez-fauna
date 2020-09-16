package agourlay.allez.api

import java.time.Instant

import io.circe.generic.extras.semiauto._
import faunadb.values.{ Codec, FieldPath, RecordCodec, Result, StringV, Value }
import agourlay.allez.util.AllezStringUtil._
import cats.data.{ NonEmptyList, Validated, ValidatedNel }
import org.http4s.{ ParseFailure, QueryParamDecoder, QueryParameterValue }

trait Aggregate {
  def id: String
  def createdAt: Instant
}

case class Page[A](data: Seq[A], before: Option[String], after: Option[String])
case class PaginationOptions(size: Option[Int], before: Option[String], after: Option[String])

case class Gym(id: String, name: String, address: String, website: Option[String], createdAt: Instant) extends Aggregate

object Gym {
  def fromDraft(id: String, draft: GymDraft): Gym =
    Gym(id, draft.name, draft.address, draft.website, Instant.now())
}

case class GymDraft(name: String, address: String, website: Option[String])

sealed trait Scale
object Hueco extends Scale
object Fontainebleau extends Scale
object YosemiteDecimal extends Scale

object Scale {
  implicit val codecCirce = deriveEnumerationCodec[Scale]
  implicit val codecFauna: RecordCodec[Scale] = new RecordCodec[Scale] {
    // TODO how to derive sum types?
    override def decode(v: Value, path: FieldPath): Result[Scale] =
      v match {
        case StringV("Hueco") => Result.successful(Hueco, path)
        case StringV("Fontainebleau") => Result.successful(Fontainebleau, path)
        case StringV("YosemiteDecimal") => Result.successful(YosemiteDecimal, path)
        case v => Result.Unexpected(v, "Hueco|Fontainebleau|YosemiteDecimal", path)
      }

    override def encode(t: Scale): Value = StringV(classNameObject(t))
  }
}

// TODO the label is actually an enum of the Scale...
case class Grade(label: String, scale: Scale)

object Grade {
  implicit val codecFauna: RecordCodec[Grade] = Codec.Record[Grade]
}

sealed trait ClimbProfile
object Crimp extends ClimbProfile
object Reach extends ClimbProfile
object Dynamic extends ClimbProfile
object Pocket extends ClimbProfile
object Overhang extends ClimbProfile

object ClimbProfile {
  implicit val codecCirce = deriveEnumerationCodec[ClimbProfile]
  implicit val codecFauna: RecordCodec[ClimbProfile] = new RecordCodec[ClimbProfile] {
    // TODO how to derive sum types?
    override def decode(v: Value, path: FieldPath): Result[ClimbProfile] =
      v match {
        case StringV("Crimp") => Result.successful(Crimp, path)
        case StringV("Reach") => Result.successful(Reach, path)
        case StringV("Dynamic") => Result.successful(Dynamic, path)
        case StringV("Pocket") => Result.successful(Pocket, path)
        case StringV("Overhang") => Result.successful(Overhang, path)
        case v => Result.Unexpected(v, "Crimp|Reach|Dynamic|Pocket|Overhang", path)
      }

    override def encode(t: ClimbProfile): Value = StringV(classNameObject(t))
  }

  // TODO derive this for http4s?
  implicit val queryParamDecoder: QueryParamDecoder[ClimbProfile] = new QueryParamDecoder[ClimbProfile] {
    override def decode(value: QueryParameterValue): ValidatedNel[ParseFailure, ClimbProfile] = value.value match {
      case "Crimp" => Validated.Valid(Crimp)
      case "Reach" => Validated.Valid(Reach)
      case "Dynamic" => Validated.Valid(Dynamic)
      case "Pocket" => Validated.Valid(Pocket)
      case "Overhang" => Validated.Valid(Overhang)
      case v => Validated.Invalid(NonEmptyList.one(ParseFailure(v, "Choose from Crimp|Reach|Dynamic|Pocket|Overhang")))
    }
  }
}

sealed trait ClimbingType
object Bouldering extends ClimbingType
object TopRope extends ClimbingType
object Lead extends ClimbingType

object ClimbingType {
  implicit val codecCirce = deriveEnumerationCodec[ClimbingType]
  implicit val codecFauna: RecordCodec[ClimbingType] = new RecordCodec[ClimbingType] {
    // TODO how to derive sum types?
    override def decode(v: Value, path: FieldPath): Result[ClimbingType] =
      v match {
        case StringV("Bouldering") => Result.successful(Bouldering, path)
        case StringV("TopRope") => Result.successful(TopRope, path)
        case StringV("Lead") => Result.successful(Lead, path)
        case v => Result.Unexpected(v, "Bouldering|TopRope|Lead", path)
      }

    override def encode(t: ClimbingType): Value = StringV(classNameObject(t))
  }

  // TODO derive this for http4s?
  implicit val queryParamDecoder: QueryParamDecoder[ClimbingType] = new QueryParamDecoder[ClimbingType] {
    override def decode(value: QueryParameterValue): ValidatedNel[ParseFailure, ClimbingType] = value.value match {
      case "Bouldering" => Validated.Valid(Bouldering)
      case "TopRope" => Validated.Valid(TopRope)
      case "Lead" => Validated.Valid(Lead)
      case v => Validated.Invalid(NonEmptyList.one(ParseFailure(v, "Choose from Bouldering|TopRope|Lead")))
    }
  }
}

sealed trait Color
object Blue extends Color
object Yellow extends Color
object Red extends Color
object Green extends Color
object Orange extends Color
object Brown extends Color
object Black extends Color
object Grey extends Color
object OtherColor extends Color

object Color {
  implicit val codecCirce = deriveEnumerationCodec[Color]
  implicit val codecFauna: RecordCodec[Color] = new RecordCodec[Color] {
    // TODO how to derive sum types?
    override def decode(v: Value, path: FieldPath): Result[Color] =
      v match {
        case StringV("Blue") => Result.successful(Blue, path)
        case StringV("Yellow") => Result.successful(Yellow, path)
        case StringV("Red") => Result.successful(Red, path)
        case StringV("Green") => Result.successful(Green, path)
        case StringV("Orange") => Result.successful(Orange, path)
        case StringV("Brown") => Result.successful(Brown, path)
        case StringV("Black") => Result.successful(Black, path)
        case StringV("Grey") => Result.successful(Grey, path)
        case StringV("OtherColor") => Result.successful(OtherColor, path)
        case v => Result.Unexpected(v, "Blue|Yellow|Red|Green|Orange|Brown|Black|Grey|OtherColor", path)
      }

    override def encode(t: Color): Value = StringV(classNameObject(t))
  }
}

case class Route(
  id: String,
  gymId: String,
  name: String,
  climbingType: ClimbingType,
  grade: Grade,
  profile: List[ClimbProfile],
  gripsColor: Option[Color],
  setAt: Instant,
  closedAt: Option[Instant],
  createdAt: Instant) extends Aggregate

object Route {
  def fromDraft(id: String, draft: RouteDraft): Route =
    Route(id, draft.gymId, draft.name, draft.climbingType, draft.grade, draft.profile, draft.gripsColor, draft.setAt, draft.closedAt, Instant.now())
}

case class RouteDraft(
  gymId: String,
  name: String,
  climbingType: ClimbingType,
  grade: Grade,
  profile: List[ClimbProfile] = Nil,
  gripsColor: Option[Color] = None,
  setAt: Instant,
  closedAt: Option[Instant] = None)

case class User(id: String, firstName: String, surname: String, email: String, country: Option[String], createdAt: Instant) extends Aggregate
case class UserDraft(firstName: String, surname: String, email: String, password: String, country: Option[String])
case class UserLogin(email: String, password: String)

object User {
  def fromDraft(id: String, draft: UserDraft): User =
    User(id, draft.firstName, draft.surname, draft.email, draft.country, Instant.now())
}

case class SuggestedGrade(id: String, userId: String, routeId: String, grade: Grade, comment: Option[String], createdAt: Instant) extends Aggregate
case class SuggestedGradeDraft(routeId: String, grade: Grade, comment: Option[String])

object SuggestedGrade {
  def fromDraft(id: String, userId: String, draft: SuggestedGradeDraft): SuggestedGrade =
    SuggestedGrade(id, userId, draft.routeId, draft.grade, draft.comment, Instant.now())
}