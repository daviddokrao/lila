package controllers
import play.api.data.Form
import play.api.libs.json.*
import play.api.mvc.*
// Can cho `.post[JsValue](...)` khi gan quan he gioi thieu sang service diem.
import play.api.libs.ws.JsonBodyWritables.writeableOf_JsValue

import lila.app.{ *, given }
import lila.common.HTTPRequest
import lila.common.Json.given
import lila.core.id.SessionId
import lila.core.email.{ UserIdOrEmail, UserStrOrEmail }
import lila.core.net.ValidReferrer
import lila.core.security.{ ClearPassword, TurnstilePublicConfig }
import lila.core.misc.AuthCustomUi
import lila.memo.RateLimit
import lila.security.SecurityForm.{ MagicLink, PasswordReset }
import lila.security.{ FingerPrint, Signup, EmailConfirm, IsPwned, PasswordReset as PasswordResetService }

final class Auth(env: Env, accountC: => Account) extends LilaController(env):

  import env.security.{ api, forms }
  def logger = lila.security.loggerAuth

  private given (using Context): Option[ValidReferrer] = env.web.referrerRedirect.fromReq

  private def referrerOr(default: => Call)(using referrer: Option[ValidReferrer]): String =
    referrer.fold(default.url)(_.value)

  // HungKings P1.2: bản nhận URL thô (Call không mang được fragment #hv2-play)
  private def referrerOrUrl(default: String)(using referrer: Option[ValidReferrer]): String =
    referrer.fold(default)(_.value)

  def authenticateUser(
      u: UserModel,
      pwned: IsPwned,
      remember: Boolean,
      result: => Option[Result] = None
  )(using ctx: Context): Fu[Result] = {
    for
      sessionId <- api.saveAuthentication(u.id, ctx.mobileApiVersion, pwned)
      res <- negotiate(
        result | Redirect(referrerOr(routes.Lobby.home)),
        for
          povs <- env.round.proxyRepo.urgentGames(u)
          perfs <- ctx.pref.showRatings.optionFu(env.user.perfsRepo.perfsOf(u))
          _ <- env.msg.systemMsg.lichobileDeprecationMessage(u)
        yield Ok:
          env.user.jsonView.full(u, perfs, withProfile = true) ++ Json.obj(
            "nowPlaying" -> JsArray(povs.value.take(20).map(env.api.lobbyApi.nowPlaying)),
            "sessionId" -> sessionId
          )
      ).map(authenticateCookie(sessionId, remember))
    yield res
  }.recoverWith(authRecovery)

  private def authenticateAppealUser(
      u: UserModel,
      redirect: String => Result,
      url: Call = routes.Appeal.landing
  )(using
      ctx: Context
  ): Fu[Result] =
    api.appeal
      .saveAuthentication(u.id)
      .flatMap: sessionId =>
        authenticateCookie(sessionId, remember = false):
          redirect(url.url)
      .recoverWith(authRecovery)

  private def authenticateCookie(sessionId: SessionId, remember: Boolean)(
      result: Result
  )(using RequestHeader) =
    result.withCookies(
      env.security.lilaCookie.withSession(remember = remember) {
        _ + (api.sessionIdKey -> sessionId.value) - EmailConfirm.cookie.name
      }
    )

  private def authRecovery(using ctx: Context): PartialFunction[Throwable, Fu[Result]] =
    case lila.security.SecurityApi.MustConfirmEmail(_) =>
      if HTTPRequest.isXhr(ctx.req)
      then Ok(s"ok:${routes.Auth.checkYourEmail}")
      else BadRequest.async(accountC.renderCheckYourEmail)

  def login = Open:
    renderLogin(AuthVariant.Lichess, routes.Auth.login)

  def loginTakex3 = Open:
    withTakex3Referrer(routes.Auth.login):
      renderLogin(AuthVariant.Takex3, routes.Auth.loginTakex3)

  def loginLang = LangPage(routes.Auth.login)(serveLogin)

  private enum AuthVariant:
    case Lichess, Takex3

  private def takex3Client = env.oAuth.signedClients.takex3

  private def signedClient(using ref: Option[ValidReferrer]) =
    ref.flatMap(env.oAuth.signedClients.signedReferrerClient)

  private def withTakex3Referrer(fallback: Call, requireSimpleSignup: Boolean = false)(run: => Fu[Result])(
      using Option[ValidReferrer]
  ) =
    val allowed =
      if requireSimpleSignup then simpleSignup.exists(_.client == takex3Client)
      else signedClient.contains(takex3Client)
    if allowed then run else Redirect(fallback).toFuccess

  private def authCustomUi(variant: AuthVariant)(using
      Option[ValidReferrer]
  ): Option[AuthCustomUi] =
    variant match
      case AuthVariant.Takex3 => takex3Client.design
      case AuthVariant.Lichess => signedClient.flatMap(_.design)

  private def serveLogin(using ctx: Context, referrer: Option[ValidReferrer]): Fu[Result] =
    renderLogin(AuthVariant.Lichess, routes.Auth.login)

  private def renderLogin(
      variant: AuthVariant,
      canonical: Call
  )(using ctx: Context, referrer: Option[ValidReferrer]) = NoBot:
    val switch = get("switch").orElse(get("as"))
    t3Counter(_.login.load)
    referrer.ifTrue(ctx.isAuth).ifTrue(switch.isEmpty) match
      case Some(url) =>
        t3Counter(_.login.success)
        Redirect(url.value) // redirect immediately if already logged in
      case None =>
        val prefillUsername = UserStrOrEmail(~switch.filter(_ != "1"))
        val form = api.loginFormFilled(prefillUsername)
        Ok.page(loginPage(variant, form)).map(_.withCanonical(canonical))

  def authenticate = OpenBody:
    serveAuthenticate(AuthVariant.Lichess)

  def authenticateTakex3 = OpenBody:
    withTakex3Referrer(routes.Auth.login):
      serveAuthenticate(AuthVariant.Takex3)

  private def serveAuthenticate(variant: AuthVariant)(using BodyContext[?]) =
    NoCrawlers:
      Firewall:
        def redirectTo(url: String) = if HTTPRequest.isXhr(ctx.req) then Ok(s"ok:$url") else Redirect(url)
        val isRemember = api.rememberForm.bindFromRequest().value | true
        val isLichobile = HTTPRequest.isLichobile(ctx.req)
        if isLichobile && !env.security.lichobileLogin.get() then
          BadRequest(Json.obj("global" -> List("Please use our new mobile app! lichess.org/app")))
        else
          bindForm(api.loginForm)(
            err =>
              negotiate(
                Unauthorized.page(loginPage(variant, err, isRemember)),
                Unauthorized(doubleJsonFormErrorBody(err))
              ),
            loginData =>
              val turnstileResult = fuccess(isLichobile) >>|
                env.security.turnstileCookie.test(loginData) >>|
                env.security.turnstile.verify()
              turnstileResult.flatMap:
                if _ then
                  LoginRateLimit(loginData.username.normalize, ctx.req): chargeLimiters =>
                    env.security.pwned
                      .isPwned(loginData.password)
                      .flatMap: pwned =>
                        if pwned.yes then chargeLimiters()
                        val isEmail = EmailAddress.isValid(loginData.username.value)
                        api.loadLoginForm(loginData.username, pwned).flatMap {
                          _.bindFromRequest()
                            .fold(
                              err =>
                                chargeLimiters()
                                lila.mon.security.login
                                  .attempt(isEmail, pwned = pwned.yes, result = false)
                                  .increment()
                                negotiate(
                                  lila.security.LoginCandidate.totpError(err) match
                                    case None =>
                                      t3Counter(_.login.failure("credentials"))
                                      // HungKings: người mang sang từ bản cũ không thể
                                      // biết mật khẩu của chính mình — xem
                                      // migratedAccountRescue. Mọi trường hợp khác rơi
                                      // về đúng hành vi cũ.
                                      migratedAccountRescue(loginData.username, isEmail, variant)(
                                        Unauthorized.page(loginPage(variant, err, isRemember))
                                      )
                                    case Some(err) =>
                                      for cookie <- env.security.turnstileCookie.create(loginData)
                                      yield Ok(err).withCookies(cookie),
                                  Unauthorized(doubleJsonFormErrorBody(err))
                                )
                              ,
                              _.toOption match
                                case None => InternalServerError("Authentication error")
                                case Some(u) if u.enabled.no =>
                                  t3Counter(_.login.failure("closed"))
                                  negotiate(
                                    env.mod.logApi.closedByTeacher(u).flatMap {
                                      if _ then
                                        authenticateAppealUser(u, redirectTo, routes.Appeal.closedByTeacher)
                                      else
                                        env.mod.logApi.closedByMod(u).flatMap {
                                          if _ then authenticateAppealUser(u, redirectTo)
                                          else redirectTo(routes.Account.reopen.url)
                                        }
                                    },
                                    Unauthorized(jsonError("This account is closed."))
                                  )
                                case Some(u) =>
                                  lila.mon.security.login
                                    .attempt(isEmail, pwned = pwned.yes, result = true)
                                    .increment()
                                  t3Counter(_.login.success)
                                  env.user.repo.email(u.id).foreach(_.foreach(garbageCollect(u)))
                                  val ref = referrerOr(routes.Lobby.home)
                                  authenticateUser(u, pwned, isRemember, redirectTo(ref).some)
                            )
                        }
                else
                  t3Counter(_.login.failure("turnstile"))
                  BadRequest.page:
                    loginPage(
                      variant,
                      api.loginForm.fill(loginData).withGlobalError("Session timed out, please try again"),
                      isRemember
                    )
          )

  private def loginPage(variant: AuthVariant, form: Form[?], isRemember: Boolean = true)(using
      Context,
      TurnstilePublicConfig
  ) =
    given Option[AuthCustomUi] = authCustomUi(variant)
    variant match
      case AuthVariant.Takex3 => views.authTakex3.login(form, isRemember)
      case AuthVariant.Lichess => views.auth.login(form, isRemember)

  private def t3Counter(counter: lila.mon.signedClient.type => String => kamon.metric.Counter)(using
      Option[ValidReferrer]
  ) = simpleSignup.foreach: ss =>
    counter(lila.mon.signedClient)(ss.client.clientId.value).increment()

  private val clasLoginRateLimit =
    env.security.ipTrust.rateLimit(300, 1.hour, "clas.login")

  def clasLogin = OpenBody:
    Firewall:
      val failRedir = Redirect(routes.Clas.index).flashFailure(
        if ctx.lang.language == "vi" then "Mã đăng nhập sai hoặc đã hết hạn"
        else "Invalid or expired login code"
      )
      bindForm(lila.clas.ClasForm.login)(
        _ => failRedir,
        code =>
          clasLoginRateLimit(rateLimited):
            for
              found <- env.clas.login.login(code)
              res <- found.fold(failRedir.toFuccess): (user, clsId) =>
                val redir = Redirect(routes.Clas.show(clsId)).flashSuccess:
                  lila.core.i18n.I18nKey.emails.welcome_subject.txt(user.username)
                authenticateUser(user, IsPwned.No, false, redir.some)
            yield res
      )

  def logout = Open:
    val sid = env.security.api.reqSessionId(ctx.req)
    for
      _ <- sid.so(env.security.store.delete)
      _ <- sid.so(env.push.browserSub.unsubscribeBySession)
      res <- negotiate(Redirect(routes.Auth.login), jsonOkResult)
    yield res.withCookies(env.security.lilaCookie.newSession)

  // mobile app BC logout with GET
  def logoutGet = Auth { ctx ?=> _ ?=>
    negotiate(
      html = Ok.page(views.auth.logout),
      json = ctx.req.session.get(api.sessionIdKey).map(SessionId.apply).so(env.security.store.delete) >>
        jsonOkResult.withCookies(env.security.lilaCookie.newSession)
    )
  }

  def signup = Open:
    serveSignup(AuthVariant.Lichess, routes.Auth.signup)

  def signupTakex3 = Open:
    withTakex3Referrer(routes.Auth.signup, requireSimpleSignup = true):
      serveSignup(AuthVariant.Takex3, routes.Auth.signupTakex3)

  def signupLang = LangPage(routes.Auth.signup)(serveSignup)

  private def serveSignup(using Context, Option[ValidReferrer]): Fu[Result] =
    serveSignup(AuthVariant.Lichess, routes.Auth.signup)

  private def serveSignup(
      variant: AuthVariant,
      canonical: Call
  )(using Context, Option[ValidReferrer]) = NoTor:
    t3Counter(_.signup.load)
    val form = forms.signup.full(simpleSignup)
    Ok.page(signupPage(variant, form.form, form.simple)).map(_.withCanonical(canonical))

  private def simpleSignup(using ref: Option[ValidReferrer]) =
    ref.flatMap(env.oAuth.signedClients.simpleSignupFrom)

  private def authLog(user: UserName, email: Option[EmailAddress], msg: String)(using ctx: Context) =
    for proxy <- env.security.ip2proxy.ofReq(ctx.req)
    do logger.info(s"$proxy $user ${email.fold("-")(_.value)} $msg")

  def signupPost = OpenBody:
    serveSignupPost(AuthVariant.Lichess)

  def signupPostTakex3 = OpenBody:
    withTakex3Referrer(routes.Auth.signup, requireSimpleSignup = true):
      serveSignupPost(AuthVariant.Takex3)

  private def serveSignupPost(variant: AuthVariant)(using BodyContext[?]) =
    NoTor:
      Firewall:
        WithProxy: _ ?=>
          if HTTPRequest.isLichobile(ctx.req)
          then
            BadRequest:
              jsonError:
                Json.obj("username" -> List("Please use our new mobile app! https://lichess.org/app"))
          else
            limit.enumeration.signup(rateLimited):
              import Signup.Result.*
              env.security.signup
                .website(ctx.blind, simpleSignup)
                .flatMap:
                  case RateLimited | ForbiddenNetwork | SimpleSignupDuplicate =>
                    t3Counter(_.signup.failure("rateLimit"))
                    rateLimited
                  case TurnstileFail =>
                    t3Counter(_.signup.failure("turnstile"))
                    val f = forms.signup.full(simpleSignup)
                    val form = f.form.withGlobalError("Invalid captcha")
                    BadRequest.page(signupPage(variant, form, f.simple))
                  case FormInvalid(err) =>
                    t3Counter(_.signup.failure("form"))
                    val f = forms.signup.full(simpleSignup)
                    BadRequest.page(signupPage(variant, err, f.simple))
                  case ConfirmEmail(user, email) =>
                    t3Counter(_.signup.step("emailConfirm"))
                    redirectWithReferrer(routes.Auth.checkYourEmail).withCookies:
                      EmailConfirm.cookie.newSession(env.security.lilaCookie, user, email)
                  case AllSet(user, email) =>
                    t3Counter(_.signup.success)
                    welcome(user, email, sendWelcomeEmail = true) >> redirectNewUser(user)

  private def signupPage(variant: AuthVariant, form: Form[?], simple: Boolean)(using
      Context,
      TurnstilePublicConfig
  ) =
    given Option[AuthCustomUi] = authCustomUi(variant)
    variant match
      case AuthVariant.Takex3 => views.authTakex3.signup(form, simple)
      case AuthVariant.Lichess => views.auth.signup(form, simple)

  private def welcome(user: UserModel, email: EmailAddress, sendWelcomeEmail: Boolean)(using
      ctx: Context
  ): Funit =
    garbageCollect(user)(email)
    if sendWelcomeEmail then env.mailer.automaticEmail.welcomeEmail(user, email)
    env.mailer.automaticEmail.welcomePM(user)
    bindPointsReferral(user.id)
    env.pref.api.saveNewUserPrefs(user, ctx.req)

  /**
   * HungKings: gan quan he gioi thieu neu nguoi nay den tu link /r/<ma>.
   *
   * Day la thoi diem DUY NHAT bat duoc: cookie chi noi "nguoi nay tung bam link
   * moi", con quan he chi co nghia khi da co tai khoan de gan vao.
   *
   * BAN-VA-QUEN CO CHU Y (khong `>>`, khong await): he diem chet KHONG duoc lam
   * hong viec dang ky. Mat mot quan he gioi thieu la mat mot phan thuong; chan
   * duoc mot nguoi dang ky la mat han nguoi do. Hai thu do khong cung hang.
   */
  private def bindPointsReferral(userId: UserId)(using ctx: Context): Unit =
    if env.web.config.pointsEnabled then
      ctx.req.cookies
        .get(Main.referralCookieName)
        .map(_.value)
        .filter(_.nonEmpty)
        .foreach: code =>
          env.web.ws
            .url(s"${Main.pointsInternalUrl}/internal/referral/bind")
            .addHttpHeaders(Main.pointsIdentityHeaders(userId.value)*)
            // Kieu tuong minh `[JsValue]`: BodyWritable khong hiep bien, de suy
            // luan tu do thi T = JsObject va khong tim thay instance nao.
            .post[JsValue](Json.obj("code" -> code))
            .addFailureEffect: e =>
              lila.log("points").warn(s"gan gioi thieu that bai cho $userId", e)

  private def garbageCollect(user: UserModel)(email: EmailAddress)(using ctx: Context) =
    env.security.garbageCollector.delay(user, email, ctx.req, quickly = lila.web.AnnounceApi.get.isDefined)

  def checkYourEmail = Open:
    RedirectToProfileIfLoggedIn:
      EmailConfirm.cookie.get(ctx.req) match
        case None => Ok.async(accountC.renderCheckYourEmail)
        case Some(userEmail) =>
          env.user.repo
            .exists(userEmail.username)
            .flatMap:
              if _ then Ok.async(accountC.renderCheckYourEmail)
              else Redirect(routes.Auth.signup).withCookies(env.security.lilaCookie.newSession)

  // after signup and before confirmation
  def fixEmail = OpenBody:
    EmailConfirm.cookie.get(ctx.req).so { userEmail =>
      forms.preloadEmailDns() >>
        bindForm(forms.fixEmail(userEmail.email))(
          err => BadRequest.page(views.auth.checkYourEmail(userEmail.email.some, err.some)),
          email =>
            env.user.repo
              .byId(userEmail.username)
              .flatMap:
                _.fold(Redirect(routes.Auth.signup).toFuccess): user =>
                  env.user.repo
                    .mustConfirmEmail(user.id)
                    .flatMap:
                      if _ then
                        val newUserEmail = userEmail.copy(email = email)
                        EmailConfirmRateLimit(newUserEmail, ctx.req, rateLimited):
                          lila.mon.email.send.fix.increment()
                          for
                            _ <- env.user.repo.setEmail(user.id, newUserEmail.email)
                            _ <- env.security.emailConfirm.send(user, newUserEmail.email)
                          yield redirectWithReferrer(routes.Auth.checkYourEmail).withCookies:
                            EmailConfirm.cookie.newSession(env.security.lilaCookie, user, newUserEmail.email)
                      else Redirect(routes.Auth.login)
        )
    }

  private def redirectWithReferrer(call: Call)(using referrer: Option[ValidReferrer]) =
    Redirect(call.url, referrer.so(r => Map("referrer" -> List(r.value))))

  def signupConfirmEmail(token: String) = Open:
    val ref = summon[Option[ValidReferrer]]
    val result =
      if ref.exists(env.oAuth.signedClients.isSignedReferrer) then
        t3Counter(_.signup.success)
        env.security.emailConfirm.confirm(token)
      else env.security.emailConfirm.dryTest(token)
    result.flatMap(emailConfirmResult(token))

  def signupConfirmEmailPost(token: String) = Open:
    env.security.emailConfirm.confirm(token).flatMap(emailConfirmResult(token))

  private def emailConfirmResult(
      token: String
  )(using ctx: Context): EmailConfirm.Result => Fu[Result] =
    case EmailConfirm.Result.NotFound =>
      lila.mon.user.register.confirmEmailResult(false).increment()
      notFound
    case EmailConfirm.Result.NeedsConfirm(user) =>
      Ok.page(views.auth.signupConfirm(user, token))
    case EmailConfirm.Result.AlreadyConfirmed(user) =>
      if ctx.is(user) then Redirect(routes.User.show(user.username))
      else Redirect(routes.Auth.login)
    case EmailConfirm.Result.JustConfirmed(user) =>
      lila.mon.user.register.confirmEmailResult(true).increment()
      for
        email <- env.user.repo.email(user.id)
        _ <- email.so: email =>
          authLog(user.username, email.some, "Confirmed email")
          welcome(user, email, sendWelcomeEmail = false)
        res <- redirectNewUser(user)
      yield res

  private def redirectNewUser(user: UserModel)(using Context) =
    api
      .saveAuthentication(user.id, ctx.mobileApiVersion, pwned = IsPwned.No)
      .flatMap: sessionId =>
        authenticateCookie(sessionId, remember = true):
          // HungKings P1.2: tài khoản mới hạ cánh vào khu thi đấu trang chủ thay vì
          // trang hồ sơ RỖNG của chính mình (điểm đứt phễu — audit 03/08).
          // B2 (18/08): khi cờ LILA_ONBOARDING_CHOICE bật, hạ cánh ở /bat-dau (3 lựa chọn
          // trình độ, xem controllers.Main.onboardingStart) thay vì thẳng vào khu thi đấu.
          // Cờ TẮT (mặc định) = HÀNH VI CŨ giữ nguyên. referrer (nếu có) vẫn thắng cả hai.
          val landing = if Main.onboardingEnabled then "/bat-dau" else "/#hv2-play"
          Redirect(referrerOrUrl(landing))
            // HungKings: dong nay la CAU DAU TIEN mot tai khoan moi nhin thay, ngay
            // sau khi bam dang ky. Re theo ngon ngu tai cho — khong them khoa registry.
            .flashSuccess(
              if ctx.lang.language == "vi" then "Chào mừng bạn! Tài khoản đã sẵn sàng."
              else "Welcome! Your account is now active."
            )
      .recoverWith(authRecovery)

  def setFingerPrint(fp: String, ms: Int) = Auth { ctx ?=> me ?=>
    lila.mon.http.fingerPrint.record(ms)
    api
      .setFingerPrint(ctx.req, FingerPrint(fp))
      .logFailure(logger, _ => s"FP ${HTTPRequest.print(ctx.req)} $fp")
      .flatMapz { hash =>
        (!me.lame).so(for
          otherIds <- api.recentUserIdsByFingerHash(hash).map(_.filterNot(_.is(me)))
          _ <- (otherIds.sizeIs >= 2).so(env.user.repo.countLameOrTroll(otherIds).flatMap {
            case nb if nb >= 2 && nb >= otherIds.size / 2 => env.report.api.autoAltPrintReport(me)
            case _ => funit
          })
        yield ())
      }
      .inject(NoContent)
  }

  private def renderPasswordReset(
      form: Option[Form[PasswordReset]],
      fail: Option[String],
      variant: AuthVariant
  )(using Context) =
    renderAsync:
      passwordResetPage(variant, form | env.security.forms.passwordReset, fail)

  def passwordReset = Open:
    renderPasswordReset(none, fail = none, AuthVariant.Lichess).map { Ok(_) }

  def passwordResetTakex3 = Open:
    withTakex3Referrer(routes.Auth.passwordReset):
      renderPasswordReset(none, fail = none, AuthVariant.Takex3).map { Ok(_) }

  def passwordResetApply = OpenBody:
    servePasswordResetApply(AuthVariant.Lichess)

  def passwordResetApplyTakex3 = OpenBody:
    withTakex3Referrer(routes.Auth.passwordReset):
      servePasswordResetApply(AuthVariant.Takex3)

  private def servePasswordResetApply(variant: AuthVariant)(using BodyContext[?]) =
    def badRequest(msg: String): Fu[Result] =
      renderPasswordReset(none, fail = msg.some, variant).map(BadRequest(_))
    env.security.turnstile
      .verify()
      .flatMap:
        if _ then
          forms.passwordReset
            .bindFromRequest()
            .fold(
              err => renderPasswordReset(err.some, fail = "".some, variant).map { BadRequest(_) },
              data =>
                env.security.passwordReset
                  .limiter(data.email -> req.ipAddress, badRequest("Too many requests")):
                    env.user.repo.notClosedForeverWithEmail(data.email.normalize).flatMap {
                      case Some(user, storedEmail) =>
                        lila.mon.user.auth.passwordResetRequest("success").increment()
                        for _ <- env.security.passwordReset.send(
                            user,
                            storedEmail,
                            origin = passwordResetOrigin(variant)
                          )
                        yield redirectWithReferrer(passwordResetSentRoute(storedEmail.value, variant))
                      case _ =>
                        lila.mon.user.auth.passwordResetRequest("noEmail").increment()
                        redirectWithReferrer(passwordResetSentRoute(data.email.value, variant))
                    }
            )
        else badRequest("Invalid captcha")

  private def passwordResetPage(variant: AuthVariant, form: Form[?], fail: Option[String])(using
      Context,
      TurnstilePublicConfig
  ) =
    given Option[AuthCustomUi] = authCustomUi(variant)
    variant match
      case AuthVariant.Takex3 => views.authTakex3.passwordReset(form, fail)
      case AuthVariant.Lichess => views.auth.passwordReset(form, fail)

  private def passwordResetSentRoute(email: String, variant: AuthVariant) =
    variant match
      case AuthVariant.Takex3 => routes.Auth.passwordResetSentTakex3(email)
      case AuthVariant.Lichess => routes.Auth.passwordResetSent(email)

  /** 16 người dùng mang sang từ HungKings bản cũ được chèn thẳng vào MongoDB với một mật
    * khẩu ngẫu nhiên KHÔNG AI biết. Trả "sai mật khẩu" cho họ tuy đúng về kỹ thuật nhưng
    * là ngõ cụt: họ không có mật khẩu nào để thử cho đúng. Thay vào đó gửi thư đặt lại
    * rồi GỠ DẤU ngay.
    *
    * Gỡ dấu là thứ chặn luồng này biến thành máy gửi thư: mỗi tài khoản kích hoạt được
    * đúng MỘT lần, nên trần số thư mà toàn bộ cơ chế có thể sinh ra bằng số tài khoản
    * còn dấu. Không gửi thư hàng loạt — chỉ gửi cho người thực sự quay lại.
    */
  private def migratedAccountRescue(
      login: UserStrOrEmail,
      isEmail: Boolean,
      variant: AuthVariant
  )(fallback: => Fu[Result])(using ctx: Context): Fu[Result] =
    // Ô đăng nhập nhận cả email lẫn tên tài khoản. 16 người mang sang từ HungKings bản cũ
    // CHỈ biết email của mình — username là chuỗi sinh tự động họ chưa từng thấy — nên bắt
    // buộc phải cứu được cả khi họ gõ EMAIL (đường họ sẽ đi). Gõ email thì tra ngược userId
    // qua email đã CHUẨN HOÁ (đúng thứ nằm ở trường `email`, giống mọi tra cứu email khác
    // của lila; bản gốc có dấu chấm nằm ở `verbatimEmail`). Gõ email SAI cú pháp thì không
    // tra được, rơi về hành vi cũ.
    val userIdFu: Fu[Option[UserId]] =
      if isEmail then
        EmailAddress.from(login.value) match
          case Some(email) => env.user.repo.byEmail(email.normalize).map(_.map(_.id))
          case None => fuccess(none)
      else fuccess(UserId(login.normalize.value).some)
    userIdFu.flatMap:
      case Some(userId) =>
        env.security.migratedAccount
          .isMigrated(userId)
          .flatMap:
            if _ then
              env.user.repo
                .byId(userId)
                .zip(env.user.repo.email(userId))
                .flatMap:
                  case (Some(user), Some(email)) =>
                    // Khoá giới hạn tần suất lấy email ĐÃ TRA TỪ DB, không lấy chuỗi người
                    // gửi tự nhập: khoá dựng từ input chưa xác thực là khoá do kẻ tấn công
                    // chọn. Và hạn theo danh tính là CỘNG THÊM vào hạn theo IP, không thay thế.
                    env.security.passwordReset.limiter(email -> ctx.req.ipAddress, fallback):
                      for
                        _ <- env.security.passwordReset
                          .send(user, email, origin = passwordResetOrigin(variant))
                        // Gỡ dấu SAU khi gửi xong. Gỡ trước mà gửi hỏng là người đó mất hẳn
                        // đường quay lại, phải nhờ người can thiệp tay.
                        _ <- env.security.migratedAccount.clear(userId)
                      yield
                        // Form đăng nhập của lila gửi bằng XHR và chờ chuỗi `ok:<url>`;
                        // trả thẳng một trang HTML thì client không chuyển trang, người
                        // dùng đứng im trên form dù thư đã bay đi. Đây là đúng khuôn
                        // `redirectTo` mà serveAuthenticate dùng cho nhánh đăng nhập thành công.
                        // `?migrated=1` để trang "đã gửi thư" nêu rõ lý do là ĐỔI HỆ THỐNG
                        // (xem passwordResetSent). Chỉ luồng cứu di cư gắn cờ này.
                        val url = passwordResetSentRoute(maskEmail(email), variant).url + "?migrated=1"
                        if HTTPRequest.isXhr(ctx.req) then Ok(s"ok:$url") else Redirect(url)
                  case _ => fallback
            else fallback
      case None => fallback

  /** Che phần tên trước @ trước khi ĐƯA VÀO URL của trang "đã gửi thư". Luồng trên kích
    * hoạt bằng TÊN TÀI KHOẢN do người lạ gõ vào, nên để nguyên địa chỉ là biến ô đăng
    * nhập thành máy tra email — lila tự nó đặt email vào URL được vì ở đó chính người
    * dùng vừa gõ email ra. Giữ tên miền để người dùng biết mở hòm thư nào.
    */
  private def maskEmail(email: EmailAddress): String =
    val raw = email.value
    val at = raw.indexOf('@')
    if at <= 0 then "***"
    else s"${raw.take(1)}***${raw.substring(at)}"

  private def passwordResetOrigin(variant: AuthVariant) =
    variant match
      case AuthVariant.Takex3 => PasswordResetService.Origin.Takex3
      case AuthVariant.Lichess => PasswordResetService.Origin.Lichess

  def passwordResetSent(email: String) = Open:
    // `?migrated=1` do luồng cứu tài khoản di cư gắn vào (migratedAccountRescue): người
    // mang sang từ HungKings bản cũ cần biết mật khẩu bị đặt lại vì ĐỔI HỆ THỐNG, không
    // phải họ quên. Người quên mật khẩu thường không có cờ này nên vẫn thấy trang cũ.
    passwordResetSentPage(email, AuthVariant.Lichess, migrated = ctx.req.queryString.contains("migrated"))

  def passwordResetSentTakex3(email: String) = Open:
    withTakex3Referrer(routes.Auth.passwordResetSent(email)):
      passwordResetSentPage(email, AuthVariant.Takex3)

  private def passwordResetSentPage(email: String, variant: AuthVariant, migrated: Boolean = false)(using
      Context,
      Option[ValidReferrer]
  ) =
    variant match
      case AuthVariant.Lichess => Ok.page(views.auth.passwordResetSent(email, migrated))
      case AuthVariant.Takex3 =>
        given Option[AuthCustomUi] = takex3Client.design
        Ok.page(views.authTakex3.passwordResetSent(email))

  def passwordResetConfirm(token: String) = Open:
    servePasswordResetConfirm(token, AuthVariant.Lichess)

  def passwordResetConfirmTakex3(token: String) = Open:
    servePasswordResetConfirm(token, AuthVariant.Takex3)

  private def servePasswordResetConfirm(token: String, variant: AuthVariant)(using Context) =
    given Option[AuthCustomUi] = authCustomUi(variant)
    env.security.passwordReset
      .confirm(token)
      .flatMap:
        case None =>
          lila.mon.user.auth.passwordResetConfirm("tokenFail").increment()
          notFound
        case Some(user) if user.enabled.no => authenticateAppealUser(user, Redirect(_))
        case Some(user) =>
          given Me = Me(user)
          authLog(user.username, none, "Reset password")
          lila.mon.user.auth.passwordResetConfirm("tokenOk").increment()
          Ok.page:
            passwordResetConfirmPage(variant, token, forms.passwdResetForMe)

  def passwordResetConfirmApply(token: String) = OpenBody:
    servePasswordResetConfirmApply(token, AuthVariant.Lichess)

  def passwordResetConfirmApplyTakex3(token: String) = OpenBody:
    servePasswordResetConfirmApply(token, AuthVariant.Takex3)

  private def servePasswordResetConfirmApply(token: String, variant: AuthVariant)(using BodyContext[?]) =
    given Option[AuthCustomUi] = authCustomUi(variant)
    env.security.passwordReset
      .confirm(token)
      .flatMap:
        case None =>
          lila.mon.user.auth.passwordResetConfirm("tokenPostFail").increment()
          notFound
        case Some(user) if user.enabled.no => authenticateAppealUser(user, Redirect(_))
        case Some(user) =>
          given Me = Me(user)
          FormFuResult(forms.passwdResetForMe) { err =>
            renderPage:
              passwordResetConfirmPage(variant, token, err, formOk = false.some)
          } { data =>
            HasherRateLimit:
              for
                _ <- env.security.authenticator.setPassword(user.id, ClearPassword(data.newPasswd1))
                _ <- env.mod.logApi.setPassword
                confirmed <- env.user.repo.setEmailConfirmed(user.id)
                _ <- confirmed.so:
                  welcome(user, _, sendWelcomeEmail = false)
                _ <- env.user.repo.disableTwoFactor(user.id)
                _ <- env.security.store.closeAllSessionsOf(user.id)
                _ <- env.push.browserSub.unsubscribeByUser(user)
                _ <- env.push.unregisterDevices(user)
                result <-
                  passwordResetSuccessResult(variant)
                res <- authenticateUser(user, remember = true, pwned = IsPwned.No, result = result)
              yield
                lila.mon.user.auth.passwordResetConfirm("success").increment()
                res
          }

  private def passwordResetConfirmPage(
      variant: AuthVariant,
      token: String,
      form: Form[?],
      formOk: Option[Boolean] = none
  )(using Context, Me, Option[AuthCustomUi]) =
    variant match
      case AuthVariant.Takex3 => views.authTakex3.passwordResetConfirm(token, form)
      case AuthVariant.Lichess => views.auth.passwordResetConfirm(token, form, formOk)

  private def passwordResetSuccessResult(variant: AuthVariant)(using Context, Option[AuthCustomUi]) =
    variant match
      case AuthVariant.Takex3 => renderPage(views.authTakex3.passwordResetSuccess).map(page => Ok(page).some)
      case AuthVariant.Lichess => fuccess(none)

  private def renderMagicLink(form: Option[Form[MagicLink]], fail: Boolean)(using
      Context,
      Option[ValidReferrer]
  ) =
    views.auth.magicLink(form | env.security.forms.magicLink, fail)

  def magicLink = Open:
    Firewall:
      Ok.async(renderMagicLink(none, fail = false))

  def magicLinkApply = OpenBody:
    Firewall:
      env.security.turnstile.verify().flatMap {
        if _ then
          forms.magicLink
            .bindFromRequest()
            .fold(
              err => BadRequest.async(renderMagicLink(err.some, fail = true)),
              data =>
                env.user.repo.notClosedForeverWithEmail(data.email.normalize).flatMap {
                  case Some(user, storedEmail) =>
                    env.security.loginToken.rateLimit[Result](user, storedEmail, ctx.req, rateLimited):
                      for _ <- env.security.loginToken.send(user, storedEmail)
                      yield Redirect(routes.Auth.magicLinkSent)
                  case _ => Redirect(routes.Auth.magicLinkSent)
                }
            )
        else BadRequest.async(renderMagicLink(none, fail = true))
      }

  def magicLinkSent = Open:
    Ok.page(views.auth.magicLinkSent)

  def makeLoginToken = Auth { ctx ?=> me ?=>
    JsonOk:
      env.security.loginToken
        .generate(me)
        .map: token =>
          Json.obj(
            "userId" -> me.userId,
            "url" -> routeUrl(routes.Auth.loginWithToken(token))
          )
  }

  def loginWithToken(token: String) = Open:
    if ctx.isAuth then Redirect(referrerOr(routes.Lobby.home))
    else
      Firewall:
        consumingToken(token): user =>
          Ok.async:
            env.security.loginToken
              .generate(user)
              .map(views.auth.tokenLoginConfirmation(user, _))

  def loginWithTokenPost(token: String) =
    Open:
      if ctx.isAuth then Redirect(referrerOr(routes.Lobby.home))
      else
        Firewall:
          consumingToken(token): user =>
            if user.enabled.yes then authenticateUser(user, remember = true, pwned = IsPwned.No)
            else authenticateAppealUser(user, Redirect(_))

  def check = OpenOrScoped() { ctx ?=>
    ctx.me match
      case Some(me) =>
        val tier =
          if me.is(UserId.lichess) then 4
          else if me.isVerified then 2
          else 1
        NoContent.withHeaders(
          "X-User" -> me.userId.value,
          "X-Tier" -> tier.toString
        )
      case None => Unauthorized
  }

  def apiEmailValidate = ScopedBody() { _ ?=> me ?=>
    if me.isnt(UserId.t3) then notFound
    else bindForm(env.security.forms.signup.emailCheck)(jsonFormError, JsonOk(_))
  }

  private def consumingToken(token: String)(f: UserModel => Fu[Result])(using Context) =
    env.security.loginToken
      .consume(token)
      .flatMap:
        case None =>
          BadRequest.page:
            import scalatags.Text.all.stringFrag
            views.site.message("This token has expired.")(stringFrag("Please go back and try again."))
        case Some(user) => f(user)

  private[controllers] object LoginRateLimit:
    def apply(id: UserIdOrEmail, req: RequestHeader)(run: RateLimit.Charge => Fu[Result])(using
        Context
    ): Fu[Result] =
      passwordCost(req).flatMap: cost =>
        env.security.passwordHasher.rateLimit[Result](
          rateLimited,
          enforce = env.net.rateLimit,
          ipCost = cost.toInt
        )(id, req)(run)

  private[controllers] def HasherRateLimit(run: => Fu[Result])(using me: Me, ctx: Context): Fu[Result] =
    passwordCost(req).flatMap: cost =>
      env.security.passwordHasher.rateLimit[Result](
        rateLimited,
        enforce = env.net.rateLimit,
        ipCost = cost.toInt
      )(me.userId.into(UserIdOrEmail), req)(_ => run)

  private def passwordCost(req: RequestHeader): Fu[Float] =
    env.security.ipTrust
      .rateLimitCostFactor(req, _.proxyMultiplier(if HTTPRequest.nginxWhitelist(req) then 1 else 2))

  private[controllers] def EmailConfirmRateLimit = EmailConfirm.rateLimit[Result]

  private[controllers] def RedirectToProfileIfLoggedIn(f: => Fu[Result])(using ctx: Context): Fu[Result] =
    ctx.me.fold(f)(me => Redirect(routes.User.show(me.username)))
