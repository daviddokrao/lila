package lila.web
package ui

import lila.ui.*
import lila.ui.ScalatagsTemplate.*
import lila.core.i18n.Translate

object bits:

  object splitNumber extends NumberHelper:
    private val NumberFirstRegex = """(\d++)\s(.+)""".r
    private val NumberLastRegex = """\s(\d++)$""".r.unanchored

    def apply(s: Frag)(using ctx: Context)(using Translate): Frag =
      if ctx.blind then s
      else
        val rendered = s.render
        rendered match
          case NumberFirstRegex(number, html) =>
            frag(
              strong((~number.toIntOption).localize),
              br,
              raw(html)
            )
          case NumberLastRegex(n) if rendered.length > n.length + 1 =>
            frag(
              raw(rendered.dropRight(n.length + 1)),
              br,
              strong((~n.toIntOption).localize)
            )
          case h => raw(h.replaceIf('\n', "<br>"))

  // Chỉ hiện khi net.stage.banner = true (mặc định false, bản demo không bật).
  // Upstream dùng nó để đẩy người dùng từ site thử sang site thật; HungKings không
  // có site thứ hai nên biển chỉ nói đây là bản thử, không dẫn đi đâu khác.
  lazy val stage = a(
    href := "/",
    style := """
background: #7f1010;
color: #fff;
position: fixed;
bottom: 0;
left: 0;
padding: .5em 1em;
border-top-right-radius: 3px;
z-index: 99;
"""
  ):
    "This is a HungKings preview website"

  // Khối này hiện ngay trên TRANG CHỦ. Upstream liệt kê Mastodon/Discord/Bluesky/
  // YouTube/Twitch của Lichess; để nguyên dưới thương hiệu HungKings là dẫn người
  // dùng sang cộng đồng của người khác mà làm như của mình. Đã gỡ, chỉ giữ GitHub
  // trỏ về kho nguồn thật của bản fork — AGPL-3.0 buộc phải công khai nó.
  // Thêm lại từng mục khi HungKings có tài khoản thật trên các nền tảng đó.
  val connectLinks: Frag = div(cls := "connect-links")(
    a(
      href := "https://github.com/daviddokrao/lila",
      targetBlank,
      noFollow
    )("GitHub")
  )

  // Hiện ở trang cấp quyền OAuth và trang đăng nhập Take Take Take. Trước đây vẫn là
  // logo con mã của Lichess — thương hiệu của tổ chức khác, đặt cạnh chữ "HungKings"
  // thì sai hẳn. Nay là mark tam giác dùng chung của hệ sinh thái, cùng hình với
  // public/logo/lichess.svg (tên tệp giữ nguyên để khỏi phá manifest asset).
  //
  // fill/stroke đặt thẳng trên <path>: CSS ở 3 tệp scss gán màu cho <svg>, mà giá trị
  // KẾ THỪA thua giá trị đặt trực tiếp trên phần tử con, nên nét vẫn ăn theo màu chữ
  // trong khi ruột tam giác không bị tô đặc.
  val logo = raw:
    """<svg class="lichess-logo-svg" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 48 48"><path d="M 7.55 14.5 L 40.45 14.5 L 24 43 Z" fill="none" stroke="currentColor" stroke-width="4.25" stroke-linejoin="miter" stroke-linecap="butt"/></svg>"""
