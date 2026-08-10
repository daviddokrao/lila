package lila.web

import play.api.libs.json.{ JsArray, Json }
import play.api.mvc.RequestHeader

import lila.common.HTTPRequest
import lila.common.Json.given
import lila.core.config.NetConfig

object StaticContent:

  // HungKings: nhận `net` để khai dòng `Sitemap:` bằng ĐÚNG baseUrl đang chạy. Hardcode
  // tên miền ở đây là thứ sẽ âm thầm sai ngay lần đổi domain kế tiếp (đã đổi 1 lần rồi).
  def robotsTxt(net: NetConfig) = s"""User-agent: *
Allow: /
Disallow: /game/export/
Disallow: /games/export/
Disallow: /api/
Disallow: /opening/config/
Disallow: /study/search
Allow: /game/export/gif/thumbnail/

Sitemap: ${net.baseUrl.value.stripSuffix("/")}/sitemap.xml
"""

  /**
   * HungKings — sitemap.xml (P2.3). Chỉ liệt kê các trang CÔNG KHAI, ỔN ĐỊNH và có nội
   * dung thật; cố ý KHÔNG liệt kê trang sinh động (ván, hồ sơ, giải cụ thể) vì chúng đổi
   * liên tục và Google tự tìm qua liên kết.
   *
   * Chỉ khai những đường CÒN SỐNG: các tính năng đã tắt theo cờ (/forum, /broadcast,
   * /patron, /features, /video) đều 404, khai vào sitemap là tự nộp lỗi cho Search Console.
   * Bật lại cờ nào thì thêm lại dòng đó ở đây.
   *
   * Song ngữ: mỗi trang khai kèm `xhtml:link hreflang` cho vi/en/x-default, khớp với
   * hreflang lila đã phát trong `<head>` (LangPath) — hai nơi lệch nhau là lỗi SEO khó thấy.
   */
  // Ký tự xuống dòng đặt thành hằng số và viết bằng mã unicode: viết escape thẳng trong
  // chuỗi Scala mà đi qua vài lớp script thì rất dễ bị nuốt mất một lớp và biến thành
  // xuống dòng THẬT giữa string literal (đã mất một vòng build vì đúng lỗi này).
  private val newline = "\u000a"

  private val sitemapPaths = List(
    "",
    "training",
    "training/themes",
    "storm",
    "racer",
    "streak",
    "learn",
    "practice",
    "analysis",
    "study",
    "editor",
    "tournament",
    "tournament/calendar",
    "tournament/leaderboard",
    "swiss",
    "player",
    "team",
    "games",
    "streamer",
    "simul",
    "hlv",
    "about",
    "faq",
    "contact",
    "source",
    "terms-of-service",
    "page/privacy",
    "developers",
    "login",
    "signup"
  )

  def sitemapXml(net: NetConfig): String =
    val base = net.baseUrl.value.stripSuffix("/")
    val urls = sitemapPaths
      .map: path =>
        val loc = if path.isEmpty then s"$base/" else s"$base/$path"
        val vi = if path.isEmpty then s"$base/vi" else s"$base/vi/$path"
        val en = if path.isEmpty then s"$base/en" else s"$base/en/$path"
        s"""  <url>
    <loc>$loc</loc>
    <xhtml:link rel="alternate" hreflang="vi" href="$vi"/>
    <xhtml:link rel="alternate" hreflang="en" href="$en"/>
    <xhtml:link rel="alternate" hreflang="x-default" href="$loc"/>
  </url>"""
      .mkString(newline)
    s"""<?xml version="1.0" encoding="UTF-8"?>
<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9" xmlns:xhtml="http://www.w3.org/1999/xhtml">
$urls
</urlset>
"""

  def manifest(net: NetConfig) =
    Json.obj(
      // Tên hiện trong hộp thoại "Cài đặt ứng dụng" — net.domain sẽ ra tên miền trần
      "name" -> "HungKings – Cờ vua trực tuyến",
      "short_name" -> "HungKings",
      "id" -> "/",
      "scope" -> "/",
      "lang" -> "vi",
      "categories" -> Json.arr("games", "education"),
      "start_url" -> "/",
      "display" -> "standalone",
      "shortcuts" -> Json.arr(
        Json.obj(
          "name" -> "Chơi với máy",
          "url" -> "/#ai",
          "icons" -> Json.arr(
            Json.obj("src" -> s"//${net.assetDomain}/assets/logo/lichess-favicon-192.png", "sizes" -> "192x192")
          )
        ),
        Json.obj(
          "name" -> "Câu đố",
          "url" -> "/training",
          "icons" -> Json.arr(
            Json.obj("src" -> s"//${net.assetDomain}/assets/logo/lichess-favicon-192.png", "sizes" -> "192x192")
          )
        )
      ),
      // Màu nền HungKings (khớp --c-bg-page theme tối) — trước là nâu Lichess #161512.
      "background_color" -> "#070e22",
      "theme_color" -> "#070e22",
      "description" -> "Chơi cờ vua trực tuyến miễn phí, không quảng cáo. Free online chess.",
      "icons" -> (List(32, 64, 128, 192, 256, 512, 1024).map: size =>
        Json.obj(
          "src" -> s"//${net.assetDomain}/assets/logo/lichess-favicon-$size.png",
          "sizes" -> s"${size}x$size",
          "type" -> "image/png"
        )
      ).appendedAll(
        // maskable: mark đặt trong safe-zone 20% trên nền đặc — icon thường sẽ bị
        // Android cắt cụt góc nếu chỉ gắn nhãn maskable
        List(192, 512).map: size =>
          Json.obj(
            "src" -> s"//${net.assetDomain}/assets/logo/lichess-maskable-$size.png",
            "sizes" -> s"${size}x$size",
            "type" -> "image/png",
            "purpose" -> "maskable"
          )
      )
      // related_applications đã gỡ: nó trỏ app store của Lichess, HungKings chưa có app.
    )

  val mobileAndroidId = "org.lichess.mobileV2"
  val mobileAndroidUrl = s"https://play.google.com/store/apps/details?id=$mobileAndroidId"
  val mobileIosUrl = "https://apps.apple.com/app/lichess/id1662361230"
  val mobileFdroidUrl = s"https://f-droid.org/packages/$mobileAndroidId"

  def appStoreUrl(using req: RequestHeader) =
    if HTTPRequest.isAndroid(req) then mobileAndroidUrl else mobileIosUrl

  val swagStoreTlds = Map(
    "US" -> "com",
    "CA" -> "ca",
    "DE" -> "de",
    "FR" -> "fr",
    "UK" -> "co.uk",
    "IT" -> "it",
    "ES" -> "es",
    "NL" -> "nl",
    "PL" -> "pl",
    "BE" -> "be",
    "DK" -> "dk",
    "AU" -> "com.au",
    "IE" -> "ie",
    "NO" -> "no",
    "CH" -> "ch",
    "FI" -> "fi",
    "SE" -> "se",
    "AT" -> "at"
  )
  def swagUrl(countryCode: Option[String]) =
    val tld = swagStoreTlds.getOrElse(~countryCode, "net")
    s"https://lichess.myspreadshop.$tld/"

  val variantsJson =
    JsArray(chess.variant.Variant.list.all.map { v =>
      Json.obj(
        "id" -> v.id,
        "key" -> v.key,
        "name" -> v.name
      )
    })

  def legacyQaQuestion(id: Int) =
    val faq = routes.Main.faq.url
    id match
      case 103 => s"$faq#acpl"
      case 258 => s"$faq#marks"
      case 13 => s"$faq#titles"
      case 87 => routes.User.ratingDistribution(PerfKey.blitz).url
      case 110 => s"$faq#name"
      case 29 => s"$faq#titles"
      case 4811 => s"$faq#lm"
      case 216 => routes.Main.app.url
      case 340 => s"$faq#trophies"
      case 6 => s"$faq#ratings"
      case 207 => s"$faq#hide-ratings"
      case 547 => s"$faq#leaving"
      case 259 => s"$faq#trophies"
      case 342 => s"$faq#provisional"
      case 50 => routes.Cms.help.url
      case 46 => s"$faq#name"
      case 122 => s"$faq#marks"
      case _ => faq
