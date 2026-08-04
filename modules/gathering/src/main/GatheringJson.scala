package lila.gathering

import play.api.libs.json.*
import chess.format.Fen
import lila.common.Json.given

object GatheringJson:

  // scalachess ghi cứng "https://lichess.org/opening/<tên>" trong StartingPosition.url, nên MỌI
  // giải đấu có thế cờ mở đầu (arena lẫn swiss) đều phát ra một liên kết sang Lichess. scalachess
  // là dependency lấy qua JitPack — fork nó là gánh merge upstream vĩnh viễn cho cả scalachess lẫn
  // lila (xem HANDOFF, mục "hoãn luật chơi riêng"), nên chặn ngay tại chỗ dùng thay vì sửa ở đó.
  //
  // Cắt lấy đúng phần "/opening/<tên>" và trỏ về chính HungKings: định dạng tên trùng khớp, đã đo
  // /opening/Kings_Gambit_Declined_Falkbeer_Countergambit = 200 và /opening/Vienna_Game = 200.
  // Nếu upstream đổi định dạng url thì lùi về trang /opening — TUYỆT ĐỐI không trả lại lichess.org,
  // vì một fallback "giữ nguyên giá trị cũ" sẽ âm thầm dựng lại đúng lỗi này mà không ai thấy.
  private def openingUrl(rawUrl: String): String =
    rawUrl.indexOf("/opening/") match
      case -1 => "/opening"
      case i => rawUrl.substring(i)

  def position(fen: Fen.Standard): JsObject =
    Thematic.byFen(fen) match
      case Some(pos) =>
        Json
          .obj(
            "eco" -> pos.eco,
            "name" -> pos.name,
            "fen" -> pos.fen,
            "url" -> openingUrl(pos.url.toString)
          )
      case None =>
        Json
          .obj(
            "name" -> "Custom position",
            "fen" -> fen
          )
