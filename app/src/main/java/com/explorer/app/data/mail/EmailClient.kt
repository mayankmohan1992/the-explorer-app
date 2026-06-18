package com.explorer.app.data.mail

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Properties
import javax.mail.*
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMessage

data class EmailMessage(
    val id: String,
    val sender: String,
    val senderAddress: String,
    val recipient: String,
    val subject: String,
    val date: String,
    val body: String,
    val isRead: Boolean = false
)

object EmailClient {

    // Detailed high-quality mock data for testing and offline sandbox demo
    val mockEmails = listOf(
        EmailMessage(
            id = "mock_1",
            sender = "DeepMind Coding Agent",
            senderAddress = "agent@deepmind.google.com",
            recipient = "user@explorer.app",
            subject = "Project Update: The Explorer App Layout Approved",
            date = "Jun 15, 2026",
            body = "Hello! The launcher, browser, RSS reader, and custom email integrations have been initialized. You are currently viewing the simulated email box. Enter your credentials in the account settings to fetch real IMAP logs.",
            isRead = false
        ),
        EmailMessage(
            id = "mock_2",
            sender = "GitHub",
            senderAddress = "noreply@github.com",
            recipient = "user@explorer.app",
            subject = "[GitHub] Security Alert: New SSH key added to account",
            date = "Jun 14, 2026",
            body = "A new public SSH key was associated with your account. If this was you, no action is needed. If you do not recognize this activity, please audit your credentials immediately.",
            isRead = true
        ),
        EmailMessage(
            id = "mock_3",
            sender = "Tech Crunch Feed",
            senderAddress = "newsletter@techcrunch.com",
            recipient = "user@explorer.app",
            subject = "Weekly Round-Up: AI and Mobile Launcher Ecosystems",
            date = "Jun 12, 2026",
            body = "This week we dive deep into custom Android launchers, their security sandbox environments, and how AI-enabled mobile devices are shifting user interaction patterns away from single-app icons to dynamic tools panels.",
            isRead = true
        )
    )

    suspend fun fetchEmails(
        host: String,
        port: String,
        user: String,
        pass: String,
        folderName: String = "INBOX"
    ): List<EmailMessage> = withContext(Dispatchers.IO) {
        val emailList = mutableListOf<EmailMessage>()
        
        val properties = Properties().apply {
            put("mail.store.protocol", "imaps")
            put("mail.imaps.host", host)
            put("mail.imaps.port", port)
            put("mail.imaps.timeout", "10000")
            put("mail.imaps.connectiontimeout", "10000")
        }

        val session = Session.getInstance(properties, null)
        var store: Store? = null
        var folder: Folder? = null
        try {
            store = session.getStore("imaps")
            store.connect(host, user, pass)
            
            folder = store.getFolder(folderName)
            folder.open(Folder.READ_ONLY)
            
            val count = folder.messageCount
            val limit = if (count > 20) count - 20 else 1
            
            if (count > 0) {
                val messages = folder.getMessages(limit, count)
                // Reverse to show newest first
                for (i in messages.indices.reversed()) {
                    val msg = messages[i]
                    val fromAddresses = msg.from
                    val senderName = if (fromAddresses.isNotEmpty()) fromAddresses[0].toString() else "Unknown"
                    val senderEmail = if (fromAddresses.isNotEmpty() && fromAddresses[0] is InternetAddress) {
                        (fromAddresses[0] as InternetAddress).address
                    } else senderName

                    val subject = msg.subject ?: "(No Subject)"
                    val dateStr = msg.sentDate?.toString() ?: ""
                    
                    var bodyText = ""
                    try {
                        bodyText = when {
                            msg.isMimeType("text/plain") -> msg.content.toString()
                            msg.isMimeType("text/html") -> msg.content.toString()
                            msg.isMimeType("multipart/*") -> {
                                val multipart = msg.content as Multipart
                                var result = ""
                                for (j in 0 until multipart.count) {
                                    val bodyPart = multipart.getBodyPart(j)
                                    if (bodyPart.isMimeType("text/plain")) {
                                        result += bodyPart.content.toString()
                                    } else if (bodyPart.isMimeType("text/html")) {
                                        result += bodyPart.content.toString()
                                    }
                                }
                                result
                            }
                            else -> msg.description ?: ""
                        }
                    } catch (e: Exception) {
                        bodyText = "[Non-text content or parse error]"
                    }

                    emailList.add(
                        EmailMessage(
                            id = msg.messageNumber.toString(),
                            sender = senderName,
                            senderAddress = senderEmail,
                            recipient = user,
                            subject = subject,
                            date = dateStr,
                            body = bodyText.take(1000), // Cap length
                            isRead = msg.flags.contains(Flags.Flag.SEEN)
                        )
                    )
                }
            }
        } finally {
            try { folder?.close(false) } catch (e: Exception) {}
            try { store?.close() } catch (e: Exception) {}
        }
        
        emailList
    }

    suspend fun sendEmail(
        host: String,
        port: String,
        user: String,
        pass: String,
        to: String,
        subject: String,
        body: String
    ): Boolean = withContext(Dispatchers.IO) {
        val properties = Properties().apply {
            put("mail.smtp.auth", "true")
            put("mail.smtp.starttls.enable", "true")
            put("mail.smtp.host", host)
            put("mail.smtp.port", port)
            put("mail.smtp.timeout", "10000")
            put("mail.smtp.connectiontimeout", "10000")
        }

        val session = Session.getInstance(properties, object : Authenticator() {
            override fun getPasswordAuthentication(): PasswordAuthentication {
                return PasswordAuthentication(user, pass)
            }
        })

        try {
            val message = MimeMessage(session).apply {
                setFrom(InternetAddress(user))
                setRecipients(Message.RecipientType.TO, InternetAddress.parse(to))
                setSubject(subject)
                setText(body)
            }
            Transport.send(message)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
