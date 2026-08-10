package views.base

import lila.app.UiEnv.{ *, given }

// HungKings — trang 404 là trang người dùng THẬT SỰ gặp (gõ sai URL, link cũ, và cả
// những tính năng ta cố ý tắt: /forum, /broadcast, /patron, /video đều rơi vào đây).
// Trước đó nó hiện 100% tiếng Anh giữa một site tiếng Việt. Rẽ theo ngôn ngữ tại chỗ —
// "đường thứ tư" — KHÔNG thêm khoá vào registry (ranh giới P0.8).
private def isVi(using ctx: Context): Boolean = ctx.lang.language == "vi"

def notFound(msg: Option[String])(using Context) =
  Page(msg | (if isVi then "Không tìm thấy trang" else "Page not found")).css("bits.not-found"):
    main(cls := "not-found page-small box box-pad")(
      header(
        h1("404"),
        div(
          strong(if isVi then "Không tìm thấy trang này!" else "Page not found!"),
          msg.map(em(_)),
          if isVi then
            p(
              "Mời bạn ",
              a(href := routes.Lobby.home)("về trang chủ"),
              span(cls := "or-play")(", hoặc chơi thử trò nhỏ này")
            )
          else
            p(
              "Return to ",
              a(href := routes.Lobby.home)("the homepage"),
              span(cls := "or-play")(", or play this mini-game")
            )
        )
      ),
      div(cls := "game")(
        iframe(
          src := staticAssetUrl(s"vendor/ChessPursuit/bin-release/index.html"),
          st.frameborder := 0,
          widthA := 400,
          heightA := 500,
          frame.credentialless
        ),
        p(cls := "credits")(
          a(href := "https://github.com/Saturnyn/ChessPursuit")("ChessPursuit"),
          " courtesy of ",
          a(href := "https://github.com/Saturnyn")("Saturnyn")
        )
      )
    )

def notFoundEmbed(msg: Option[String])(using ctx: EmbedContext) =
  val viE = ctx.lang.language == "vi"
  views.base.embed.site(
    title = msg | (if viE then "Không tìm thấy trang" else "Page not found"),
    cssKeys = List("bits.embed-not-found")
  )(
    main(cls := "not-found page-small box box-pad")(
      header(
        h1("404"),
        div(
          strong(if viE then "Không tìm thấy trang này!" else "Page not found!"),
          msg.map(em(_))
        )
      )
    )
  )
