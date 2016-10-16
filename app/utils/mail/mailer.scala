package utils.mail

/**
 * Created by madalien on 31/05/16.
 */

import models.daos.user.User
import play.api.i18n.Messages
import play.twirl.api.Html
import views.html.mails

object Mailer {

  implicit def html2String(html: Html): String = html.toString

  def welcome(user: User, identifier: String, pwd: String)(implicit ms: MailService, m: Messages) {
    ms.sendEmailAsync(user.email)(
      subject = Messages("web.mail.welcome.subject"),
      bodyHtml = mails.welcome(user.firstName, identifier, pwd),
      bodyText = mails.welcomeTxt(user.firstName, identifier, pwd)
    )
  }

  def forgotPassword(email: String, link: String)(implicit ms: MailService, m: Messages) {
    ms.sendEmailAsync(email)(
      subject = Messages("web.mail.forgotpwd.subject"),
      bodyHtml = mails.forgotPassword(email, link),
      bodyText = mails.forgotPasswordTxt(email, link)
    )
  }

  def emailValidation(email: String, link: String, name: String)(implicit ms: MailService, m: Messages) {
    val siteUrl = Messages("web.mail.sign")
    ms.sendEmailAsync(email)(
      subject = Messages("web.mail.validation.subject", siteUrl),
      bodyHtml = mails.emailValidation(name, link, siteUrl),
      bodyText = mails.emailValidationTxt(name, link, siteUrl)
    )
  }

}
