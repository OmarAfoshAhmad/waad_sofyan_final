package com.waad.tba.modules.rbac.permission;

import java.util.Arrays;

/** Version-controlled catalogue; codes are permanent security identifiers. */
public enum SystemPermission {
    MEMBER_VIEW(PermissionCategory.MEMBERS, "عرض المستفيدين", false),
    MEMBER_CREATE(PermissionCategory.MEMBERS, "إضافة مستفيد", false),
    MEMBER_EDIT_IDENTITY(PermissionCategory.MEMBERS, "تعديل بيانات المستفيد", true),
    MEMBER_CHANGE_STATUS(PermissionCategory.MEMBERS, "تغيير حالة المستفيد", true),
    MEMBER_REINSTATE_TERMINATED(PermissionCategory.MEMBERS, "إعادة عضوية منتهية استثنائياً", true),
    MEMBER_TRANSFER_EMPLOYER(PermissionCategory.MEMBERS, "نقل المستفيد أو الأسرة", true),
    MEMBER_HARD_DELETE(PermissionCategory.MEMBERS, "حذف المستفيد نهائياً", true),
    MEMBER_FINANCIAL_VIEW(PermissionCategory.MEMBERS, "عرض الحسابات المالية للمستفيد", true),
    MEMBER_LIMIT_VIEW(PermissionCategory.MEMBERS, "عرض رصيد سقف المستفيد للتعامل الطبي", false),
    MEMBER_LIMIT_LIST_VIEW(PermissionCategory.MEMBERS, "عرض أرصدة السقوف في قائمة المستفيدين", false),
    MEMBER_LIMIT_UPLIFT_MANAGE(PermissionCategory.MEMBERS, "منح ورفع استثناءات السقف العام للمستفيد", true),
    MEMBER_IMPORT(PermissionCategory.MEMBERS, "استيراد المستفيدين", true),
    MEMBER_EXPORT(PermissionCategory.MEMBERS, "تصدير المستفيدين", true),
    CLAIM_VIEW(PermissionCategory.CLAIMS, "عرض المطالبات", false),
    CLAIM_CREATE(PermissionCategory.CLAIMS, "إدخال مطالبة", false),
    CLAIM_REVIEW(PermissionCategory.CLAIMS, "مراجعة مطالبة", true),
    CLAIM_APPROVE(PermissionCategory.CLAIMS, "اعتماد مطالبة", true),
    CLAIM_REVERSE(PermissionCategory.CLAIMS, "عكس أثر مطالبة", true),
    PREAUTH_VIEW(PermissionCategory.PREAUTHORIZATIONS, "عرض الموافقات المسبقة", false),
    PREAUTH_CREATE(PermissionCategory.PREAUTHORIZATIONS, "إنشاء موافقة مسبقة", false),
    PREAUTH_REVIEW(PermissionCategory.PREAUTHORIZATIONS, "مراجعة موافقة مسبقة", true),
    PREAUTH_APPROVE(PermissionCategory.PREAUTHORIZATIONS, "اعتماد موافقة مسبقة", true),
    PREAUTH_CANCEL(PermissionCategory.PREAUTHORIZATIONS, "إلغاء موافقة مسبقة", true),
    PREAUTH_DELETE(PermissionCategory.PREAUTHORIZATIONS, "حذف مسودة موافقة مسبقة", true),
    PROVIDER_VIEW(PermissionCategory.PROVIDERS, "عرض مقدمي الخدمة", false),
    PROVIDER_MANAGE(PermissionCategory.PROVIDERS, "إدارة مقدمي الخدمة", true),
    EMPLOYER_VIEW(PermissionCategory.EMPLOYERS, "عرض جهات العمل", false),
    EMPLOYER_MANAGE(PermissionCategory.EMPLOYERS, "إدارة جهات العمل", true),
    CONTRACT_VIEW(PermissionCategory.CONTRACTS_PRICING, "عرض العقود والأسعار", false),
    CONTRACT_MANAGE(PermissionCategory.CONTRACTS_PRICING, "إدارة العقود والأسعار", true),
    PRICE_LIST_IMPORT(PermissionCategory.CONTRACTS_PRICING, "استيراد قوائم الأسعار", true),
    PRICE_LIST_POST(PermissionCategory.CONTRACTS_PRICING, "ترحيل قوائم الأسعار للعقود", true),
    BENEFIT_POLICY_VIEW(PermissionCategory.BENEFITS, "عرض وثائق المنافع", false),
    BENEFIT_POLICY_MANAGE(PermissionCategory.BENEFITS, "إدارة وثائق المنافع", true),
    SETTLEMENT_VIEW(PermissionCategory.SETTLEMENTS, "عرض التسويات", true),
    SETTLEMENT_MANAGE(PermissionCategory.SETTLEMENTS, "إدارة التسويات", true),
    FINANCIAL_REPORT_VIEW(PermissionCategory.REPORTS, "عرض التقارير المالية", true),
    OPERATIONAL_REPORT_VIEW(PermissionCategory.REPORTS, "عرض التقارير التشغيلية", false),
    USER_VIEW(PermissionCategory.USERS_SECURITY, "عرض المستخدمين", true),
    USER_MANAGE(PermissionCategory.USERS_SECURITY, "إدارة المستخدمين", true),
    ROLE_PERMISSION_MANAGE(PermissionCategory.USERS_SECURITY, "إدارة الأدوار والصلاحيات", true),
    SECURITY_AUDIT_VIEW(PermissionCategory.USERS_SECURITY, "عرض سجل التدقيق الأمني", true),
    SESSION_REVOKE(PermissionCategory.USERS_SECURITY, "سحب جلسات المستخدمين", true),
    SYSTEM_SETTINGS_VIEW(PermissionCategory.SYSTEM, "عرض إعدادات النظام", true),
    SYSTEM_SETTINGS_MANAGE(PermissionCategory.SYSTEM, "إدارة إعدادات النظام", true),
    DANGER_ZONE_EXECUTE(PermissionCategory.SYSTEM, "تنفيذ العمليات الخطرة", true);

    private final PermissionCategory category;
    private final String displayNameAr;
    private final boolean sensitive;

    SystemPermission(PermissionCategory category, String displayNameAr, boolean sensitive) {
        this.category = category;
        this.displayNameAr = displayNameAr;
        this.sensitive = sensitive;
    }

    public PermissionCategory category() { return category; }
    public String displayNameAr() { return displayNameAr; }
    public boolean sensitive() { return sensitive; }
    public String authority() { return "PERM_" + name(); }

    public static SystemPermission parse(String code) {
        if (code == null) throw new IllegalArgumentException("رمز الصلاحية مطلوب");
        return Arrays.stream(values())
                .filter(value -> value.name().equals(code.trim().toUpperCase()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("رمز صلاحية غير معروف: " + code));
    }
}
