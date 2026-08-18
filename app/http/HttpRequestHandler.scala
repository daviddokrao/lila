package lila.app
package http

import play.api.http.{ DefaultHttpRequestHandler, HttpConfiguration, HttpErrorHandler }
import play.api.mvc.{ ControllerComponents, EssentialFilter, Handler, RequestHeader, Results }
import play.api.routing.Router

final class HttpRequestHandler(
    router: Router,
    errorHandler: HttpErrorHandler,
    configuration: HttpConfiguration,
    filters: Seq[EssentialFilter],
    controllerComponents: ControllerComponents,
    // HungKings: diễn đàn tạm tắt (env LILA_FORUM). Xem forumOffHandler bên dưới.
    forumEnabled: Boolean,
    // HungKings: broadcast tạm tắt (env LILA_BROADCAST). Cùng cơ chế chặn với forum.
    broadcastEnabled: Boolean,
    // HungKings: trang bảo trợ tạm tắt (env LILA_PATRON). Cùng cơ chế chặn.
    patronEnabled: Boolean,
    // HungKings: thư viện video tạm tắt (env LILA_VIDEO). Cùng cơ chế chặn.
    videoEnabled: Boolean,
    // HungKings: khám phá khai cuộc (/opening) cần explorer thật, mà explorer TẮT có chủ ý
    // trên bản gộp này (env LILA_EXPLORER, xem explorer.enabled trong WebConfig.analyseEndpoints).
    // Dùng lại ĐÚNG cờ explorer — không tạo cờ riêng — vì /opening sống hay chết phụ thuộc
    // 100% vào explorer có mặt hay không.
    openingEnabled: Boolean,
    // HungKings: danh bạ HLV người thật (/coach, env LILA_COACH_DIRECTORY). Cùng cơ chế chặn.
    coachEnabled: Boolean
) extends DefaultHttpRequestHandler(() => router, errorHandler, configuration, filters)
    with lila.web.ResponseHeaders:

  override def routeRequest(request: RequestHeader): Option[Handler] =
    if request.method == "OPTIONS"
    then optionsHandler.some
    else if isForumPath(request) || isBroadcastPath(request) || isPatronPath(request) ||
      isVideoPath(request) || isOpeningPath(request) || isCoachPath(request)
    then forumOffHandler.some
    else router.handlerFor(request)

  // Chặn ở ĐÂY chứ không rải guard vào 3 controller Forum*: một chỗ duy nhất phủ hết
  // 18 route (kể cả /diagnostic, vốn nằm trong ForumTopic) và không phải đụng module
  // forum, nên bật lại chỉ là đổi env. Diễn đàn ĐỘI (/forum/team-<id>) cũng nằm dưới
  // tiền tố này nên tắt theo — đúng ý "tắt tính năng forum".
  private def isForumPath(req: RequestHeader): Boolean =
    !forumEnabled && {
      val p = req.path
      p == "/forum" || p.startsWith("/forum/") || p.startsWith("/diagnostic")
    }

  // Cùng cơ chế: một chỗ phủ hết ~40 route RelayTour/RelayRound thay vì rải guard vào 2
  // controller. Phủ cả bản có tiền tố (`/api/broadcast`, `/api/stream/broadcast/`,
  // `/embed/broadcast/`) lẫn bản có tiền tố ngôn ngữ (`/vi/broadcast` — route
  // `/$lang<\w\w\w?>/broadcast`). `endsWith("/broadcast")` bắt cả `/broadcast` lẫn
  // `/<lang>/broadcast`; không route hợp lệ nào khác kết thúc bằng đúng segment này.
  private def isBroadcastPath(req: RequestHeader): Boolean =
    !broadcastEnabled && {
      val p = req.path
      p.endsWith("/broadcast") ||
      p.startsWith("/broadcast/") ||
      p.startsWith("/api/broadcast") ||
      p.startsWith("/api/stream/broadcast/") ||
      p.startsWith("/embed/broadcast/")
    }

  // Cùng cơ chế: 15 route của module plan (/patron*, /api/patron/*) + /features. Mọi nút
  // Donate đã gỡ từ Mốc D nên trang "trở thành Người bảo trợ" là ngõ cụt: nó mời góp tiền
  // mà không có cổng thanh toán nào phía sau, lại còn liệt kê tiền tệ như thể mua được.
  // /features cũng tắt theo vì nó là bảng SO SÁNH "tài khoản miễn phí vs Người bảo trợ" —
  // khoe một gói trả phí không tồn tại. Bật lại = LILA_PATRON=true + deploy, KHÔNG rebuild.
  private def isPatronPath(req: RequestHeader): Boolean =
    !patronEnabled && {
      val p = req.path
      p == "/patron" || p.startsWith("/patron/") ||
      p.startsWith("/api/patron/") ||
      p.endsWith("/features")
    }

  // Cùng cơ chế: 8 route /video. Thư viện này là bộ video do LICHESS tuyển chọn — 546 bài
  // "Opening" v.v., tiêu đề toàn tiếng Anh, và trang kéo ảnh thu nhỏ từ img.youtube.com.
  // Tức một trang nội dung không phải của mình, không dịch, lại thêm phụ thuộc ngoài, và
  // không có lối vào nào từ thanh điều hướng. `endsWith("/video")` bắt cả `/vi/video`.
  private def isVideoPath(req: RequestHeader): Boolean =
    !videoEnabled && {
      val p = req.path
      p.endsWith("/video") || p.contains("/video/")
    }

  // /opening (khám phá khai cuộc) trả 200 nhưng HỎNG khi explorer tắt: "Couldn't fetch the
  // next moves, try again later", toàn tiếng Anh giữa site tiếng Việt (đo trên live 18/08).
  // 6 route đều nằm dưới tiền tố /opening, không có bản lang-prefix nào trong conf/routes.
  private def isOpeningPath(req: RequestHeader): Boolean =
    !openingEnabled && {
      val p = req.path
      p == "/opening" || p.startsWith("/opening/")
    }

  // Cùng cơ chế: /coach (danh bạ HLV người thật). `endsWith("/coach")` bắt cả /coach,
  // /$lang/coach (Coach.homeLang) lẫn /upload/image/coach; `startsWith("/coach/")` bắt
  // /coach/edit, /coach/:username, /coach/:lang/:country/:order. Không đụng /hlv (AI coach,
  // tiền tố khác hẳn, không kết thúc bằng "/coach").
  private def isCoachPath(req: RequestHeader): Boolean =
    !coachEnabled && {
      val p = req.path
      p.endsWith("/coach") || p.startsWith("/coach/")
    }

  // Trả về đúng trang 404 của lila khi request đủ điều kiện hiện trang lỗi (xem
  // ErrorHandler.canShowErrorPage), còn lại là 404 văn bản. Không dùng Results.NotFound
  // thẳng vì như thế người dùng nhận một trang trắng không có chrome nào.
  //
  // Thông điệp để RỖNG có chủ ý: ErrorHandler lấy chuỗi này làm TIÊU ĐỀ trang, nên một
  // câu tiếng Anh kiểu "Forum is disabled" sẽ đứng chình ình trên trang tiếng Việt (đã
  // đo). Rỗng thì lila dùng trang "không tìm thấy" chuẩn, đã dịch sẵn.
  // `async` bị nạp chồng 3 lần trong ActionBuilder; viết đủ ngoặc + ghi rõ kiểu tham số
  // để suy diễn không phải chọn giữa các bản, tránh mất một vòng compile 20 phút.
  private val forumOffHandler =
    controllerComponents.actionBuilder.async((req: play.api.mvc.Request[play.api.mvc.AnyContent]) =>
      errorHandler.onClientError(req, play.api.http.Status.NOT_FOUND, "")
    )

  // should be handled by nginx in production
  private val optionsHandler =
    controllerComponents.actionBuilder: (req: RequestHeader) =>
      if lila.common.HTTPRequest.isApiOrApp(req)
      then Results.NoContent.withHeaders(optionsHeaders*)
      else Results.NotFound
