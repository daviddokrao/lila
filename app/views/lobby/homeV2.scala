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

  // HungKings B5 (ui-redesign/reports/02-benchmark-ia.md mục 1.4 + khuyến nghị B5): khối
  // DEMO tương tác của HLV AI trên trang chủ — khách đi một nước, AI nhận xét tại chỗ bằng
  // tiếng Việt. Trước bản này chỗ đó chỉ có một trích dẫn TĨNH, tức lời quảng cáo chứ không
  // phải bằng chứng, nên khách không có cách nào hiểu điều khác biệt thật của site trong
  // 10 giây.
  //
  // Cùng khuôn với lobbyEscapeHatchEnabled / roundReviewCtaEnabled ở page.scala:
  // sys.env.get(...).contains("true") nên biến RỖNG hoặc bất kỳ giá trị nào khác "true"
  // đều là TẮT (tránh bẫy HOCON "biến rỗng vẫn tính đã đặt"). Tắt là mặc định, và khi tắt
  // thì trang chủ không phát thêm một byte HTML/CSS/JS nào của khối này.
  // Bật: LILA_HOME_COACH_DEMO=true trong deploy/.env rồi deploy — KHÔNG build lại image.
  private val coachDemoEnabled: Boolean =
    sys.env.get("LILA_HOME_COACH_DEMO").contains("true")

  def apply(
      homepage: Homepage,
      multiview: List[(lila.tv.Tv.Channel, Option[lila.core.game.Game])],
      leaderboards: lila.rating.UserPerfs.Leaderboards,
      replay: Option[lila.core.game.Game]
  )(using ctx: Context) =
    import homepage.*
    // Sidebar KHÔNG còn dựng ở đây (04/08): nó là chrome toàn site, page.scala dựng cho
    // mọi trang từ lila.web.ui.layout.siteSidebar. Trang chủ chỉ còn góp dải thống kê
    // xuống cụm đáy qua Page.sidebarFoot, vì chỉ nó mới có dữ liệu sảnh.
    // Phải đặt TRƯỚC các val bên dưới: Spotlight.select và các helper user cần given này.
    // (home.scala đặt nó trong khối hrefLangs vì mọi thứ đều dựng tại chỗ trong đó.)
    given Option[UserWithPerfs] = homepage.me
    // Chuỗi mới: song ngữ tại chỗ, KHÔNG thêm key registry dịch (19' compile LangList).
    // K7 (20/08): đi qua `HkI18n` — xem ghi chú đầy đủ ở `modules/web/src/main/HkI18n.scala`.
    // 61 call site trong file này giữ nguyên; chưa có file JSON nào thì output không đổi 1 byte.
    def t(viText: String, enText: String): String = lila.web.HkI18n(ctx.lang.language, viText, enText)
    // Nhãn kênh khớp bản dịch vi-VN của lila (bullet="Cờ đạn"... đã tra file dịch)
    def channelName(c: lila.tv.Tv.Channel): String = c.key match
      case "blitz"     => t("Cờ chớp", "Blitz")
      case "rapid"     => t("Cờ nhanh", "Rapid")
      case "chess960"  => t("Cờ 960", "Chess960")
      case "classical" => t("Cờ chậm", "Classical")
      case _           => c.name

    // Tên kênh đứng GIỮA câu phải viết thường, không thì ra "Chơi Cờ chớp". Tiếng Anh giữ
    // nguyên vì "Chess960" là danh từ riêng.
    def channelNameMid(c: lila.tv.Tv.Channel): String =
      val n = channelName(c)
      if ctx.lang.language == "vi" && n.nonEmpty then s"${n.head.toLower}${n.tail}" else n

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
                // `routes.Tv.games` (/games) chứ KHÔNG phải `routes.Tv.index` (/tv): trên bản
                // này `/tv` trả 404 VĨNH VIỄN có chủ ý cho tới khi có ván tiêu chuẩn tính hệ số
                // của người thật (xem HANDOFF). Sidebar đã tránh `/tv` từ lâu, nhưng trang chủ
                // thì chưa — nút vàng nổi nhất của nhánh này vẫn trỏ thẳng vào trang lỗi.
                // Đo trên live 18/08: `/tv` = 404, `/games` = 200.
                a(cls := "hv2-btn hv2-btn--gold", href := routes.Tv.games)(t("Vào phòng xem", "Watch now")),
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
                    // A2 (báo cáo 02, mục 2.1): nút vàng đầu tiên phải là hành động CHƠI của
                    // chính người dùng, không phải "nghe AI giải ván người lạ". Giữ nguyên
                    // href="#ai" (2 cú bấm khách→ván đầu, benchmark ngang chess.com/lichess).
                    a(cls := "hv2-btn hv2-btn--gold", href := "#ai")(t("Chơi với máy", "Play the computer")),
                    a(cls := "hv2-btn hv2-btn--line", href := s"/hlv/${g.id}")(
                      t("Nghe AI giải cả ván", "Hear the full AI commentary")
                    )
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
                  a(cls := "hv2-btn hv2-btn--gold", href := "#ai")(t("Chơi với máy", "Play the computer")),
                  a(cls := "hv2-btn hv2-btn--line", href := "#hv2-play")(t("Ghép trận nhanh", "Quick pairing"))
                )
              )

    val multiviewFrag: Frag =
      div(cls := "hv2-multi")(
        // B3 (David chốt 03/08): CẢ 4 kênh rỗng thì gộp một dải mời gọi thay vì 4 hộp
        // "chưa có ván" lặp nhau; có ván trở lại là tự về lưới 4 ô (nhánh dưới giữ nguyên).
        if multiview.nonEmpty && multiview.forall(_._2.isEmpty) then
          div(cls := "hv2-inviteband")(
            span(
              t(
                "Các kênh đang chờ ván đầu tiên — bạn mở màn nhé?",
                "The channels await their first game — care to open?"
              )
            ),
            a(cls := "hv2-btn hv2-btn--gold hv2-btn--sm", href := "#hv2-play")(t("Chơi ngay", "Play now"))
          )
        else
          frag(
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
                        t("Chơi ", "Play ") + channelNameMid(channel)
                      )
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
      // P0.3d: Spotlight rỗng thì đừng để khối biến mất — /tournament luôn có lịch giải
      // tự động của lila, mời sang đó thay vì bỏ trống cột.
      if selected.nonEmpty then
        div(cls := "hv2-tours")(
          selected.map(views.tournament.list.homepageSpotlight(_))
        )
      else
        div(cls := "hv2-tours")(
          div(cls := "hv2-empty")(
            span(t("Chưa có giải nổi bật lúc này.", "No featured tournament right now.")),
            a(cls := "hv2-btn hv2-btn--line hv2-btn--sm", href := routes.Tournament.home)(
              t("Xem lịch giải hôm nay", "See today's schedule")
            )
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
        // P0.3e: cùng lối "giữ khung" với streamer/blog — bảng trống là lời mời
        if leaderboards.blitz.isEmpty && leaderboards.rapid.isEmpty then
          div(cls := "hv2-empty")(
            span(
              t(
                "Bảng xếp hạng đang chờ những ván đầu tiên — chỗ này có thể là tên bạn.",
                "The leaderboard awaits its first games — your name could be here."
              )
            ),
            a(cls := "hv2-btn hv2-btn--line hv2-btn--sm", href := "#hv2-play")(t("Chơi ngay", "Play now"))
          )
        else
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

    // Giữ khung kể cả khi chưa có bài — cùng lối với khối streamer. Bỏ hẳn khối thì lưới
    // 3 cột của zone Cộng đồng thủng mất một cột.
    val blogFrag: Frag =
      frag(
        h3(cls := "hv2-ct")(t("Blog cộng đồng", "Community blog")),
        if ublogPosts.isEmpty then
          div(cls := "hv2-empty")(
            span(t("Chưa có bài viết nào — bạn kể ván cờ của mình nhé?", "No posts yet — tell us about your game?")),
            a(cls := "hv2-btn hv2-btn--line hv2-btn--sm", href := routes.Ublog.communityAll())(
              t("Xem blog cộng đồng", "Browse the blog")
            )
          )
        else
          div(cls := "hv2-posts")(
            ublogPosts.take(4).map: post =>
              a(cls := "hv2-post", href := views.ublog.ui.urlOfPost(post))(post.title)
          )
      )

    // P1.3 — trưng 3 chế độ câu đố lila ĐÃ CÓ SẴN và khách vãng lai chơi được ngay
    // (không cần đăng nhập). Trước đây chúng chỉ nằm trong menu Câu đố ở thanh điều hướng,
    // tức phải biết mà tìm. Không thêm khoá dịch: dùng helper song ngữ `t` tại chỗ.
    val hooksFrag: Frag =
      div(cls := "hv2-hooks")(
        List(
          (
            routes.Puzzle.streak.url,
            Icon.ArrowThruApple,
            t("Chuỗi câu đố", "Puzzle Streak"),
            t("Không đồng hồ. Sai một nước là hết — hôm nay bạn tới số mấy?",
              "No clock. One wrong move ends it — how far can you go?")
          ),
          (
            routes.Storm.home.url,
            Icon.Storm,
            t("Bão câu đố", "Puzzle Storm"),
            t("Ba phút, giải được nhiều nhất có thể.", "Three minutes, solve as many as you can.")
          ),
          (
            routes.Racer.home.url,
            Icon.Bullseye,
            t("Đua câu đố", "Puzzle Racer"),
            t("Rủ bạn bè đua 90 giây.", "Race your friends for 90 seconds.")
          )
        ).map: (url, icon, title, desc) =>
          a(cls := "hv2-hook", href := url)(
            span(cls := "hv2-hook__i", dataIcon := icon),
            span(cls := "hv2-hook__b")(
              span(cls := "hv2-hook__t")(title),
              span(cls := "hv2-hook__d")(desc)
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
            a(cls := "hv2-step", href := "#ai")(
              span(cls := "hv2-step__t")(t("Đánh ván đầu với máy", "Play your first game vs computer")),
              span(cls := "hv2-step__d")(t("Cấp độ 1, không áp lực", "Level 1, no pressure"))
            )
          )
        ),
        a(cls := "hv2-btn hv2-btn--gold", href := routes.Learn.index)(t("Bắt đầu miễn phí", "Start for free"))
      )

    // A3 (báo cáo 02, mục 1.6 T8 + 2.2): trước bản này KHÔNG có nhánh ctx.me nào — người đã
    // đăng nhập thấy trang chủ y hệt khách lạ. Chỉ dùng dữ liệu ĐÃ CÓ SẴN trong Homepage
    // (currentGame, ctx.me) — KHÔNG thêm truy vấn Mongo mới. BỎ (không đủ dữ liệu sẵn có):
    // "ván gần đây" (cần query lịch sử ván), "gợi ý đối thủ" (cần rating range), "tiếp tục
    // học/luyện" theo tiến độ — homepage không preload các thứ đó.
    val continueFrag: Frag =
      ctx.me.map { user =>
        st.section(cls := "hv2-zone hv2-zone--continue")(
          div(cls := "hv2-continue")(
            span(cls := "hv2-continue__hi")(
              t(s"Chào mừng trở lại, ${user.username.value}", s"Welcome back, ${user.username.value}")
            ),
            currentGame.fold(
              div(cls := "hv2-cta")(
                a(cls := "hv2-btn hv2-btn--gold", href := "#hv2-play")(t("Chơi tiếp", "Play again")),
                a(cls := "hv2-btn hv2-btn--line", href := "/hlv")(t("Xem HLV AI", "See AI coach"))
              )
            ) { cg =>
              div(cls := "hv2-cta")(
                a(cls := "hv2-btn hv2-btn--gold", href := routes.Round.player(cg.pov.fullId))(
                  t("Vào ván đang chờ bạn", "Resume your game")
                ),
                span(cls := "hv2-hint")(
                  t(s"Đang đấu với ${cg.opponent}", s"Playing against ${cg.opponent}")
                )
              )
            }
          )
        )
      }

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
        a(cls := "hv2-btn hv2-btn--line", href := "/hlv")(
          t("Xem AI phân tích ván của bạn", "Have the AI analyse your game")
        )
      )

    // B5 — khối demo tương tác của HLV AI. Chỉ là một CÁI VỎ: bàn cờ, hàng nút chọn nhanh và
    // mọi lượt gọi mạng đều do site.coachDemo.ts dựng SAU khi trang đã render xong.
    //
    // Vỏ này ra đời ở trạng thái ẨN (class hk-cdemo--init, CSS display:none) và chỉ hiện sau
    // khi module hỏi được /hlv-app/healthz. Nhờ vậy có ba bảo đảm:
    //   - coach chết / chưa deploy ⇒ khách KHÔNG thấy khối, không thấy hộp lỗi nào;
    //   - không có JS ⇒ vỏ nằm im, không để lại nút chết;
    //   - trang chủ không bao giờ phụ thuộc một service có thể chết. Đây là ràng buộc nặng
    //     nhất của cả tính năng.
    //
    // Đường gọi coach là SAME-ORIGIN qua /hlv-app (proxy của lila), KHÔNG sang
    // coach.hungkings.com: fetch cross-origin bị extension trình duyệt chặn (trả giá 06/08).
    val coachDemoFrag: Option[Frag] =
      coachDemoEnabled.option:
        st.section(cls := "hk-cdemo hk-cdemo--init")(
          div(cls := "hk-cdemo__grid")(
            // `manipulable` cho phép quân đang kéo lòi ra ngoài mép bàn (component/board
            // cắt overflow của mọi .mini-board KHÔNG mang class này) và đổi con trỏ.
            div(cls := "hk-cdemo__board mini-board manipulable cg-wrap is2d")(cgWrapContent),
            div(
              h3(cls := "hk-cdemo__t")(
                t("Đi thử một nước — nghe HLV AI giải thích", "Play a move — hear the AI coach")
              ),
              p(cls := "hk-cdemo__d")(
                t(
                  "Không cần đăng ký. Đi một nước cho Trắng, HLV sẽ nói ngay VÌ SAO nước đó hay hoặc dở — bằng tiếng Việt.",
                  "No sign-up. Play one move for White and the coach explains WHY it works — or doesn't."
                )
              ),
              // role=status + aria-live=polite: trình đọc màn hình đọc câu trả lời ngay khi
              // nó về, mà không cướp tiêu điểm của người đang dùng bàn phím.
              p(cls := "hk-cdemo__say", attr("role") := "status", aria("live") := "polite")(),
              div(cls := "hk-cdemo__foot")(
                a(cls := "hk-cdemo__more", href := "/hlv", attr("hidden").empty)(
                  t("Cho HLV giải cả một ván →", "Have the coach explain a whole game →")
                )
              )
            )
          )
        )

    // Dải thống kê nay xuống cụm đáy của sidebar TOÀN SITE (page.scala dựng sidebar cho
    // mọi trang, nên nó không thấy dữ liệu sảnh — chỉ trang chủ có). Xem Page.sidebarFoot.
    // Số tĩnh lúc render (LobbyApi luôn phát counters); live-update là phase 2.
    // P0.3f: số nhỏ TỆ HƠN không có số — dưới 20 người thì ẩn (luật không-nói-dối).
    val sidebarStats: Option[Frag] =
      val members = (data \ "counters" \ "members").asOpt[Int]
      val rounds = (data \ "counters" \ "rounds").asOpt[Int]
      (members, rounds) match
        case (Some(m), Some(r)) if m >= 20 =>
          p(cls := "hv2-side__stats")(
            t(s"$m người trực tuyến · $r ván đang đấu", s"$m online · $r playing")
          ).some
        case _ => None

    Page("")
      .copy(
        fullTitle = s"$siteName • ${trans.site.freeOnlineChess.txt()}".some,
        sidebarFoot = sidebarStats
      )
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
      // B5: module + bó CSS RIÊNG, chỉ nạp khi cờ bật. Tắt cờ ⇒ không thêm request nào.
      .js(coachDemoEnabled.option(esmInit("site.coachDemo")))
      .css("home-v2")
      .css(coachDemoEnabled.option("coach-demo"))
      .graph(
        OpenGraph(
          image = staticAssetUrl("logo/lichess-tile-wide.png").some,
          // P0.3g: og:title theo ngôn ngữ — câu cũ là nguyên văn của Lichess, share
          // link lên Zalo/Facebook hiện chữ Anh trên trang tiếng Việt.
          title = t("HungKings — Chơi cờ vua trực tuyến miễn phí", "HungKings — Free online chess"),
          url = netBaseUrl.into(Url),
          description = trans.site.siteDescription.txt()
        )
      )
      .hrefLangs(lila.ui.LangPath("/")):
        frag(
          // P0.3b: skip-link cho bàn phím — phần tử focus đầu tiên của trang
          a(cls := "hv2-skip", href := "#hv2-play")(t("Bỏ qua, tới khu thi đấu", "Skip to play area")),
          main(
            cls := List(
              "lobby" -> true,
              "home-v2" -> true,
              "lobby-nope" -> (playban.isDefined || currentGame.isDefined || homepage.hasUnreadLichessMessage)
            )
          )(
          // H1 CỦA TRANG — phải là phần tử tiêu đề ĐẦU TIÊN và nói đúng trang này là gì.
          //
          // Trước 18/08 trang chủ KHÔNG có h1 nào ở đầu: h1 duy nhất là tiêu đề của hero
          // ("Xem lại và học từ ván này" / "Bàn cờ đang được theo dõi"), tức mô tả MỘT VÁN
          // của người lạ chứ không mô tả trang. Sau khi đảo zone theo benchmark thì hero
          // xuống cuối, nên đo trên live thấy thứ tự heading là H2 → H3 → H3 → H3 → H1,
          // với h1 nằm ở y=3207 (đáy trang). Hỏng cả hai đường: trình đọc màn hình duyệt
          // theo heading gặp cấu trúc lộn ngược, và h1 mà Google đọc được lại là tên một ván.
          //
          // Ba hero h1 bên dưới đã hạ xuống h2 — chúng là tiêu đề của KHỐI, không phải của
          // trang. Giữ nguyên class `hv2-matchup` nên không đổi một pixel giao diện nào.
          h1(cls := "hv2-h1")(
            t("Cờ vua trực tuyến — chơi miễn phí, không quảng cáo", "Online chess — free, no ads")
          ),
          // ---------- Zone 0: CHƠI TIẾP — chỉ người ĐÃ ĐĂNG NHẬP (A3), trên cùng ----------
          continueFrag,
          // ---------- Zone 1: THI ĐẤU (lobby app + bảng + giải) ----------
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
          // ---------- Zone 2: HLV AI + RÈN LUYỆN + BẮT ĐẦU ----------
          st.section(cls := "hv2-zone hv2-zone--train")(
            zlabel(t("Rèn luyện", "Train")),
            div(cls := "hv2-train")(
              coachFrag,
              div(cls := "lobby__puzzle hv2-puzzle")(
                puzzle.map(p => views.puzzle.bits.dailyLink(p)())
              )
            ),
            // B5: ngay dưới trích dẫn tĩnh của HLV — lời quảng cáo rồi tới bằng chứng.
            // None khi cờ tắt, tức trang chủ về đúng HTML cũ, không thừa một thẻ nào.
            coachDemoFrag,
            hooksFrag,
            // Người đã đăng nhập không cần mời "học cờ từ đầu" — họ đã ở đây rồi (A3).
            ctx.me.isEmpty.option(starterFrag)
          ),
          // ---------- Zone 3: CỘNG ĐỒNG ----------
          st.section(cls := "hv2-zone hv2-zone--comm", id := "hv2-comm")(
            zlabel(t("Cộng đồng", "Community")),
            div(cls := "hv2-comm")(
              div(leadersFrag),
              div(communityStreams),
              div(blogFrag)
            )
          ),
          // ---------- Zone 4: TRỰC TIẾP — khối rỗng nhất, xuống cuối (báo cáo 02, mục 3) ----------
          st.section(cls := "hv2-zone hv2-zone--live")(
            zlabel(t("Trực tiếp", "Live"), Some((t("Tất cả ván →", "All games →"), routes.Tv.games.url))),
            heroFrag,
            multiviewFrag
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
      )
