package lila.web
package ui

import lila.core.id.CmsPageKey
import lila.ui.*

import ScalatagsTemplate.{ *, given }

// broadcastEnabled: broadcast tạm tắt bằng env LILA_BROADCAST (David chốt 06/08) — mọi route
// relay đều 404, nên trang tài liệu không được dạy nhúng một tính năng đang không tồn tại.
final class SitePages(helpers: Helpers, assetHelper: AssetFullHelper)(broadcastEnabled: Boolean):
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
            val args =
              """style="width: 400px; aspect-ratio: 10/11;" allowtransparency="true" frameborder="0""""
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
              """style="width: 400px; aspect-ratio: 10/11;" allowtransparency="true" frameborder="0""""
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
            val args = """style="width: 100%; aspect-ratio: 3/2;" frameborder="0""""
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
            val args = """style="width: 100%; aspect-ratio: 3/2;" frameborder="0""""
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
                val args = """style="width: 100%; aspect-ratio: 4/3;" frameborder="0""""
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
            val args = """style="width: 100%; aspect-ratio: 4/3;" frameborder="0""""
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

  // Truyền ngôn ngữ người dùng đang dùng trên site xuống coach: proxy hlvCoachProxy
  // chuyển tiếp query string (KHÔNG chuyển Accept-Language), nên đây là cách duy nhất
  // để coach nhúng biết viết tiếng gì. Coach hỗ trợ vi/en; mã khác coach tự lùi mặc định.
  private def hlvEmbed(path: String, title: String)(using ctx: Context) =
    val lang     = ctx.lang.language
    val embedUrl =
      if path.isEmpty then s"/hlv-app?embed=1&lang=$lang" else s"/hlv-app/$path?embed=1&lang=$lang"
    Page(title):
      main(style := "width:100%;max-width:1100px;margin:0 auto;padding:1rem 1rem 2rem")(
        iframe(
          src := embedUrl,
          st.frameborder := "0",
          style := "width:100%;height:80vh;border:0;display:block;border-radius:14px;background:transparent"
        )
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
