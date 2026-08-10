package lila.practice

import lila.core.study.data.StudyName

private object PracticeSections:

  // HungKings — BỎ 12 BÀI TRỎ VÀO STUDY KHÔNG TỒN TẠI.
  //
  // Danh sách gốc của Lichess có 32 bài, nhưng bộ study đi kèm bản seed chỉ có 20 trong
  // số đó. 12 bài còn lại (X-Ray, Deflection, Attraction, Underpromotion, Desperado,
  // Counter Check, Undermining, Clearance, Opposition, Basic/Intermediate/Practical Rook
  // Endings) vẫn render ra thẻ bấm được nhưng đi tới **404** — tức hơn một phần ba trang
  // "Luyện thế cờ" là link chết. Đã đo trên bản live:
  //   /practice/advanced-tactics/deflection/kdKpaYLW      -> 404
  //   /practice/fundamental-tactics/the-pin/9ogFv8Ac      -> 200
  // Thay hai bài tàn cuộc Xe bằng đúng hai study CÓ THẬT trong DB (9c6GrCTk, Z1DKk4Rl).
  // Có study thật cho bài nào thì thêm lại bài đó vào đây.
  //
  // Tên/mô tả ở đây GIỮ TIẾNG ANH có chủ ý: `PracticeStudy.slug` sinh từ `name` và slug
  // nằm trong URL, còn `PracticeStructure` thì được cache DÙNG CHUNG cho mọi ngôn ngữ.
  // Dịch ở đây là làm URL đổi theo ngôn ngữ người xem. Bản dịch nằm ở tầng view
  // (`PracticeUi.viName` / `viDesc`), tra theo study id.

  val list = List(
    PracticeSection(
      name = "Checkmates",
      id = "checkmates",
      studies = List(
        study("BJy6fEDf", "Piece Checkmates I", "Basic checkmates"),
        study("fE4k21MW", "Checkmate Patterns I", "Recognize the patterns"),
        study("8yadFPpU", "Checkmate Patterns II", "Recognize the patterns"),
        study("PDkQDt6u", "Checkmate Patterns III", "Recognize the patterns"),
        study("96Lij7wH", "Checkmate Patterns IV", "Recognize the patterns"),
        study("Rg2cMBZ6", "Piece Checkmates II", "Challenging checkmates"),
        study("ByhlXnmM", "Knight & Bishop Mate", "Interactive lesson")
      )
    ),
    PracticeSection(
      name = "Fundamental Tactics",
      id = "fundamental-tactics",
      studies = List(
        study("9ogFv8Ac", "The Pin", "Pin it to win it"),
        study("tuoBxVE5", "The Skewer", "Yum - skewers!"),
        study("Qj281y1p", "The Fork", "Use the fork, Luke"),
        study("MnsJEWnI", "Discovered Attacks", "Including discovered checks"),
        study("RUQASaZm", "Double Check", "A very powerful tactic"),
        study("o734CNqp", "Overloaded Pieces", "They have too much work"),
        study("ITWY4GN2", "Zwischenzug", "In-between moves")
      )
    ),
    PracticeSection(
      name = "Advanced Tactics",
      id = "advanced-tactics",
      studies = List(
        study("9cKgYrHb", "Zugzwang", "Being forced to move"),
        study("g1fxVZu9", "Interference", "Interpose a piece to great effect"),
        study("s5pLU7Of", "Greek Gift", "Study the greek gift sacrifice")
      )
    ),
    PracticeSection(
      name = "Pawn Endgames",
      id = "pawn-endgames",
      studies = List(
        study("xebrDvFe", "Key Squares", "Reach a key square"),
        study("pt20yRkT", "7th-Rank Rook Pawn", "Versus a Queen")
      )
    ),
    PracticeSection(
      name = "Rook Endgames",
      id = "rook-endgames",
      studies = List(
        study("MkDViieT", "7th-Rank Rook Pawn", "And Passive Rook vs Rook"),
        study("9c6GrCTk", "Basic Rook Endgames", "Lucena and Philidor"),
        study("Z1DKk4Rl", "Practical Rook Endings", "Rook endings with several pawns")
      )
    )
  )

  private def study(id: String, name: String, desc: String) =
    PracticeStudy(
      id = StudyId(id),
      name = StudyName(name),
      desc = desc,
      chapters = Nil // Chapters will be filled later
    )
