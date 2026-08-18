package controllers
import play.api.libs.json.*
import play.api.mvc.*
// Can cho streamProxy: `.withBody(raw: String)` doi mot BodyWritable[String], ma
// lila khong mixin DefaultBodyWritables o day.
import play.api.libs.ws.DefaultBodyWritables.writeableOf_String

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

  // HLV AI giai thich MOT THE CO bat ky. FEN den qua query vi no chua dau cach va "/".
  def hlvCoachPosition = Open:
    Ok.page(views.site.ui.hlvCoachPosition(get("fen").getOrElse("")))

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

  private def pointsInternalUrl = Main.pointsInternalUrl

  /**
   * Proxy dung chung cho ca coach (/hlv-app) lan he diem (/diem-app).
   *
   * `withFollowRedirects(false)` la BAT BUOC: he diem dung mau POST -> 303 ->
   * GET de nap lai trang sau khi doi qua khong doi them lan nua. Neu WS tu di
   * theo redirect thi trinh duyet khong bao gio thay 303, va URL tren thanh dia
   * chi ket lai o /redeem — nap lai trang la doi qua lan hai.
   */
  private def streamProxy(
      baseUrl: String,
      basePath: String,
      path: String,
      method: String,
      body: Option[(String, String)],
      extraHeaders: List[(String, String)]
  )(using req: RequestHeader): Fu[Result] =
    val qs     = req.rawQueryString
    val target = s"$baseUrl/$path" + (if qs.nonEmpty then s"?$qs" else "")
    val base = env.web.ws
      .url(target)
      .withMethod(method)
      .withFollowRedirects(false)
      .addHttpHeaders(
        (("X-Base-Path" -> basePath) ::
          ("X-Forwarded-For" -> lila.common.HTTPRequest.ipAddressStr(req)) ::
          extraHeaders)*
      )
    val withBody = body.fold(base): (contentType, raw) =>
      base.addHttpHeaders("Content-Type" -> contentType).withBody(raw)
    withBody
      .stream()
      .map: res =>
        def header(name: String) =
          res.headers.get(name).orElse(res.headers.get(name.toLowerCase)).flatMap(_.headOption)
        val ct  = header("Content-Type").getOrElse("text/html; charset=utf-8")
        val out = Status(res.status).chunked(res.bodyAsSource).as(ct).noProxyBuffer
        header("Location").fold(out)(loc => out.withHeaders("Location" -> loc))

  def hlvCoachProxy(path: String) = Anon:
    streamProxy(coachInternalUrl, "/hlv-app", path, "GET", none, Nil)(using ctx.req)

  // Real Chess (bàn cờ 3D vật lý) same-origin tại /realchess — David chốt 13/08.
  // Env RỖNG = tính năng TẮT (404): bật/tắt bằng env + deploy, KHÔNG build lại image
  // (đúng khuôn mọi cờ tính năng của dự án).
  private val realchessInternalUrl =
    sys.env.getOrElse("LILA_REALCHESS_INTERNAL_URL", "")

  def realchessProxy(path: String) = Anon:
    if realchessInternalUrl.isEmpty then fuccess(NotFound("Real Chess chưa bật"))
    else streamProxy(realchessInternalUrl, "/realchess", path, "GET", none, Nil)(using ctx.req)

  /**
   * GIAI CO 2 PHAI (arena) — trang vo co sidebar tai /giai.
   *
   * KHAC ba app kia: KHONG co proxy o day. Arena song bang WebSocket, ma
   * `streamProxy` la HTTP client cua Play (khong nang cap duoc Upgrade), va Caddy
   * noi bo cua image gop bat MOI request co `Upgrade: websocket` nem sang lila-ws
   * TRUOC khi toi Play. Nen nhanh duoi `/giai-app` do CHINH Caddy noi bo phuc vu —
   * xem khoi `handle_path` cua `/giai-app` trong conf/mono.Caddyfile. O day chi con
   * trang vo nhung iframe vao duong do.
   *
   * DUNG viet glob kieu dau-sao ngay sau dau gach trong khoi chu thich nay: block
   * comment cua Scala LONG NHAU duoc, nen chuoi do mo mot khoi con va khoi ngoai
   * khong bao gio dong. Da tra gia dung o day — build 3 ngay 18/08 chet voi
   * "unclosed comment" o dong nay, keo theo mot loat Cyclic Error o file khac
   * khien nguyen nhan that bi chon duoi nhieu chuc dong nhieu.
   *
   * Co RIENG (khong dung chung voi duong proxy): tat co nay chi an LOI VAO, con
   * /giai-app van song vi Caddy khong biet gi ve env cua Play. Do la chu y —
   * tat loi vao khong duoc lam gay ket noi cua nguoi dang choi do dang.
   */
  private val arenaEnabled = sys.env.getOrElse("LILA_ARENA", "false") == "true"

  def giaiHome = Open:
    if !arenaEnabled then notFound
    else Ok.page(views.site.ui.giaiHome)

  // HungKings B2 (18/08): trang /bat-dau — 3 lựa chọn sau đăng ký. Cờ TẮT (mặc định) = 404,
  // y hệt trước khi trang này tồn tại. Xem Main.onboardingEnabled ở companion object dưới.
  def onboardingStart = Open:
    if !Main.onboardingEnabled then notFound
    else Ok.page(views.onboarding.page.start)

  // Ghi lại lựa chọn rồi chuyển tới đích tương ứng. KHÔNG ghi DB: một cookie là đủ rẻ để
  // "nhớ" mà không cần thêm cột Pref mới (xem Main.onboardingCookieName). `choice` không
  // khớp "hoc"/"choi" — kể cả "bo-qua" — đều rơi về "/#hv2-play", tức HÀNH VI CŨ trước khi
  // có trang này.
  def onboardingChoose(choice: String) = Open:
    if !Main.onboardingEnabled then notFound
    else
      val dest = choice match
        case "hoc" => routes.Learn.index.url
        case "choi" => "/#ai"
        case _ => "/#hv2-play"
      Redirect(dest)
        .withCookies(
          env.security.lilaCookie.cookie(
            Main.onboardingCookieName,
            choice,
            maxAge = (180 * 24 * 3600).some, // 180 ngày, viết bằng giây cho khỏi phụ thuộc import
            httpOnly = true.some
          )
        )
        .toFuccess

  /**
   * DANH TINH CO KY cho he diem.
   *
   * Service diem nam trong mang noi bo, nhung neu no chi doc `X-HK-User` tran
   * thi bat cu thu gi cham duoc cong 8091 deu tu xung la ai cung duoc. Ky HMAC
   * thi ke tan cong phai co secret, ma secret chi nam o day va o service diem.
   *
   * Han dung 5 phut de mot header lo lot ra ngoai (log, anh chup man hinh)
   * khong dung lai duoc mai.
   *
   * Secret RONG => khong gui header nao => service diem coi nhu khach chua dang
   * nhap va tra trang "can dang nhap". Hong an toan, khong hong im lang.
   */
  private def pointsIdentity(using ctx: Context): List[(String, String)] =
    ctx.me.so(me => Main.pointsIdentityHeaders(me.userId.value))

  // He diem: trang lila (co sidebar) nhung service points qua iframe same-origin.
  def pointsHome = Open:
    if !env.web.config.pointsEnabled then notFound
    else Ok.page(views.site.ui.pointsHome)

  def pointsShopHome = Open:
    if !env.web.config.pointsEnabled then notFound
    else Ok.page(views.site.ui.pointsShop)

  /**
   * Link moi `/r/<ma>`: dat cookie roi chuyen ve trang chu.
   *
   * CHI dat cookie, KHONG ghi gi vao DB — nguoi bam link co the khong bao gio dang
   * ky, va mot ban ghi cho moi luot bam la mot be mat rac ai cung tao duoc.
   * Quan he giới thiệu chi duoc ghi luc TAO TAI KHOAN (xem `pointsReferralCookie`).
   *
   * 90 ngay: du dai de nguoi ta suy nghi vai tuan, du ngan de khong gan nham mot
   * lan bam tu nam ngoai.
   */
  def pointsReferral(code: String) = Open:
    if !env.web.config.pointsEnabled then notFound
    else
      Redirect(routes.Lobby.home)
        .withCookies(
          env.security.lilaCookie.cookie(
            Main.referralCookieName,
            code,
            maxAge = (90 * 24 * 3600).some, // 90 ngày, viết bằng giây cho khỏi phụ thuộc import
            httpOnly = false.some
          )
        )
        .toFuccess

  def pointsProxy(path: String) = Open:
    if !env.web.config.pointsEnabled then notFound
    else streamProxy(pointsInternalUrl, "/diem-app", path, "GET", none, pointsIdentity)(using ctx.req)

  /**
   * POST di qua proxy — can cho viec doi qua. Chi chuyen tiep form-urlencoded
   * (thu duy nhat cua hang gui) roi ma hoa lai; khong bung nguyen luong byte,
   * de be mat proxy khong rong hon viec no thuc su phuc vu.
   */
  // `OpenBody: ctx ?=>` CO CHU Y: `ctx` mac dinh cua LilaController la
  // `inline def ctx(using it: Context) = it`, tuc luon tra ve `Context` chu khong
  // phai `BodyContext` — nen `ctx.body` khong ton tai. Dat ten tuong minh thi no
  // che dinh nghia kia (dung khuon Setup.scala). Than request la kieu ton tai nen
  // phai khop mau ve AnyContent (dung khuon FormCompatLayer).
  def pointsProxyPost(path: String) = OpenBody: ctx ?=>
    if !env.web.config.pointsEnabled then notFound
    else
      val form: Map[String, Seq[String]] = ctx.body.body match
        case content: AnyContent => content.asFormUrlEncoded.getOrElse(Map.empty)
        case _                   => Map.empty
      val encoded = form.toList
        .flatMap((k, vs) => vs.map(v => s"${urlEncode(k)}=${urlEncode(v)}"))
        .mkString("&")
      streamProxy(
        pointsInternalUrl,
        "/diem-app",
        path,
        "POST",
        ("application/x-www-form-urlencoded" -> encoded).some,
        pointsIdentity
      )(using ctx.req)

  private def urlEncode(s: String): String =
    java.net.URLEncoder.encode(s, "UTF-8")

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
      then StaticContent.robotsTxt(env.net)
      else "User-agent: *\nDisallow: /"

  // HungKings: sitemap.xml (P2.3). Cùng cổng opt-in với robots.txt — site nào đang
  // Disallow toàn bộ thì nộp sitemap là mâu thuẫn, nên 404 luôn cho gọn.
  val sitemap = Anon:
    if env.net.crawlable && req.domain == env.net.domain.value
    then Ok(StaticContent.sitemapXml(env.net)).as("application/xml")
    else NotFound("")

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

