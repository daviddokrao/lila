package lila.web
package ui

import lila.ui.*

import ScalatagsTemplate.*

/**
 * @param locale HungKings L10 (20/08): `og:locale` cho biết trang ĐANG ở ngôn ngữ nào khi
 *   được chia sẻ lên mạng xã hội. Trước đó site có đủ og:title/description/url/type/image
 *   nhưng KHÔNG có og:locale, nên bản tiếng Việt và bản tiếng Nga trông giống hệt nhau với
 *   bộ đọc thẻ.
 *   KHÔNG kèm `og:locale:alternate`: nó cần dạng `ll_CC` (vd `es_ES`), mà danh sách ngôn ngữ
 *   thay thế của lila chỉ có mã hai ký tự — ghép region bừa là đoán sai nước. Vai trò "trang
 *   này còn bản ngôn ngữ khác" đã do **hreflang** đảm nhiệm, và phần đó đã đúng sẵn (đo
 *   20/08: `x-default` + `en` trỏ `/`, 20 ngôn ngữ còn lại trỏ `/vi` `/ru` `/es`…).
 */
def openGraph(graph: OpenGraph, locale: Option[String] = None): List[Frag] =
  import graph.*
  val property = attr("property")
  def tag(name: String, value: String) =
    meta(
      property := s"og:$name",
      content := value
    )
  List(
    "title" -> title,
    "description" -> description,
    "url" -> url.value,
    "type" -> `type`,
    "site_name" -> siteName
  ).map(tag) ::: image.map(i => tag("image", i.value)).toList :::
    locale.map(l => tag("locale", l)).toList
