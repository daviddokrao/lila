package lila.web

import org.apache.pekko.stream.Materializer
import play.api.mvc.*
import play.api.http.Status.*

import lila.common.{ HTTPRequest, ClientName }
import lila.core.config.NetConfig
import lila.core.net.LichessMobileUa

final class HttpFilter(
    net: NetConfig,
    parseMobileUa: RequestHeader => Option[LichessMobileUa],
    // HungKings B4: dung mot HAM thay vi keo ca `lila.security.LilaCookie` vao day —
    // cung khuon voi `parseMobileUa` ngay tren, va giu module `web` khong phai phu
    // thuoc module `security` chi de dat mot cai cookie.
    hkReferralCookie: String => Cookie
)(using val mat: Materializer)(using Executor)
    extends Filter
    with ResponseHeaders:

  private def logger = lila.log("http")

  def apply(handle: RequestHeader => Fu[Result])(req: RequestHeader): Fu[Result] =
    if HTTPRequest.isAssets(req) then serveAssets(handle(req))
    else
      val startTime = nowMillis
      redirectWrongDomain(req)
        .map(fuccess)
        .getOrElse:
          val lilaReq = toLilaReq(req)
          handle(lilaReq).map: result =>
            monitoring(lilaReq, startTime):
              addContextualResponseHeaders(lilaReq):
                addEmbedderPolicyHeaders(lilaReq):
                  addHkReferralCookie(lilaReq):
                    result

  private def toLilaReq(req: RequestHeader) =
    val clientName =
      import ClientName.*
      if HTTPRequest.isXhr(req) then if HTTPRequest.isLichobile(req) then lichobile else xhr
      else if HTTPRequest.isLichessMobile(req) then mobile
      else if crawlerMatcher(req) then crawler
      else if req.path.startsWith("/fishnet/") then fishnet
      else browser
    req.addAttr(ClientName.reqAttr, clientName)

  private val crawlerMatcher = HTTPRequest.UaMatcher:
    // spiders/crawlers
    """Qwantbot|Googlebot|GoogleOther|AdsBot|Google-Read-Aloud|bingbot|BingPreview|facebookexternalhit|meta-externalagent|SemrushBot|AhrefsBot|PetalBot|Applebot|YandexBot|YandexAdNet|YandexImages|Twitterbot|Bluesky|Baiduspider|Amazonbot|Bytespider|yacybot|ImagesiftBot|ChatGLM-Spider|YisouSpider|Yeti/|DataForSeoBot|ChatGPT|openai.com|anthropic.com|TikTokSpider|MJ12bot|SeznamBot|Mwmbl|DotBot|IABot|rednote-websearch-bot|kagi-fetcher|kagibot|Bravebot""" +
      // apps and servers that load previews
      """|Discordbot|WhatsApp""" +
      // http libs
      """|HeadlessChrome|okhttp|axios|undici|wget|curl|python-requests|aiohttp|commons-httpclient|python-urllib|python-httpx|Nessus|imroc/req"""

  private def monitoring(req: RequestHeader, startTime: Long)(result: Result) =
    val actionName = HTTPRequest.actionName(req)
    val reqTime = nowMillis - startTime
    val statusCode = result.header.status
    val mobile = parseMobileUa(req)
    val client = ClientName(req)
    lila.mon.http.count(actionName, client.name, req.method, statusCode).increment()
    lila.mon.http.time(actionName).record(reqTime)
    if net.logRequests then logger.info(s"$statusCode $client $req $actionName ${reqTime}ms")
    mobile.foreach: m =>
      lila.mon.http.mobileCount(actionName, m.version, m.userId.isDefined, m.osName).increment()
    result

  private def serveAssets(res: Fu[Result]) =
    res.dmap:
      _.withHeaders(assetsHeaders*)

  private def redirectWrongDomain(req: RequestHeader): Option[Result] = {
    req.host != net.domain.value &&
    HTTPRequest.isRedirectable(req) &&
    !HTTPRequest.isProgrammatic(req) &&
    // asset request going through the CDN, don't redirect
    !(req.host == net.assetDomain.value && HTTPRequest.hasFileExtension(req))
  }.option(Results.MovedPermanently(s"http${if req.secure then "s" else ""}://${net.domain}${req.uri}"))

  private def addContextualResponseHeaders(req: RequestHeader)(result: Result) =
    if HTTPRequest.isApiOrApp(req)
    then result.withHeaders(headersForApiOrApp(using req)*)
    else if result.header.status == OK
    then result.withHeaders(permissionsPolicyHeader)
    else result

  /**
   * HungKings B4 — loi moi mot lien ket: `https://hungkings.com/<idVan>?moi=HK7F2QX`.
   *
   * Truoc day viec bat `?moi=` nam trong module JS `bits.hkInvite`, ma module do CHI duoc
   * nap o trang thach dau. Nguoi nhan bam link SAU khi da co nguoi khac vao van thi roi
   * vao trang round — khong co gi doc tham so, cookie khong duoc dat, cong gioi thieu mat
   * am tham. Dat o tang loc thi moi trang deu bat duoc, va bat duoc CA KHI TAT JavaScript.
   *
   * Chi dat cookie, KHONG ghi DB — dung nguyen tac cua `/r/<ma>` (Main.pointsReferral):
   * quan he gioi thieu chi ghi luc TAO TAI KHOAN. Cookie dung chung ham sinh voi `/r/`
   * nen mien/secure/sameSite khong the lech nhau.
   *
   * Chi nhan GET cua trinh duyet: response API khong phai cho nguoi bam link, va POST mang
   * tham so `moi` thi khong phai luot ghe tham.
   *
   * KHONG chuyen huong de go tham so khoi thanh dia chi (JS o trang thach dau lam viec do):
   * doi mot lien ket van thanh 302 la doi hanh vi dieu huong cua MOI loi moi, dat hon nhieu
   * so voi cai duoc — mot tham so con lai tren URL.
   */
  private val hkInviteEnabled: Boolean = sys.env.get("LILA_INVITE_LINK").contains("true")

  /** Trung rang buoc route `/r/$code<HK[A-Z2-9]{5}>` va `CODE_RE` trong bits.hkInvite.ts. */
  private val hkRefCodePattern = """HK[A-Z2-9]{5}""".r

  private def addHkReferralCookie(req: RequestHeader)(result: Result): Result =
    if !hkInviteEnabled || req.method != "GET" || HTTPRequest.isApiOrApp(req) then result
    else
      req.queryString
        .get("moi")
        .flatMap(_.headOption)
        .collect { case code @ hkRefCodePattern() => code }
        // Da co dung ma do roi thi khong gui lai header Set-Cookie moi lan tai trang.
        .filterNot(code => req.cookies.get("hk_ref").exists(_.value == code))
        .fold(result)(code => result.withCookies(hkReferralCookie(code)))

  private def addEmbedderPolicyHeaders(req: RequestHeader)(result: Result) =
    if result.header.status != NO_CONTENT
      && !crossOriginPolicy.isSet(result)
      && crossOriginPolicy.supportsCredentiallessIFrames(req)
    then result.withHeaders(crossOriginPolicy.credentialless*)
    else result