/**
 * HungKings: phan dung chung cho HE DIEM.
 *
 * De o companion object vi CA HAI controller can: `Main` ky danh tinh cho moi
 * request di qua proxy /diem-app, con `Auth` ky mot lan luc tao tai khoan de gan
 * quan he gioi thieu. Nhan ban doan ky HMAC sang ca hai noi la cach chac chan
 * nhat de mot ngay nao do hai noi ky khac nhau.
 */
object Main:

  /** Cookie link moi. `hk_ref` — dat boi /r/<ma>, doc luc TAO TAI KHOAN. */
  val referralCookieName = "hk_ref"

  // B2 (18/08): 3 lua chon o /bat-dau. RONG hoac bat ky gia tri nao khac "true" = TAT
  // (route tra 404) — dung sys.env truc tiep, cung khuon voi moi co feature khac cua du
  // an. Bat: LILA_ONBOARDING_CHOICE=true trong deploy/.env roi deploy, KHONG build lai
  // image.
  val onboardingEnabled: Boolean = sys.env.get("LILA_ONBOARDING_CHOICE").contains("true")

  // Cookie ghi lai lua chon o /bat-dau. Chi GHI trong vong nay — chua co noi nao doc lai
  // no (de danh cho ca nhan hoa sau nay); xem Main.onboardingChoose.
  val onboardingCookieName = "hk_onboard"

  val pointsInternalUrl =
    sys.env.getOrElse("LILA_POINTS_INTERNAL_URL", "http://hungkings-points-web:8091")

  /**
   * Khoa ky danh tinh. RONG = khong gui header nao = service diem coi nhu khach
   * chua dang nhap. Hong AN TOAN, khong hong im lang: khong bao gio co chuyen
   * thieu khoa ma van tin duoc nguoi goi la ai.
   */
  private val pointsSecret = sys.env.getOrElse("LILA_POINTS_HMAC_SECRET", "")

  private def hmacSha256Hex(secret: String, msg: String): String =
    val mac = javax.crypto.Mac.getInstance("HmacSHA256")
    mac.init(new javax.crypto.spec.SecretKeySpec(secret.getBytes("UTF-8"), "HmacSHA256"))
    mac.doFinal(msg.getBytes("UTF-8")).map("%02x".format(_)).mkString

  /** Han 5 phut: header lo lot ra ngoai (log, anh chup man hinh) khong dung lai duoc mai. */
  def pointsIdentityHeaders(userId: String): List[(String, String)] =
    if pointsSecret.isEmpty then Nil
    else
      val exp = java.time.Instant.now.getEpochSecond + 300
      List(
        "X-HK-User" -> userId,
        "X-HK-Exp"  -> exp.toString,
        "X-HK-Sig"  -> hmacSha256Hex(pointsSecret, s"$userId.$exp")
      )
