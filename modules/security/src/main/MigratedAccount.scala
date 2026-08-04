package lila.security

import lila.core.userId.UserId
import lila.db.dsl.{ *, given }

/** Tài khoản mang sang từ HungKings bản cũ.
  *
  * 16 người dùng của bản cũ được chèn thẳng vào MongoDB với một mật khẩu ngẫu nhiên mà
  * KHÔNG AI biết — kể cả họ. Nếu để nguyên thì lần đầu họ quay lại sẽ nhận "sai mật
  * khẩu" và không có cách nào hiểu chuyện gì đã xảy ra.
  *
  * Dấu di cư nằm ở collection RIÊNG chứ không phải một trường mới trên `user4`: thêm
  * trường vào model người dùng kéo theo sửa BSON handler của lila và làm bẩn diff với
  * upstream, trong khi thứ ta cần chỉ là một tập hợp 16 id sống được vài tuần rồi biến
  * mất. Ở đây tra cứu cũng chỉ chạy trên nhánh ĐĂNG NHẬP HỎNG của tài khoản có thật,
  * tức gần như không bao giờ chạm tới trong lưu lượng bình thường.
  *
  * Dấu bị gỡ NGAY sau khi thư đặt lại mật khẩu gửi đi, nên mỗi người chỉ kích hoạt được
  * đúng một lần. Đó cũng là thứ chặn biến luồng này thành máy gửi thư: tổng số thư mà
  * toàn bộ cơ chế có thể sinh ra bị chặn trên bởi số tài khoản còn dấu.
  */
final class MigratedAccount(coll: Coll)(using Executor):

  def isMigrated(id: UserId): Fu[Boolean] = coll.exists($id(id))

  /** Gỡ dấu. Gọi SAU khi thư đã gửi thành công — gửi hỏng mà đã gỡ dấu thì người đó mất
    * hẳn đường quay lại, phải nhờ người can thiệp tay.
    */
  def clear(id: UserId): Funit = coll.delete.one($id(id)).void
