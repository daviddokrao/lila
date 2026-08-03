package lila.web

import play.api.libs.json.{ JsArray, Json }
import play.api.mvc.RequestHeader

import lila.common.HTTPRequest
import lila.common.Json.given
import lila.core.config.NetConfig

object StaticContent:

  val robotsTxt = """User-agent: *
Allow: /
Disallow: /game/export/
Disallow: /games/export/
Disallow: /api/
Disallow: /opening/config/
Disallow: /study/search
Allow: /game/export/gif/thumbnail/
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
