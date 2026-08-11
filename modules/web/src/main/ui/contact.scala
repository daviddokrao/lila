package lila.web
package ui

import lila.core.i18n.{ I18nKey as trans, Translate }
import lila.core.id.ForumCategId
import lila.ui.*

import ScalatagsTemplate.{ *, given }

object contact:

  import trans.contact.*
  import navTree.*
  import navTree.Node.*

  def contactEmailLink(email: String)(using Translate) =
    bits.contactEmailLinkEmpty(email)(trans.site.clickToRevealEmailAddress())

  // HungKings — "đường thứ tư": vài nhánh của cây Liên hệ chưa bao giờ có khoá dịch ở
  // upstream nên hiện tiếng Anh giữa một trang tiếng Việt. Rẽ tại chỗ, KHÔNG thêm khoá.
  private def viEn(vi: String, en: String)(using t: Translate): String =
    if t.lang.language == "vi" then vi else en

  def apply(contactEmail: EmailAddress, forumEnabled: Boolean, broadcastEnabled: Boolean)(using
      Translate
  ): Frag =
    frag(
      h1(cls := "box__top")(contactLichess()),
      div(cls := "nav-tree")(renderNode(menu(contactEmail, forumEnabled, broadcastEnabled), none))
    )

  private def reopenLeaf(prefix: String)(using Translate) =
    Leaf(
      s"$prefix-reopen",
      wantReopen(),
      frag(
        p(a(href := routes.Account.reopen)(reopenOnThisPage())),
        p(doNotAskByEmailToReopen())
      )
    )

  // Mọi kênh ở đây phải là kênh của HungKings. Bản fork vốn trỏ thẳng sang hạ tầng
  // Lichess (github lichess-org/lila + lichess-org/mobile, discord.gg/lichess) => người
  // dùng HungKings gặp lỗi lại đi báo cho dự án khác. Đã gỡ cả ba 04/08 theo David.
  // Đã cân nhắc và LOẠI kho GitHub riêng: `daviddokrao/lila` TẮT tab Issues, nên
  // `/issues` trả 200 (xem được) nhưng `/issues/new` trả 404 (không nộp được) — link
  // trông sống mà thực chất là ngõ cụt. Muốn bật lại kênh này phải bật Issues TRƯỚC.
  // Kênh "app mobile" gỡ HẲN: HungKings không có app mobile.
  private def howToReportBugs(contactEmail: EmailAddress, forumEnabled: Boolean)(using Translate): Frag =
    frag(
      ul(
        // Diễn đàn tạm tắt => /forum/hungkings-feedback trả 404, đừng mời người dùng bấm.
        forumEnabled.option(
          li(
            a(href := routes.ForumCateg.show(ForumCategId("hungkings-feedback")))(reportBugInForum())
          )
        ),
        li(sendEmailAt(contactEmailLink(contactEmail.value)))
      ),
      p(howToReportBug())
    )

  def menu(contactEmail: EmailAddress, forumEnabled: Boolean, broadcastEnabled: Boolean)(using
      Translate
  ): Branch =
    Branch(
      "root",
      whatCanWeHelpYouWith(),
      List(
        Branch(
          "login",
          iCantLogIn(),
          List(
            Leaf(
              "email-confirm",
              noConfirmationEmail(),
              p(
                a(href := routes.Account.emailConfirmHelp)(visitThisPage()),
                "."
              )
            ),
            Leaf(
              "forgot-password",
              forgotPassword(),
              p(
                a(href := routes.Auth.passwordReset)(visitThisPage()),
                "."
              )
            ),
            Leaf(
              "forgot-username",
              forgotUsername(),
              p(
                a(href := routes.Auth.login)(youCanLoginWithEmail()),
                "."
              )
            ),
            Leaf(
              "lost-2fa",
              lost2FA(),
              p(a(href := routes.Auth.passwordReset)(doPasswordReset()), ".")
            ),
            reopenLeaf("login")
          )
        ),
        Branch(
          "account",
          accountSupport(),
          List(
            Leaf(
              "title",
              wantTitle(),
              p(
                a(href := routes.TitleVerify.index)(visitTitleConfirmation()),
                "."
              )
            ),
            Leaf(
              "close",
              wantCloseAccount(),
              frag(
                p(a(href := routes.Account.close)(closeYourAccount()), "."),
                p(doNotAskByEmail())
              )
            ),
            reopenLeaf("account"),
            Leaf(
              "change-username",
              wantChangeUsername(),
              frag(
                p(a(href := routes.Account.username)(changeUsernameCase()), "."),
                p(cantChangeMore()),
                p(orCloseAccount())
              )
            ),
            Leaf(
              "clear-history",
              wantClearHistory(),
              frag(
                p(cantClearHistory()),
                p(orCloseAccount())
              )
            )
          )
        ),
        Leaf(
          "report",
          wantReport(),
          frag(
            p(
              a(href := routes.Report.form)(toReportAPlayerUseForm()),
              "."
            ),
            p(
              youCanAlsoReachReportPage(
                // HungKings a11y `button-name`: nut nay chi co bieu tuong. Day chi la HINH
                // MINH HOA cho cau "bam nut hinh tam giac o goc trang" chu khong bam duoc,
                // nen aria-hidden moi dung — dat ten cho no la moi nguoi di tim mot nut khong
                // ton tai.
                button(
                  cls := "thin button button-empty",
                  dataIcon := Icon.CautionTriangle,
                  aria("hidden") := "true"
                )
              )
            ),
            p(
              doNotMessageModerators(),
              br,
              doNotReportInForum(),
              br,
              doNotSendReportEmails(),
              br,
              onlyReports()
            )
          )
        ),
        Branch(
          "bug",
          wantReportBug(),
          List(
            Leaf(
              "enpassant",
              illegalPawnCapture(),
              frag(
                p(calledEnPassant()),
                p(a(href := "/learn#/15")(tryEnPassant()))
              )
            ),
            Leaf(
              "castling",
              illegalCastling(),
              frag(
                p(castlingPrevented()),
                p(a(href := "https://en.wikipedia.org/wiki/Castling#Requirements")(castlingRules()), "."),
                p(a(href := "/learn#/14")(tryCastling()), "."),
                p(castlingImported())
              )
            ),
            Leaf(
              "insufficient",
              insufficientMaterial(),
              frag(
                p(a(href := fideHandbookUrl)(fideMate()), "."),
                p(knightMate())
              )
            ),
            Leaf(
              "casual",
              noRatingPoints(),
              frag(
                p(ratedGame()),
                botRatingAbuse()
              )
            ),
            Leaf(
              "error-page",
              errorPage(),
              frag(
                p(reportErrorPage()),
                howToReportBugs(contactEmail, forumEnabled)
              )
            ),
            Leaf(
              "security",
              viEn("Lỗ hổng bảo mật", "Security vulnerability"),
              // Trước đây trỏ security policy của lichess-org => lỗ hổng của HungKings
              // báo về đội bảo mật Lichess. Nay về hòm thư đã cấu hình (net.email).
              p(sendEmailAt(contactEmailLink(contactEmail.value)))
            ),
            Leaf(
              "other-bug",
              viEn("Lỗi khác", "Other bug"),
              frag(
                p(viEn("Nếu bạn tìm ra một lỗi mới, hãy báo cho chúng tôi:", "If you found a new bug, you may report it:")),
                howToReportBugs(contactEmail, forumEnabled)
              )
            )
          )
        ),
        frag(
          p(doNotMessageModerators()),
          p(sendAppealTo(a(href := routes.Appeal.home)(routes.Appeal.home.url))),
          p(
            falsePositives(),
            br,
            ifLegit()
          )
        ).pipe { appealBase =>
          Branch(
            "appeal",
            banAppeal(),
            List(
              Leaf(
                "appeal-cheat",
                engineAppeal(),
                frag(
                  appealBase,
                  p(
                    accountLost(),
                    br,
                    doNotDeny()
                  )
                )
              ),
              Leaf(
                "appeal-other",
                otherRestriction(),
                appealBase
              )
            )
          )
        },
        Branch(
          "collab",
          collaboration(),
          List(
            Leaf(
              "gdpr",
              viEn("Xoá dữ liệu theo GDPR", "GDPR erasure"),
              p(
                viEn("Bạn có quyền yêu cầu ", "You may request the "),
                a(href := routes.Account.delete)(
                  viEn(
                    "xoá hoàn toàn tài khoản HungKings của bạn.",
                    "complete deletion of your HungKings account."
                  )
                )
              )
            ),
            Leaf(
              "dmca",
              viEn("Thông báo gỡ bỏ theo DMCA / quyền sở hữu trí tuệ", "DMCA / Intellectual Property Take Down Notice"),
              p(
                a(href := "/dmca")(viEn("Điền vào biểu mẫu này", "Complete this form")),
                " ",
                viEn(
                  "nếu bạn là chủ sở hữu bản quyền gốc, hoặc là người đại diện được uỷ quyền, và cho rằng HungKings đang lưu trữ tác phẩm mà bạn giữ bản quyền.",
                  "if you are the original copyright holder, or an agent acting on behalf of the copyright holder, and believe HungKings is hosting work(s) you hold the copyright to."
                )
              )
            ),
          ) ::: broadcastEnabled.option(
            // Broadcast ẩn toàn site từ 06/08 (LILA_BROADCAST=false) — còn mời người dùng
            // "tôi muốn tiếp sóng giải đấu" là chỉ đường vào tính năng 404. Bật lại cờ là
            // node tự quay về.
            Leaf(
              "contact-broadcast",
              broadcastTournamentOnLichess(),
              frag(
                p(ifYouWantToBroadcastClause1()),
                // Dùng địa chỉ đã cấu hình (net.email) chứ không phải hòm thư
                // broadcast@lichess.org của Lichess — thư gửi tới đó không ai
                // ở HungKings đọc được.
                // Bỏ luôn ifYouWantToBroadcastClause2: chuỗi đó BẮT BUỘC 2 tham số
                // ("...at %1$s or on %2$s") mà tham số thứ hai là Discord của Lichess.
                // Dùng sendEmailAt (khoá đã có sẵn) thay vì thêm khoá mới — ranh giới P0.8.
                p(sendEmailAt(contactEmailLink(contactEmail.value)))
              )
            )
          ).toList ::: List(
            Leaf(
              "authorize",
              authorizationToUse(),
              frag(
                p(welcomeToUse()),
                p(videosAndBooks()),
                p(creditAppreciated())
              )
            ),
            Leaf(
              "monetize",
              monetizing(),
              frag(
                p(monetiseNotInterested()),
                p(
                  monetiseNoAdsTrackingOrTraffic()
                ),
                p(monetiseNoMarketingEmail()),
                br,
                p(
                  monetiseEncourageEveryoneTo(a(href := "/ads")(monetiseBlockAllAdsAndTrackers()))
                )
              )
            ),
            Leaf(
              "buy",
              buyingLichess(),
              p(
                viEn(
                  "Chúng tôi không bán, cho bất kỳ ai, với bất kỳ giá nào. Không bao giờ.",
                  "We are not selling, to anyone, for any price. Ever."
                )
              )
            ),
            Leaf(
              "contact-other",
              noneOfTheAbove(),
              frag(
                p(sendEmailAt(contactEmailLink(contactEmail.value))),
                p(explainYourRequest())
              )
            )
          )
        )
      )
    )
