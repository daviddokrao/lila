package lila.practice

import lila.core.study.data.StudyName
import lila.study.Chapter

case class PracticeStructure(sections: List[PracticeSection]):

  def study(id: StudyId): Option[PracticeStudy] =
    sections.flatMap(_.study(id)).headOption

  lazy val studiesByIds: Map[StudyId, PracticeStudy] =
    sections.view
      .flatMap(_.studies)
      .mapBy(_.id)

  lazy val sectionsByStudyIds: Map[StudyId, PracticeSection] =
    sections.view.flatMap { sec =>
      sec.studies.map { stu =>
        stu.id -> sec
      }
    }.toMap

  lazy val chapterIds: List[StudyChapterId] = sections.flatMap(_.studies).flatMap(_.chapterIds)

  lazy val nbChapters = sections.flatMap(_.studies).map(_.chapterIds.size).sum

  def findSection(id: StudyId): Option[PracticeSection] = sectionsByStudyIds.get(id)

case class PracticeSection(
    id: String,
    name: String,
    studies: List[PracticeStudy]
):
  lazy val studiesByIds: Map[StudyId, PracticeStudy] = studies.mapBy(_.id)

  def study(id: StudyId): Option[PracticeStudy] = studiesByIds.get(id)

case class PracticeStudy(
    id: StudyId,
    name: StudyName,
    desc: String,
    chapters: List[Chapter.IdName]
) extends lila.core.practice.Study:
  val slug = scalalib.StringOps.slug(name.value)
  val chapterIds = chapters.map(_.id)

object PracticeStructure:

  // HungKings: hằng số này là MẪU SỐ của thanh "Tiến trình %". Upstream để 233 cho danh
  // sách 32 bài của họ; danh sách của ta còn 22 bài (12 bài kia trỏ vào study không tồn
  // tại — xem PracticeSections). Đếm thật trên DB: 240 chương. Để nguyên 233 thì thanh
  // tiến trình vượt quá 100%; đây là con số duy nhất phải sửa kèm khi đổi danh sách bài.
  private[practice] val totalChapters = 240

  private[practice] def studyIds: List[StudyId] = PracticeSections.list.flatMap(_.studies.map(_.id))

  def withChapters(chapters: Map[StudyId, Vector[Chapter.IdName]]) = PracticeStructure:
    PracticeSections.list.map: sec =>
      sec.copy(
        studies = sec.studies.map: stu =>
          stu.copy(chapters = chapters.get(stu.id).so(_.toList))
      )
