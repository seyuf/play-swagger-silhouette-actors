package controllers

/**
 * Created by madalien on 13/05/16.
 */

import java.lang.Boolean
import java.util.UUID
import javax.inject.Inject

import _root_.services.token.TokenService
import _root_.services.user.UserService
import com.mohiva.play.silhouette.api._
import com.mohiva.play.silhouette.api.exceptions.ProviderException
import com.mohiva.play.silhouette.api.repositories.AuthInfoRepository
import com.mohiva.play.silhouette.api.util.{Clock, Credentials, PasswordHasher, PasswordInfo}
import com.mohiva.play.silhouette.impl.exceptions.IdentityNotFoundException
import com.mohiva.play.silhouette.impl.providers.CredentialsProvider
import models.daos.bodyparser.SignIn
import models.daos.token.Token
import models.daos.user.User
import org.joda.time.DateTime
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.json.Json
import play.api.mvc.{Action, Controller, Cookie, DiscardingCookie}
import play.api.{Configuration, Logger}
import play.modules.reactivemongo.json._
import reactivemongo.bson.BSONDocument
import utils.DefaultEnv
import utils.auth.{IsOwner, WithRoles}
import utils.mail.MailService

import scala.concurrent.Future

class Session @Inject() (
                          val messagesApi: MessagesApi,
                          val userService: UserService,

                          passwordHasher: PasswordHasher,
                          silhouette: Silhouette[DefaultEnv],
                          authInfoRepository: AuthInfoRepository,
                          configuration: Configuration,
                          credentialsProvider: CredentialsProvider,
                          tokenService: TokenService,
                          val mailService: MailService,
                          clock: Clock
) extends Controller with I18nSupport {
  val logger = Logger("WRTC." + this.getClass.getSimpleName);
  implicit val ms = mailService;
  implicit val m = messagesApi;

  def randomString(length: Int) = scala.util.Random.alphanumeric.take(length).mkString

  def logout = silhouette.SecuredAction.async {
    implicit request =>
      silhouette.env.eventBus.publish(LogoutEvent(request.identity, request))
      logger.debug(s"LOGGIN OUT [ ${request.identity.lastName} ]\n")
      silhouette.env.authenticatorService.discard(
        request.authenticator,
        Ok.discardingCookies(DiscardingCookie(
          "X-Auth-Token"
        ))
      )
  }

  /**
   *  create a new user
   */

  def signUp(generated: Boolean) = silhouette.SecuredAction(WithRoles("Administrator")).async(parse.json[User]) {
    implicit request =>
      {
        val curUser = request.body
        var curPassword: String = null
        val loginInfo = LoginInfo(CredentialsProvider.ID, curUser.email)

        var authInfo: PasswordInfo = null
        //logger.debug(s"SIGN UP password [${authInfo}] generated [${generated} ${generated.getClass}] password [${"".isEmpty}] random string:[${randomString(8)}]")
        if (generated) {

          curPassword = randomString(8)
          //logger.debug(s"SIGN UP with generated pwd")
          authInfo = passwordHasher.hash(curPassword)
        } else if (!curUser.password.getOrElse("").isEmpty) {
          //logger.debug(s"SIGN UP with user pwd")
          curPassword = curUser.password.get
          authInfo = passwordHasher.hash(curPassword)
        } else {
          //logger.debug(s"SIGN UP error")
          Future.successful(BadRequest(Json.obj("res" -> "Please specify the password")))
        }

        //logger.debug(s"SIGN UP [${curUser.lastName}] ${loginInfo.providerID} ${loginInfo.providerKey}")
        userService.retrieve(loginInfo).flatMap {
          case Some(user) => {
            Future.successful(Conflict(Json.obj("res" -> "error user already exists")))
          }
          case curUser.password if (curUser.password.isEmpty) => {
            Future.successful(BadRequest(Json.obj("res" -> "Please specify the password")))
          }
          case None => {
            val user = curUser.copy(
              userID = Some(UUID.randomUUID()),
              loginInfo = Some(loginInfo),
              password = None,
              updated_date = Some(new DateTime())
            )
            for {
              // avatar <- avatarService.retrieveURL(data.email)
              user <- userService.save(user)
              authInfo <- authInfoRepository.add(loginInfo, authInfo)
              authenticator <- silhouette.env.authenticatorService.create(loginInfo)
              authenticator <- Future.successful(authenticator.copy(expirationDateTime = new DateTime().plusDays(30)))
              token <- silhouette.env.authenticatorService.init(authenticator)
              result <- silhouette.env.authenticatorService.embed(token, Ok(Json.obj("res" -> Json.obj("message" -> "Account created", "user" -> user, "token" -> token, "expirationDate" -> authenticator.expirationDateTime))))
            } yield {
              //Mailer.welcome(user, curUser.email, curPassword)
              silhouette.env.eventBus.publish(SignUpEvent(user, request))
              silhouette.env.eventBus.publish(LoginEvent(user, request))
              result
            }
          }
          case err => Future.successful(BadRequest(Json.obj("res" -> "Please specify the password $err")))
        }
      }
  }

  /**
   * sign in controller
   */

  /**
   * Authenticates a user against the credentials provider.
   *
   * @return The result to display.
   */

  //reset password
  def modifyPassword = silhouette.SecuredAction.async(parse.json) {

    implicit request =>
      {
        val user = request.identity
        val oldPassword = (request.body \ "oldpassword").asOpt[String].getOrElse("")
        val newPassword = (request.body \ "newpassword").asOpt[String].getOrElse("")

        credentialsProvider.authenticate(Credentials(user.email, oldPassword)).flatMap { loginInfo =>
          authInfoRepository.save(loginInfo, passwordHasher.hash(newPassword))
          silhouette.env.authenticatorService.create(user.loginInfo.get).flatMap { authenticator =>
            silhouette.env.eventBus.publish(LoginEvent(user, request))
            silhouette.env.authenticatorService.init(authenticator).flatMap {
              token =>
                silhouette.env.authenticatorService.embed(token, Ok(Json.obj("res" -> Json.obj("token" -> token))))
            }
          }
        }.recover {
          case e: ProviderException =>
            BadRequest(Json.obj("res" -> "Does not match current password!"))
        }
      }
  }

  def closeAccount(email: String) = silhouette.SecuredAction(IsOwner(email)).async {
    val logInfo = new LoginInfo(credentialsProvider.id, email)
    for {
      // avatar <- avatarService.retrieveURL(data.email)
      user <- userService.remove(BSONDocument("loginInfo" -> Json.toJson(email)))
      removedUser <- authInfoRepository.remove[PasswordInfo](logInfo)
    } yield {
      Ok
    }
  }

  //reset password
  def resetPasswordFromToken() = Action.async(parse.json) {
    implicit request =>
      {
        val tokenId = (request.body \ "token").asOpt[String].getOrElse("")
        val password = (request.body \ "password").asOpt[String].getOrElse("")

        if (password == "") {
          Future.successful(BadRequest(s"No password found"))
        } else {
          tokenService.retrieve(tokenId).flatMap {
            case Some(token) => {
              val tokenEmail = token.entity
              val loginInfo = LoginInfo(CredentialsProvider.ID, tokenEmail)
              userService.retrieve(loginInfo).flatMap {
                case Some(user) => {
                  val authInfo = passwordHasher.hash(password)
                  authInfoRepository.save(loginInfo, authInfo)
                  silhouette.env.authenticatorService.create(user.loginInfo.get).flatMap { authenticator =>
                    silhouette.env.eventBus.publish(LoginEvent(user, request))
                    tokenService.consume(tokenId)
                    silhouette.env.authenticatorService.init(authenticator).flatMap {
                      token =>
                        silhouette.env.authenticatorService.embed(token, Ok(Json.obj("res" -> Json.obj("token" -> token))))
                    }
                  }
                }
                case None => Future.failed(new RuntimeException("Couldn't find user"))
              }
            }
            case None => {
              Future.successful(NotFound(Json.obj("res" -> "invalid token")))
            }
          }
        }
      }
  }

  // forgotten password
  def forgottenPassword(email: String) = Action.async {
    implicit request =>
      {

        val loginInfo = LoginInfo(CredentialsProvider.ID, email)
        val selecUser = BSONDocument("loginInfo" -> Json.toJson(loginInfo))
        userService.find(selecUser).flatMap {
          userArr =>
            {
              userArr match {
                case userArr if (userArr.length > 0) => {

                  val tId = UUID.randomUUID()
                  val token = new Token(id = Some(tId), entity = email)
                  tokenService.create(email, token).map {
                    isError =>
                      isError.inError match {
                        case true => {
                          InternalServerError(Json.obj("res" -> "an error"))
                        }
                        case false => {
                          //Mailer.forgotPassword(email, s"/#/app/password/reset/" + tId.toString)
                          Ok(Json.obj("res" -> "pwd reset email sent to user."))
                        }
                      }
                  }
                }
                case _ => {
                  Future.successful(NotFound(Json.obj("res" -> "not a valid email")))
                }
              }
            }
        }
      }
  }

  def authenticate = Action.async(parse.json[SignIn]) { implicit request =>
    {
      val data = request.body
      //logger.info(s"authentication ==> ${data}")
      val credentials = Credentials(data.email, data.password)
      credentialsProvider.authenticate(credentials).flatMap { loginInfo =>

        userService.retrieve(loginInfo).flatMap {

          case Some(user) => silhouette.env.authenticatorService.create(loginInfo).map {
            authenticator =>
              authenticator.copy(
                expirationDateTime = new DateTime().plusDays(30)
              )
          }.flatMap { authenticator =>
            silhouette.env.eventBus.publish(LoginEvent(user, request))
            silhouette.env.authenticatorService.init(authenticator).flatMap { v =>
              silhouette.env.authenticatorService.embed(v, Ok(Json.obj("res" -> Json.obj("user" -> user, "token" -> v, "expirationDate" -> authenticator.expirationDateTime))))
            }
          }
          case None => Future.failed(new IdentityNotFoundException("Couldn't find user"))

          /*
          case Some(user) => {
            val c = configuration.underlying
            silhouette.env.authenticatorService.create(loginInfo).map {
              authenticator =>
                val authenticatorRenew = authenticator.copy(
                  idleTimeout = Some(30 days), expirationDateTime = new DateTime().plusDays(30)
                )
                silhouette.env.eventBus.publish(LoginEvent(user, request))
                silhouette.env.authenticatorService.init(authenticatorRenew).flatMap { v =>
                  silhouette.env.authenticatorService.embed(v, Ok(Json.obj("res" -> Json.obj("user" -> user, "token" -> v, "expirationDate" -> authenticatorRenew.expirationDateTime))))
                }
            }
          }
          case None => Future.failed(new IdentityNotFoundException("Couldn't find user"))
            */
        }
      }.recover {
        case e: ProviderException =>
          BadRequest(Json.obj("res" -> e.getMessage))
      }
    }
  }

  def signUpWithValidation() = Action.async(parse.json[User]) {
    implicit request =>
      {
        val curUser = request.body
        var curPassword: String = null
        val loginInfo = LoginInfo(CredentialsProvider.ID, curUser.email)

        var authInfo: PasswordInfo = null
        //logger.debug(s"SIGN UP password [${authInfo}]")
        if (!curUser.password.getOrElse("").isEmpty) {

          val curPassword = curUser.password.get
          val authInfo = passwordHasher.hash(curPassword)

          //logger.debug(s"SIGN UP with user pwd $curPassword")

          //logger.debug(s"SIGN UP [${curUser.lastName}] ${loginInfo.providerID} ${loginInfo.providerKey}")
          userService.retrieve(loginInfo, Some(true)).flatMap {
            case Some(user) => {
              Future.successful(Conflict(Json.obj("res" -> "error user already exists")))
            }
            case None => {
              val user = curUser.copy(
                userID = Some(UUID.randomUUID()),
                loginInfo = Some(loginInfo),
                active = Some(false),
                password = None,
                password_info = Some(authInfo),
                updated_date = Some(new DateTime())
              )

              val tId = UUID.randomUUID()
              val token = new Token(id = Some(tId), entity = curUser.email)
              val emailTokenFuture = tokenService.create(curUser.email, token).flatMap {
                isError =>
                  isError.inError match {
                    case true => {
                      Future.successful(Option.empty)
                    }
                    case false => {
                      userService.save(user).map {
                        user =>
                          {

                            //Mailer.emailValidation(curUser.email, s"/#/app/account/validate/" + tId.toString, user.firstName + " " + user.lastName)
                            Some(user)
                          }
                      }
                    }
                  }
              }

              for {
                // avatar <- avatarService.retrieveURL(data.email)
                emailToken <- emailTokenFuture
                //authenticator <- silhouette.env.authenticatorService.create(loginInfo)
              } yield {
                emailToken match {
                  case Some(user) => Ok(s"$user")
                  case None => InternalServerError(s"Empty token/user")
                  case _ => InternalServerError(s"Error")
                }
              }
            }
            case err => Future.successful(BadRequest(Json.obj("res" -> "Please specify the password $err")))
          }

        } else {
          logger.debug(s"SIGN UP error")
          Future.successful(BadRequest(Json.obj("res" -> "Please specify the password")))

        }

      }
  }

  def authenticateWithValidation = Action.async(parse.json[SignIn]) { implicit request =>
    {
      val data = request.body
      //logger.info(s"authentication ==> ${data}")
      val credentials = Credentials(data.email, data.password)
      credentialsProvider.authenticate(credentials).flatMap { loginInfo =>

        userService.retrieve(loginInfo, active = Some(true)).flatMap {

          case Some(user) => silhouette.env.authenticatorService.create(loginInfo).map {
            authenticator =>
              authenticator.copy(
                expirationDateTime = new DateTime().plusDays(30)
              )
          }.flatMap { authenticator =>
            silhouette.env.eventBus.publish(LoginEvent(user, request))
            silhouette.env.authenticatorService.init(authenticator).flatMap { v =>
              silhouette.env.authenticatorService.embed(v, Ok(Json.obj("res" -> Json.obj("user" -> user, "token" -> v, "expirationDate" -> authenticator.expirationDateTime))).withCookies(
                Cookie(
                  name = "X-Auth-Token",
                  value = v.toString,
                  maxAge = Some(30 * 60 * 1000),
                  secure = false,
                  httpOnly = false
                )
              ))
            }
          }
          case None => Future.failed(new IdentityNotFoundException("Couldn't find user"))
        }
      }.recover {
        case e: ProviderException =>
          BadRequest(Json.obj("res" -> e.getMessage))
      }
    }
  }

  def userValidation(token: String) = Action.async {
    implicit request =>
      {
        val tokenId = token

        tokenService.retrieve(tokenId).flatMap {
          case Some(token) => {
            val tokenEmail = token.entity
            val loginInfo = LoginInfo(CredentialsProvider.ID, tokenEmail)
            val selec = BSONDocument("loginInfo" -> Json.toJson(loginInfo))
            val modifier = BSONDocument("$set" -> BSONDocument("active" -> true))
            val modifierPassInfo = BSONDocument("$unset" -> BSONDocument("password_info" -> ""))
            userService.update(selec, modifier).flatMap {
              case Some(user) => {

                val authInfo = user.password_info.get
                for {
                  token <- tokenService.consume(tokenId)
                  repo <- authInfoRepository.add(loginInfo, authInfo)
                  user <- userService.update(selec, modifierPassInfo)
                } yield {
                  if (token.ok) {
                    Ok
                  } else {
                    InternalServerError
                  }

                }
              }
              case None => Future.successful(NotFound)
            }

          }
          case None => {
            Future.successful(NotFound(Json.obj("res" -> "invalid token")))
          }
        }
      }
  }

}
