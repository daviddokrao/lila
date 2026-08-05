package lila.tournament
package ui

import play.api.libs.json.*
import scalalib.paginator.Paginator

import lila.rating.PerfType
import lila.tournament.Schedule.Freq
import lila.ui.*

import ScalatagsTemplate.{ *, given }

final class TournamentList(helpers: Helpers, ui: TournamentUi)(
    communityMenu: Context ?=> Frag,
    shieldMenu: Context ?=> Frag
):
  import helpers.{ *, given }

  // HungKings: khối "nhà vô địch" đọc Winner.tourName — trường THÔ đóng băng tiếng Anh lúc
  // tạo giải, KHÔNG đi qua Tournament.name() nên G2 không chạm tới. Với người đọc tiếng Việt,
  // đổi các từ FREQ tiếng Anh sang tiếng Việt (Speed như HyperBullet giữ nguyên, khớp danh
  // sách giải; Marathon vốn giữ nguyên cả hai ngôn ngữ). Rẽ theo ctx.lang — không thêm khoá
  // dịch, không đụng model Winner/cache. Xem PROGRESS Mốc G nợ nhỏ #2.
  private val viFreqWords = List(
    "Hourly" -> "Hàng giờ",
    "Daily" -> "Hàng ngày",
    "Eastern" -> "phương Đông",
    "Weekend" -> "Cuối tuần",
    "Weekly" -> "Hàng tuần",
    "Monthly" -> "Hàng tháng",
    "Shield" -> "Khiên",
    "Yearly" -> "Hàng năm",
    "Unique" -> "Đặc biệt",
    "Elite" -> "Ưu tú"
  )
  private def viTourName(name: String)(using t: Translate): String =
    if t.lang.language == "vi" then
      viFreqWords.foldLeft(name) { case (n, (en, vi)) => n.replace(en, vi) }
    else name

  // Song ngữ tại chỗ (đường thứ tư) cho nhãn hardcode tiếng Anh ở trang bảng vàng.
  // KHÔNG đặt tên `tr` — trùng thẻ HTML <tr> (table row) của scalatags.
  private def viEn(vi: String, en: String)(using t: Translate): String =
    if t.lang.language == "vi" then vi else en

  def home(
      scheduled: List[Tournament],
      finished: List[Tournament],
      winners: AllWinners,
      json: JsObject
  )(using ctx: Context) =
    Page(trans.site.tournaments.txt())
      .css("tournament.home")
      .js(infiniteScrollEsmInit)
      .js(PageModule("tournament.schedule", Json.obj("data" -> json)))
      .hrefLangs(LangPath(routes.Tournament.home))
      .flag(_.fullScreen)
      .graph(
        url = routeUrl(routes.Tournament.home),
        title = trans.site.tournamentHomeTitle.txt(),
        description = trans.site.tournamentHomeDescription.txt()
      ):
        main(cls := "tour-home")(
          st.aside(cls := "tour-home__side")(
            h2(
              a(href := routes.Tournament.leaderboard)(trans.site.leaderboard())
            ),
            ul(cls := "leaderboard")(
              winners.top.map: w =>
                val nm = viTourName(w.tourName)
                li(
                  userIdLink(w.userId.some),
                  a(title := nm, href := routes.Tournament.show(w.tourId))(
                    ui.scheduledTournamentNameShortHtml(nm)
                  )
                )
            ),
            p(cls := "tour__links")(
              ctx.me.map: me =>
                frag(
                  a(href := routes.UserTournament.path(me.username, "created"))(trans.arena.myTournaments()),
                  br
                ),
              a(href := routes.Tournament.calendar)(trans.site.tournamentCalendar()),
              br,
              a(href := routes.Tournament.history(Freq.Unique.name))(trans.arena.history()),
              br,
              a(href := routes.Tournament.help)(trans.site.tournamentFAQ()),
              br,
              a(href := routes.Cms.lonePage(lila.core.id.CmsPageKey("leagues-and-battles")))(
                "Leagues & Streamer Battles"
              )
            ),
            h2(trans.site.lichessTournaments()),
            div(cls := "scheduled")(
              scheduled.map: tour =>
                tour.schedule
                  .exists(_.freq != Freq.Hourly)
                  .option:
                    a(href := routes.Tournament.show(tour.id), dataIcon := ui.tournamentIcon(tour))(
                      strong(tour.name(full = false)),
                      momentFromNow(tour.startsAt)
                    )
            )
          ),
          st.section(cls := "tour-home__schedule box")(
            boxTop(
              h1(trans.site.tournaments()),
              ctx.isAuth.option(
                div(cls := "box__top__actions")(
                  a(
                    href := routes.Tournament.form,
                    cls := "button button-green text",
                    dataIcon := Icon.PlusButton
                  )(trans.site.createANewTournament())
                )
              )
            ),
            div(cls := "tour-chart")
          ),
          div(cls := "arena-list box")(
            table(cls := "slist slist-pad")(
              thead(
                tr(
                  th(colspan := 2, cls := "large")(trans.site.finished()),
                  th(cls := "date"),
                  th(cls := "players")
                )
              ),
              ui.finishedList(finished)
            )
          )
        )

  def history(freq: Freq, pager: Paginator[Tournament])(using Context) =
    Page("Tournament history")
      .js(infiniteScrollEsmInit)
      .css("tournament.history"):
        main(cls := "page-menu arena-history")(
          lila.ui.bits.pageMenuSubnav(
            allFreqs.map: f =>
              a(cls := freq.name.active(f.name), href := routes.Tournament.history(f.name))(
                nameOf(f)
              )
          ),
          div(cls := "page-menu__content box")(
            boxTop(h1(nameOf(freq), " tournaments")),
            div(cls := "arena-list")(
              table(cls := "slist slist-pad")(
                tbody(cls := "infinite-scroll")(
                  pager.currentPageResults.map(ui.finishedList.apply),
                  pagerNextTable(pager, p => routes.Tournament.history(freq.name, p).url)
                )
              )
            )
          )
        )

  def calendar(json: play.api.libs.json.JsObject)(using Context) =
    Page("Tournament calendar")
      .js(PageModule("tournament.calendar", Json.obj("data" -> json)))
      .css("tournament.calendar"):
        main(cls := "box")(
          h1(cls := "box__top")(trans.site.tournamentCalendar()),
          div(id := "tournament-calendar")
        )

  def homepageSpotlight(tour: Tournament)(using Context) =
    val schedClass = tour.scheduleData.so: (freq, speed) =>
      val invert = (freq.isWeeklyOrBetter && tour.isNowOrSoon).so(" invert")
      val distant = tour.isDistant.so(" distant little")
      s"$freq $speed ${tour.variant.key}$invert$distant"
    val tourClass = s"tour-spotlight id_${tour.id} $schedClass"
    tour.spotlight
      .map { spot =>
        a(href := routes.Tournament.show(tour.id), cls := tourClass)(
          frag(
            spot.iconImg
              .map { i =>
                img(cls := "img", src := assetUrl(s"images/$i"))
              }
              .getOrElse {
                spot.iconFont.fold[Frag](iconTag(Icon.Trophy)(cls := "img")) {
                  case Icon.Globe => img(cls := "img icon", src := assetUrl(s"images/globe.svg"))
                  case i => iconTag(i)(cls := "img")
                }
              },
            span(cls := "content")(
              span(cls := "name")(tour.name()),
              if tour.isDistant then span(cls := "more")(momentFromNow(tour.startsAt))
              else
                frag(
                  span(cls := "headline")(spot.headline),
                  span(cls := "more")(
                    trans.site.nbPlayers.plural(tour.nbPlayers, tour.nbPlayers.localize),
                    " • ",
                    if tour.isStarted then timeRemaining(tour.finishesAt)
                    else momentFromNow(tour.startsAt)
                  )
                )
            )
          )
        )
      }
      .getOrElse(
        a(href := routes.Tournament.show(tour.id), cls := s"little $tourClass")(
          iconTag(tour.perfType.icon)(cls := "img"),
          span(cls := "content")(
            span(cls := "name")(
              tour.name(),
              tour.isTeamRelated.option(
                iconTag(Icon.Group)(
                  cls := "tour-team-icon",
                  title := tour.conditions.teamMember.fold(trans.team.teamBattle.txt())(_.teamName)
                )
              )
            ),
            span(cls := "more")(
              trans.site.nbPlayers.plural(tour.nbPlayers, tour.nbPlayers.localize),
              " • ",
              if tour.isStarted then trans.site.eventInProgress() else momentFromNow(tour.startsAt)
            )
          )
        )
      )

  private def nameOf(f: Freq) = if f == Freq.Weekend then "Elite" else f.name

  private val allFreqs = List(
    Freq.Unique,
    Freq.Marathon,
    Freq.Shield,
    Freq.Yearly,
    Freq.Monthly,
    Freq.Weekend,
    Freq.Weekly,
    Freq.Daily,
    Freq.Eastern,
    Freq.Hourly
  )

  object leaderboard:

    private def freqWinner(w: Winner, freq: String)(using Translate) =
      li(
        userIdLink(w.userId.some),
        a(title := viTourName(w.tourName), href := routes.Tournament.show(w.tourId))(freq)
      )

    private val section = st.section(cls := "tournament-leaderboards__item")

    private def freqWinners(fws: FreqWinners, perfType: PerfType, name: String)(using Translate) =
      section(
        h2(cls := "text", dataIcon := perfType.icon)(name),
        ul(
          fws.yearly.map: w =>
            freqWinner(w, viEn("Hàng năm", "Yearly")),
          fws.monthly.map: w =>
            freqWinner(w, viEn("Hàng tháng", "Monthly")),
          fws.weekly.map: w =>
            freqWinner(w, viEn("Hàng tuần", "Weekly")),
          fws.daily.map: w =>
            freqWinner(w, viEn("Hàng ngày", "Daily"))
        )
      )

    def apply(winners: AllWinners)(using Context) =
      def eliteWinners = section(
        h2(cls := "text", dataIcon := Icon.CrownElite)(viEn("Đấu trường Ưu tú", "Elite Arena")),
        ul(
          winners.elite.map: w =>
            li(
              userIdLink(w.userId.some),
              a(title := viTourName(w.tourName), href := routes.Tournament.show(w.tourId))(showDate(w.date))
            )
        )
      )
      def marathonWinners = section(
        h2(cls := "text", dataIcon := Icon.Globe)("Marathon"),
        ul(
          winners.marathon.map { w =>
            li(
              userIdLink(w.userId.some),
              a(title := viTourName(w.tourName), href := routes.Tournament.show(w.tourId))(
                viTourName(w.tourName).replace(" Marathon", "")
              )
            )
          }
        )
      )
      Page(viEn("Bảng vàng giải đấu", "Tournament leaderboard"))
        .css("tournament.leaderboard")
        .flag(_.fullScreen):
          main(cls := "page-menu")(
            communityMenu,
            div(cls := "page-menu__content box box-pad")(
              h1(cls := "box__top")(trans.arena.tournamentWinners()),
              div(cls := "tournament-leaderboards")(
                eliteWinners,
                freqWinners(winners.hyperbullet, PerfType.Bullet, "HyperBullet"),
                freqWinners(winners.bullet, PerfType.Bullet, "Bullet"),
                freqWinners(winners.superblitz, PerfType.Blitz, "SuperBlitz"),
                freqWinners(winners.blitz, PerfType.Blitz, "Blitz"),
                freqWinners(winners.rapid, PerfType.Rapid, "Rapid"),
                marathonWinners,
                lila.tournament.WinnersApi.variants.map: v =>
                  PerfKey.byVariant(v).map { pk =>
                    winners.variants.get(chess.variant.Variant.LilaKey(pk.value)).map {
                      freqWinners(_, pk, v.name)
                    }
                  }
              )
            )
          )

  object shields:

    private val section = st.section(cls := "tournament-shields__item")

    def apply(history: TournamentShield.History)(using Context) =
      Page(viEn("Khiên giải đấu", "Tournament shields"))
        .css("tournament.leaderboard")
        .flag(_.fullScreen):
          main(cls := "page-menu")(
            shieldMenu,
            div(cls := "page-menu__content box box-pad")(
              h1(cls := "box__top")(trans.arena.tournamentShields()),
              div(cls := "tournament-shields")(
                history.sorted.map { (categ, awards) =>
                  section(
                    h2(
                      a(href := routes.Tournament.categShields(categ.key))(
                        span(cls := "shield-trophy")(categ.icon),
                        categ.name
                      )
                    ),
                    ol(awards.map { aw =>
                      li(
                        userIdLink(aw.owner.some),
                        a(href := routes.Tournament.show(aw.tourId))(showDate(aw.date))
                      )
                    })
                  )
                }
              )
            )
          )

    def byCateg(categ: TournamentShield.Category, awards: List[TournamentShield.Award])(using Context) =
      Page("Tournament shields")
        .css("tournament.leaderboard", "slist"):
          main(cls := "page-menu page-small tournament-categ-shields")(
            shieldMenu,
            div(cls := "page-menu__content box")(
              boxTop(
                h1(
                  a(href := routes.Tournament.shields, dataIcon := Icon.LessThan, cls := "text"),
                  frag(categ.name, " • ", trans.arena.tournamentShields())
                )
              ),
              ol(awards.map { aw =>
                li(
                  span(cls := "shield-trophy")(categ.icon),
                  userIdLink(aw.owner.some),
                  a(href := routes.Tournament.show(aw.tourId))(showDate(aw.date))
                )
              })
            )
          )
