package controllers
import play.api.libs.json.*
import play.api.mvc.*

import lila.app.{ *, given }
import lila.common.Json.given
import lila.core.id.{ GameFullId, ImageId }
import lila.web.{ StaticContent, WebForms }
import scalalib.model.Language

final class Main(env: Env, assetsC: ExternalAssets) extends LilaController(env):

  def toggleBlindMode = OpenBody:
    bindForm(WebForms.blind)(
      _ => BadRequest,
      (enable, redirect) =>
        Redirect(redirect).withCookies:
          lila.web.WebConfig.blindCookie.make(env.security.lilaCookie)(enable != "0")
    )

  def handlerNotFound(msg: Option[String])(using RequestHeader) =
    makeContext.flatMap:
      keyPages.notFound(msg)(using _)

  def captchaCheck(id: GameId) = Anon:
    env.game.captcha.validate(id, ~get("solution")).map { valid =>
      Ok(if valid then 1 else 0)
    }

  def webmasters = Open:
    Ok.page(views.site.page.webmasters)

  // HLV AI: trang lila (có sidebar) nhúng service coach qua iframe. Có/không mã ván.
  def hlvCoach(id: String) = Open:
    Ok.page(views.site.ui.hlvCoach(id.some))

  def hlvCoachPuzzle(id: String) = Open:
    Ok.page(views.site.ui.hlvCoachPuzzle(id))

  def hlvCoachHome = Open:
    Ok.page(views.site.ui.hlvCoach(none))

  // Proxy SAME-ORIGIN cho service coach: trình duyệt gọi hungkings.com/hlv-app/*,
  // lila chuyển tiếp nội bộ sang container coach (cùng mạng docker `edge`) rồi stream
  // trả về. Nhờ vậy iframe ở /hlv là same-origin — không extension/quy tắc cross-origin
  // nào chặn được (đó là gốc lỗi "ảnh vỡ" trước đây). Truyền `X-Base-Path` để coach
  // phát URL tự trỏ dưới /hlv-app, và `X-Forwarded-For` để coach tính hạn mức theo IP
  // thật. `.stream()` + chunked + `noProxyBuffer` để SSE (text/event-stream) chảy realtime
  // chứ không bị đệm tới lúc đóng kết nối. Đích đổi được qua env, không cần dựng lại image.
  private val coachInternalUrl =
    sys.env.getOrElse("LILA_COACH_INTERNAL_URL", "http://hungkings-coach-web:8090")

  def hlvCoachProxy(path: String) = Anon:
    val qs     = ctx.req.rawQueryString
    val target = s"$coachInternalUrl/$path" + (if qs.nonEmpty then s"?$qs" else "")
    env.web.ws
      .url(target)
      .withMethod("GET")
      .addHttpHeaders(
        "X-Base-Path"     -> "/hlv-app",
        "X-Forwarded-For" -> lila.common.HTTPRequest.ipAddressStr(ctx.req)
      )
      .stream()
      .map: res =>
        val ct = res.headers
          .get("Content-Type")
          .orElse(res.headers.get("content-type"))
          .flatMap(_.headOption)
          .getOrElse("text/html; charset=utf-8")
        Status(res.status).chunked(res.bodyAsSource).as(ct).noProxyBuffer

  def lag = Open:
    Ok.page(views.site.ui.lag)

  def app = Open(serveApp)
  def appLang = LangPage(routes.Main.app)(serveApp)
  def mobile = Anon(MovedPermanently(routes.Main.app.url))
  def mobileLang(lang: Language) = Anon(MovedPermanently(routes.Main.appLang(lang).url))

  def redirectToAppStore = Anon:
    pageHit
    Redirect(StaticContent.appStoreUrl)

  // Route /swag đã gỡ khỏi conf/routes: nó chuyển hướng sang cửa hàng đồ lưu niệm
  // của Lichess (lichess.myspreadshop.net). Giữ hàm ở đây để diff với upstream còn
  // gọn; nối lại route khi nào HungKings có cửa hàng riêng và đã sửa
  // StaticContent.swagUrl trỏ về đó.
  def redirectToSwag = Anon:
    Redirect(StaticContent.swagUrl(env.security.geoIP(ctx.ip).so(_.countryCode)))

  private def serveApp(using Context) =
    pageHit
    FoundPage(env.cms.renderKey("mobile"))(views.mobile)

  def jslog(id: GameFullId) = Open:
    env.round.selfReport(
      userId = ctx.userId,
      ip = ctx.ip,
      fullId = id,
      name = get("n") | "?"
    )
    NoContent

  val robots = Anon:
    Ok:
      // HungKings: bỏ ràng buộc `env.mode.isProd`. Image mono demo cố ý chạy Play ở
      // Dev-mode (PlayServer đọc play.mode; ta KHÔNG lật sang prod vì đổi hành vi toàn
      // app trên site thật). Tín hiệu opt-in cho crawl ở đây là `net.crawlable=true`
      // (LILA_CRAWLABLE, mặc định false) + đúng domain chuẩn — đủ và tường minh. Máy dev
      // và upstream để crawlable=false nên vẫn trả Disallow như cũ.
      if env.net.crawlable && req.domain == env.net.domain.value
      then StaticContent.robotsTxt
      else "User-agent: *\nDisallow: /"

  def manifest = Anon:
    JsonOk:
      StaticContent.manifest(env.net)

  def getFishnet = Open:
    pageHit
    Ok.page(views.site.ui.getFishnet)

  def costs = Anon:
    pageHit
    Redirect:
      "https://docs.google.com/spreadsheets/d/1Si3PMUJGR9KrpE5lngSkHLJKJkb0ZuI4/preview"

  def contact = Open:
    pageHit
    Ok.page(views.site.page.contact)

  def faq = Open:
    pageHit
    Ok.page(views.site.page.faq)

  def temporarilyDisabled(@annotation.nowarn path: String) = Open:
    pageHit
    NotImplemented.page(views.site.message.temporarilyDisabled)

  def helpPath(path: String) = Open:
    path match
      case "keyboard-move" => Ok.snip(lila.web.ui.help.keyboardMove)
      case "voice/move" => Ok.snip(lila.web.ui.help.voiceMove)
      case "master" => Redirect(routes.TitleVerify.index.url)
      case _ => notFound

  def movedPermanently(to: String) = Anon:
    MovedPermanently(to)

  def instantChess = Open:
    pageHit
    if ctx.isAuth then Redirect(routes.Lobby.home)
    else
      Redirect(s"${routes.Lobby.home}#pool/10+0").withCookies:
        env.security.lilaCookie.withSession(remember = true): s =>
          s + ("theme" -> "ic") + ("pieceSet" -> "icpieces")

  def prometheusMetrics(key: String) = Anon:
    if key == env.web.config.prometheusKey
    then
      lila.web.PrometheusReporter
        .latestScrapeData()
        .fold(NotFound("No metrics found")): data =>
          lila.mon.prometheus.lines.update(data.lines.count.toDouble)
          Ok(data)
    else NotFound("Invalid prometheus key")

  def legacyQaQuestion(id: Int, @annotation.nowarn slug: String) = Anon:
    MovedPermanently:
      StaticContent.legacyQaQuestion(id)

  def devAsset(@annotation.nowarn v: String, path: String, file: String) = assetsC.at(path, file)

  def uploadImage(rel: String) = AuthBody(lila.web.HashedMultiPart(parse)) { ctx ?=> me ?=>
    lila.core.security
      .canUploadImages(rel)
      .so:
        limit.imageUpload(rateLimited):
          ctx.body.body.file("image") match
            case None => JsonBadRequest("Image content only")
            case Some(image) =>
              val meta = lila.memo.PicfitApi.form.upload.bindFromRequest().value
              (for
                image <- env.memo.picfitApi.uploadFile(image, me, none, meta)
                maxWidth = lila.ui.bits.imageDesignWidth(rel)
                url = meta match
                  case Some(info) if maxWidth.exists(dw => info.dim.width > dw) =>
                    maxWidth.map(dw => env.memo.picfitUrl.resize(image.id, Left(dw)))
                  case _ => env.memo.picfitUrl.raw(image.id).some
              yield JsonOk(Json.obj("imageUrl" -> url))).recover:
                case lila.core.lilaism.LilaInvalid(msg) => UnprocessableEntity(jsonError(msg))
  }

  def imageUrl(id: ImageId, width: Int) = Auth { _ ?=> _ ?=>
    if width < 1 then JsonBadRequest("Invalid width")
    else
      JsonOk(
        Json.obj(
          "imageUrl" -> env.memo.picfitUrl
            .resize(id, Left(width.min(lila.ui.bits.imageDesignWidth(id.value).getOrElse(1920))))
        )
      )
  }
