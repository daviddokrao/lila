package views.onboarding

import lila.app.UiEnv.{ *, given }

// HungKings B2 (18/08): trang /bat-dau — 3 lựa chọn sau đăng ký (mới học / biết chơi rồi /
// chơi lâu rồi), thay vì thả thẳng vào khu thi đấu. Gác sau cờ LILA_ONBOARDING_CHOICE ở
// controllers.Main (mặc định TẮT = route 404, hành vi y hệt trước khi có trang này).
//
// KHÔNG hỏi tuổi/giới tính, KHÔNG hứa hệ số khởi điểm — ván với máy không tính hệ số trên
// bản này. CSS nằm trọn trong <style> nội tuyến bên dưới (namespace `.bat-dau-*`) để không
// phải mở một bundle SCSS mới hay đụng ui/lobby|round|site đang do agent khác giữ.
object page:

  // Song ngữ tại chỗ theo ctx.lang, không thêm key registry dịch (19' compile LangList) —
  // cùng khuôn `t(vi, en)` đã dùng ở homeV2.scala, đặt tên theo helper mà quy ước dự án gọi.
  private def HomeI18n(vi: String, en: String)(using ctx: Context): String =
    if ctx.lang.language == "vi" then vi else en

  // Inline vì CSP styleSrc đã có 'unsafe-inline' (xem ContentSecurityPolicy.scala) — cùng
  // khuôn thẻ <style id="bg-data"> mà page.scala đã dùng. Đo 380px tiếng Việt + reduced
  // motion + focus-visible ngay trong khối này.
  private val pageStyle = raw("""<style>
.bat-dau-wrap { max-width: 900px; margin: 0 auto; }
.bat-dau-lead { margin: 0 0 24px; font-size: 1.05rem; opacity: .85; }
.bat-dau-cards {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(230px, 1fr));
  gap: 16px;
  margin: 0 0 28px;
}
.bat-dau-card {
  display: block;
  padding: 20px;
  min-height: 44px;
  border: 1px solid rgba(128, 128, 128, .3);
  border-radius: 10px;
  text-decoration: none;
  color: inherit;
}
.bat-dau-card h2 { margin: 0 0 8px; font-size: 1.15rem; line-height: 1.3; }
.bat-dau-card p { margin: 0 0 16px; font-size: .95rem; opacity: .85; line-height: 1.4; }
.bat-dau-card .hv2-btn { pointer-events: none; }
.bat-dau-card:focus-visible, .bat-dau-skip:focus-visible {
  outline: 2px solid var(--c-primary, #3893e8);
  outline-offset: 2px;
}
@media (prefers-reduced-motion: no-preference) {
  .bat-dau-card { transition: transform .15s ease, border-color .15s ease; }
  .bat-dau-card:hover { transform: translateY(-2px); border-color: rgba(128, 128, 128, .6); }
}
.bat-dau-skip {
  display: inline-block;
  min-height: 44px;
  padding: 12px 4px;
  font-size: .95rem;
}
@media (max-width: 380px) {
  .bat-dau-card { padding: 16px; }
  .bat-dau-wrap h1 { font-size: 1.4rem; }
}
</style>""")

  def start(using ctx: Context) =
    Page(HomeI18n("Bắt đầu — HungKings", "Get started — HungKings"))
      .css("bits.page")
      .flag(_.noRobots)(
        main(cls := "page box box-pad bat-dau-wrap")(
          pageStyle,
          h1(HomeI18n("Bắt đầu hành trình cờ vua", "Start your chess journey")),
          p(cls := "bat-dau-lead")(
            HomeI18n(
              "Chọn đúng một lối để chúng tôi dẫn bạn đi tiếp — không có lựa chọn sai.",
              "Pick the one that fits — there's no wrong answer."
            )
          ),
          div(cls := "bat-dau-cards")(
            a(cls := "bat-dau-card", href := routes.Main.onboardingChoose("hoc"))(
              h2(HomeI18n("Tôi mới học cờ", "I'm new to chess")),
              p(
                HomeI18n(
                  "Học luật chơi và các bước cơ bản, từng bước một.",
                  "Learn the rules and the basics, step by step."
                )
              ),
              span(cls := "hv2-btn hv2-btn--gold")(HomeI18n("Bắt đầu học", "Start learning"))
            ),
            a(cls := "bat-dau-card", href := routes.Main.onboardingChoose("choi"))(
              h2(HomeI18n("Tôi biết chơi rồi", "I already know how to play")),
              p(
                HomeI18n(
                  "Chơi thử một ván với máy để làm quen bàn cờ.",
                  "Try a game against the computer to get a feel for the board."
                )
              ),
              span(cls := "hv2-btn hv2-btn--gold")(HomeI18n("Chơi với máy", "Play the computer"))
            ),
            a(cls := "bat-dau-card", href := routes.Main.onboardingChoose("thi"))(
              h2(HomeI18n("Tôi chơi lâu rồi", "I've been playing for years")),
              p(
                HomeI18n(
                  "Vào thẳng khu thi đấu: ghép cặp nhanh, tạo ván, thách đấu bạn bè.",
                  "Head straight to the arena: quick pairing, custom games, challenge a friend."
                )
              ),
              span(cls := "hv2-btn hv2-btn--gold")(HomeI18n("Vào khu thi đấu", "Go to the arena"))
            )
          ),
          a(cls := "bat-dau-skip", href := routes.Main.onboardingChoose("bo-qua"))(
            HomeI18n("Bỏ qua, để sau →", "Skip for now →")
          )
        )
      )
