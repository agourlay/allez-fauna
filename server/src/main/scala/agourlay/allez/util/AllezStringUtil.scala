package agourlay.allez.util

object AllezStringUtil {

  def classNameObject[O](t: O): String =
    t.getClass.getSimpleName.dropRight(1)

}
