package lila.ui

import play.api.libs.json.JsValue
import scalatags.Text.all.Frag

import lila.core.data.SafeJsonStr
import lila.core.i18n.I18nKey
import lila.core.perf.{ KeyedPerf, UserPerfs }

export lila.core.lilaism.Lilaism.{ *, given }

trait RatingApi:
  val toNameKey: PerfKey => I18nKey
  val toDescKey: PerfKey => I18nKey
  val toIcon: PerfKey => Icon
  val bestRated: UserPerfs => Option[KeyedPerf]
  val dubiousPuzzle: UserPerfs => Boolean

case class PageModule(name: String, data: JsValue | SafeJsonStr)
case class Esm(key: String, init: WithNonce[Frag] = _ => ScalatagsExtensions.emptyFrag)
type EsmList = List[Option[Esm]]

final class AnalyseEndpoints(
    val explorer: String,
    val tablebase: String,
    val externalEngine: String,
    // HungKings: khám phá khai cuộc + tablebase cần service riêng mà image demo không có.
    // Tắt = ẩn nút khỏi bàn phân tích VÀ bỏ 2 endpoint khỏi CSP, thay vì bảo trình duyệt
    // người dùng tự gọi localhost của CHÍNH HỌ. Bật lại bằng env, không cần dựng lại image.
    val explorerEnabled: Boolean
)
