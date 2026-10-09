package com.resilientechnology.starandcar.event.listener;

import com.resilientechnology.starandcar.event.PropertyCreatedEvent;
import com.resilientechnology.starandcar.record.PropertyDetailVO;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Component
public class PropertyEventListener {

    @Autowired
    private JavaMailSender mailSender;

    @Value("${file.template-dir}")
    private String templateDir;

    @Value("${spring.mail.username}")
    private String username;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handlePropertyCreated(PropertyCreatedEvent event) {
        PropertyDetailVO property = event.getPropertyDetailVO();
        try {
            sendEmail(property);
        } catch (Exception e) {
            // Log the exception (use Logger in production instead of System.err)
            System.err.printf("Error sending email asynchronously for property: %s. Error: %s%n", property.getTitle(), e.getMessage());
            e.printStackTrace();
        }
    }

    private void sendEmail(PropertyDetailVO property) throws IOException, MessagingException {
        // 1. Resolve and read the HTML template file
        Path templatePath = Paths.get(templateDir, "email_template.html");
        if (!Files.exists(templatePath)) {
            throw new IOException("Email template file not found at path: " + templatePath.toAbsolutePath());
        }

        // Read template file contents
        String htmlBody = Files.readString(templatePath, StandardCharsets.UTF_8);

        // 2. Replace placeholders with dynamic property data
        // (Assuming fields like getTitle(), getPrice(), getOwnerEmail() exist in PropertyDetailVO)
        htmlBody = htmlBody
                .replace("${title}", property.getTitle() != null ? property.getTitle() : "")
                .replace("${price}", property.getPrice() != null ? String.valueOf(property.getPrice()) : "N/A")
                .replace("${address}", property.getAddress() != null ? property.getAddress() : "");

        // 3. Send with this class's e-mail send below - the one proven send path.
        String subject = "Listing Successful! - Payment Details for Registration - Reference ID #" + Math.random()*100000;
        sendEmail(property.getEmail(), subject, htmlBody, true);
    }

    /**
     * The application's e-mail send - the code that has been working and tested in
     * production, configured entirely by application.yaml. Anything that needs to post an
     * e-mail makes one method call here; there is deliberately no second send implementation
     * anywhere else in the application.
     */
    public void sendEmail(String to, String subject, String body, boolean html) throws MessagingException {
        // 1. Create MIME message for HTML support
        MimeMessage mimeMessage = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");

        helper.setTo(to);
        helper.setFrom(username);
        helper.setSubject(subject);
        helper.setText(body, html); // Second parameter 'true' enables HTML parsing

        // 2. Send email
        mailSender.send(mimeMessage);
        System.out.printf("Email successfully sent to: %s%n", to);
    }
}