package lila.web
package ui

import lila.core.id.CmsPageKey
import lila.ui.*

import ScalatagsTemplate.{ *, given }

// broadcastEnabled: broadcast tạm tắt bằng env LILA_BROADCAST (David chốt 06/08) — mọi route
// relay đều 404, nên trang tài liệu không được dạy nhúng một tính năng đang không tồn tại.
// forumEnabled: HungKings B6 (18/08) — trang hub /cong-dong ẩn mục "Diễn đàn" khi diễn đàn
// tắt, đúng cơ chế sidebar đang dùng (xem layout.scala).
final class SitePages(helpers: Helpers, assetHelper: AssetFullHelper)(
    broadcastEnabled: Boolean,
    forumEnabled: Boolean
):
  import helpers.{ *, given }
  // Nhãn "Giới thiệu về X" phải đi theo net.site.name, đừng viết cứng: bản dịch
  // được thay thương hiệu lúc build, còn tham số truyền vào đây thì không — viết
  // cứng là chỗ duy nhất trong menu tụt lại tên cũ mỗi lần đổi thương hiệu.
  import assetHelper.siteName

  def SitePage(title: String, active: String, contentCls: String = "")(using Context): Page =
    Page(title).wrap: body =>
      main(cls := "page-menu")(
        menu(active),
        div(cls := s"page-menu__content $contentCls")(body)
      )

  def menu(active: String)(using Translate) =
    val sep = div(cls := "sep")
    def activeCls(c: String) = cls := active.activeO(c)
    lila.ui.bits.pageMenuSubnav(
      a(activeCls("about"), href := "/about")(trans.site.aboutX(siteName)),
      a(activeCls("news"), href := routes.Feed.index(1))("HungKings updates"),
      a(activeCls("faq"), href := routes.Main.faq)(trans.faq.faqAbbreviation()),
      a(activeCls("contact"), href := routes.Main.contact)(trans.contact.contact()),
      a(activeCls("tos"), href := routes.Cms.tos)(trans.site.termsOfService()),
      a(activeCls("privacy"), href := "/privacy")(trans.site.privacy()),
      a(activeCls("title"), href := routes.TitleVerify.index)(trans.site.titleVerification()),
      sep,
      a(activeCls("source"), href := routes.Cms.source)(trans.site.sourceCode()),
      a(activeCls("help"), href := routes.Cms.help)(trans.site.contribute()),
      a(activeCls("changelog"), href := routes.Cms.menuPage(CmsPageKey("changelog")))("Changelog"),
      a(activeCls("thanks"), href := "/thanks")(trans.site.thankYou()),
      sep,
      a(activeCls("webmasters"), href := routes.Main.webmasters)(trans.site.webmasters()),
      // Bỏ mục "Database": nó trỏ tới database.lichess.org. Nằm trong menu của
      // HungKings, cạnh Webmasters và API, nó đọc như kho dữ liệu của mình — mà
      // HungKings không xuất kho ván nào cả. Trang Câu đố vẫn ghi công nguồn thật,
      // ở đó lời ghi công là đúng vì bộ câu đố lấy từ Lichess.
      // Mục "API" đã GỠ HẲN (David chốt 04/08). Lịch sử để khỏi làm lại vòng nữa:
      // /api KHÔNG có route trong lila (trên lichess.org nó do một site tài liệu
      // riêng phục vụ) nên link gốc trả 404 ở chân MỌI trang; đợt 02/08 chữa bằng
      // cách trỏ thẳng sang lichess.org/api, tức đổi link chết lấy link đưa người
      // dùng sang site khác. Muốn có lại mục này thì phải TỰ dựng trang tài liệu,
      // đừng trỏ ra ngoài. Kéo theo: `val external` cũng gỡ vì chỉ dùng ở đây.
      sep,
      a(activeCls("lag"), href := routes.Main.lag)(trans.lag.isLichessLagging()),
      a(activeCls("ads"), href := "/ads")("Block ads")
    )

  def webmasters(pieceNames: List[String])(using Context) =
    def parameters(extra: Modifier*) = frag(
      p("Parameters:"),
      ul(
        // actual supported board theme list from lila-gif/src/assets.rs
        li(strong("theme"), ": ", List("blue", "brown", "green", "ic", "purple").mkString(", ")),
        li(strong("pieceSet"), ": ", pieceNames.mkString(", ")),
        li(strong("bg"), ": light, dark, system"),
        extra
      )
    )
    // Bỏ hẳn .csp(_.copy(frameSrc = "https://lichess.org" :: Nil)) từng đứng ở đây. Mọi iframe
    // minh hoạ bên dưới nay trỏ về chính HungKings, tức same-origin, mà CSP mặc định đã có
    // 'self' nên không cần khai thêm gì. Dòng csp đó chính là thứ CHO PHÉP hai iframe tải nội
    // dung sống của Lichess vào trang này — gỡ iframe mà để lại nó là dọn nửa vời: cửa vẫn mở
    // cho lần sau ai đó nhúng lại mà không ai thấy.
    SitePage(
      title = "Webmasters",
      active = "webmasters",
      contentCls = "page force-ltr"
    ).css("bits.page"):
      frag(
          st.section(cls := "box box-pad developers")(
            h1(cls := "box__top")("HTTP API"),
            p(
              "HungKings exposes a RESTish HTTP/JSON API that you are welcome to use. ",
              "Point your client at this server. The embed endpoints documented below work as shown."
            )
          ),
          br,
          st.section(cls := "box box-pad developers") {
            // HungKings a11y `frame-title`: hai iframe minh hoa nay khong co title, trinh
            // doc man hinh chi doc "khung". Them title vao chuoi thuoc tinh dung chung.
            val args =
              """title="HungKings" style="width: 400px; aspect-ratio: 10/11;" allowtransparency="true" frameborder="0""""
            frag(
              a(href := "#embed-tv")(
                h1(cls := "box__top", id := "embed-tv")("Embed HungKings TV in your site")
              ),
              div(cls := "body")(
                div(cls := "center")(raw(s"""<iframe src="/tv/frame?theme=brown&bg=dark" $args></iframe>""")),
                p("Add the following HTML to your site:"),
                copyMeInput(s"""<iframe src="$netBaseUrl/tv/frame?theme=brown&bg=dark" $args></iframe>"""),
                parameters(),
                p(
                  "You can also show the channel for a specific variant or time control by adding the channel key to the URL, corresponding to the channels available at ",
                  // Nhãn cũ là chuỗi "lichess.org/tv" trên một liên kết NỘI BỘ (/tv) — link đi
                  // đúng chỗ nhưng chữ hiện ra lại là địa chỉ của site khác. Sống sót qua đợt
                  // rebrand 76 file vì hàm đổi thương hiệu CỐ Ý bỏ qua chuỗi dạng URL trọn gói.
                  // Dùng $netBaseUrl để nhãn tự đi theo domain, khỏi phải sửa tay lần sau.
                  a(href := "/tv")(s"$netBaseUrl/tv"),
                  ". If not included, the top rated game will be shown."
                ),
                copyMeInput(
                  s"""<iframe src="$netBaseUrl/tv/rapid/frame?theme=brown&bg=dark" $args></iframe>"""
                )
              )
            )
          },
          br,
          st.section(cls := "box box-pad developers") {
            val args =
              """title="HungKings" style="width: 400px; aspect-ratio: 10/11;" allowtransparency="true" frameborder="0""""
            frag(
              a(href := "#embed-puzzle")(
                h1(cls := "box__top", id := "embed-puzzle")("Embed the daily puzzle in your site")
              ),
              div(cls := "body")(
                div(cls := "center")(
                  raw(s"""<iframe src="/training/frame?theme=brown&bg=dark" $args></iframe>""")
                ),
                p("Add the following HTML to your site:"),
                copyMeInput(
                  s"""<iframe src="$netBaseUrl/training/frame?theme=brown&bg=dark" $args></iframe>"""
                ),
                parameters(),
                p("The text is automatically translated to your visitor's language.")
              )
            )
          },
          br,
          st.section(cls := "box box-pad developers") {
            val args = """title="HungKings" style="width: 100%; aspect-ratio: 3/2;" frameborder="0""""
            frag(
              a(href := "#embed-study")(
                h1(cls := "box__top", id := "embed-study")("Embed a chess analysis in your site")
              ),
              div(cls := "body")(
                div(cls := "center"):
                  // ID cũ (XtFCFYlM/GCUTf2Jk) là một study CỦA LICHESS — trên HungKings nó
                  // trả 404, tức trang tài liệu này khoe một khung nhúng vỡ. Loại lỗi này
                  // grep "lichess" KHÔNG bắt được vì nó là mã ID chứ không phải tên miền
                  // (cùng họ với watermark con mã Lichess vẽ bằng path SVG). Nay dùng study
                  // có thật của mình — đã đo /study/embed/9c6GrCTk/Il66Z5ua = 200.
                  raw(s"""<iframe src="/study/embed/9c6GrCTk/Il66Z5ua?bg=auto&theme=auto" $args></iframe>""")
                ,
                p(
                  "Create ",
                  a(href := routes.Study.allDefault())("a study"),
                  ", then click the share button to get the HTML code for the current chapter."
                ),
                parameters(),
                p("The text is automatically translated to your visitor's language.")
              )
            )
          },
          br,
          st.section(cls := "box box-pad developers") {
            val args = """title="HungKings" style="width: 100%; aspect-ratio: 3/2;" frameborder="0""""
            frag(
              a(href := "#embed-game")(
                h1(cls := "box__top", id := "embed-game")("Embed a chess game in your site")
              ),
              div(cls := "body")(
                div(cls := "center"):
                  // MPJcy1JW là một ván CỦA LICHESS -> 404 trên HungKings. Thay bằng ván thật
                  // của mình (đã đo /embed/game/TaHSAsYD = 200).
                  raw(s"""<iframe src="/embed/game/TaHSAsYD?bg=auto&theme=auto" $args></iframe>""")
                ,
                p(
                  "On a game analysis page, click the ",
                  em("FEN & PGN"),
                  " tab at the bottom, then ",
                  "\"",
                  em(trans.site.embedInYourWebsite(), "\".")
                ),
                parameters(),
                p("The text is automatically translated to your visitor's language.")
              )
            )
          },
          br,
          // Mục broadcast chỉ hiện khi module còn bật: từ 06/08 broadcast ẩn toàn site
          // (LILA_BROADCAST=false → mọi route relay + /embed/broadcast/* đều 404), giữ mục
          // này là dạy webmaster nhúng một khung chắc chắn vỡ. Bật lại cờ là mục tự quay về.
          broadcastEnabled.option(
            frag(
              st.section(cls := "box box-pad developers", id := "broadcast") {
                val args = """title="HungKings" style="width: 100%; aspect-ratio: 4/3;" frameborder="0""""
                frag(
                  a(href := "#embed-broadcast")(
                    h1(cls := "box__top", id := "embed-broadcast")("Embed a broadcast in your site")
                  ),
                  div(cls := "body")(
                    // Iframe cũ TẢI THẬT một broadcast của Lichess (giải FIDE Rapid&Blitz 2024) vào
                    // trang này — đó là nội dung SỐNG của site khác chạy trên tên miền mình, không
                    // phải mẫu copy-paste. HungKings chưa có broadcast nào nên không có gì để chiếu
                    // thử; thay khung chiếu bằng đúng mẫu HTML, giống cách upstream vốn đã hướng
                    // dẫn ở câu ngay dưới.
                    p(
                      "On a broadcast page, select the embed iframe code, then optionally add query parameters to customize the appearance."
                    ),
                    copyMeInput(
                      s"""<iframe src="$netBaseUrl/embed/broadcast/<slug>/<roundId>" $args></iframe>"""
                    ),
                    parameters(),
                    p("The text is automatically translated to your visitor's language.")
                  )
                )
              },
              br
            )
          ),
          st.section(cls := "box box-pad developers", id := "analysis") {
            val args = """title="HungKings" style="width: 100%; aspect-ratio: 4/3;" frameborder="0""""
            // Iframe này TẢI THẬT bàn phân tích của lichess.org vào trang HungKings, và mẫu
            // copy-paste ngay dưới còn dạy webmaster khác đi nhúng Lichess — về hiệu ứng còn
            // tệ hơn, vì nó phát tán ra ngoài. /embed/analysis của CHÍNH HungKings đã đo = 200
            // nên trỏ về mình được ngay. Dùng $netBaseUrl để mẫu tự đi theo domain.
            val iframe =
              s"""<iframe src="$netBaseUrl/embed/analysis" $args></iframe>"""
            frag(
              a(href := "#embed-analysis")(
                h1(cls := "box__top", id := "embed-analysis")("Embed an analysis board")
              ),
              div(cls := "body")(
                div(cls := "center")(raw(iframe)),
                p(
                  "Embeds the ",
                  a(href := routes.UserAnalysis.index)("fully-featured HungKings analysis board"),
                  " with stockfish evaluation, opening explorer and tablebase."
                ),
                copyMeInput(iframe),
                parameters(
                  li(strong("fen"), ": custom initial position as a FEN with underscores instead of spaces"),
                  li(strong("color"), ": initial orientation, either black or white")
                ),
                div(
                  "Example using a custom initial position:",
                  copyMeInput:
                    s"""<iframe src="$netBaseUrl/embed/analysis?fen=r1bqkb1r/pp2pppp/2np1n2/6B1/3NP3/2N5/PPP2PPP/R2QKB1R_b_KQkq_-_1_6&color=black" $args></iframe>"""
                ),
                p("The text is automatically translated to your visitor's language.")
              )
            )
          }
        )

  def source(title: String, rendered: Frag, version: Option[WebConfig.LilaVersion])(using
      Context
  ) =
    SitePage(title = title, active = "source", contentCls = "page force-ltr")
      .css("bits.source")
      .js(esmInitBit("setAssetInfo")):
        frag(
          st.section(cls := "box")(
            h1(cls := "box__top")(title),
            table(cls := "slist slist-pad", id := "version")(
              thead(
                tr(
                  th("Current versions"),
                  th(colspan := 2)("Last boot: ", momentFromNow(lila.common.Uptime.startedAt))
                )
              ),
              tbody(
                version.map: v =>
                  tr(
                    td(
                      span("Server"),
                      timeTag(v.date),
                      // Hai link này trỏ kho lichess-org/lila, tức trang khoe MÃ NGUỒN CỦA
                      // HUNGKINGS lại dẫn người xem sang kho của Lichess — mà commit sha in ra
                      // là sha của fork, nên link còn 404 luôn khi commit chỉ có ở fork. Mã
                      // đang thực sự chạy là daviddokrao/lila. Phần ghi công AGPL trong ruột
                      // trang (do CMS render) GIỮ NGUYÊN — đó là nghĩa vụ giấy phép.
                      span(a(href := s"https://github.com/daviddokrao/lila/commits/${v.commit}"):
                        pre(v.commit.take(7)))
                    ),
                    td(v.message),
                    td:
                      a(href := s"https://github.com/daviddokrao/lila/compare/${v.commit}...master"):
                        pre("...")
                  ),
                tr(
                  td(
                    "Assets",
                    timeTag(id := "asset-version-date"),
                    span(a(id := "asset-version-commit")(pre))
                  ),
                  td(id := "asset-version-message"),
                  td(a(id := "asset-version-upcoming")(pre("...")))
                )
              )
            )
          ),
          st.section(cls := "box box-pad body")(rendered)
        )

  // HLV AI: giữ coach TRONG site (cùng domain + sidebar) thay vì bắn người dùng ra
  // subdomain riêng. Coach vẫn là service Node độc lập (đúng chủ ý tách khỏi lila) —
  // ở đây nhúng nó qua iframe SAME-ORIGIN `/hlv-app` (lila proxy sang container coach,
  // xem Main.hlvCoachProxy). Trước đây iframe trỏ thẳng coach.hungkings.com (cross-origin)
  // nên bị một số extension chặn → khung hiện "ảnh vỡ" dù coach vẫn khoẻ. Same-origin thì
  // không quy tắc cross-origin nào chặn được, và KHÔNG cần nới CSP frame-src cho domain ngoài.
  def hlvCoach(gameId: Option[String])(using Context) =
    hlvEmbed(gameId.fold("")(id => id), "Giải thích ván (AI)")

  // Câu đố: nhúng trang giải thích câu đố của coach (/hlv-app/puzzle/<id>).
  def hlvCoachPuzzle(puzzleId: String)(using Context) =
    hlvEmbed(s"puzzle/$puzzleId", "Giải thích câu đố (AI)")

  // The co bat ky tren ban phan tich: nhung trang /hlv-app/position?fen=...
  // FEN phai duoc ma hoa URL: no chua dau cach va "/" nen de nguyen la vo query string.
  def hlvCoachPosition(fen: String)(using Context) =
    val encoded = java.net.URLEncoder.encode(fen, "UTF-8")
    hlvEmbed(s"position?fen=$encoded", "Giải thích thế cờ (AI)")

  // Truyền ngôn ngữ người dùng đang dùng trên site xuống coach: proxy hlvCoachProxy
  // chuyển tiếp query string (KHÔNG chuyển Accept-Language), nên đây là cách duy nhất
  // để coach nhúng biết viết tiếng gì. Coach hỗ trợ vi/en; mã khác coach tự lùi mặc định.
  private def hlvEmbed(path: String, title: String)(using ctx: Context) =
    val lang     = ctx.lang.language
    // `path` co the DA mang san query (vd "position?fen=..."), luc do phai noi bang "&"
    // chu khong phai "?" — neu khong coach nhan mot query string hong va tra 400.
    val sep = if path.contains("?") then "&" else "?"
    val embedUrl =
      if path.isEmpty then s"/hlv-app?embed=1&lang=$lang"
      else s"/hlv-app/$path${sep}embed=1&lang=$lang"
    Page(title):
      main(style := "width:100%;max-width:1100px;margin:0 auto;padding:1rem 1rem 2rem")(
        iframe(
          src := embedUrl,
          // HungKings a11y `frame-title`: iframe khong ten thi trinh doc man hinh chi
          // doc "khung", khong biet ben trong la gi. Dung chinh tieu de trang.
          st.title := title,
          st.frameborder := "0",
          style := "width:100%;height:80vh;border:0;display:block;border-radius:14px;background:transparent"
        )
      )

  // HungKings: HỆ ĐIỂM. Cùng cách nhúng như HLV AI — service Node riêng, iframe
  // same-origin qua proxy /diem-app. Lý do tách y hệt: sửa hệ điểm không được phải
  // dựng lại image lila (13-20 phút mỗi vòng).
  def pointsHome(using Context) = pointsEmbed("", "Điểm HungKings")

  def pointsShop(using Context) = pointsEmbed("shop", "Đổi quà")

  /**
   * Giải cờ 2 Phái — trang vỏ có sidebar, nhúng arena qua /giai-app.
   *
   * KHÁC pointsEmbed ở một chỗ: arena phát URL theo header `X-Base-Path` mà Caddy nội bộ
   * gắn, nên ở đây KHÔNG cần truyền tiền tố qua query. Vẫn truyền `lang` để arena biết
   * ngôn ngữ, và `theme` để app nhúng khớp theme của trang cha — lila đổi theme bằng class
   * `html.light` còn app nhúng vốn nghe `prefers-color-scheme`, hai cơ chế không biết nhau
   * thì lila sáng + máy tối làm chữ trong iframe tụt xuống ~1:1 (đã đo ở /hlv).
   */
  def giaiHome(using ctx: Context) =
    val lang = ctx.lang.language
    // `currentBg` tra 4 gia tri: "light" | "dark" | "transp" | "system" (Pref.scala:130).
    // "system" phai TRUYEN NGUYEN — ep thanh "dark" la sai dung truong hop nguoi dung chon
    // "theo may": lila se render sang theo HDH trong khi iframe bi bao dark. Voi "system",
    // app nhung tu roi ve `prefers-color-scheme` (dung thiet ke cua bo token dung chung).
    // "transp" la bien the nen toi nen gom vao dark.
    val theme = ctx.pref.currentBg match
      case "light"  => "light"
      case "system" => "system"
      case _        => "dark"
    Page("Giải cờ 2 Phái"):
      // `width:100%` BẮT BUỘC đi kèm `margin:0 auto` — xem ghi chú ở pointsEmbed ngay dưới.
      main(style := "width:100%;max-width:1100px;margin:0 auto;padding:1rem 1rem 2rem")(
        iframe(
          src            := s"/giai-app/?embed=1&lang=$lang&theme=$theme",
          st.title       := "Giải cờ 2 Phái",
          st.frameborder := "0",
          style := "width:100%;height:88vh;border:0;display:block;border-radius:14px;background:transparent"
        )
      )

  private def pointsEmbed(path: String, title: String)(using ctx: Context) =
    val lang = ctx.lang.language
    val sep  = if path.contains("?") then "&" else "?"
    val embedUrl =
      if path.isEmpty then s"/diem-app?embed=1&lang=$lang"
      else s"/diem-app/$path${sep}embed=1&lang=$lang"
    Page(title):
      // `width:100%` BẮT BUỘC đi kèm `margin:0 auto`: `main` là grid item
      // (`main{grid-area:main}`), mà margin auto trên grid item làm nó co về
      // max-content = 300px mặc định của iframe. Đã trả giá đúng lỗi này ở /hlv.
      main(style := "width:100%;max-width:1100px;margin:0 auto;padding:1rem 1rem 2rem")(
        iframe(
          src            := embedUrl,
          st.title       := title,
          st.frameborder := "0",
          style := "width:100%;height:88vh;border:0;display:block;border-radius:14px;background:transparent"
        )
      )

  // ---------- HungKings B6 (18/08): 6 trang HUB cấp 1 ----------
  // Mỗi hub thay MỘT mục cấp 1 sidebar đang trỏ neo/route "cụt" bằng một trang liệt kê + MỘT
  // câu mô tả — chia sẻ được, index được, và không phụ thuộc có ai đang online (khuyến nghị
  // B6, ui-redesign/reports/02-benchmark-ia.md mục 1.6 T6, 1.7 X2). "Huấn luyện AI" (đã có
  // /hlv) và "Trực tiếp" (đã trỏ routes.Tv.games thật) KHÔNG có hub — không thuộc phạm vi B6.
  // Route phía sau gác bởi cờ LILA_HUBS ở controllers.Main; file này chỉ vẽ, không tự đọc cờ
  // đó (đối xứng với cách SitePages không đọc LILA_POINTS cho pointsHome/pointsShop).
  //
  // Song ngữ tại chỗ theo ctx.lang, KHÔNG thêm khoá registry dịch (sửa LangList ~19' compile)
  // — cùng khuôn `t(vi, en)` mà layout.scala (sidebar) và onboarding.scala đã dùng, đặt tên
  // theo quy ước dự án gọi hàm này ở onboarding.scala.
  private def HomeI18n(vi: String, en: String)(using ctx: Context): String =
    if ctx.lang.language == "vi" then vi else en

  // Cùng lý do đã ghi ở layout.scala/Main.scala cho realchessEnabled/arenaEnabled: đọc THẲNG
  // sys.env thay vì đi qua HOCON/WebConfig — giữ MỘT nguồn sự thật với route đằng sau mỗi
  // link, và biến RỖNG không làm nổ parse bool lúc khởi động. Đây là "dùng lại cùng cơ chế
  // cờ" theo đúng nghĩa: cùng tên biến, cùng ngữ nghĩa rỗng-là-tắt — không phải cùng một
  // instance Scala (layout.scala ở module `web`, đây cũng ở module `web` nhưng lớp khác).
  private val hubRealchessEnabled = sys.env.get("LILA_REALCHESS_INTERNAL_URL").exists(_.nonEmpty)
  private val hubArenaEnabled     = sys.env.getOrElse("LILA_ARENA", "false") == "true"

  // Tham so ten `url` chu KHONG phai `href`: dat ten `href` thi no CHE mat thuoc tinh
  // `href` cua Scalatags trong pham vi ham, nen `href := href` bi hieu la gan vao mot
  // String va compile chet ("value := is not a member of String").
  // Doi chieu TAY khong bat duoc loai loi nay vi ca hai ten deu ton tai that — chi trinh
  // bien dich moi thay. Da lam chet build 13 ngay 18/08.
  private def hubItem(url: String, vi: String, en: String)(using ctx: Context) =
    // Link chu inline trong danh sach: WCAG 2.5.8 chi doi >=24px, khong phai 44px
    // (BRAND.md §3b) — padding duoi day cho ra ~30px, co bien an toan.
    li(style := "margin:0.5em 0;line-height:1.5")(
      a(href := url, style := "display:inline-block;padding:0.3em 0.1em")(HomeI18n(vi, en))
    )

  private def hubPage(
      icon: Icon,
      titleVi: String,
      titleEn: String,
      descVi: String,
      descEn: String
  )(items: Frag*)(using ctx: Context) =
    Page(HomeI18n(titleVi, titleEn)).css("bits.page"):
      main(cls := "page box box-pad")(
        h1(cls := "box__top", dataIcon := icon)(HomeI18n(titleVi, titleEn)),
        p(HomeI18n(descVi, descEn)),
        ul(items)
      )

  def hubChoi(using ctx: Context) =
    hubPage(
      Icon.PlayTriangle,
      "Chơi",
      "Play",
      "Vào đấu ngay: ghép cặp nhanh, tạo ván riêng, thách đấu bạn bè, hoặc chơi với máy.",
      "Jump into a game: quick pairing, a private game, a friendly challenge, or play the computer."
    )(
      hubItem("/#hv2-play", "Xếp cặp nhanh", "Quick pairing"),
      hubItem("/#hook", "Tạo ván tại sảnh", "Create a game"),
      hubItem("/#friend", "Thách đấu bạn bè", "Play a friend"),
      hubItem("/#ai", "Chơi với máy", "Play the computer"),
      if hubRealchessEnabled then hubItem("/realchess", "Bàn cờ 3D", "3D board") else emptyFrag
    )

  def hubCauDo(using ctx: Context) =
    hubPage(
      Icon.ArcheryTarget,
      "Câu đố",
      "Puzzles",
      "Luyện chiến thuật qua câu đố: hằng ngày, theo chủ đề, bão tốc độ, đua với người khác, hoặc giữ chuỗi thắng.",
      "Sharpen your tactics: daily puzzles, puzzles by theme, puzzle storm, puzzle racer, or a puzzle streak."
    )(
      hubItem(routes.Puzzle.home.url, "Câu đố hằng ngày", "Daily puzzle"),
      hubItem(routes.Puzzle.themes.url, "Theo chủ đề", "Puzzle themes"),
      hubItem(routes.Storm.home.url, "Bão câu đố", "Puzzle storm"),
      hubItem(routes.Racer.home.url, "Đua câu đố", "Puzzle racer"),
      hubItem(routes.Puzzle.streak.url, "Chuỗi thắng", "Puzzle streak")
    )

  def hubHoc(using ctx: Context) =
    hubPage(
      Icon.GraduateCap,
      "Học cờ",
      "Learn",
      "Học và luyện cờ vua: luật cơ bản, luyện thế cờ, bàn phân tích, và nghiên cứu ván đấu.",
      "Learn and train: chess basics, practice positions, an analysis board, and studies."
    )(
      hubItem(routes.Learn.index.url, "Học cơ bản", "Chess basics"),
      hubItem(routes.Practice.index.url, "Luyện thế cờ", "Practice"),
      hubItem(routes.UserAnalysis.index.url, "Bàn phân tích", "Analysis board"),
      hubItem(routes.Study.allDefault().url, "Nghiên cứu", "Studies")
    )

  def hubGiaiDau(using ctx: Context) =
    hubPage(
      Icon.Trophy,
      "Giải đấu",
      "Tournaments",
      "Thi đấu trong các giải: đấu trường đang mở và lịch giải sắp tới.",
      "Compete in tournaments: open arenas and the upcoming schedule."
    )(
      hubItem(routes.Tournament.home.url, "Đấu trường", "Arenas"),
      hubItem(routes.Tournament.calendar.url, "Lịch giải", "Calendar"),
      if hubArenaEnabled then hubItem("/giai", "Giải 2 Phái", "Two Sides") else emptyFrag
    )

  def hubCongDong(using ctx: Context) =
    hubPage(
      Icon.Group,
      "Cộng đồng",
      "Community",
      "Kết nối với người chơi khác: danh sách kỳ thủ, các đội, và tin tức HungKings.",
      "Connect with other players: the player list, teams, and HungKings news."
    )(
      hubItem(routes.User.list.url, "Kỳ thủ", "Players"),
      hubItem(routes.Team.home().url, "Đội", "Teams"),
      hubItem(routes.Feed.index().url, "Tin HungKings", "News"),
      if forumEnabled then hubItem(routes.ForumCateg.index.url, "Diễn đàn", "Forum") else emptyFrag
    )

  def hubCongCu(using ctx: Context) =
    hubPage(
      Icon.Tools,
      "Công cụ",
      "Tools",
      "Công cụ cờ vua độc lập: bàn phân tích, sửa bàn cờ, nhập ván PGN, và luyện toạ độ.",
      "Standalone chess tools: analysis board, board editor, PGN import, and coordinate trainer."
    )(
      hubItem(routes.UserAnalysis.index.url, "Bàn phân tích", "Analysis board"),
      hubItem(routes.Editor.index.url, "Sửa bàn cờ", "Board editor"),
      hubItem(routes.Importer.importGame.url, "Nhập ván PGN", "Import game"),
      hubItem(routes.Coordinate.home.url, "Luyện toạ độ", "Coordinate trainer")
    )

  def lag(using Context) =
    import trans.lag as trl
    SitePage(title = "Is HungKings lagging?", active = "lag")
      .css("bits.lag")
      .js(esmInit("chart.lag")):
        div(cls := "box box-pad lag")(
          h1(cls := "box__top")(
            trl.isLichessLagging(),
            span(cls := "answer short")(
              span(cls := "waiting")(trl.measurementInProgressThreeDot()),
              span(cls := "nope-nope none")(trl.noAndYourNetworkIsGood()),
              span(cls := "nope-yep none")(trl.noAndYourNetworkIsBad()),
              span(cls := "yep none")(trl.yesItWillBeFixedSoon())
            )
          ),
          div(cls := "answer long")(
            trl.andNowTheLongAnswerLagComposedOfTwoValues()
          ),
          div(cls := "sections")(
            st.section(cls := "server")(
              h2(trl.lichessServerLatency()),
              div(cls := "meter")(canvas(cls := "server-chart")),
              p(
                trl.lichessServerLatencyExplanation()
              )
            ),
            st.section(cls := "network")(
              h2(trl.networkBetweenLichessAndYou()),
              div(cls := "meter")(canvas(cls := "network-chart")),
              p(
                trl.networkBetweenLichessAndYouExplanation()
              )
            )
          ),
          div(cls := "last-word")(
            p(trl.youCanFindTheseValuesAtAnyTimeByClickingOnYourUsername()),
            h2(trl.lagCompensation()),
            p(trl.lagCompensationExplanation())
          )
        )

  def getFishnet =
    Page("fishnet API key request")
      .csp(_.withGoogleForm):
        main:
          iframe(
            src := "https://docs.google.com/forms/d/e/1FAIpQLSeGgDHgWGP0uobQknF92eCMXqebyNBTyzJoJqbeGjRezlbWOw/viewform?embedded=true",
            style := "width:100%;height:1400px",
            st.frameborder := 0,
            frame.credentialless
          )(spinner)

  def errorPage =
    Page("Internal server error"):
      main(cls := "page-small box box-pad")(
        h1(cls := "box__top")("Something went wrong on this page"),
        p(
          "If the problem persists, please ",
          a(href := s"${routes.Main.contact}#help-error-page")("report the bug"),
          "."
        )
      )
