package views.lobby

import play.api.libs.json.Json

import lila.app.UiEnv.{ *, given }
import lila.app.mashup.Preload.Homepage
import lila.core.perf.UserWithPerfs
import lila.core.user.LightPerf

/** Trang chủ v2 của HungKings (spec: mockups/home-v4.html, kế hoạch: HOME-REDESIGN-PLAN.md).
  *
  * Bật/tắt bằng env LILA_HOME_V2 (rẽ nhánh duy nhất ở KeyPages). File này SONG SONG với
  * home.scala — không đụng bản cũ để rollback sạch.
  *
  * Hợp đồng DOM với lobby TS app (thiếu là crash client, xem báo cáo A1):
  *   - main.lobby (lobby.inline.ts đọc ---cols trên đây)
  *   - .lobby__app + .lobby__table (mount point, snabbdom patch đè lên)
  *   - .lobby__tv chứa .mini-game (socket event "featured" thay HTML bên trong)
  *   - PageModule("lobby", ...) giữ NGUYÊN shape JSON như home.scala
  * CỐ Ý KHÔNG render: .lobby__support / .lobby__blog / .lobby__timeline — cặp
  * carousel+support có bẫy ẩn-vĩnh-viễn (carousel.ts non-null assertion).
  */
object homeV2:

  private val coachUrl = "https://hungkings-coach.vssa.com"

  def apply(
      homepage: Homepage,
      multiview: List[(lila.tv.Tv.Channel, Option[lila.core.game.Game])],
      leaderboards: lila.rating.UserPerfs.Leaderboards,
      replay: Option[lila.core.game.Game]
  )(using ctx: Context) =
    import homepage.*
    // Phải đặt TRƯỚC các val bên dưới: Spotlight.select và các helper user cần given này.
    // (home.scala đặt nó trong khối hrefLangs vì mọi thứ đều dựng tại chỗ trong đó.)
    given Option[UserWithPerfs] = homepage.me
    // Chuỗi mới: song ngữ tại chỗ, KHÔNG thêm key registry dịch (19' compile LangList).
    def t(viText: String, enText: String): String = if ctx.lang.language == "vi" then viText else enText
    // Nhãn kênh khớp bản dịch vi-VN của lila (bullet="Cờ đạn"... đã tra file dịch)
    def channelName(c: lila.tv.Tv.Channel): String = c.key match
      case "blitz"     => t("Cờ chớp", "Blitz")
      case "rapid"     => t("Cờ nhanh", "Rapid")
      case "chess960"  => t("Cờ 960", "Chess960")
      case "classical" => t("Cờ chậm", "Classical")
      case _           => c.name

    def zlabel(title: String, more: Option[(String, String)] = None) =
      div(cls := "hv2-zlabel")(
        span(cls := "hv2-tick"),
        span(cls := "hv2-tt")(title),
        span(cls := "hv2-rule"),
        more.map((label, url) => a(cls := "hv2-more", href := url)(label))
      )

    def miniOf(g: lila.core.game.Game, tv: Boolean) =
      views.game.mini(Pov.naturalOrientation(g), tv = tv)

    val heroFrag: Frag =
      featured match
        case Some(g) =>
          div(cls := "hv2-hero")(
            div(cls := "lobby__tv hv2-hero__board")(miniOf(g, tv = true)),
            div(cls := "hv2-hero__cast")(
              div(cls := "hv2-onair hv2-onair--live")(t("Đang phát trực tiếp", "Live now")),
              h2(cls := "hv2-matchup")(t("Bàn cờ đang được theo dõi", "The board being watched")),
              p(cls := "hv2-sub")(
                t(
                  "Ván hay nhất đang diễn ra — bấm để vào phòng xem, bình luận cùng mọi người.",
                  "The best game happening right now — join the watch room."
                )
              ),
              div(cls := "hv2-cta")(
                a(cls := "hv2-btn hv2-btn--gold", href := routes.Tv.index)(t("Vào phòng xem", "Watch now")),
                span(cls := "hv2-hint")(
                  t("Ván kết thúc sẽ có lời giải AI tiếng Việt", "Finished games get AI commentary")
                )
              )
            )
          )
        case None =>
          replay match
            case Some(g) =>
              div(cls := "hv2-hero")(
                div(cls := "hv2-hero__board")(miniOf(g, tv = false)),
                div(cls := "hv2-hero__cast")(
                  div(cls := "hv2-onair hv2-onair--replay")(
                    t("Ván hay gần đây · AI bình giải", "Recent game · AI commentary")
                  ),
                  h2(cls := "hv2-matchup")(t("Xem lại và học từ ván này", "Replay and learn from this game")),
                  p(cls := "hv2-sub")(
                    t(
                      "Chưa có ván trực tiếp lúc này. Trong lúc chờ, để AI giải thích ván gần nhất bằng tiếng Việt.",
                      "No live game right now. Meanwhile, let the AI explain the latest game."
                    )
                  ),
                  div(cls := "hv2-cta")(
                    a(cls := "hv2-btn hv2-btn--gold", href := s"$coachUrl/${g.id}")(
                      t("Nghe AI giải cả ván", "Hear the full AI commentary")
                    ),
                    a(cls := "hv2-btn hv2-btn--line", href := s"#hv2-play")(t("Chơi với máy", "Play the computer"))
                  )
                )
              )
            case None =>
              div(cls := "hv2-hero hv2-hero--empty")(
                h2(cls := "hv2-matchup")(t("Sân khấu đang chờ ván đầu tiên", "The stage awaits its first game")),
                p(cls := "hv2-sub")(
                  t(
                    "Chưa có ván nào đang diễn ra. Hãy mở màn — ván tiếp theo của bạn có thể là bàn cờ cả cộng đồng dõi theo.",
                    "No games in progress. Open the stage — your next game could be the one everyone watches."
                  )
                ),
                div(cls := "hv2-cta")(
                  a(cls := "hv2-btn hv2-btn--gold", href := "#hv2-play")(t("Chơi với máy", "Play the computer")),
                  a(cls := "hv2-btn hv2-btn--line", href := "#hv2-play")(t("Ghép trận nhanh", "Quick pairing"))
                )
              )

    val multiviewFrag: Frag =
      div(cls := "hv2-multi")(
        multiview.map: (channel, gameOpt) =>
          gameOpt match
            case Some(g) =>
              div(cls := "hv2-mv")(
                div(cls := "hv2-mv__chn")(channelName(channel)),
                miniOf(g, tv = false)
              )
            case None =>
              div(cls := "hv2-mv hv2-mv--empty")(
                div(cls := "hv2-mv__chn")(channelName(channel)),
                div(cls := "hv2-mv__note")(
                  span(t("Chưa có ván — mở màn kênh này", "No game yet — open this channel")),
                  a(cls := "hv2-btn hv2-btn--line hv2-btn--sm", href := "#hv2-play")(
                    t("Chơi ", "Play ") + channelName(channel)
                  )
                )
              )
      )

    val appSlot: Frag =
      currentGame
        .map(bits.currentGameInfo)
        .orElse:
          hasUnreadLichessMessage.option(bits.showUnreadLichessMessage)
        .orElse:
          playban.map(bits.playbanInfo)
        .getOrElse:
          if ctx.blind then blindLobby(blindGames) else bits.lobbyApp

    val toursFrag: Frag =
      val selected = lila.tournament.Spotlight.select(tours, 4)
      selected.nonEmpty.option(
        div(cls := "hv2-tours")(
          selected.map(views.tournament.list.homepageSpotlight(_))
        )
      )

    val leadersFrag: Frag =
      def row(l: LightPerf) =
        a(cls := "hv2-lb", href := routes.User.show(l.user.name))(
          span(cls := "hv2-lb__nm")(titleTag(l.user), l.user.name),
          span(cls := "hv2-lb__el")(l.rating)
        )
      frag(
        h3(cls := "hv2-ct")(t("Bảng xếp hạng", "Leaderboard")),
        div(cls := "hv2-lbgroup")(
          div(cls := "hv2-lbcol")(
            h4(t("Cờ chớp", "Blitz")),
            leaderboards.blitz.take(5).map(row)
          ),
          div(cls := "hv2-lbcol")(
            h4(t("Cờ nhanh", "Rapid")),
            leaderboards.rapid.take(5).map(row)
          )
        )
      )

    val streamsFrag: Frag =
      frag(
        h3(cls := "hv2-ct")(t("Đang phát sóng", "Streaming now")),
        if streams.live.streams.isEmpty then
          div(cls := "hv2-empty")(
            span(t("Chưa có ai phát sóng — bạn muốn là người đầu tiên?", "Nobody is streaming — want to be the first?")),
            a(cls := "hv2-btn hv2-btn--line hv2-btn--sm", href := routes.Streamer.index())(
              t("Tìm hiểu về phát sóng", "About streaming")
            )
          )
        else views.streamer.bits.liveStreams(streams)
      )
    // Chế độ trẻ em: upstream giấu hẳn khối streamer ở home.scala — giữ đúng luật đó.
    val communityStreams: Frag = ctx.kid.no.option(streamsFrag)

    val blogFrag: Frag =
      ublogPosts.nonEmpty.option(
        frag(
          h3(cls := "hv2-ct")(t("Blog cộng đồng", "Community blog")),
          div(cls := "hv2-posts")(
            ublogPosts.take(4).map: post =>
              a(cls := "hv2-post", href := views.ublog.ui.urlOfPost(post))(post.title)
          )
        )
      )

    val starterFrag: Frag =
      div(cls := "hv2-starter")(
        div(
          h2(cls := "hv2-zt")(t("Mới học cờ? Ba bước là vào trận.", "New to chess? Three steps to your first game.")),
          div(cls := "hv2-steps")(
            a(cls := "hv2-step", href := routes.Learn.index)(
              span(cls := "hv2-step__t")(t("Học luật trong 10 phút", "Learn the rules in 10 minutes")),
              span(cls := "hv2-step__d")(t("Bài học tương tác từ số 0", "Interactive lessons from zero"))
            ),
            a(cls := "hv2-step", href := routes.Puzzle.home)(
              span(cls := "hv2-step__t")(t("Giải câu đố đầu tiên", "Solve your first puzzle")),
              span(cls := "hv2-step__d")(t("Nhìn ra đòn chiến thuật", "Spot the tactic"))
            ),
            a(cls := "hv2-step", href := "#hv2-play")(
              span(cls := "hv2-step__t")(t("Đánh ván đầu với máy", "Play your first game vs computer")),
              span(cls := "hv2-step__d")(t("Cấp độ 1, không áp lực", "Level 1, no pressure"))
            )
          )
        ),
        a(cls := "hv2-btn hv2-btn--gold", href := routes.Learn.index)(t("Bắt đầu miễn phí", "Start for free"))
      )

    val coachFrag: Frag =
      div(cls := "hv2-coach")(
        div(cls := "hv2-coach__who")(t("Huấn luyện viên AI · tiếng Việt", "AI coach"))
        ,
        blockquote(
          t(
            "“Nước 14…Mã e5 trông chủ động, nhưng nó rời bỏ ô d7 đúng lúc cột d sắp mở — ba nước sau, chính ô ấy là nơi ván cờ sụp đổ.”",
            "“14...Ne5 looks active, but it abandons d7 just as the d-file opens — three moves later, that square is where the game collapses.”"
          )
        ),
        p(cls := "hv2-coach__src")(t("— trích một lời giải thật của AI", "— from a real AI annotation")),
        a(cls := "hv2-btn hv2-btn--line", href := coachUrl)(
          t("Xem AI phân tích ván của bạn", "Have the AI analyse your game")
        )
      )

    Page("")
      .copy(fullTitle = s"$siteName • ${trans.site.freeOnlineChess.txt()}".some)
      .i18n(_.variant)
      .js(
        PageModule(
          "lobby",
          Json
            .obj(
              "data" -> data,
              "showRatings" -> ctx.pref.showRatings
            )
            .add("hasUnreadLichessMessage", hasUnreadLichessMessage)
            .add("bots", Granter.opt(_.Beta))
            .add("playban", playban.map(lila.playban.TempBan.lobbyJson))
        )
      )
      .css("home-v2")
      .graph(
        OpenGraph(
          image = staticAssetUrl("logo/lichess-tile-wide.png").some,
          title = "The best free, adless Chess server",
          url = netBaseUrl.into(Url),
          description = trans.site.siteDescription.txt()
        )
      )
      .hrefLangs(lila.ui.LangPath("/")):
        main(
          cls := List(
            "lobby"     -> true,
            "home-v2"   -> true,
            "lobby-nope" -> (playban.isDefined || currentGame.isDefined || homepage.hasUnreadLichessMessage)
          )
        )(
          // ---------- Zone 1: TRỰC TIẾP ----------
          st.section(cls := "hv2-zone hv2-zone--live")(
            zlabel(t("Trực tiếp", "Live"), Some((t("Tất cả kênh →", "All channels →"), routes.Tv.index.url))),
            heroFrag,
            multiviewFrag
          ),
          // ---------- Zone 2: THI ĐẤU (lobby app + bảng + giải) ----------
          st.section(cls := "hv2-zone hv2-zone--play", id := "hv2-play")(
            zlabel(t("Thi đấu", "Play")),
            div(cls := "hv2-play")(
              appSlot,
              div(cls := "lobby__table")(
                div(cls := "lobby__start")(
                  button(cls := "button button-metal lobby__start__button lobby__start__button--hook")(
                    trans.site.createLobbyGame()
                  ),
                  button(cls := "button button-metal lobby__start__button lobby__start__button--friend")(
                    trans.site.challengeAFriend()
                  ),
                  button(cls := "button button-metal lobby__start__button lobby__start__button--ai")(
                    trans.site.playAgainstComputer()
                  )
                )
              )
            ),
            toursFrag
          ),
          // ---------- Zone 3: HLV AI + RÈN LUYỆN + BẮT ĐẦU ----------
          st.section(cls := "hv2-zone hv2-zone--train")(
            zlabel(t("Rèn luyện", "Train")),
            div(cls := "hv2-train")(
              coachFrag,
              div(cls := "lobby__puzzle hv2-puzzle")(
                puzzle.map(p => views.puzzle.bits.dailyLink(p)())
              )
            ),
            starterFrag
          ),
          // ---------- Zone 4: CỘNG ĐỒNG ----------
          st.section(cls := "hv2-zone hv2-zone--comm")(
            zlabel(t("Cộng đồng", "Community")),
            div(cls := "hv2-comm")(
              div(leadersFrag),
              div(communityStreams),
              div(blogFrag)
            )
          ),
          // ---------- chân trang chủ: liên kết về site (giữ nghĩa vụ AGPL /source) ----------
          div(cls := "lobby__about")(
            a(href := "/about")(trans.site.aboutX("HungKings")),
            a(href := "/faq")(trans.faq.faqAbbreviation()),
            a(href := "/contact")(trans.contact.contact()),
            a(href := routes.Cms.tos)(trans.site.termsOfService()),
            a(href := "/privacy")(trans.site.privacy()),
            a(href := "/source")(trans.site.sourceCode())
          )
        )
