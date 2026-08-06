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
    broadcastEnabled: Boolean
) extends DefaultHttpRequestHandler(() => router, errorHandler, configuration, filters)
    with lila.web.ResponseHeaders:

  override def routeRequest(request: RequestHeader): Option[Handler] =
    if request.method == "OPTIONS"
    then optionsHandler.some
    else if isForumPath(request) || isBroadcastPath(request) then forumOffHandler.some
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
