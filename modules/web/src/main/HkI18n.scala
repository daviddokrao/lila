package lila.web

import play.api.libs.json.{ JsObject, JsString, Json }
import java.nio.file.{ Files, Paths }

/**
 * HkI18n — chuỗi riêng của HungKings, tra theo ngôn ngữ, nạp từ file JSON NGOÀI image.
 *
 * MỤC TIÊU (NANG-CAP §P2.1): thêm một ngôn ngữ = thêm MỘT file JSON, **0 vòng compile**.
 * Vòng compile duy nhất của cả lộ trình i18n chính là lần thêm file này.
 *
 * KHOÁ TRA CỨU LÀ CHÍNH CHUỖI TIẾNG ANH — và đó là một quyết định có chủ đích, khác đặc tả
 * gốc ("đổi 54 call site sang `t("key")`"). Lý do, đo được:
 *  - Số call site thật là **116**, không phải 54 (đếm 20/08 ở `homeV2.scala` + `layout.scala`).
 *  - Mỗi lỗi trong lila tốn **13–20 phút** build qua CI, và không compile được tại chỗ.
 *    Đổi 116 chỗ sang khoá tay là 116 cơ hội gõ nhầm, mà nhầm khoá thì **không** làm build đỏ:
 *    nó lặng lẽ hiển thị sai chuỗi — đúng loại lỗi tệ nhất.
 *  - Giữ nguyên `t(vi, en)` tại chỗ ⇒ diff HTML trước/sau refactor = **0 byte** khi chưa có
 *    file JSON nào, tức điều kiện qua "render đúng như trước" được bảo đảm bằng CẤU TRÚC chứ
 *    không phải bằng việc rà tay 116 chỗ.
 *  - Vẫn đạt đúng mục tiêu: `fr.json` = `{"Play now": "Jouer"}` là đủ để có tiếng Pháp.
 * Đánh đổi đã biết: sửa chuỗi tiếng Anh gốc = mất bản dịch của chuỗi đó (nó rơi về en cho tới
 * khi ai đó cập nhật file). Đây là mô hình của gettext; chọn nó vì hỏng theo hướng AN TOÀN.
 *
 * Nạp một lần cho mỗi ngôn ngữ rồi giữ trong bộ nhớ. Thả file mới vào thư mục thì cần deploy
 * lại (container khởi động lại) — **không** phải build lại image, cùng triết lý với các cờ env.
 * Thiếu thư mục, thiếu file, JSON hỏng: đều rơi về hành vi cũ, KHÔNG ném lỗi ra người dùng.
 */
object HkI18n:

  /** Đặt bằng env để trỏ tới thư mục bind-mount trên VPS. Mặc định hợp với compose hiện tại. */
  private val thuMuc: String = sys.env.getOrElse("LILA_HK_I18N_DIR", "/hk-i18n")

  private val cache = java.util.concurrent.ConcurrentHashMap[String, Map[String, String]]()

  private def doc(lang: String): Map[String, String] =
    val f = Paths.get(thuMuc, s"$lang.json")
    if !Files.isReadable(f) then Map.empty
    else
      scala.util
        .Try:
          Json.parse(Files.readString(f)).as[JsObject].value.collect { case (k, JsString(v)) =>
            k -> v
          }.toMap
        .getOrElse(Map.empty)

  private def bang(lang: String): Map[String, String] =
    Option(cache.get(lang)).getOrElse:
      val m = doc(lang)
      cache.put(lang, m)
      m

  /**
   * @param lang mã ngôn ngữ 2 ký tự của người dùng (`ctx.lang.language`)
   * @param vi   bản tiếng Việt viết tại chỗ (nguồn chính, vì site gốc là tiếng Việt)
   * @param en   bản tiếng Anh viết tại chỗ — cũng là KHOÁ tra cứu cho mọi ngôn ngữ khác
   */
  def apply(lang: String, vi: String, en: String): String =
    if lang == "vi" then bang("vi").getOrElse(en, vi)
    else bang(lang).getOrElse(en, en)
