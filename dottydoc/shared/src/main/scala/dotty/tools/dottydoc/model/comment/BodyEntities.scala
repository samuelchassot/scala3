package dotty.tools.dottydoc
package model
package comment

import scala.collection._

/** A body of text. A comment has a single body, which is composed of
  * at least one block. Inside every body is exactly one summary (see
  * [[scala.tools.nsc.doc.model.comment.Summary]]). */
final case class Body(blocks: Seq[Block]) {

  /** The summary text of the comment body. */
  lazy val summary: Option[Body] = {
    def summaryInBlock(block: Block): Seq[Inline] = block match {
      case Title(text, _)        => summaryInInline(text)
      case Paragraph(text)       => summaryInInline(text)
      case UnorderedList(items)  => items flatMap summaryInBlock
      case OrderedList(items, _) => items flatMap summaryInBlock
      case DefinitionList(items) => items.values.toSeq flatMap summaryInBlock
      case _                     => Nil
    }
    def summaryInInline(text: Inline): Seq[Inline] = text match {
      case Summary(text)     => List(text)
      case Chain(items)      => items flatMap summaryInInline
      case Italic(text)      => summaryInInline(text)
      case Bold(text)        => summaryInInline(text)
      case Underline(text)   => summaryInInline(text)
      case Superscript(text) => summaryInInline(text)
      case Subscript(text)   => summaryInInline(text)
      case Link(_, title)    => summaryInInline(title)
      case _                 => Nil
    }
    (blocks flatMap summaryInBlock).toList match {
      case Nil => None
      case inline :: Nil => Some(Body(Seq(Paragraph(inline))))
      case inlines => Some(Body(Seq(Paragraph(Chain(inlines)))))
    }
  }
}

/** A block-level element of text, such as a paragraph or code block. */
sealed abstract class Block

final case class Title(text: Inline, level: Int) extends Block
final case class Paragraph(text: Inline) extends Block
final case class Code(data: String) extends Block
final case class UnorderedList(items: Seq[Block]) extends Block
final case class OrderedList(items: Seq[Block], style: String) extends Block
final case class DefinitionList(items: SortedMap[Inline, Block]) extends Block
final case class HorizontalRule() extends Block

/** An section of text inside a block, possibly with formatting. */
sealed abstract class Inline

final case class Chain(items: Seq[Inline]) extends Inline
final case class Italic(text: Inline) extends Inline
final case class Bold(text: Inline) extends Inline
final case class Underline(text: Inline) extends Inline
final case class Superscript(text: Inline) extends Inline
final case class Subscript(text: Inline) extends Inline
final case class Link(target: String, title: Inline) extends Inline
final case class Monospace(text: Inline) extends Inline
final case class Text(text: String) extends Inline
abstract class EntityLink(val title: Inline) extends Inline { def link: LinkTo }
object EntityLink {
  def apply(title: Inline, linkTo: LinkTo) = new EntityLink(title) { def link: LinkTo = linkTo }
  def unapply(el: EntityLink): Option[(Inline, LinkTo)] = Some((el.title, el.link))
}
final case class HtmlTag(data: String) extends Inline {
  private val Pattern = """(?ms)\A<(/?)(.*?)[\s>].*\z""".r
  private val (isEnd, tagName) = data match {
    case Pattern(s1, s2) =>
      (! s1.isEmpty, Some(s2.toLowerCase))
    case _ =>
      (false, None)
  }

  def canClose(open: HtmlTag) = {
    isEnd && tagName == open.tagName
  }

  private val TagsNotToClose = Set("br", "img")
  def close = tagName collect { case name if !TagsNotToClose(name) => HtmlTag(s"</$name>") }
}

/** The summary of a comment, usually its first sentence. There must be exactly one summary per body. */
final case class Summary(text: Inline) extends Inline

sealed trait LinkTo
final case class LinkToExternal(name: String, url: String) extends LinkTo
final case class Tooltip(name: String) extends LinkTo

/** Linking directly to entities is not picklable because of cyclic references */
final case class LinkToEntity(entity: Entity) extends LinkTo

/** Use MaterializableLink for entities that need be picklable */
sealed trait MaterializableLink { def title: String }
final case class UnsetLink(title: String, query: String) extends MaterializableLink
final case class MaterializedLink(title: String, target: String) extends MaterializableLink
final case class NoLink(title: String, target: String) extends MaterializableLink

sealed trait Reference
final case class TypeReference(title: String, tpeLink: MaterializableLink, paramLinks: List[Reference]) extends Reference
final case class OrTypeReference(left: Reference, right: Reference) extends Reference
final case class AndTypeReference(left: Reference, right: Reference) extends Reference
final case class FunctionReference(args: List[Reference], returnValue: Reference) extends Reference
final case class BoundsReference(low: Reference, high: Reference) extends Reference
final case class NamedReference(title: String, ref: Reference, isByName: Boolean = false, isRepeated: Boolean = false) extends Reference
final case class ConstantReference(title: String) extends Reference
