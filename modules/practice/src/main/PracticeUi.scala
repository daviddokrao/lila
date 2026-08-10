package lila.practice
package ui

import play.api.libs.json.*

import lila.ui.*
import lila.ui.ScalatagsTemplate.{ *, given }

final class PracticeUi(helpers: Helpers)(
    csp: Update[ContentSecurityPolicy],
    explorerAndCevalConfig: Context ?=> JsObject
):
  import helpers.{ *, given }
  import trans.learn as trl

  def show(us: UserStudy, data: JsonView.JsData)(using ctx: Context) =
    Page(us.practiceStudy.name.value)
      .css("analyse.practice")
      .i18n(_.study)
      .i18nOpt(ctx.speechSynthesis, _.nvui)
      .i18nOpt(ctx.blind, _.keyboardMove)
      .js(analyseNvuiTag)
      .js(
        PageModule(
          "analyse.study",
          Json.obj(
            "practice" -> data.practice,
            "study" -> data.study,
            "data" -> data.analysis
          ) ++ explorerAndCevalConfig
        )
      )
      .csp(csp)
      .flag(_.zoom):
        main(cls := "analyse")

  // HungKings — "đường thứ tư": rẽ theo ngôn ngữ ngay tại chỗ cho chuỗi hardcode tiếng
  // Anh của upstream, KHÔNG thêm khoá vào registry (ranh giới P0.8). Trang này trước đó
  // hiện 100% tiếng Anh trên bản tiếng Việt: tiêu đề tab, h1/h2, và lời mời đăng ký.
  private def viEn(vi: String, en: String)(using t: Translate): String =
    if t.lang.language == "vi" then vi else en

  // Tên + mô tả các bài luyện là DỮ LIỆU hiển thị thẳng ra trang, không đi qua khoá dịch
  // nào, nên bản tiếng Việt trước đó đọc nguyên tiếng Anh ("Pin it to win it", "Use the
  // fork, Luke"…). Dịch ở ĐÂY chứ không ở `PracticeSections` vì bên đó `name` sinh ra
  // `slug` nằm trong URL và `PracticeStructure` được cache dùng chung cho mọi ngôn ngữ —
  // dịch bên đó là làm URL đổi theo ngôn ngữ người xem. Tra theo study id nên đổi tên
  // tiếng Anh ở nguồn cũng không vỡ bản dịch.
  private val viStudy: Map[String, (String, String)] = Map(
    "BJy6fEDf" -> ("Chiếu hết bằng quân nhẹ I", "Những thế chiếu hết cơ bản"),
    "fE4k21MW" -> ("Các thế chiếu hết I", "Nhận ra thế cờ quen thuộc"),
    "8yadFPpU" -> ("Các thế chiếu hết II", "Nhận ra thế cờ quen thuộc"),
    "PDkQDt6u" -> ("Các thế chiếu hết III", "Nhận ra thế cờ quen thuộc"),
    "96Lij7wH" -> ("Các thế chiếu hết IV", "Nhận ra thế cờ quen thuộc"),
    "Rg2cMBZ6" -> ("Chiếu hết bằng quân nhẹ II", "Những thế chiếu hết khó"),
    "ByhlXnmM" -> ("Chiếu hết bằng Mã và Tượng", "Bài học tương tác"),
    "9ogFv8Ac" -> ("Đòn ghim", "Ghim được là thắng được"),
    "tuoBxVE5" -> ("Đòn xiên", "Ép quân lớn né ra để ăn quân đứng sau"),
    "Qj281y1p" -> ("Đòn chĩa đôi", "Một quân doạ hai mục tiêu cùng lúc"),
    "MnsJEWnI" -> ("Đòn mở tuyến", "Gồm cả chiếu mở tuyến"),
    "RUQASaZm" -> ("Chiếu đôi", "Một đòn cực mạnh"),
    "o734CNqp" -> ("Quân quá tải", "Chúng phải gánh quá nhiều việc"),
    "ITWY4GN2" -> ("Nước xen giữa", "Chen một nước trước khi đáp trả"),
    "9cKgYrHb" -> ("Thế bí nước đi", "Buộc phải đi, mà đi nước nào cũng hỏng"),
    "g1fxVZu9" -> ("Đòn cản đường", "Chen một quân vào giữa tuyến phòng thủ"),
    "s5pLU7Of" -> ("Đòn thí Tượng h7", "Đòn thí kinh điển \"Món quà Hy Lạp\""),
    "xebrDvFe" -> ("Ô then chốt", "Chiếm bằng được ô then chốt"),
    "pt20yRkT" -> ("Tốt hàng 7 cánh Xe", "Chống lại quân Hậu"),
    "MkDViieT" -> ("Tốt hàng 7 cánh Xe", "Khi Xe của bạn bị động"),
    "9c6GrCTk" -> ("Tàn cuộc Xe cơ bản", "Lucena và Philidor"),
    "Z1DKk4Rl" -> ("Tàn cuộc Xe thực chiến", "Tàn cuộc Xe với nhiều Tốt")
  )

  private val viSection: Map[String, String] = Map(
    "checkmates" -> "Chiếu hết",
    "fundamental-tactics" -> "Đòn chiến thuật cơ bản",
    "advanced-tactics" -> "Đòn chiến thuật nâng cao",
    "pawn-endgames" -> "Tàn cuộc Tốt",
    "rook-endgames" -> "Tàn cuộc Xe"
  )

  def index(data: lila.practice.UserPractice)(using ctx: Context) =
    Page(viEn("Luyện thế cờ", "Practice chess positions"))
      .css("bits.practice.index")
      .graph(
        title = viEn("Luyện thế cờ cùng HungKings", "Practice your chess"),
        description = viEn(
          "Học cách xử lý thuần thục những thế cờ hay gặp nhất.",
          "Learn how to master the most common chess positions"
        ),
        url = routeUrl(routes.Practice.index)
      ):
        main(cls := "page-menu force-ltr")(
          st.aside(cls := "page-menu__menu practice-side")(
            div(cls := "practice-side__header")(
              img(
                cls := "practice-side__decoration",
                alt := "Decorative image of a robotic golem",
                src := assetUrl("images/practice/robot-golem.svg")
              ),
              div(cls := "practice-side__title")(
                h1(trans.site.practice()),
                h2(viEn("giúp cờ của bạn hoàn hảo", "makes your chess perfect"))
              )
            ),
            div(cls := "progress")(
              div(cls := "text")(trl.progressX(s"${data.progressPercent}%")),
              div(cls := "bar", style := s"width: ${data.progressPercent}%")
            ),
            postForm(action := routes.Practice.reset)(
              if ctx.isAuth then
                (data.nbDoneChapters > 0).option(
                  submitButton(
                    cls := "ok-cancel-confirm",
                    title := trl.youWillLoseAllYourProgress.txt()
                  )(trl.resetMyProgress.txt())
                )
              else
                a(href := routes.Auth.signup)(
                  viEn("Đăng ký để lưu tiến độ", "Sign up to save your progress")
                )
            )
          ),
          div(cls := "page-menu__content practice-app")(
            data.structure.sections.map: section =>
              st.section(
                h2(
                  if ctx.lang.language == "vi" then viSection.getOrElse(section.id, section.name)
                  else section.name
                ),
                div(cls := "studies")(
                  section.studies.map: stud =>
                    val prog = data.progressOn(stud.id)
                    val stateClas =
                      if prog.complete then "done" else if prog.done > 0 then "ongoing" else "future";
                    a(
                      cls := s"study ${stateClas}",
                      href := routes.Practice.show(section.id, stud.slug, stud.id)
                    )(
                      ctx.isAuth.option(
                        span(cls := "ribbon-wrapper")(
                          span(cls := s"ribbon ${stateClas}")(
                            prog.done,
                            " / ",
                            prog.total
                          )
                        )
                      ),
                      iconTag(cls := stud.id),
                      span(cls := "text")(
                        {
                          val tr = (ctx.lang.language == "vi").so(viStudy.get(stud.id.value))
                          frag(
                            h3(tr.fold(stud.name.value)(_._1)),
                            p(tr.fold(stud.desc)(_._2))
                          )
                        }
                      ),
                      (!prog.complete).option(div(cls := "attention-effect"))
                    )
                )
              )
          )
        )
