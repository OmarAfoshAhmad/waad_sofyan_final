package com.waad.tba.common.service;

import com.waad.tba.common.entity.SystemSetting;
import com.waad.tba.common.repository.SystemSettingRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class SettingsInitializationService {

    private final SystemSettingRepository settingRepository;

    public static final String CLAIM_SLA_DAYS_KEY = "CLAIM_SLA_DAYS";
    public static final int DEFAULT_CLAIM_SLA_DAYS = 10;

    public static final String PRE_APPROVAL_SLA_DAYS_KEY = "PRE_APPROVAL_SLA_DAYS";
    public static final int DEFAULT_PRE_APPROVAL_SLA_DAYS = 3;

    public static final String CLAIM_BACKDATED_MONTHS_KEY = "CLAIM_BACKDATED_MONTHS";
    public static final int DEFAULT_CLAIM_BACKDATED_MONTHS = 3;

    public static final String PASSWORD_RESET_METHOD_KEY = "PASSWORD_RESET_METHOD";
    public static final String PASSWORD_RESET_TOKEN_EXPIRY_MINUTES_KEY = "PASSWORD_RESET_TOKEN_EXPIRY_MINUTES";
    public static final String PASSWORD_RESET_OTP_EXPIRY_MINUTES_KEY = "PASSWORD_RESET_OTP_EXPIRY_MINUTES";
    public static final String PASSWORD_RESET_OTP_LENGTH_KEY = "PASSWORD_RESET_OTP_LENGTH";

    public static final String AI_CLASSIFIER_API_KEY = "AI_CLASSIFIER_API_KEY";
    public static final String AI_CLASSIFIER_MODEL = "AI_CLASSIFIER_MODEL";
    public static final String AI_CLASSIFIER_ENDPOINT = "AI_CLASSIFIER_ENDPOINT";
    public static final String DEFAULT_AI_CLASSIFIER_MODEL = "qwen/qwen2.5-14b-instruct:free";
    public static final String DEFAULT_AI_CLASSIFIER_ENDPOINT = "https://openrouter.ai/api/v1/chat/completions";

    public static final String BIOBERT_API_URL = "BIOBERT_API_URL";
    public static final String DEFAULT_BIOBERT_API_URL = "http://localhost:8000/predict";

    public static final String LOGO_URL_KEY = "LOGO_URL";
    public static final String FONT_FAMILY_KEY = "FONT_FAMILY";
    public static final String FONT_SIZE_BASE_KEY = "FONT_SIZE_BASE";
    public static final String SYSTEM_NAME_AR_KEY = "SYSTEM_NAME_AR";
    public static final String SYSTEM_NAME_EN_KEY = "SYSTEM_NAME_EN";

    public static final String BENEFICIARY_NUMBER_FORMAT_KEY = "BENEFICIARY_NUMBER_FORMAT";
    public static final String BENEFICIARY_NUMBER_PREFIX_KEY = "BENEFICIARY_NUMBER_PREFIX";
    public static final String BENEFICIARY_NUMBER_DIGITS_KEY = "BENEFICIARY_NUMBER_DIGITS";

    public static final String ELIGIBILITY_STRICT_MODE_KEY = "ELIGIBILITY_STRICT_MODE";
    public static final String WAITING_PERIOD_DAYS_DEFAULT_KEY = "WAITING_PERIOD_DAYS_DEFAULT";
    public static final String ELIGIBILITY_GRACE_PERIOD_DAYS_KEY = "ELIGIBILITY_GRACE_PERIOD_DAYS";

    @PostConstruct
    @Transactional
    public void initializeDefaultSettings() {
        log.info("🔧 Initializing system settings...");

        if (settingRepository.findBySettingKey(CLAIM_SLA_DAYS_KEY).isEmpty()) {
            SystemSetting slaSetting = SystemSetting.builder()
                    .settingKey(CLAIM_SLA_DAYS_KEY)
                    .settingValue(String.valueOf(DEFAULT_CLAIM_SLA_DAYS))
                    .valueType(SystemSetting.SettingValueType.INTEGER)
                    .description("Number of business days allowed for claim processing (SLA)")
                    .category("CLAIMS")
                    .isEditable(true)
                    .defaultValue(String.valueOf(DEFAULT_CLAIM_SLA_DAYS))
                    .validationRules("min:1,max:30")
                    .active(true)
                    .build();

            settingRepository.save(slaSetting);
            log.info("✅ Created default setting: {} = {}", CLAIM_SLA_DAYS_KEY, DEFAULT_CLAIM_SLA_DAYS);
        }

        if (settingRepository.findBySettingKey(PRE_APPROVAL_SLA_DAYS_KEY).isEmpty()) {
            SystemSetting preApprovalSlaSetting = SystemSetting.builder()
                    .settingKey(PRE_APPROVAL_SLA_DAYS_KEY)
                    .settingValue(String.valueOf(DEFAULT_PRE_APPROVAL_SLA_DAYS))
                    .valueType(SystemSetting.SettingValueType.INTEGER)
                    .description("Number of business days allowed for pre-approval processing (SLA)")
                    .category("PRE_APPROVALS")
                    .isEditable(true)
                    .defaultValue(String.valueOf(DEFAULT_PRE_APPROVAL_SLA_DAYS))
                    .validationRules("min:1,max:10")
                    .active(true)
                    .build();

            settingRepository.save(preApprovalSlaSetting);
            log.info("✅ Created default setting: {} = {}", PRE_APPROVAL_SLA_DAYS_KEY, DEFAULT_PRE_APPROVAL_SLA_DAYS);
        }

        if (settingRepository.findBySettingKey(CLAIM_BACKDATED_MONTHS_KEY).isEmpty()) {
            settingRepository.save(SystemSetting.builder()
                    .settingKey(CLAIM_BACKDATED_MONTHS_KEY)
                    .settingValue(String.valueOf(DEFAULT_CLAIM_BACKDATED_MONTHS))
                    .valueType(SystemSetting.SettingValueType.INTEGER)
                    .description(
                            "أقصى عدد أشهر سابقة يُسمح فيها بإدخال مطالبات قديمة انطلاقاً من الشهر الحالي. (0 = الشهر الحالي فقط)")
                    .category("CLAIMS")
                    .isEditable(true)
                    .defaultValue(String.valueOf(DEFAULT_CLAIM_BACKDATED_MONTHS))
                    .validationRules("min:0,max:24")
                    .active(true)
                    .build());
            log.info("✅ Created default setting: {} = {}", CLAIM_BACKDATED_MONTHS_KEY, DEFAULT_CLAIM_BACKDATED_MONTHS);
        }

        if (settingRepository.findBySettingKey(PASSWORD_RESET_METHOD_KEY).isEmpty()) {
            settingRepository.save(SystemSetting.builder()
                    .settingKey(PASSWORD_RESET_METHOD_KEY)
                    .settingValue("TOKEN")
                    .valueType(SystemSetting.SettingValueType.STRING)
                    .description("Password reset method. Allowed values: TOKEN or OTP")
                    .category("SECURITY")
                    .isEditable(true)
                    .defaultValue("TOKEN")
                    .validationRules("enum:TOKEN|OTP")
                    .active(true)
                    .build());
            log.info("✅ Created default setting: {} = TOKEN", PASSWORD_RESET_METHOD_KEY);
        }

        if (settingRepository.findBySettingKey(PASSWORD_RESET_TOKEN_EXPIRY_MINUTES_KEY).isEmpty()) {
            settingRepository.save(SystemSetting.builder()
                    .settingKey(PASSWORD_RESET_TOKEN_EXPIRY_MINUTES_KEY)
                    .settingValue("60")
                    .valueType(SystemSetting.SettingValueType.INTEGER)
                    .description("Password reset token validity in minutes")
                    .category("SECURITY")
                    .isEditable(true)
                    .defaultValue("60")
                    .validationRules("min:5,max:1440")
                    .active(true)
                    .build());
            log.info("✅ Created default setting: {} = 60", PASSWORD_RESET_TOKEN_EXPIRY_MINUTES_KEY);
        }

        if (settingRepository.findBySettingKey(PASSWORD_RESET_OTP_EXPIRY_MINUTES_KEY).isEmpty()) {
            settingRepository.save(SystemSetting.builder()
                    .settingKey(PASSWORD_RESET_OTP_EXPIRY_MINUTES_KEY)
                    .settingValue("10")
                    .valueType(SystemSetting.SettingValueType.INTEGER)
                    .description("Password reset OTP validity in minutes")
                    .category("SECURITY")
                    .isEditable(true)
                    .defaultValue("10")
                    .validationRules("min:1,max:60")
                    .active(true)
                    .build());
            log.info("✅ Created default setting: {} = 10", PASSWORD_RESET_OTP_EXPIRY_MINUTES_KEY);
        }

        if (settingRepository.findBySettingKey(PASSWORD_RESET_OTP_LENGTH_KEY).isEmpty()) {
            settingRepository.save(SystemSetting.builder()
                    .settingKey(PASSWORD_RESET_OTP_LENGTH_KEY)
                    .settingValue("6")
                    .valueType(SystemSetting.SettingValueType.INTEGER)
                    .description("Password reset OTP code length")
                    .category("SECURITY")
                    .isEditable(true)
                    .defaultValue("6")
                    .validationRules("min:4,max:10")
                    .active(true)
                    .build());
            log.info("✅ Created default setting: {} = 6", PASSWORD_RESET_OTP_LENGTH_KEY);
        }

        if (settingRepository.findBySettingKey(AI_CLASSIFIER_API_KEY).isEmpty()) {
            settingRepository.save(SystemSetting.builder()
                    .settingKey(AI_CLASSIFIER_API_KEY)
                    .settingValue("")
                    .valueType(SystemSetting.SettingValueType.STRING)
                    .description("API key used by AI classifier for facility price-list categorization")
                    .category("AI")
                    .isEditable(true)
                    .defaultValue("")
                    .active(true)
                    .build());
            log.info("✅ Created default setting: {} = <empty>", AI_CLASSIFIER_API_KEY);
        }

        if (settingRepository.findBySettingKey(AI_CLASSIFIER_MODEL).isEmpty()) {
            settingRepository.save(SystemSetting.builder()
                    .settingKey(AI_CLASSIFIER_MODEL)
                    .settingValue(DEFAULT_AI_CLASSIFIER_MODEL)
                    .valueType(SystemSetting.SettingValueType.STRING)
                    .description("Model ID used for AI classifier requests")
                    .category("AI")
                    .isEditable(true)
                    .defaultValue(DEFAULT_AI_CLASSIFIER_MODEL)
                    .active(true)
                    .build());
            log.info("✅ Created default setting: {} = {}", AI_CLASSIFIER_MODEL, DEFAULT_AI_CLASSIFIER_MODEL);
        }

        if (settingRepository.findBySettingKey(AI_CLASSIFIER_ENDPOINT).isEmpty()) {
            settingRepository.save(SystemSetting.builder()
                    .settingKey(AI_CLASSIFIER_ENDPOINT)
                    .settingValue(DEFAULT_AI_CLASSIFIER_ENDPOINT)
                    .valueType(SystemSetting.SettingValueType.STRING)
                    .description("OpenAI-compatible endpoint for AI classifier")
                    .category("AI")
                    .isEditable(true)
                    .defaultValue(DEFAULT_AI_CLASSIFIER_ENDPOINT)
                    .active(true)
                    .build());
            log.info("✅ Created default setting: {} = {}", AI_CLASSIFIER_ENDPOINT, DEFAULT_AI_CLASSIFIER_ENDPOINT);
        }

        if (settingRepository.findBySettingKey(BIOBERT_API_URL).isEmpty()) {
            settingRepository.save(SystemSetting.builder()
                    .settingKey(BIOBERT_API_URL)
                    .settingValue(DEFAULT_BIOBERT_API_URL)
                    .valueType(SystemSetting.SettingValueType.STRING)
                    .description("BioBERT/ClinicalBERT Python Microservice Endpoint")
                    .category("AI")
                    .isEditable(true)
                    .defaultValue(DEFAULT_BIOBERT_API_URL)
                    .active(true)
                    .build());
            log.info("✅ Created default setting: {} = {}", BIOBERT_API_URL, DEFAULT_BIOBERT_API_URL);
        }

        ensureDefaultSetting(LOGO_URL_KEY, "", SystemSetting.SettingValueType.STRING,
                "Public logo URL used by the frontend shell", "UI", true, "");
        ensureDefaultSetting(FONT_FAMILY_KEY, "Tajawal", SystemSetting.SettingValueType.STRING,
                "Default UI font family", "UI", true, "");
        ensureDefaultSetting(FONT_SIZE_BASE_KEY, "14", SystemSetting.SettingValueType.INTEGER,
                "Base UI font size in pixels", "UI", true, "min:10,max:20");
        ensureDefaultSetting(SYSTEM_NAME_AR_KEY, "نظام واعد الطبي", SystemSetting.SettingValueType.STRING,
                "Arabic system display name", "UI", true, "");
        ensureDefaultSetting(SYSTEM_NAME_EN_KEY, "TBA WAAD System", SystemSetting.SettingValueType.STRING,
                "English system display name", "UI", true, "");

        ensureDefaultSetting(BENEFICIARY_NUMBER_FORMAT_KEY, "PREFIX_SEQUENCE", SystemSetting.SettingValueType.STRING,
                "Beneficiary number generation format", "MEMBERS", true, "enum:PREFIX_SEQUENCE");
        ensureDefaultSetting(BENEFICIARY_NUMBER_PREFIX_KEY, "MEM", SystemSetting.SettingValueType.STRING,
                "Beneficiary number prefix", "MEMBERS", true, "");
        ensureDefaultSetting(BENEFICIARY_NUMBER_DIGITS_KEY, "6", SystemSetting.SettingValueType.INTEGER,
                "Beneficiary sequence digit count", "MEMBERS", true, "min:3,max:12");

        ensureDefaultSetting(ELIGIBILITY_STRICT_MODE_KEY, "false", SystemSetting.SettingValueType.BOOLEAN,
                "Use strict eligibility checks", "ELIGIBILITY", true, "");
        ensureDefaultSetting(WAITING_PERIOD_DAYS_DEFAULT_KEY, "30", SystemSetting.SettingValueType.INTEGER,
                "Default waiting period in days", "ELIGIBILITY", true, "min:0,max:365");
        ensureDefaultSetting(ELIGIBILITY_GRACE_PERIOD_DAYS_KEY, "7", SystemSetting.SettingValueType.INTEGER,
                "Eligibility grace period in days", "ELIGIBILITY", true, "min:0,max:90");
    }

    private void ensureDefaultSetting(
            String key,
            String defaultValue,
            SystemSetting.SettingValueType valueType,
            String description,
            String category,
            boolean editable,
            String validationRules) {
        if (settingRepository.findBySettingKey(key).isPresent()) {
            return;
        }

        settingRepository.save(SystemSetting.builder()
                .settingKey(key)
                .settingValue(defaultValue)
                .valueType(valueType)
                .description(description)
                .category(category)
                .isEditable(editable)
                .defaultValue(defaultValue)
                .validationRules(validationRules)
                .active(true)
                .build());
        log.info("✅ Created default setting: {} = {}", key, defaultValue);
    }
}
