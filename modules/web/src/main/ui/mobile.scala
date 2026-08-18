package lila.web
package ui

import play.api.mvc.RequestHeader

import lila.core.i18n.{ I18nKey as trans, Translate }
import lila.ui.*

import ScalatagsTemplate.{ *, given }

def mobileRedirect(using req: RequestHeader)(using Translate) =
  val callbackUrl = "org.lichess.mobile://login-callback" + req.rawQueryString.nonEmptyOption.so("?" + _)
  Page(trans.app.returningToApp.txt()).i18n(_.app):
    main(cls := "page-small box box-pad")(
      boxTop(
        h1(cls := "text")(trans.app.returningToApp())
      ),
      p(trans.app.ifAppDoesNotOpenAutomatically(trans.app.openTheApp())),
      a(href := callbackUrl, cls := "button")(trans.app.openTheApp())
    )

// HungKings: KHÔNG có app native (App Store / Google Play / bản phát hành GitHub) — dự án
// đã chốt không làm app native (xem "danh giới KHÔNG LÀM"). Trang /app cũ của upstream hứa
// cả ba thứ đó, đo được trên live 18/08, tức lời hứa sai với người dùng thật. Viết lại
// thành hướng dẫn cài PWA lên màn hình chính, "đường thứ ba" quen thuộc: rẽ ngôn ngữ NGAY
// TẠI CHỖ bằng Translate.lang, KHÔNG thêm khoá vào registry dịch (ranh giới P0.8), KHÔNG
// hardcode một thứ tiếng (site chạy 94 ngôn ngữ, người dùng en vẫn phải đọc được).
//
// `renderedCmsPage` (CMS key "mobile", nội dung sống trong MongoDB, sửa qua trang quản trị)
// CỐ Ý không hiển thị nữa: nội dung đó là bài mô tả app mobile gốc của Lichess, nhiều khả
// năng vẫn nhắc app store/GitHub releases mà code này không kiểm soát được. Tham số vẫn giữ
// nguyên để khớp chữ ký gọi từ Main.serveApp — chỉ đơn giản không render.
//
// Giữ nguyên `(using Translate)` như bản gốc (không đổi sang `Context`): `Translate` đã
// mang sẵn `.lang`, đủ để rẽ vi/en, và Page(...)/i18n(...) bên dưới cần đúng `Translate`
// trong scope — đổi sang Context sẽ mất given này (lila.ui.Context không tự suy ra Translate).
private def isVi(using t: Translate): Boolean = t.lang.language == "vi"

def mobile(helpers: Helpers)(renderedCmsPage: Frag)(using Translate) =
  import helpers.*

  val title =
    if isVi then "Cài lên màn hình chính" else "Add to your home screen"

  val intro =
    if isVi then
      "HungKings không có ứng dụng riêng trên App Store hay Google Play. Bạn có thể cài " +
        "thẳng trang web này lên màn hình chính máy — mở nhanh như một ứng dụng, không cần " +
        "tải gì thêm."
    else
      "HungKings has no separate app on the App Store or Google Play. You can install this " +
        "website straight to your home screen instead — it opens just like an app, with " +
        "nothing extra to download."

  val iosSteps = div(cls := "block")(
    h2(dataIcon := Icon.ShareIos, cls := "text")(
      if isVi then "Trên iPhone/iPad (Safari)" else "On iPhone/iPad (Safari)"
    ),
    ul(
      li(if isVi then "Mở HungKings bằng trình duyệt " else "Open HungKings in ", strong("Safari")),
      li(
        if isVi then "Bấm nút " else "Tap the ",
        strong(dataIcon := Icon.ShareIos, cls := "text")(if isVi then "Chia sẻ" else "Share")
      ),
      li(
        if isVi then "Chọn " else "Choose ",
        strong(dataIcon := Icon.PlusButton, cls := "text")(
          if isVi then "Thêm vào MH chính" else "Add to Home Screen"
        )
      ),
      li(if isVi then "Bấm " else "Tap ", strong(if isVi then "Thêm" else "Add"))
    )
  )

  val androidSteps = div(cls := "block")(
    h2(dataIcon := Icon.ShareAndroid, cls := "text")(
      if isVi then "Trên Android (Chrome)" else "On Android (Chrome)"
    ),
    ul(
      li(if isVi then "Mở HungKings bằng " else "Open HungKings in ", strong("Chrome")),
      li(
        if isVi then "Bấm menu " else "Tap the ",
        strong(dataIcon := Icon.Hamburger, cls := "text")(if isVi then "ba chấm" else "three-dot menu")
      ),
      li(
        if isVi then "Chọn " else "Choose ",
        strong(if isVi then "Cài đặt ứng dụng" else "Install app")
      ),
      li(if isVi then "Xác nhận " else "Confirm ", strong(if isVi then "Cài đặt" else "Install"))
    )
  )

  Page(title)
    .i18n(_.app)
    .css("bits.mobile")
    .hrefLangs(lila.ui.LangPath(routes.Main.app)):
      main(
        div(cls := "mobile page-small box box-pad")(
          h1(cls := "box__top")(title),
          div(cls := "sides")(
            div(cls := "left-side")(
              p(intro),
              iosSteps,
              androidSteps
            ),
            div(cls := "right-side")(
              img(
                widthA := "358",
                heightA := "766",
                cls := "mobile-playing",
                src := assetUrl("images/mobile/lichess-mobile-screen.webp"),
                alt := ""
              )
            )
          )
        )
      )
