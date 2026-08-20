package views.base

import scalalib.StringUtils.escapeHtmlRaw

import lila.app.UiEnv.{ *, given }
import lila.common.String.html.safeJsonValue
import lila.ui.{ RenderedPage, PageFlags }
import lila.mon.extensions.*

object page:

  val pieceSetImages = lila.web.ui.PieceSetImages(assetHelper)

  val ui = lila.web.ui.layout(helpers, assetHelper)(
    popularAlternateLanguages = lila.i18n.LangList.popularAlternateLanguages,
    reportScoreThreshold = env.report.scoreThresholdsSetting.get,
    reportScore = () => env.report.api.maxScores.dmap(_.highest).awaitOrElse(50.millis, "nbReports", 0),
    forumEnabled = env.web.config.forumEnabled,
    broadcastEnabled = env.web.config.broadcastEnabled,
    pointsEnabled = env.web.config.pointsEnabled
  )
  import ui.*

  private val topnav = lila.web.ui.TopNav(helpers)(
    env.web.config.forumEnabled,
    env.web.config.broadcastEnabled,
    env.web.config.pointsEnabled
  )

  // HungKings: khối "lối thoát" khi xếp cặp ẩn danh không khớp (site còn ít người chơi
  // trực tuyến nên seek /setup/hook có thể chờ vô hạn). Đọc thẳng sys.env thay vì luồng
  // qua WebConfig/HOCON — layout.scala/homeV2.scala đang do phiên khác giữ, và đây là
  // cờ CHỈ ẢNH HƯỞNG một data-attribute dùng ở phía client (ui/lobby/src/ctrl.ts đọc
  // document.body.dataset.lobbyEscapeHatch), không cần đi qua bất kỳ view nào khác.
  // sys.env.get(...).contains("true") tránh đúng bẫy HOCON "biến rỗng vẫn tính đã đặt":
  // ở đây biến rỗng hoặc bất kỳ giá trị nào khác "true" đều là TẮT, không có gì để nổ.
  // Bật: LILA_LOBBY_ESCAPE_HATCH=true trong deploy/.env rồi deploy — KHÔNG build lại image.
  private val lobbyEscapeHatchEnabled: Boolean =
    sys.env.get("LILA_LOBBY_ESCAPE_HATCH").contains("true")

  // HungKings: cụm nút sau ván (Đấu lại/Đối thủ mới/Phân tích/Giải thích AI) đứng ngang
  // hàng nhau, chôn mất tài sản giữ chân mạnh nhất của sản phẩm — xem
  // ui-redesign/reports/02-benchmark-ia.md mục 2.3/B3. Cờ này chỉ ảnh hưởng một
  // data-attribute đọc ở phía client (ui/round/src/view/button.ts followUp() đọc
  // document.body.dataset.roundReviewCta), lặp đúng khuôn lobbyEscapeHatchEnabled ở trên:
  // sys.env.get(...).contains("true") để biến rỗng/không đặt = TẮT, tránh bẫy HOCON
  // "biến rỗng vẫn tính đã đặt". Tắt (mặc định): không thêm attribute, hành vi y hệt hôm
  // nay. Bật: LILA_ROUND_REVIEW_CTA=true trong deploy/.env rồi deploy — KHÔNG build lại
  // image.
  private val roundReviewCtaEnabled: Boolean =
    sys.env.get("LILA_ROUND_REVIEW_CTA").contains("true")

  private def metaThemeColor(using ctx: Context): Frag =
    raw(s"""<meta name="theme-color" content="${ctx.pref.themeColor}">""")

  private def boardPreload(using ctx: Context) = frag(
    imagePreload(assetUrl(s"images/board/${ctx.pref.currentTheme.file}")),
    ctx.pref.is3d.option:
      imagePreload(assetUrl(s"images/staunton/board/${ctx.pref.currentTheme3d.file}"))
  )

  def boardStyle(zoomable: Boolean)(using ctx: Context) =
    s"---board-opacity:${ctx.pref.board.opacity};" +
      s"---board-brightness:${ctx.pref.board.brightness};" +
      s"---board-contrast:${ctx.pref.board.contrast};" +
      s"---board-hue:${ctx.pref.board.hue};" +
      zoomable.so(s"---zoom:$pageZoom;")

  def apply(p: Page)(using ctx: PageContext): RenderedPage =
    import ctx.pref
    val anonOnboarding = ctx.isAnon.so(lila.security.EmailConfirm.cookie.get(ctx.req))
    // Sidebar thay header ngang trên MỌI trang khi cờ bật (David chốt 04/08). Hai ngoại lệ:
    // `noHeader` (trang oauth/takex3 cố ý không có chrome nào) và người dùng đang kháng
    // nghị — luồng đó chỉ có nút đăng xuất, sidebar đầy đủ sẽ mở lại lối đi đã bị khoá.
    val useSidebar = env.web.config.homeSidebar &&
      !p.flags(PageFlags.noHeader) && !ctx.isAppealUser
    val allModules = p.modules ++
      p.pageModule.so(module => esmPage(module.name)) ++
      ctx.needsFp.so(fingerprintTag) ++
      // esmInit chứ KHÔNG phải esmInitBit: esmInitBit nạp module GỘP "bits" rồi dispatch
      // theo tên hàm, còn bits.homeV2Sidebar.ts là module RIÊNG export initModule().
      useSidebar.so(List(esmInit("bits.homeV2Sidebar").some)) ++
      anonOnboarding.isDefined.so(esmInitBit("emailErrorCheck"))
    val zenable = p.flags(PageFlags.zen)
    val playing = p.flags(PageFlags.playing)
    val pageFrag = frag(
      doctype,
      htmlTag(
        (ctx.impersonatedBy.isEmpty && !ctx.blind)
          .option(cls := ctx.pref.themeColorClass),
        topComment,
        head(
          charset,
          viewport,
          metaCsp(p.csp.map(_(defaultCsp))),
          metaThemeColor,
          st.headTitle:
            val prodTitle = p.fullTitle | s"${p.title} • $siteName"
            if env.mode.isProd then prodTitle
            else s"${ctx.me.so(_.username.value + " ")} $prodTitle"
          ,
          cssTag("lib.theme.all"),
          cssTag("site"),
          pref.is3d.option(cssTag("lib.board-3d")),
          ctx.data.inquiry.isDefined.option(cssTag("mod.inquiry")),
          ctx.impersonatedBy.isDefined.option(cssTag("mod.impersonate")),
          ctx.blind.option(cssTag("bits.blind")),
          p.cssKeys.map(cssTag),
          meta(
            content := p.openGraph.fold(trans.site.siteDescription.txt())(o => o.description),
            name := "description"
          ),
          // Safari pinned tab: ghim màu vàng logo thay vì đen mặc định
          link(rel := "mask-icon", href := staticAssetUrl("logo/lichess.svg"), attr("color") := "#FFBF00"),
          favicons,
          (p.flags(PageFlags.noRobots) || !netConfig.crawlable).option(noRobots),
          noTranslate,
          // No caller ever sets the site name on an OpenGraph, so without this every page
          // advertises the upstream default when shared. Taken from config rather than
          // hardcoded, so each environment names itself correctly.
          // L10 (20/08): thêm og:locale theo ngôn ngữ đang render. `ctx.lang.code` cho
          // "vi-VN"; Open Graph đòi gạch dưới nên đổi "-" thành "_".
          // Dùng dạng ngoặc chứ KHÔNG dùng `map:` (fewer-braces) ở đây: phần tử này nằm giữa
          // một danh sách ngăn cách bằng dấu phẩy, nơi khối thụt lề rất dễ nuốt dấu phẩy kế
          // tiếp — và mỗi lần đoán sai cú pháp tốn một vòng build 13-20 phút.
          p.openGraph.map(og =>
            lila.web.ui.openGraph(
              og.copy(siteName = siteName),
              locale = ctx.lang.code.replace("-", "_").some
            )
          ),
          p.atomLinkTag | dailyNewsAtom,
          (pref.bg == lila.pref.Pref.Bg.TRANSPARENT).option(pref.bgImgOrDefault).map { loc =>
            val url =
              if loc.startsWith("/assets/") then assetUrl(loc.drop(8))
              else escapeHtmlRaw(loc).replace("&amp;", "&")
            raw(s"""<style id="bg-data">html.transp::before{background-image:url("$url");}</style>""")
          },
          fontsPreload,
          boardPreload,
          manifests,
          p.withHrefLangs.map(hrefLangs),
          sitePreload(p.i18nModules, ctx.data.inquiry.isDefined.option(Esm("mod.inquiry")) :: allModules),
          lichessFontFaceCss,
          pieceSetImages.load(ctx.pref.currentPieceSet.name),
          (ctx.pref.bg === lila.pref.Pref.Bg.SYSTEM || ctx.impersonatedBy.isDefined)
            .so(systemThemeScript(ctx.nonce))
        ).pipe(p.transformHead),
        st.body(
          cls := {
            val baseClass = s"${pref.currentBg} coords-${pref.coordsClass}"
            List(
              baseClass -> true,
              "simple-board" -> pref.simpleBoard,
              "piece-letter" -> pref.pieceNotationIsLetter,
              "blind-mode" -> ctx.blind,
              "kid" -> ctx.kid.yes,
              "mobile" -> lila.common.HTTPRequest.isMobileBrowser(ctx.req),
              "playing fixed-scroll" -> playing,
              "no-rating" -> (!pref.showRatings || (playing && pref.hideRatingsInGame)),
              "no-flair" -> !pref.flairs,
              "zen" -> (zenable && (pref.isZen || (playing && pref.isZenAuto))),
              "zenable" -> zenable,
              "zen-auto" -> (zenable && pref.isZenAuto)
            )
          },
          dataVapid := (ctx.isAuth && env.security.lilaCookie.isRememberMe(ctx.req))
            .option(env.push.vapidPublicKey),
          dataUser := ctx.userId,
          dataUsername := ctx.username,
          dataSoundSet := pref.currentSoundSet.toString,
          attr("data-socket-domains") := (if ~pref.usingAltSocket then netConfig.socketAlts
                                          else netConfig.socketDomains).mkString(","),
          dataAssetUrl,
          dataAssetVersion := assetVersion,
          dataNonce := ctx.nonce,
          dataTheme := pref.currentBg,
          dataBoard := pref.currentTheme.name,
          dataPieceSet := pref.currentPieceSet.name,
          dataBoard3d := pref.is3d.option(pref.currentTheme3d.name),
          dataPieceSet3d := pref.is3d.option(pref.currentPieceSet3d.name),
          dataAnnounce := lila.web.AnnounceApi.get.map(a => safeJsonValue(a.json)),
          // HungKings: xem comment `lobbyEscapeHatchEnabled` ở trên. Chỉ ghi attribute khi
          // BẬT để tắt cờ = HTML y hệt hôm nay (không thêm attribute rỗng).
          attr("data-lobby-escape-hatch") := lobbyEscapeHatchEnabled.option("1"),
          // HungKings: xem comment `roundReviewCtaEnabled` ở trên. Cùng khuôn: chỉ ghi
          // attribute khi BẬT để tắt cờ = HTML y hệt hôm nay.
          attr("data-round-review-cta") := roundReviewCtaEnabled.option("1"),
          attr("data-i18n-catalog") := assetHelper.manifest
            .js(s"i18n/${ctx.lang.code}")
            .map(name => staticAssetUrl(s"compiled/$name")),
          style := boardStyle(p.flags(PageFlags.zoom))
        )(
          blindModeForm,
          assetsMissingTroubleshooting,
          for in <- ctx.data.inquiry; me <- ctx.me yield views.mod.inquiryUi(in)(using ctx, me),
          ctx.me.ifTrue(ctx.impersonatedBy.isDefined).map { views.mod.ui.impersonate(_) },
          netConfig.stageBanner.option(views.bits.stage),
          anonOnboarding.map: u =>
            frag(cssTag("bits.email-confirm"), views.auth.checkYourEmailBanner(u.username, u.email)),
          zenable.option(zenZone),
          Option.unless(p.flags(PageFlags.noHeader) || useSidebar):
            ui.siteHeader(
              zenable = zenable,
              isAppealUser = ctx.isAppealUser,
              challenges = ctx.nbChallenges,
              notifications = ctx.nbNotifications.value,
              error = ctx.data.error,
              topnav = topnav(
                seesClassMenu = ctx.seesClassMenu,
                hasDgt = ctx.pref.hasDgt
              )
            )
          ,
          div(
            id := "main-wrap",
            cls := List(
              "full-screen-force" -> p.flags(PageFlags.fullScreen),
              "is2d" -> pref.is2d,
              "is3d" -> pref.is3d
            )
          )(
            // Sidebar nằm TRONG #main-wrap chứ không phải trước nó: bố cục 2 cột dựa vào
            // `#main-wrap:has(> aside.hv2-side)` + `grid-area: side` (_sidebar.scss).
            // Đặt ra ngoài là selector không khớp và cột trái biến mất không báo lỗi.
            useSidebar.option(
              ui.siteSidebar(
                challenges = ctx.nbChallenges,
                notifications = ctx.nbNotifications.value,
                error = ctx.data.error,
                foot = p.sidebarFoot
              )
            ),
            p.transform(p.body)
          ),
          bottomHtml,
          ctx.nonce.map(inlineJs(_, allModules)),
          modulesInit(allModules, ctx.nonce),
          p.pageModule.map { mod => frag(jsonScript(mod.data)) }
        )
      )
    )
    RenderedPage(pageFrag.render)
