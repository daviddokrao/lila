package lila.app
package http

import play.api.mvc.*

import lila.app.{ *, given }
import lila.memo.CacheApi.*
import lila.mon.extensions.*

final class KeyPages(val env: Env)(using Executor)
    extends lila.web.ResponseWriter
    with RequestContext
    with CtrlPage
    with lila.web.CtrlErrors
    with ControllerHelpers:

  // Ván replay dùng cho hero trang chủ v2 khi TV nguội. Phải CACHE: truy vấn này lọc
  // finished+rated+standard rồi sort theo `ca`, mà DB không có index phục vụ nó (đo
  // 02/08 trên DB seed: COLLSCAN 3000 doc). Không cache thì mỗi lượt vào trang chủ
  // trong "ngày vắng" là một lần quét bảng ván.
  private val homeV2Replay = env.memo.cacheApi.unit[Option[lila.core.game.Game]]("home.v2.replay"):
    _.refreshAfterWrite(3.minutes).buildAsyncFuture(_ => env.game.gameRepo.lastFinishedRated)

  def home(status: Results.Status)(using ctx: Context): Fu[Result] =
    homeHtml.map: html =>
      env.security.lilaCookie.ensure(ctx.req)(status(html))

  def homeHtml(using ctx: Context): Fu[lila.ui.RenderedPage] =
    env
      .preloader(
        tours = ctx.userId
          .so(env.team.cached.teamIdsList)
          .flatMap(env.tournament.featuring.homepage.get)
          .recoverDefault,
        swiss = env.swiss.feature.onHomepage.getUnit.getIfPresent,
        events = env.event.api.promoteTo(ctx.acceptLanguages).recoverDefault,
        simuls = env.simul.allCreatedFeaturable.get {}.recoverDefault,
        streamerSpots = env.streamer.homepageMaxSetting.get()
      )
      .mon(lila.mon.lobby.segment("preloader.total"))
      .flatMap: h =>
        ctx.me.filter(_.hasTitle).foreach(env.msg.systemMsg.twoFactorReminder(_))
        ctx.me.filterNot(_.hasEmail).foreach(env.msg.systemMsg.emailReminder(_))
        if env.web.config.homeV2 then
          // HungKings trang chủ v2 (HOME-REDESIGN-PLAN.md): fetch thêm ở ĐÂY thay vì
          // đụng Preload.Homepage — giữ data layer upstream nguyên vẹn.
          val channels = List(
            lila.tv.Tv.Channel.Blitz,
            lila.tv.Tv.Channel.Rapid,
            lila.tv.Tv.Channel.Chess960,
            lila.tv.Tv.Channel.Classical
          )
          for
            multiview <- channels
              .map(c => env.tv.tv.getGames(c, 1).map(gs => c -> gs.headOption))
              .parallel
            leaderboards <- env.user.cached.top10.get {}
            replay <-
              if h.featured.isDefined then fuccess(none)
              else homeV2Replay.get {}
            page <- renderPage:
              lila.mon.chronoSync(lila.mon.lobby.segment("renderSync")):
                views.lobby.homeV2(h, multiview, leaderboards, replay, env.web.config.homeSidebar)
          yield page
        else
          renderPage:
            lila.mon.chronoSync(lila.mon.lobby.segment("renderSync")):
              views.lobby.home(h)

  def notFound(msg: Option[String])(using Context): Fu[Result] =
    NotFound.page(views.base.notFound(msg))

  def notFoundEmbed(msg: Option[String])(using EmbedContext): Result =
    NotFound.snip(views.base.notFoundEmbed(msg))

  def blacklisted(using ctx: Context): Result =
    if lila.security.Mobile.Api.requested(ctx.req)
    then Unauthorized(jsonError(views.site.message.blacklistedMessage))
    else Unauthorized(views.site.message.blacklistedSnippet)
