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
    else router.handlerFor(request).orElse(boDauGachCuoi(request))

  /**
   * HungKings K1 (20/08): URL gõ tay kèm dấu `/` cuối → 301 về bản không dấu.
   *
   * Đo trên live: **13** đường trả 404 chỉ vì dấu gạch thừa, gồm cả 6 trang hub và ba lối
   * vào chính `/giai/` `/hlv/` `/diem/`. Gõ tay URL kèm `/` là hành vi rất thường.
   *
   * Vì sao ở ĐÂY chứ không thêm 13 dòng vào `conf/routes`: 13 dòng chỉ vá đúng 13 đường
   * đang biết, còn mọi route thêm sau này lại hỏng lại — mà chẳng ai nhớ ra để thêm dòng
   * thứ 14. Một chỗ phủ hết, kể cả route tương lai.
   *
   * Ba chốt an toàn, có chủ ý:
   *  1. **Chỉ chạy khi bản CÓ dấu `/` không có route.** Nên nó không bao giờ giẫm lên
   *     `/hlv-app/` `/diem-app/` `/realchess/` `/giai-app/` — bốn đường này CÓ route riêng
   *     và phải giữ dấu `/` (bỏ đi là đường dẫn tương đối của asset trong app con hỏng hết).
   *  2. **Chỉ chuyển khi bản KHÔNG dấu có route thật.** URL rác vẫn 404 đúng như trước,
   *     không biến 404 thành một vòng chuyển hướng vô nghĩa.
   *  3. **Chỉ GET/HEAD.** 301 một POST là làm mất thân yêu cầu.
   *
   * Dùng 301 (không phải 200 cùng nội dung) để hai URL không thành nội dung trùng lặp trước
   * Google — cùng lập luận đã dùng khi cho www 301 về apex.
   */
  private def boDauGachCuoi(req: RequestHeader): Option[Handler] =
    val p = req.path
    // KHÔNG mở điều kiện `if` bằng dấu ngoặc: Scala 3 sẽ đọc `if (a) b` theo cú pháp CŨ và
    // hiểu phần sau `||` là nhánh `then`. Đặt tên biến trước là hết mơ hồ — rẻ hơn nhiều so
    // với một vòng build 20 phút để biết mình gõ sai dấu ngoặc.
    val laGetHead = req.method == "GET" || req.method == "HEAD"
    val goc = p.reverse.dropWhile(_ == '/').reverse
    val query = if req.rawQueryString.isEmpty then "" else "?" + req.rawQueryString
    val dich = goc + query
    // Dựng request thử với ĐỦ CẢ BA phần (uriString, path, queryString), không chỉ `withPath`.
    // Đo trên live sau lần deploy đầu: `withPath` một mình đủ cho route CHỮ THƯỜNG (`/choi/`,
    // `/contact/`, cả `/tv/`) nhưng KHÔNG đủ cho route dạng tham số có ràng buộc regex —
    // `/$key<privacy|thanks|about|ads|changelog>` — nên đúng 5 trang CMS `/about/` `/privacy/`
    // `/thanks/` `/ads/` `/changelog/` vẫn 404 trong khi 12 đường kia đã 301. Bộ so khớp của
    // Play đọc phần đường dẫn THÔ từ `uriString` khi rút tham số động, mà `withPath` không
    // cập nhật trường đó. Dựng đủ ba phần là hết phân biệt.
    val thu = req.withTarget(play.api.mvc.request.RequestTarget(dich, goc, req.queryString))
    // ⚠️ GIỚI HẠN CỦA CHỖ NÀY, đo bằng log tạm ngày 20/08 (K11): hàm này chỉ chạy khi router
    // KHÔNG tìm được handler nào (`orElse` ở trên). Mà route CUỐI của lila là
    // đường bắt-tất-cả cuối `conf/routes` (`*path`) → `User.redirect` — BẮT TẤT CẢ.
    // (Đừng viết liền gạch chéo với dấu sao trong comment Scala: comment LỒNG NHAU được,
    //  nên chuỗi đó mở một comment con không đóng và giết cả file.) Log của chính lila:
    //     `404 GET /about/  User.redirect`   ← có handler ⇒ hàm này không hề chạy
    //     `301 GET /choi/   NoHandler`       ← không handler ⇒ hàm này chạy, 301 đúng
    // Nên đừng sửa thêm ở đây khi thấy một đường vẫn 404 kèm dấu `/`: phần rơi vào catch-all
    // được xử lý ở `controllers.User.boDauGachCuoi`. Hai chỗ, hai đường vào, cùng kết quả.
    if !laGetHead || p.length <= 1 || !p.endsWith("/") || goc.isEmpty then None
    else if router.handlerFor(thu).isEmpty then None
    else
      Some(controllerComponents.actionBuilder { (_: RequestHeader) =>
        Results.MovedPermanently(dich)
      })

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
