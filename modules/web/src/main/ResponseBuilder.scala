package lila.web

import org.apache.pekko.stream.scaladsl.Source
import play.api.http.*
import play.api.libs.json.*
import play.api.mvc.*

import lila.memo.RateLimit.Limited

trait ResponseBuilder(using Executor)
    extends ControllerHelpers
    with ResponseWriter
    with ResponseHeaders
    with CtrlExtensions
    with CtrlErrors:

  export scalatags.Text.Frag

  given Conversion[Result, Fu[Result]] = fuccess(_)

  val rateLimitedMsg = "Too many requests. Try again later."
  val rateLimitedJson = TooManyRequests(jsonError(rateLimitedMsg))

  val jsonOkBody = Json.obj("ok" -> true)
  val jsonOkResult = JsonOk(jsonOkBody)
  def jsonOkMsg(msg: String) = JsonOk(Json.obj("ok" -> msg))

  def JsonOk(body: JsValue): Result = Ok(body).as(JSON)
  def JsonOk[A: Writes](body: A): Result = Ok(Json.toJson(body)).as(JSON)
  def JsonOk[A: Writes](fua: Fu[A]): Fu[Result] = fua.dmap(JsonOk)
  def JsonOptionOk[A: Writes](fua: Fu[Option[A]]) = fua.map(_.fold(notFoundJson())(JsonOk))
  def JsonStrOk(str: JsonStr): Result = Ok(str).as(JSON)
  def JsonBadRequest(body: JsValue): Result = BadRequest(body).as(JSON)
  def JsonBadRequest(msg: String): Result = JsonBadRequest(jsonError(msg))
  def JsonLimited(limited: Limited): Result = TooManyRequests(Json.toJson(limited)).as(JSON)

  def strToNdJson(source: Source[String, ?]): Result =
    Ok.chunked(source).as(ndJson.contentType).noProxyBuffer

  def jsToNdJson(source: Source[JsValue, ?]): Result =
    strToNdJson(ndJson.jsToString(source))

  def jsOptToNdJson(source: Source[Option[JsValue], ?]): Result =
    strToNdJson(ndJson.jsOptToString(source))

  def jsToNdJson(source: Seq[JsValue]): Result =
    Ok(source.map(Json.stringify).mkString("\n")).as(ndJson.contentType)

  /* We roll our own action, as we don't want to compose play Actions. */
  def action[A](parser: BodyParser[A])(handler: Request[A] ?=> Fu[Result]): EssentialAction = new:
    import play.api.libs.streams.Accumulator
    import org.apache.pekko.util.ByteString
    def apply(rh: RequestHeader): Accumulator[ByteString, Result] =
      parser(rh).mapFuture:
        case Left(r) => fuccess(r)
        case Right(a) => handler(using Request(rh, a))

  def redirectWithQueryString(path: String)(using req: RequestHeader) =
    Redirect:
      path + req.rawQueryString.nonEmptyOption.so("?" + _)

  // HungKings L11 (20/08) — bảng này chưa từng bị rà. Đo trên live trước khi sửa:
  //   /yt    → 301 sang kênh YouTube của Lichess
  //   /dmca  → 301 sang Google Form DMCA của Lichess  ⇒ người dùng HungKings gửi khiếu nại
  //            bản quyền vào form của một tổ chức khác. Đây là mục nặng nhất trong ba mục.
  //   /donate → 301 sang /patron, mà /patron ĐÃ TẮT có chủ ý (LILA_PATRON=false) ⇒ ngõ cụt.
  // Gỡ hai lối trỏ sang tài sản của người khác (404 tử tế còn hơn gửi người dùng đi nhầm
  // nhà), và cho `donate` về /contact — nơi duy nhất đang thật sự có người nhận thư.
  // `fishnet` giữ nguyên: đó là link tới PHẦN MỀM nguồn mở đang thật sự chạy sau lưng, không
  // phải kênh truyền thông của ai; giữ nó là ghi nguồn đúng, cùng tinh thần credit AGPL.
  private val movedMap: Map[String, String] = Map(
    "fishnet" -> "https://github.com/lichess-org/fishnet",
    "qa" -> "/faq",
    "help" -> "/contact",
    "support" -> "/contact",
    "donate" -> "/contact",
    "how-to-cheat" -> "/page/how-to-cheat"
  )
  def staticRedirect(key: String): Option[Fu[Result]] = movedMap.get(key).map { MovedPermanently(_) }
