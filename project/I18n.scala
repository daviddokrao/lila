import java.io.{ File, FileOutputStream, ObjectOutputStream }
import java.util.{ HashMap, ArrayList }
import sbt.*
import scala.jdk.CollectionConverters.*

object I18n:
  def serialize(
      sourceDir: File,
      destDir: File,
      dbs: List[String],
      outputDir: File
  ): Seq[File] =
    val locales = "en-GB" :: (destDir / "site").listFiles.map(_.getName.takeWhile(_ != '.')).sorted.toList

    outputDir.mkdirs()

    val files = locales.map { locale =>
      val file = new File(outputDir, s"i18n.$locale.ser")
      val translations = makeMap(locale, sourceDir, destDir, dbs.asJava)
      val out = new ObjectOutputStream(new FileOutputStream(file))
      out.writeObject(translations)
      out.close()
      file
    }
    files

  private def makeMap(
      locale: String,
      sourceDir: File,
      destDir: File,
      dbs: java.util.List[String]
  ) =
    val result = new HashMap[String, Object]()
    dbs.forEach { db =>
      val file =
        if locale == "en-GB" then new File(sourceDir, s"$db.xml")
        else new File(destDir, s"$db/$locale.xml")
      if file.exists && file.isFile then
        val xml = scala.xml.XML.loadFile(file)
        xml.child.foreach { e =>
          val key = toKey(e, db)
          e.label match
            case "string" =>
              result.put(key, unescapeQuotes(e.text))
            case "plurals" =>
              val plurals = new HashMap[String, String]()
              e.child.filter(_.label == "item").foreach { i =>
                plurals.put(i.\("@quantity").toString, unescapeQuotes(i.text))
              }
              result.put(key, plurals)
            case _ =>
        }
    }
    result

  private def unescapeQuotes(s: String) =
    rebrand(s.replace("\\\"", "\"").replace("\\'", "'"))

  // Swap the upstream brand out of every translated string at build time instead of
  // editing translation/dest, so all ~100 language files stay byte-identical to
  // upstream and keep merging cleanly. Domains are left alone: a trailing .org/.dev
  // means the mention is a link target, not the product name.
  private val brandRe = """\b([Ll])ichess\b(?!\.(?:org|dev))""".r
  private def rebrand(s: String) =
    brandRe.replaceAllIn(s, m => if m.group(1) == "L" then "9Kings" else "9kings")

  private def toKey(e: scala.xml.Node, db: String) =
    if db == "site" then e.\("@name").toString
    else s"$db:${e.\("@name")}"
