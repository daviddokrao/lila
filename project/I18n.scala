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
  // upstream and keep merging cleanly. Mirrors ui/.build/src/i18n.ts, which does the
  // same for the browser bundles — keep the two in sync.
  //
  // URLs are skipped wholesale rather than guarded by a domain suffix: \b treats the
  // hyphens in a slug as word boundaries, so a bare brand regex rewrites the middle of
  // //lichess.org/blog/V0KrLSkAAMo3hsi4/study-chess-the-lichess-way and the link 404s.
  // A bare domain outside a URL (lichess.com) is a link target too, hence the .[a-z]
  // lookahead. Compounds keep working: Lichess-Konto -> HungKings-Konto.
  private val brandRe = """\b([Ll])ichess\b(?!\.[a-z])""".r
  // "lichess.org" trong CÂU VĂN (ngoài URL) là tên thương hiệu — vd subject
  // "Confirm your lichess.org account" — thay bằng TÊN brand (không phải domain,
  // nên không sinh domain giả). lichess.com/ovh vẫn giữ nguyên. Sync với i18n.ts.
  private val domainRe = """\b[Ll]ichess\.org\b""".r
  private val urlRe = """(?:https?:)?//[^\s'"<>)\]]+""".r

  private def swap(seg: String) =
    brandRe.replaceAllIn(domainRe.replaceAllIn(seg, "HungKings"), brandFor)

  private def rebrand(s: String) =
    val out = new StringBuilder
    var end = 0
    for url <- urlRe.findAllMatchIn(s) do
      out ++= swap(s.substring(end, url.start))
      out ++= url.matched
      end = url.end
    out ++= swap(s.substring(end))
    out.toString

  private def brandFor(m: scala.util.matching.Regex.Match) =
    if m.group(1) == "L" then "HungKings" else "hungkings"

  private def toKey(e: scala.xml.Node, db: String) =
    if db == "site" then e.\("@name").toString
    else s"$db:${e.\("@name")}"
