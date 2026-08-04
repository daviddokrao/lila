package lila.web
package ui

import lila.ui.*

import ScalatagsTemplate.{ *, given }

val fideHandbookUrl = "https://handbook.fide.com/chapter/E012023"

final class FaqUi(helpers: Helpers, sitePages: SitePages)(
    standardRankableDeviation: Int,
    variantRankableDeviation: Int
):
  import helpers.{ given, * }
  import trans.faq as trf

  private def cmsPageUrl(key: String) = routes.Cms.lonePage(lila.core.id.CmsPageKey(key))

  private def question(id: String, title: String, answer: Frag*) =
    details(
      st.id := id,
      cls := "question",
      name := "faq"
    )(
      summary(span(title)),
      div(cls := "answer")(answer)
    )

  // Bốn câu hỏi RIÊNG của HungKings bên dưới (name, built-on, contributing, make-a-bot) không
  // có khoá dịch nào của upstream mang đúng nghĩa đó. Hai đường quen thuộc đều bịt:
  //   - thêm khoá vào registry lila -> đụng ranh giới P0.8 (DECISIONS.md), đúng thứ đang chặn F3;
  //   - hardcode thẳng tiếng Việt -> site chạy 94 ngôn ngữ, tức bắt người dùng tiếng Anh đọc
  //     tiếng Việt. Đổi một lỗi lấy một lỗi to hơn (xem BLOCKERS.md, phương án 2).
  // Đường thứ ba, dùng ở đây: rẽ theo ngôn ngữ NGAY TẠI CHỖ. Khán giả chính là người Việt nên
  // họ đọc tiếng Việt; mọi ngôn ngữ khác lùi về tiếng Anh như cũ. Không thêm một khoá nào vào
  // registry, và không nói dối trong 94 ngôn ngữ.
  private def isVi(using ctx: Context): Boolean = ctx.lang.language == "vi"

  def apply(using ctx: Context) =
    sitePages
      .SitePage(
        // Trước đây hardcode "Frequently Asked Questions" nên <title> ra tiếng Anh trên trang
        // tiếng Việt, dù h1 ngay dưới đã dịch đúng (h1 dùng trf.frequentlyAskedQuestions()).
        // Khoá này CÓ SẴN ở upstream — dùng lại, không thêm khoá mới, không đụng P0.8.
        title = trf.frequentlyAskedQuestions.txt(),
        active = "faq"
      )
      .css("bits.faq"):
        div(cls := "faq box box-pad")(
          h1(cls := "box__top")(trf.frequentlyAskedQuestions()),
          h2("HungKings"),
          // Ba câu hỏi đầu của upstream nói về CHÍNH Lichess: tên bắt nguồn từ
          // live/light/libre, mã nguồn tên "lila", và danh sách site dựng TRÊN Lichess.
          // Bộ thay thương hiệu lúc build biến chúng thành lời khẳng định sai về mình
          // ("HungKings là ghép của live/light/libre", "các site dựng trên HungKings"),
          // nên phải viết lại chứ không dịch được. Viết thẳng tiếng Anh: khoá dịch của
          // upstream không mang nghĩa mới này, dùng lại là nói dối trong 94 ngôn ngữ.
          question(
            "name",
            if isVi then "Vì sao lại tên là HungKings?" else "Why is it called HungKings?",
            // LƯU Ý nội dung: câu "đặt theo các Vua Hùng" là SUY ĐOÁN từ tên thương hiệu, David
            // chưa từng xác nhận. Việc [human] xác nhận nội dung này vẫn đang treo trong HANDOFF
            // — bản dịch giữ nguyên ý bản gốc chứ không tự bồi thêm, để lúc David sửa thì chỉ
            // phải sửa một ý ở hai chỗ.
            if isVi then
              p(
                "Tên site đặt theo các Vua Hùng, những người dựng nước trong truyền thuyết Việt Nam. ",
                "Máy chủ cờ vua này là một phần trong hệ sinh thái sản phẩm HungKings."
              )
            else
              p(
                "The site is named after the Hùng Kings, the legendary founders of Vietnam. ",
                "This chess server is one part of the wider HungKings family of products."
              )
          ),
          question(
            // id neo lọt vào HTML dưới dạng #built-on-lichess (hiện cả trên thanh địa chỉ khi
            // bấm vào tiêu đề câu hỏi). Không nơi nào trỏ tới nó nên đổi tên an toàn. Phần
            // NỘI DUNG bên dưới thì GIỮ NGUYÊN — ghi công Lichess là nghĩa vụ AGPL-3.0, không
            // phải chuỗi thương hiệu sót lại.
            "built-on",
            if isVi then "HungKings được dựng trên nền gì?" else "What is HungKings built on?",
            // Đây là phần GHI CÔNG theo AGPL-3.0 — bắt buộc phải có và phải nói đúng sự thật.
            // Dịch sang tiếng Việt để người đọc chính hiểu được mình đang ghi công ai; nội dung
            // hai bản phải khớp nhau, đừng làm nhẹ bản nào.
            if isVi then
              p(
                "HungKings là một bản fork của ",
                a(href := "https://lichess.org")("Lichess"),
                ", máy chủ cờ vua miễn phí hoạt động như một tổ chức phi lợi nhuận. Gần như mọi thứ ",
                "bạn thấy ở đây — bàn phân tích, câu đố, giải đấu, nghiên cứu, các biến thể cờ — đều ",
                "do cộng đồng Lichess xây dựng trong nhiều năm. Chúng tôi sử dụng theo giấy phép AGPL-3.0."
              )
            else
              p(
                "HungKings is a fork of ",
                a(href := "https://lichess.org")("Lichess"),
                ", a free chess server run as a charity. Nearly everything you see here — the ",
                "analysis board, puzzles, tournaments, studies, the chess variants — was built by ",
                "the Lichess community over many years. We use it under the AGPL-3.0 licence."
              )
            ,
            if isVi then
              p(
                "Bộ máy phía sau là ",
                a(href := "https://github.com/lichess-org/lila")("lila"),
                ", viết bằng ",
                a(href := "https://scala-lang.org/")("Scala"),
                "."
              )
            else
              p(
                "The engine behind it is ",
                a(href := "https://github.com/lichess-org/lila")("lila"),
                ", written in ",
                a(href := "https://scala-lang.org/")("Scala"),
                "."
              )
          ),
          question(
            "contributing",
            if isVi then "Tôi đóng góp cho HungKings bằng cách nào?"
            else "How can I contribute to HungKings?",
            if isVi then
              p(
                "HungKings là phần mềm tự do, mã nguồn mở. Chính giấy phép AGPL-3.0 buộc chúng tôi ",
                "công khai các thay đổi của mình, nên bạn đọc được từng dòng mã, báo lỗi, hoặc gửi ",
                "bản vá trên ",
                a(href := "https://github.com/daviddokrao/lila")("GitHub"),
                "."
              )
            else
              p(
                "HungKings is free and open source. That same AGPL-3.0 licence obliges us to ",
                "publish our own changes, so you can read every line, report a problem, or send ",
                "a patch on ",
                a(href := "https://github.com/daviddokrao/lila")("GitHub"),
                "."
              )
            ,
            if isVi then
              p(
                "Xem trang ",
                a(href := "/source")(trans.site.sourceCode()),
                " để có danh sách đầy đủ các kho mã."
              )
            else
              p(
                "See the ",
                a(href := "/source")(trans.site.sourceCode()),
                " page for the full list of repositories."
              )
          ),
          question(
            "keyboard-shortcuts",
            trf.keyboardShortcuts.txt(),
            p(
              trf.keyboardShortcutsExplanation()
            )
          ),
          h2(trf.fairPlay()),
          question(
            "rating-refund",
            trf.whenAmIEligibleRatinRefund.txt(),
            p(
              trf.ratingRefundExplanation()
            )
          ),
          question(
            "leaving",
            trf.preventLeavingGameWithoutResigning.txt(),
            p(
              trf.leavingGameWithoutResigningExplanation()
            )
          ),
          question(
            "mod-application",
            trf.howCanIBecomeModerator.txt(),
            p(
              trf.youCannotApply()
            )
          ),
          question(
            "correspondence",
            trf.isCorrespondenceDifferent.txt(),
            p(
              trf.youCanUseOpeningBookNoEngine()
            ),
            p(
              trf.pleaseReadFairPlayPage(a(href := cmsPageUrl("fair-play"))(trf.fairPlayPage()))
            )
          ),
          h2(trf.gameplay()),
          question(
            "time-controls",
            trf.howBulletBlitzEtcDecided.txt(),
            p(
              trf.basedOnGameDuration(strong(trf.durationFormula()))
            ),
            ul(
              li(trf.inferiorThanXsEqualYtimeControl(29, "UltraBullet")),
              li(trf.inferiorThanXsEqualYtimeControl(179, trans.site.bullet())),
              li(trf.inferiorThanXsEqualYtimeControl(479, trans.site.blitz())),
              li(trf.inferiorThanXsEqualYtimeControl(1499, trans.site.rapid())),
              li(trf.superiorThanXsEqualYtimeControl(1500, trans.site.classical()))
            )
          ),
          question(
            "variants",
            trf.whatVariantsCanIplay.txt(),
            p(
              trf.lichessSupportChessAnd(
                a(href := routes.Cms.variantHome)(trf.eightVariants())
              )
            )
          ),
          question(
            "acpl",
            trf.whatIsACPL.txt(),
            p(
              trf.acplExplanation()
            )
          ),
          question(
            "timeout",
            trf.insufficientMaterial.txt(),
            p(
              trf.lichessFollowFIDErules(a(href := fideHandbookUrl)(trf.fideHandbookX("§6.9")))
            )
          ),
          question(
            "en-passant",
            trf.discoveringEnPassant.txt(),
            p(
              trf.explainingEnPassant(
                a(href := "https://en.wikipedia.org/wiki/En_passant")(trf.goodIntroduction()),
                a(href := fideHandbookUrl)(trf.fideHandbook()),
                a(href := s"${routes.Learn.index}#/15")(trf.lichessTraining())
              )
            ),
            p(
              trf.watchIMRosenCheckmate(
                a(href := "https://www.reddit.com/r/AnarchyChess/comments/p9wuic/eric_rosen_ascending/")(
                  "en passant"
                )
              )
            )
          ),
          question(
            "threefold",
            trf.threefoldRepetition.txt(),
            p(
              trf.threefoldRepetitionExplanation(
                a(href := "https://en.wikipedia.org/wiki/Threefold_repetition")(
                  trf.threefoldRepetitionLowerCase()
                ),
                a(href := fideHandbookUrl)(trf.fideHandbook())
              )
            ),
            h4(trf.notRepeatedMoves()),
            p(
              trf.repeatedPositionsThatMatters(
                em(trf.positions())
              )
            ),
            h4(trf.weRepeatedthreeTimesPosButNoDraw()),
            p(
              trf.threeFoldHasToBeClaimed(
                a(href := routes.Pref.form("game-behavior"))(trf.configure())
              )
            )
          ),
          h2(trf.accounts()),
          question(
            "titles",
            trf.titlesAvailableOnLichess.txt(),
            p(
              // Link cũ trỏ wiki của lichess-org/lila mô tả quy trình duyệt danh hiệu NỘI BỘ
              // CỦA LICHESS — người dùng HungKings đọc xong cũng không nộp được gì ở đó. Mà
              // HungKings có sẵn trang xác minh của mình: routes.TitleVerify.index, chính là
              // link dùng ở câu ngay dưới trong file này. Không xoá vế được vì khoá dịch
              // lichessRecognizeAllOTBtitles BẮT BUỘC 1 tham số (cùng bẫy với
              // ifYouWantToBroadcastClause2 ở trang Liên hệ), nên thay đích chứ không bỏ.
              trf.lichessRecognizeAllOTBtitles(
                a(href := routes.TitleVerify.index)(
                  trf.asWellAsManyNMtitles()
                )
              )
            ),
            ul(
              li("Grandmaster (GM)"),
              li("International Master (IM)"),
              li("FIDE Master (FM)"),
              li("Candidate Master (CM)"),
              li("Woman Grandmaster (WGM)"),
              li("Woman International Master (WIM)"),
              li("Woman FIDE Master (WFM)"),
              li("Woman Candidate Master (WCM)")
            ),
            p(
              trf.showYourTitle(
                a(href := routes.TitleVerify.index)(trf.verificationForm()),
                a(href := "#lm")("HungKings Master (LM)")
              )
            )
          ),
          question(
            "lm",
            trf.canIbecomeLM.txt(),
            p(strong(trf.noUpperCaseDot())),
            p(trf.lMtitleComesToYouDoNotRequestIt())
          ),
          question(
            "usernames",
            trf.whatUsernameCanIchoose.txt(),
            p(
              trf.usernamesNotOffensive(
                // Quy tắc đặt tên của HungKings nằm trong Điều khoản của chính mình,
                // không phải trang chính sách của Lichess.
                a(href := routes.Cms.tos)(trf.guidelines())
              )
            )
          ),
          question(
            "change-username",
            trf.canIChangeMyUsername.txt(),
            p(trf.usernamesCannotBeChanged.txt())
          ),
          // Bỏ mục "The Golden Zee": đó là cúp riêng của Lichess, trao cho đúng một
          // tài khoản Lichess (ZugAddict). Trên HungKings không ai giữ nó, nên để lại
          // là mô tả một giải thưởng không tồn tại và trỏ người đọc sang site khác.
          h2(trf.lichessRatings()),
          question(
            "ratings",
            trf.whichRatingSystemUsedByLichess.txt(),
            p(
              trf.ratingSystemUsedByLichess()
            ),
            p(
              a(href := cmsPageUrl("rating-systems"))("More about rating systems")
            )
          ),
          question(
            "provisional",
            trf.whatIsProvisionalRating.txt(),
            p(trf.provisionalRatingExplanation()),
            ul(
              li(
                trf.notPlayedEnoughRatedGamesAgainstX(
                  em(trf.similarOpponents())
                )
              ),
              li(
                trf.notPlayedRecently()
              )
            ),
            p(
              trf.ratingDeviationMorethanOneHundredTen()
            )
          ),
          question(
            "leaderboards",
            trf.howDoLeaderoardsWork.txt(),
            p(
              trf.inOrderToAppearsYouMust(
                a(href := routes.User.list)(trf.ratingLeaderboards())
              )
            ),
            ol(
              li(trf.havePlayedMoreThanThirtyGamesInThatRating()),
              li(trf.havePlayedARatedGameAtLeastOneWeekAgo()),
              li(
                trf.ratingDeviationLowerThanXinChessYinVariants(
                  standardRankableDeviation,
                  variantRankableDeviation
                )
              ),
              li(trf.beInTopTen())
            ),
            p(
              trf.secondRequirementToStopOldPlayersTrustingLeaderboards()
            )
          ),
          question(
            "high-ratings",
            trf.whyAreRatingHigher.txt(),
            p(
              trf.whyAreRatingHigherExplanation()
            ),
            p(
              a(href := cmsPageUrl("rating-systems"))("More about rating systems")
            )
          ),
          question(
            "hide-ratings",
            trf.howToHideRatingWhilePlaying.txt(),
            p(
              trf.enableZenMode(
                a(href := routes.Pref.form("game-display"))(trf.displayPreferences()),
                em("z")
              )
            )
          ),
          question(
            "disconnection-loss",
            trf.connexionLostCanIGetMyRatingBack.txt(),
            p(
              trf.weCannotDoThatEvenIfItIsServerSideButThatsRare()
            )
          ),
          h2(trf.howToThreeDots()),
          question(
            "browser-notifications",
            trf.enableDisableNotificationPopUps.txt(),
            p(
              img(
                src := assetUrl("images/connection-info.png"),
                alt := trf.viewSiteInformationPopUp.txt()
              )
            ),
            p(
              trf.lichessCanOptionnalySendPopUps()
            )
          ),
          question(
            "autoplay",
            trf.enableAutoplayForSoundsQ.txt(),
            p(trf.enableAutoplayForSoundsA()),
            h3("Mozilla Firefox (", trf.desktop(), ")"),
            p(trf.enableAutoplayForSoundsFirefox()),
            h3("Google Chrome (", trf.desktop(), ")"),
            p(trf.enableAutoplayForSoundsChrome()),
            h3("Safari (", trf.desktop(), ")"),
            p(trf.enableAutoplayForSoundsSafari()),
            h3("Microsoft Edge (", trf.desktop(), ")"),
            p(trf.enableAutoplayForSoundsMicrosoftEdge())
          ),
          question(
            "make-a-bot",
            if isVi then "Tự làm bot cho HungKings?" else "Make a HungKings bot?",
            // Hai liên kết này là tài liệu của Lichess. HungKings dùng chung bot API
            // của lila nên hướng dẫn vẫn đúng nguyên văn — nhưng phải nói rõ đó là tài
            // liệu của Lichess, đừng để anchor "HungKings bot" trỏ sang site người ta.
            if isVi then
              p(
                "HungKings dùng chung bot API với Lichess, nên tài liệu của họ áp dụng được ",
                "nguyên văn ở đây: đọc ",
                a(href := "https://lichess.org/@/thibault/blog/how-to-create-a-lichess-bot/FuKyvDuB")(
                  "cách tạo một bot"
                ),
                " và ",
                a(href := "https://lichess.org/blog/WvDNticAAMu_mHKP/welcome-lichess-bots")(
                  "bài giới thiệu bot"
                ),
                ". Chỉ cần trỏ bot vào máy chủ này thay vì lichess.org."
              )
            else
              p(
                "HungKings uses the same bot API as Lichess, so their documentation applies ",
                "here unchanged: read ",
                a(href := "https://lichess.org/@/thibault/blog/how-to-create-a-lichess-bot/FuKyvDuB")(
                  "how to create a bot"
                ),
                " and the ",
                a(href := "https://lichess.org/blog/WvDNticAAMu_mHKP/welcome-lichess-bots")(
                  "bot announcement"
                ),
                ". Point the bot at this server instead of lichess.org."
              )
          ),
          question(
            "stop-chess-addiction",
            trf.stopMyselfFromPlaying.txt(),
            p(
              trf.adviceOnMitigatingAddiction(
                a(href := "https://getcoldturkey.com")("ColdTurkey"),
                a(href := "https://freedom.to")("Freedom"),
                a(href := "https://www.proginosko.com/leechblock")("LeechBlock"),
                // Hai mục này vốn là LINK sang Lichess và cả hai đều vô dụng ở đây:
                // lichess.org/page/userstyles là trang CMS của họ (HungKings không có khoá
                // đó — /page/userstyles đo được 404), còn lichess.fewer-pools.user.css là
                // userstyle viết riêng cho tên miền lichess.org nên cài vào cũng không ăn
                // trên HungKings. Khoá adviceOnMitigatingAddiction BẮT BUỘC đủ 6 tham số
                // nên không bỏ vế được — giữ nguyên chữ, chỉ gỡ thẻ <a>. Câu vẫn đọc trôi,
                // và không còn hứa với người dùng một thứ bấm vào là hỏng.
                trf.lichessUserstyles(),
                trf.fewerLobbyPools(),
                a(href := "https://icd.who.int/browse/2024-01/mms/en#1448597234")(trf.mentalHealthCondition())
              )
            )
          )
        )
