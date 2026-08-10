package lila.web

import play.api.{ Configuration, ConfigLoader }

import lila.common.config.given
import lila.core.config.*
import lila.core.security.LilaCookie

final class WebConfig(
    val apiToken: Secret,
    val influxEventEndpoint: String,
    val influxEventEnv: String,
    val prometheusKey: String,
    val pagerDuty: WebConfig.PagerDuty,
    // HungKings: trang chủ v2 sau cờ (rollback bằng env LILA_HOME_V2, không cần rebuild)
    val homeV2: Boolean,
    // HungKings: sidebar trang chủ v2 (vòng 3) — chỉ có nghĩa khi homeV2 bật
    val homeSidebar: Boolean,
    // HungKings: diễn đàn tạm tắt (env LILA_FORUM). Tắt = ẩn mọi lối vào + /forum 404.
    val forumEnabled: Boolean,
    // HungKings: broadcast tạm tắt (env LILA_BROADCAST). Tắt = ẩn mọi lối vào + /broadcast 404.
    val broadcastEnabled: Boolean,
    // HungKings: trang bảo trợ tạm tắt (env LILA_PATRON). Tắt = /patron + /features 404.
    val patronEnabled: Boolean
)

object WebConfig:

  object blindCookie:
    val name = "mBzamRgfXgRBSnXB"
    val maxAge = 365.days
    def make(lilaCookie: LilaCookie)(enable: Boolean) = lilaCookie.cookie(
      name,
      enable.so("1"),
      maxAge = maxAge.toSeconds.toInt.some,
      httpOnly = true.some
    )

  final class PagerDuty(val serviceId: String, val apiKey: Secret)

  def loadFrom(c: Configuration) =
    WebConfig(
      c.get[Secret]("api.token"),
      c.get[String]("api.influx_event.endpoint"),
      c.get[String]("api.influx_event.env"),
      c.get[String]("kamon.prometheus.lilaKey"),
      new PagerDuty(
        c.get[String]("pagerDuty.serviceId"),
        c.get[Secret]("pagerDuty.apiKey")
      ),
      c.get[Boolean]("net.home.v2"),
      c.get[Boolean]("net.home.sidebar"),
      c.get[Boolean]("net.forum.enabled"),
      c.get[Boolean]("net.broadcast.enabled"),
      c.get[Boolean]("net.patron.enabled")
    )

  def analyseEndpoints(c: Configuration) =
    lila.ui.AnalyseEndpoints(
      explorer = c.get[String]("explorer.endpoint"),
      tablebase = c.get[String]("explorer.tablebase_endpoint"),
      externalEngine = c.get[String]("externalEngine.endpoint"),
      explorerEnabled = c.get[Boolean]("explorer.enabled")
    )

  def netConfig(c: Configuration) = NetConfig(
    domain = c.get[NetDomain]("net.domain"),
    prodDomain = c.get[NetDomain]("net.prodDomain"),
    baseUrl = c.get[BaseUrl]("net.base_url"),
    assetDomain = c.get[AssetDomain]("net.asset.domain"),
    assetBaseUrl = c.get[AssetBaseUrl]("net.asset.base_url"),
    stageBanner = c.get[Boolean]("net.stage.banner"),
    siteName = c.get[String]("net.site.name"),
    socketDomains = c.get[List[String]]("net.socket.domains"),
    socketAlts = c.get[List[String]]("net.socket.alts"),
    crawlable = c.get[Boolean]("net.crawlable"),
    rateLimit = c.get[RateLimit]("net.ratelimit"),
    email = c.get[EmailAddress]("net.email"),
    logRequests = c.get[Boolean]("net.http.log"),
    localDev = c.get[Boolean]("net.local_dev")
  )

  final class LilaVersion(val date: String, val commit: String, val message: String)

  def lilaVersion(c: Configuration): Option[LilaVersion] = (
    c.getOptional[String]("app.version.date"),
    c.getOptional[String]("app.version.commit"),
    c.getOptional[String]("app.version.message")
  ).mapN(LilaVersion.apply)
