package com.logai.common.service;

import jakarta.mail.*;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Properties;

@Slf4j
@Service
public class EmailService {

    @Value("${email.smtp.host}")
    private String host;

    @Value("${email.smtp.port}")
    private String port;

    @Value("${email.smtp.username}")
    private String username;

    @Value("${email.smtp.password}")
    private String password;

    @Value("${email.smtp.from-name}")
    private String fromName;

    public void sendVerificationEmail(String toEmail, String verificationCode, String type) {
        try {
            Properties props = new Properties();
            props.put("mail.smtp.host", host);
            props.put("mail.smtp.port", port);
            props.put("mail.smtp.auth", "true");
            props.put("mail.smtp.ssl.enable", "true");

            Session session = Session.getInstance(props, new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(username, password);
                }
            });

            Message message = new MimeMessage(session);
            message.setFrom(new InternetAddress(username, fromName, "UTF-8"));
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(toEmail));

            String subject = type.equals("login") ? "Your LogAI verification code" : "Your LogAI registration verification code";
            message.setSubject(subject);

            String htmlContent = buildEmailContent(verificationCode, type);
            message.setContent(htmlContent, "text/html; charset=UTF-8");

            Transport.send(message);
            log.info("邮件发送成功，验证码：{}，发送至：{}", verificationCode, toEmail);

        } catch (Exception e) {
            log.error("发送邮件失败：{}", e.getMessage(), e);
            throw new RuntimeException("Failed to send email: " + e.getMessage()); // 邮件发送失败：
        }
    }

    private String buildEmailContent(String verificationCode, String type) {
        String title = type.equals("login") ? "Your LogAI verification code" : "Your LogAI registration verification code";
        String description = type.equals("login") ? "Your login verification code is:" : "Your registration verification code is:";

        return String.format("""
                <div style="font-family:Arial,sans-serif;max-width:520px;margin:auto;padding:20px;border:1px solid #eee;border-radius:8px;">
                  <h2 style="color:#333;">%s</h2>
                  <p>Hello,</p>
                  <p>%s</p>
                  <div style="font-size:28px;font-weight:bold;letter-spacing:4px;padding:12px 16px;margin:20px 0;text-align:center;background:#f7f7f7;border-radius:6px;">
                    %s
                  </div>
                  <p style="color:#666;">The validation code is valid for 10 minutes. Please use it as soon as possible. If this is not your own operation, please ignore this message.</p>
                  <hr style="border:none;border-top:1px solid #eee;margin:20px 0;">
                  <p style="font-size:12px;color:#999;">This email is sent automatically by the system. Please do not reply directly.</p>
                </div>
                """, title, description, verificationCode);
    }
}