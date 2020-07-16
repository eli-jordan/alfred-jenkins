package alfred.jenkins

import io.circe.Encoder
import io.circe.generic.semiauto._

/*
  See https://www.alfredapp.com/help/workflows/inputs/script-filter/json/
 */

/**
  * Represents the json output of a script filter, that is interpreted by Alfred.
  *
  * @param variables Variables that are passed out of the script filter
  * @param rerun If set, hold the interval in seconds before the script should be re-executed
  * @param items The items in the filter list
  */
case class ScriptFilter(
    variables: Map[String, String] = Map.empty,
    rerun: Option[Double] = None,
    items: List[Item]
)

object ScriptFilter {
  implicit val encoder: Encoder[ScriptFilter] = deriveEncoder
}

sealed abstract class ItemType(val value: String)
object ItemType {
  case object Default       extends ItemType("default")
  case object File          extends ItemType("file")
  case object FileSkipCheck extends ItemType("file:skipcheck")

  val Values = List(Default, File, FileSkipCheck)

  implicit val encoder: Encoder[ItemType] = Encoder[String].contramap(_.value)

}
case class Item(
    uid: Option[String] = None,
    `type`: ItemType = ItemType.Default,
    title: String,
    subtitle: Option[String] = None,
    arg: Option[String] = None,
    icon: Option[Icon] = None,
    valid: Boolean = true,
    `match`: Option[String] = None,
    autocomplete: Option[String] = None,
    mods: Map[String, ModifierDetail] = Map.empty,
    text: Option[CopyText] = None,
    quicklookurl: Option[String] = None,
    variables: Map[String, String] = Map.empty
)

object Item {
  implicit val encoder: Encoder[Item] = deriveEncoder
}

case class CopyText(
    copy: String,
    largetype: String
)

object CopyText {
  implicit val encoder: Encoder[CopyText] = deriveEncoder
}

case class Icon(
    `type`: Option[String] = None,
    path: String
)

object Icon {
  implicit val encoder: Encoder[Icon] = deriveEncoder
}

case class ModifierDetail(
    valid: Boolean = true,
    arg: String,
    subtitle: String,
    variables: Map[String, String]
)

object ModifierDetail {
  implicit val encoder: Encoder[ModifierDetail] = deriveEncoder
}
