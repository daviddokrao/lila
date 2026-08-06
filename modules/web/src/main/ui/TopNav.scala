package lila.web
package ui

import scalalib.model.Days
import lila.ui.*
import ScalatagsTemplate.{ *, given }

// forumEnabled: diễn đàn tạm tắt bằng env LILA_FORUM (David chốt 04/08). Route đã bị
// chặn ở HttpRequestHandler; ở đây chỉ ẩn lối vào để không mời người dùng bấm vào 404.
// broadcastEnabled: broadcast tạm tắt bằng env LILA_BROADCAST (David chốt 06/08) — tương tự.
final class TopNav(helpers: Helpers)(forumEnabled: Boolean, broadcastEnabled: Boolean):
  import helpers.{ *, given }

  private def linkTitle(url: String, name: Frag)(using ctx: Context) =
    if ctx.blind then h3(name) else a(href := url)(name)

  def apply(seesClassMenu: Boolean, hasDgt: Boolean)(using ctx: Context) =
    st.nav(id := "topnav", cls := "hover")(
      st.section(
        linkTitle(
          "/",
          frag(
            span(cls := "play")(trans.site.play()),
            span(cls := "home")("HungKings")
          )
        ),
        div(role := "group")(
          if ctx.noBot then a(href := s"${langHref("/")}?any#hook")(trans.site.createLobbyGame())
          else a(href := "/?any#friend")(trans.site.challengeAFriend()),
          Option.when(ctx.noBot):
            frag(
              a(href := langHref(routes.Tournament.home))(trans.arena.arenaTournaments()),
              a(href := langHref(routes.Swiss.home))(trans.swiss.swissTournaments()),
              a(href := langHref(routes.Simul.home))(trans.site.simultaneousExhibitions()),
              hasDgt.option(a(href := routes.DgtCtrl.index)(trans.dgt.dgtBoard()))
            )
        )
      ),
      Option.when(ctx.noBot):
        val puzzleUrl = langHref(routes.Puzzle.home.url)
        st.section(
          linkTitle(puzzleUrl, trans.site.puzzles()),
          div(role := "group")(
            a(href := puzzleUrl)(trans.site.puzzles()),
            a(href := langHref(routes.Puzzle.themes))(trans.puzzle.puzzleThemes()),
            a(href := routes.Puzzle.dashboard(Days(30), "home", none))(trans.puzzle.puzzleDashboard()),
            a(href := langHref(routes.Puzzle.streak))("Puzzle Streak"),
            a(href := langHref(routes.Storm.home))("Puzzle Storm"),
            a(href := langHref(routes.Racer.home))("Puzzle Racer")
          )
        )
      ,
      st.section(
        linkTitle(routes.Learn.index.url, trans.site.learnMenu()),
        div(role := "group")(
          Option.when(ctx.noBot):
            frag(
              a(href := langHref(routes.Learn.index))(trans.site.chessBasics()),
              a(href := routes.Practice.index)(trans.site.practice()),
              a(href := langHref(routes.Coordinate.home))(trans.coordinates.coordinates())
            )
          ,
          a(href := langHref(routes.Study.allDefault()))(trans.site.studyMenu()),
          ctx.kid.no.option(a(href := langHref(routes.Coach.all(1)))(trans.site.coaches())),
          seesClassMenu.option(a(href := routes.Clas.index)(trans.clas.lichessClasses()))
        )
      ),
      st.section:
        // Broadcast tắt thì tiêu đề mục "Xem" không được trỏ /broadcast (đã 404) — lùi về
        // ván đang diễn ra. Link broadcast trong nhóm cũng ẩn theo cờ.
        val watchUrl = if broadcastEnabled then langHref(routes.RelayTour.index()) else routes.Tv.games.url
        frag(
          linkTitle(watchUrl, trans.site.watch()),
          div(role := "group")(
            broadcastEnabled.option(a(href := routes.RelayTour.index())(trans.broadcast.broadcasts())),
            a(href := langHref(routes.Tv.index))("HungKings TV"),
            a(href := routes.Tv.games)(trans.site.currentGames()),
            (ctx.kid.no && ctx.noBot).option(a(href := routes.Streamer.index())(trans.site.streamersMenu())),
            ctx.noBot.option(a(href := langHref(routes.Video.index))(trans.site.videoLibrary()))
          )
        )
      ,
      st.section(
        linkTitle(routes.User.list.url, trans.site.community()),
        div(role := "group")(
          a(href := routes.User.list)(trans.site.players()),
          ctx.me.map(me => a(href := routes.Relation.following(me.username))(trans.site.friends())),
          a(href := routes.Team.home())(trans.team.teams()),
          (forumEnabled && ctx.kid.no).option(a(href := routes.ForumCateg.index)(trans.site.forum())),
          ctx.kid.no.option(a(href := langHref(routes.Ublog.communityAll()))(trans.site.blog()))
        )
      ),
      st.section(
        linkTitle(routes.UserAnalysis.index.url, trans.site.tools()),
        div(role := "group")(
          a(href := routes.UserAnalysis.index)(trans.site.analysis()),
          a(href := routes.Opening.index())(trans.site.openings()),
          a(href := routes.Editor.index)(trans.site.boardEditor()),
          a(href := routes.Importer.importGame)(trans.site.importGame()),
          a(href := routes.Search.index())(trans.search.advancedSearch())
        )
      ),
      // Đăng nhập/Đăng ký cho khách, CHỈ hiện ở menu ngăn kéo trên điện thoại
      // (`mobile-only` bị ẩn khi menu ngang hiện — xem _topnav-visible.scss).
      //
      // Trên điện thoại, hai nút ấy đã được gỡ khỏi thanh đầu trang: chữ tiếng Việt
      // "Đăng nhập/Đăng ký" cần ~178px, đo được là không thể đứng cạnh chữ thương
      // hiệu mà không xuống dòng và lòi ra ngoài thanh. Nhưng menu trái vốn KHÔNG có
      // mục nào dẫn tới đăng nhập, nên gỡ mà không thêm vào đây là chặn hẳn đường
      // vào tài khoản trên điện thoại.
      ctx.me.isEmpty.option(
        st.section(cls := "mobile-only")(
          linkTitle(routes.Auth.login.url, trans.site.signIn()),
          div(role := "group")(
            a(href := routes.Auth.login)(trans.site.signIn()),
            a(href := routes.Auth.signup)(trans.site.signUp())
          )
        )
      )
    )
