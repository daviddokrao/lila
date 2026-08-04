package lila.tournament

import lila.core.i18n.Translate
import chess.variant.Variant
import chess.format.Fen

private object TournamentName:

  def apply(tour: Tournament, full: Boolean)(using Translate): String =
    tour.scheduleData.fold(if full then s"${tour.name} Arena" else tour.name): (freq, speed) =>
      TournamentName(tour, freq, speed, full)

  def apply(tour: Tournament, freq: Schedule.Freq, speed: Schedule.Speed, full: Boolean)(using
      Translate
  ): String =
    of(tour.variant, tour.conditions, tour.position, freq, speed, full)

  def apply(sched: Schedule, full: Boolean)(using Translate): String =
    of(sched.variant, sched.conditions, sched.position, sched.freq, sched.speed, full)

  def of(
      variant: Variant,
      conditions: TournamentCondition.All,
      position: Option[Fen.Standard],
      freq: Schedule.Freq,
      speed: Schedule.Speed,
      full: Boolean
  )(using Translate): String =
    import Schedule.Freq.*
    import Schedule.Speed.*
    import lila.core.i18n.I18nKey.tourname.*
    if variant.standard && position.isEmpty then
      (conditions.minRating, conditions.maxRating) match
        case (None, None) =>
          (freq, speed) match
            case (Hourly, Rapid) if full => hourlyRapidArena.txt()
            case (Hourly, Rapid) => hourlyRapid.txt()
            case (Hourly, _) if full => hourlyXArena.txt(speed.trans)
            case (Hourly, _) => hourlyX.txt(speed.trans)
            case (Daily, Rapid) if full => dailyRapidArena.txt()
            case (Daily, Rapid) => dailyRapid.txt()
            case (Daily, Classical) if full => dailyClassicalArena.txt()
            case (Daily, Classical) => dailyClassical.txt()
            case (Daily, _) if full => dailyXArena.txt(speed.trans)
            case (Daily, _) => dailyX.txt(speed.trans)
            case (Eastern, Rapid) if full => easternRapidArena.txt()
            case (Eastern, Rapid) => easternRapid.txt()
            case (Eastern, Classical) if full => easternClassicalArena.txt()
            case (Eastern, Classical) => easternClassical.txt()
            case (Eastern, _) if full => easternXArena.txt(speed.trans)
            case (Eastern, _) => easternX.txt(speed.trans)
            case (Weekly, Rapid) if full => weeklyRapidArena.txt()
            case (Weekly, Rapid) => weeklyRapid.txt()
            case (Weekly, Classical) if full => weeklyClassicalArena.txt()
            case (Weekly, Classical) => weeklyClassical.txt()
            case (Weekly, _) if full => weeklyXArena.txt(speed.trans)
            case (Weekly, _) => weeklyX.txt(speed.trans)
            case (Monthly, Rapid) if full => monthlyRapidArena.txt()
            case (Monthly, Rapid) => monthlyRapid.txt()
            case (Monthly, Classical) if full => monthlyClassicalArena.txt()
            case (Monthly, Classical) => monthlyClassical.txt()
            case (Monthly, _) if full => monthlyXArena.txt(speed.trans)
            case (Monthly, _) => monthlyX.txt(speed.trans)
            case (Yearly, Rapid) if full => yearlyRapidArena.txt()
            case (Yearly, Rapid) => yearlyRapid.txt()
            case (Yearly, Classical) if full => yearlyClassicalArena.txt()
            case (Yearly, Classical) => yearlyClassical.txt()
            case (Yearly, _) if full => yearlyXArena.txt(speed.trans)
            case (Yearly, _) => yearlyX.txt(speed.trans)
            case (Shield, Rapid) if full => rapidShieldArena.txt()
            case (Shield, Rapid) => rapidShield.txt()
            case (Shield, Classical) if full => classicalShieldArena.txt()
            case (Shield, Classical) => classicalShield.txt()
            case (Shield, _) if full => xShieldArena.txt(speed.trans)
            case (Shield, _) => xShield.txt(speed.trans)
            // HungKings: Marathon/Weekend/Unique là 3 freq DUY NHẤT không có khoá dịch ở
            // upstream — chúng rơi xuống nhánh fallback và ghép `freq.toString`, tức TÊN
            // ENUM SCALA, tiếng Anh cứng, cho MỌI ngôn ngữ. Tên đó còn bị `Tournament.name()`
            // đọc thẳng từ DB (short-circuit cho Marathon/Unique) nên nó dính vĩnh viễn vào
            // bản ghi giải. Dùng `xArena` sẵn có cho biến thể "full" để chỉ tốn 3 khoá mới.
            case (Marathon | ExperimentalMarathon, _) if full => xArena.txt(marathonX.txt(speed.trans))
            case (Marathon | ExperimentalMarathon, _) => marathonX.txt(speed.trans)
            case (Weekend, _) if full => xArena.txt(weekendX.txt(speed.trans))
            case (Weekend, _) => weekendX.txt(speed.trans)
            case (Unique, _) if full => xArena.txt(uniqueX.txt(speed.trans))
            case (Unique, _) => uniqueX.txt(speed.trans)
            case _ if full => xArena.txt(s"${freq.toString} ${speed.trans}")
            case _ => s"${freq.toString} ${speed.trans}"
        case (Some(_), _) if full => eliteXArena.txt(speed.trans)
        case (Some(_), _) => eliteX.txt(speed.trans)
        case (_, Some(max)) if full => s"≤${max.rating} ${xArena.txt(speed.trans)}"
        case (_, Some(max)) => s"≤${max.rating} ${speed.trans}"
    else if variant.standard then
      val n = position.flatMap(lila.gathering.Thematic.byFen).fold(speed.trans) { pos =>
        s"${pos.family.name} ${speed.trans}"
      }
      if full then xArena.txt(n) else n
    else
      freq match
        case Hourly if full => hourlyXArena.txt(variant.name)
        case Hourly => hourlyX.txt(variant.name)
        case Daily if full => dailyXArena.txt(variant.name)
        case Daily => dailyX.txt(variant.name)
        case Eastern if full => easternXArena.txt(variant.name)
        case Eastern => easternX.txt(variant.name)
        case Weekly if full => weeklyXArena.txt(variant.name)
        case Weekly => weeklyX.txt(variant.name)
        case Monthly if full => monthlyXArena.txt(variant.name)
        case Monthly => monthlyX.txt(variant.name)
        case Yearly if full => yearlyXArena.txt(variant.name)
        case Yearly => yearlyX.txt(variant.name)
        case Shield if full => xShieldArena.txt(variant.name)
        case Shield => xShield.txt(variant.name)
        // HungKings: cùng lỗ hổng như nhánh cờ tiêu chuẩn ở trên, nhưng ghép với tên biến thể.
        case Marathon | ExperimentalMarathon if full => xArena.txt(marathonX.txt(variant.name))
        case Marathon | ExperimentalMarathon => marathonX.txt(variant.name)
        case Weekend if full => xArena.txt(weekendX.txt(variant.name))
        case Weekend => weekendX.txt(variant.name)
        case Unique if full => xArena.txt(uniqueX.txt(variant.name))
        case Unique => uniqueX.txt(variant.name)
        case _ =>
          val n = s"${freq.name} ${variant.name}"
          if full then xArena.txt(n) else n
