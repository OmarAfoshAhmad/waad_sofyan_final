package com.waad.tba.modules.preauthorization.service;

import com.waad.tba.common.file.FileStorageService;
import com.waad.tba.common.file.FileUploadResult;
import com.waad.tba.modules.preauthorization.entity.PreAuthEmailAttachment;
import com.waad.tba.modules.preauthorization.entity.PreAuthEmailRequest;
import com.waad.tba.modules.preauthorization.repository.PreAuthEmailAttachmentRepository;
import com.waad.tba.modules.preauthorization.repository.PreAuthEmailRequestRepository;
import com.waad.tba.modules.provider.repository.ProviderRepository;
import com.waad.tba.modules.systemadmin.entity.EmailSettings;
import com.waad.tba.modules.systemadmin.repository.EmailSettingsRepository;
import jakarta.mail.*;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.search.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.Properties;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailPreAuthService {

    private final EmailSettingsRepository emailSettingsRepository;
    private final PreAuthEmailRequestRepository emailRequestRepository;
    private final PreAuthEmailAttachmentRepository emailAttachmentRepository;
    private final ProviderRepository providerRepository;
    private final com.waad.tba.modules.member.repository.MemberRepository memberRepository;
    private final com.waad.tba.modules.medicaltaxonomy.repository.MedicalServiceRepository medicalServiceRepository;
    private final FileStorageService fileStorageService;
    private final com.waad.tba.modules.preauthorization.mapper.PreAuthEmailMapper emailMapper;

    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<com.waad.tba.modules.preauthorization.dto.PreAuthEmailRequestDto> getAll(Boolean processed, org.springframework.data.domain.Pageable pageable) {
        org.springframework.data.domain.Page<PreAuthEmailRequest> entities;
        if (processed != null) {
            entities = emailRequestRepository.findByProcessed(processed, pageable);
        } else {
            entities = emailRequestRepository.findAll(pageable);
        }
        return entities.map(emailMapper::toDto);
    }

    @Transactional(readOnly = true)
    public com.waad.tba.modules.preauthorization.dto.PreAuthEmailRequestDto getById(Long id) {
        return emailRequestRepository.findById(id)
                .map(emailMapper::toDto)
                .orElseThrow(() -> new com.waad.tba.common.exception.ResourceNotFoundException("Email request not found"));
    }

    @Transactional
    public void processEmails() {
        EmailSettings settings = emailSettingsRepository.findFirstByIsActiveTrueOrderByIdDesc().orElse(null);
        if (settings == null || !Boolean.TRUE.equals(settings.getListenerEnabled())) {
            log.debug("Email listener is disabled or settings not found.");
            return;
        }

        Properties props = new Properties();
        String protocol = "imaps";
        if ("NONE".equalsIgnoreCase(settings.getEncryptionType())) {
            protocol = "imap";
        }

        props.put("mail.store.protocol", protocol);
        props.put("mail." + protocol + ".host", settings.getImapHost());
        props.put("mail." + protocol + ".port", String.valueOf(settings.getImapPort()));
        props.put("mail." + protocol + ".timeout", "10000");

        try {
            Session session = Session.getInstance(props);
            try (Store store = session.getStore(protocol)) {
                store.connect(settings.getImapHost(), settings.getImapUsername(), settings.getImapPassword());
                try (Folder inbox = store.getFolder("INBOX")) {
                    inbox.open(Folder.READ_WRITE);

                    // Create search term for unread emails
                    SearchTerm unreadTerm = new FlagTerm(new Flags(Flags.Flag.SEEN), false);
                    Message[] allUnread = inbox.search(unreadTerm);
                    log.info("Total unread emails in inbox: {}", allUnread.length);

                    SearchTerm searchTerm = unreadTerm;
                    
                    // Add subject filter to search term if enabled
                    if (settings.getSubjectFilter() != null && !settings.getSubjectFilter().isEmpty()) {
                        log.info("Applying subject filter: '{}'", settings.getSubjectFilter());
                        searchTerm = new AndTerm(unreadTerm, new SubjectTerm(settings.getSubjectFilter()));
                    }

                    Message[] messages = inbox.search(searchTerm);
                    log.info("Found {} emails matching both UNREAD and SUBJECT criteria", messages.length);

                    // If NO unread messages match, but we have a subject filter, 
                    // maybe the user already read them while testing. 
                    // Let's try searching by subject only (without SEEN flag) if unread count is 0
                    if (messages.length == 0 && settings.getSubjectFilter() != null && !settings.getSubjectFilter().isEmpty()) {
                        log.info("No unread matches found. Checking for READ emails matching subject...");
                        messages = inbox.search(new SubjectTerm(settings.getSubjectFilter()));
                        log.info("Found {} total emails (Read+Unread) matching subject filter", messages.length);
                    }

                    // Process up to 50 messages
                    int limit = Math.min(messages.length, 50);
                    int processedCount = 0;
                    for (int i = 0; i < limit; i++) {
                        Message message = messages[i];
                        try {
                            if (processMessage(message, settings)) {
                                processedCount++;
                            }
                            // message.setFlag(Flags.Flag.SEEN, true); // Don't force seen here if we want to allow re-runs
                        } catch (Exception e) {
                            log.error("Failed to process email message index {}: {}", i, e.getMessage());
                        }
                    }
                    log.info("Completed processing. Successfully imported {} new requests.", processedCount);
                    if (messages.length > limit) {
                        log.info("Processed {} messages, {} remaining. Will fetch more in next sync.", limit, messages.length - limit);
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to process emails from IMAP server: {}", e.getMessage());
        }
    }

    private boolean processMessage(Message message, EmailSettings settings) throws MessagingException, IOException {
        String messageId = "";
        if (message instanceof MimeMessage mimeMessage) {
            messageId = mimeMessage.getMessageID();
        } else {
            String[] headers = message.getHeader("Message-ID");
            messageId = (headers != null && headers.length > 0) ? headers[0] : "MSG-" + System.currentTimeMillis();
        }

        if (emailRequestRepository.existsByMessageId(messageId)) {
            log.debug("Message {} already processed, skipping", messageId);
            return false;
        }

        Address[] from = message.getFrom();
        String senderEmail = "unknown";
        String senderName = "unknown";
        
        if (from != null && from.length > 0) {
            if (from[0] instanceof InternetAddress ia) {
                senderEmail = ia.getAddress().trim();
                senderName = ia.getPersonal();
            } else {
                senderEmail = from[0].toString();
            }
        }
        
        String subject = message.getSubject();
        log.info("Processing email: Subject='{}', From='{}' ({})", subject, senderEmail, senderName);

        Date receivedDate = message.getReceivedDate();
        LocalDateTime receivedAt = receivedDate != null ? receivedDate.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime() : LocalDateTime.now();

        PreAuthEmailRequest request = PreAuthEmailRequest.builder()
                .messageId(messageId)
                .senderEmail(senderEmail)
                .senderName(senderName)
                .subject(subject)
                .receivedAt(receivedAt)
                .processed(false)
                .build();

        // Initial identification
        identifyProvider(request);
        identifyMember(request);
        identifyService(request);

        // Process content and attachments
        Object content = message.getContent();
        if (content instanceof String) {
            String text = (String) content;
            if (message.isMimeType("text/html")) {
                request.setBodyHtml(text);
            } else {
                request.setBodyText(text);
            }
        } else if (content instanceof Multipart multipart) {
            parseMultipart(multipart, request);
        }

        // Re-identify in case content-based logic needs the body (which we just set)
        identifyMember(request);
        identifyService(request);

        // check if still blocked if settings require only from providers
        if (Boolean.TRUE.equals(settings.getOnlyFromProviders()) && request.getProviderId() == null) {
            log.info("Skipping email from {} - Not a registered provider and onlyFromProviders is enabled", senderEmail);
            return false;
        }

        // Save final request
        emailRequestRepository.save(request);
        message.setFlag(Flags.Flag.SEEN, true);
        return true;
    }

    @Transactional
    public void reidentifyRequest(Long requestId) {
        PreAuthEmailRequest request = emailRequestRepository.findById(requestId)
                .orElseThrow(() -> new com.waad.tba.common.exception.ResourceNotFoundException("Email request not found"));

        if (Boolean.TRUE.equals(request.getProcessed())) {
            throw new com.waad.tba.common.exception.BusinessRuleException("لا يمكن إعادة تعريف طلب تمت معالجته بالفعل.");
        }

        identifyProvider(request);
        identifyMember(request);
        identifyService(request);

        emailRequestRepository.save(request);
        log.info("Email request {} re-identified: Provider={}, Member={}, Service={}", 
            requestId, request.getProviderId(), request.getMemberId(), request.getDetectedServiceId());
    }

    private void identifyProvider(PreAuthEmailRequest request) {
        if (request.getSenderEmail() == null) return;
        providerRepository.findByEmailIgnoreCase(request.getSenderEmail())
                .ifPresent(p -> request.setProviderId(p.getId()));
    }

    private void identifyService(PreAuthEmailRequest request) {
        String content = request.getBodyText();
        if (content == null || content.isEmpty()) content = request.getBodyHtml();
        if (content == null || content.isEmpty()) return;

        java.util.regex.Pattern codePattern = java.util.regex.Pattern.compile("(SVC-|CODE:|Service:)\\s*([A-Z0-9-]{3,10})", java.util.regex.Pattern.CASE_INSENSITIVE);
        java.util.regex.Matcher matcher = codePattern.matcher(content);
        if (matcher.find()) {
            String code = matcher.group(2).trim();
            medicalServiceRepository.findActiveByCode(code).ifPresent(s -> request.setDetectedServiceId(s.getId()));
        }
    }

    private void identifyMember(PreAuthEmailRequest request) {
        String content = request.getBodyText();
        if (content == null || content.isEmpty()) content = request.getBodyHtml();
        if (content == null || content.isEmpty()) return;

        // Try Barcode: WAD-YYYY-NNNNNNNN
        java.util.regex.Pattern barcodePattern = java.util.regex.Pattern.compile("WAD-\\d{4}-\\d{8}");
        java.util.regex.Matcher barcodeMatcher = barcodePattern.matcher(content);
        if (barcodeMatcher.find()) {
            memberRepository.findByBarcode(barcodeMatcher.group()).ifPresent(m -> request.setMemberId(m.getId()));
            return;
        }

        // Try Card Number: Digit sequence (e.g. 12345678)
        java.util.regex.Pattern cardPattern = java.util.regex.Pattern.compile("\\b\\d{4,12}(-[WHSDFBSR]\\w*)?\\b");
        java.util.regex.Matcher cardMatcher = cardPattern.matcher(content);
        while (cardMatcher.find()) {
            String candidate = cardMatcher.group();
            var member = memberRepository.findByCardNumber(candidate);
            if (member.isPresent()) {
                request.setMemberId(member.get().getId());
                return;
            }
        }
    }

    private void parseMultipart(Multipart multipart, PreAuthEmailRequest request) throws MessagingException, IOException {
        for (int i = 0; i < multipart.getCount(); i++) {
            BodyPart bodyPart = multipart.getBodyPart(i);
            if (bodyPart.isMimeType("text/plain")) {
                if (request.getBodyText() == null) {
                    request.setBodyText((String) bodyPart.getContent());
                }
            } else if (bodyPart.isMimeType("text/html")) {
                if (request.getBodyHtml() == null) {
                    request.setBodyHtml((String) bodyPart.getContent());
                }
            } else if (bodyPart.isMimeType("multipart/*")) {
                parseMultipart((Multipart) bodyPart.getContent(), request);
            } else if (Part.ATTACHMENT.equalsIgnoreCase(bodyPart.getDisposition()) || bodyPart.getFileName() != null) {
                saveAttachment(bodyPart, request);
            }
        }
    }

    private void saveAttachment(BodyPart part, PreAuthEmailRequest request) throws MessagingException, IOException {
        String fileName = part.getFileName();
        if (fileName == null || fileName.isEmpty()) {
            fileName = "attachment_" + System.currentTimeMillis();
        }

        try (InputStream is = part.getInputStream()) {
            // Use the updated FileStorageService
            FileUploadResult result = fileStorageService.upload(is, fileName, part.getContentType(), (long) part.getSize(), "preauth_emails");
            
            PreAuthEmailAttachment attachment = PreAuthEmailAttachment.builder()
                    .emailRequest(request)
                    .originalFileName(result.getFileName())
                    .storedFileName(result.getFileKey())
                    .filePath(result.getFilePath())
                    .fileType(result.getContentType())
                    .fileSize(result.getSize())
                    .build();
            
            emailAttachmentRepository.save(attachment);
            request.getAttachments().add(attachment);
        }
    }

    @Transactional
    public void markAsProcessed(Long requestId, Long preAuthId) {
        emailRequestRepository.findById(requestId).ifPresent(request -> {
            request.setProcessed(true);
            request.setConvertedToPreAuthId(preAuthId);
            emailRequestRepository.save(request);
            log.info("Email request {} marked as processed and linked to pre-auth {}", requestId, preAuthId);
        });
    }
}
