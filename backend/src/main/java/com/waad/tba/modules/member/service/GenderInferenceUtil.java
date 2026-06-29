package com.waad.tba.modules.member.service;

import com.waad.tba.modules.member.entity.Member;

import java.util.Arrays;
import java.util.List;

public class GenderInferenceUtil {

    private static final List<String> COMMON_MALE_NAMES = Arrays.asList(
            "محمد", "أحمد", "علي", "عمر", "خالد", "عبد", "حسين", "حسن", "يوسف", "محمود", 
            "إبراهيم", "مصطفى", "طارق", "سامي", "وليد", "ياسر", "سعيد", "صالح", "ماجد", "أيمن",
            "سليمان", "أسامة", "حمزة", "طلحة", "عبيدة", "معاوية", "عكرمة", "حذيفة", "قتادة", "عطية", "خليفة"
    );

    private static final List<String> COMMON_FEMALE_NAMES = Arrays.asList(
            "فاطمة", "زينب", "مريم", "عائشة", "خديجة", "نور", "سارة", "هند", "أمل", "سعاد", 
            "سمر", "سحر", "فرح", "نجلاء", "شيماء", "أسماء", "ليلى", "هدى", "سلوى", "نهى", "منى", "رنا",
            "انتصار", "إنتصار", "ايناس", "إيناس", "ابتسام", "إبتسام", "نجاح", "احلام", "أحلام", "امال", "آمال",
            "ايمان", "إيمان", "الهام", "إلهام", "انوار", "أنوار", "اشواق", "أشواق", "افراح", "أفراح",
            "عبير", "غدير", "ريم", "ملاك", "نوال", "وداد", "صباح", "حنان", "سهام", "مها", "رشا", "ندى",
            "شهد", "شروق", "رحاب", "تغريد", "جواهر", "خلود", "عنود", "العنود", "دلال", "سماح", "اماني", "أماني",
            "هاجر", "حصة", "منيرة", "نورة", "اميرة", "أميرة", "نجوى", "فدوى", "سمية", "تهاني"
    );

    private static final List<String> MALE_EXCEPTIONS_TA_MARBUTA = Arrays.asList(
            "أسامة", "اسامة", "حمزة", "حمزه", "طلحة", "طلحه", "عبيدة", "عبيده", "معاوية", "معاويه",
            "عكرمة", "عكرمه", "حذيفة", "حذيفه", "قتادة", "قتاده", "عطية", "عطيه", "خليفة", "خليفه", "عروة", "عروه", "طه"
    );

    private static String normalizeArabic(String text) {
        if (text == null) return "";
        return text.trim()
                .replaceAll("[أإآ]", "ا")
                .replaceAll("ة", "ه")
                .replaceAll("ي$", "ى");
    }

    public static Member.Gender inferGender(String fullName) {
        if (fullName == null || fullName.trim().isEmpty()) {
            return Member.Gender.UNDEFINED;
        }

        String[] parts = fullName.trim().split("\\s+");
        String firstName = parts[0];
        String normalizedFirstName = normalizeArabic(firstName);

        // Check for compound names like 'ام السعد'
        if (normalizedFirstName.equals("ام") || normalizedFirstName.equals("اميره") || normalizedFirstName.equals("امه")) {
            return Member.Gender.FEMALE;
        }

        // Check common female names (normalized)
        List<String> femaleNamesNorm = Arrays.asList(
                "فاطمه", "زينب", "مريم", "عائشه", "خديجه", "نور", "ساره", "هند", "امل", "سعاد", 
                "سمر", "سحر", "فرح", "نجلاء", "شيماء", "اسماء", "ليلى", "هدى", "سلوى", "نهى", "منى", "رنا",
                "انتصار", "ايناس", "ابتسام", "نجاح", "احلام", "امال", "ايمان", "الهام", "انوار", "اشواق", "افراح",
                "عبير", "غدير", "ريم", "ملاك", "نوال", "وداد", "صباح", "حنان", "سهام", "مها", "رشا", "ندى",
                "شهد", "شروق", "رحاب", "تغريد", "جواهر", "خلود", "عنود", "العنود", "دلال", "سماح", "امانى",
                "هاجر", "حصه", "منيره", "نوره", "نجوى", "فدوى", "سميه", "تهانى", "ريهان", "سندس", "نيروز", "كوثر",
                "وعد", "بلقيس", "حنين", "رويده", "اريج", "غاده", "مروه", "صفاء", "دعاء", "ولاء", "هبه", "ايات", "ايه"
        );

        if (femaleNamesNorm.contains(normalizedFirstName)) {
            return Member.Gender.FEMALE;
        }

        // Check common male names
        if (COMMON_MALE_NAMES.contains(firstName) || firstName.startsWith("عبد") || firstName.startsWith("أبو") || firstName.startsWith("ابو")) {
            return Member.Gender.MALE;
        }

        // Check male exceptions
        if (MALE_EXCEPTIONS_TA_MARBUTA.contains(firstName) || MALE_EXCEPTIONS_TA_MARBUTA.contains(normalizedFirstName)) {
            return Member.Gender.MALE;
        }

        // General rules for Arabic names
        if (normalizedFirstName.endsWith("ه") || normalizedFirstName.endsWith("اء") || normalizedFirstName.endsWith("ى")) {
            // But wait, there are male names ending in 'اء' or 'ى' or 'ه'
            if (normalizedFirstName.equals("بهاء") || normalizedFirstName.equals("علاء") || normalizedFirstName.equals("ضياء") || normalizedFirstName.equals("عطاء") || 
                normalizedFirstName.equals("يحيى") || normalizedFirstName.equals("عيسى") || normalizedFirstName.equals("موسى") || normalizedFirstName.equals("مرتضى") || 
                normalizedFirstName.equals("مصطفى") || normalizedFirstName.equals("رضا") || normalizedFirstName.equals("طه") || normalizedFirstName.equals("عبدالله") ||
                normalizedFirstName.equals("عطالله") || normalizedFirstName.equals("خيرالله") || normalizedFirstName.equals("فضل الله")) {
                return Member.Gender.MALE;
            }
            return Member.Gender.FEMALE;
        }

        // By default, if it doesn't match female rules and isn't a known female name, we might guess MALE,
        // but to be safe we can return UNDEFINED if we are not sure, or we can guess MALE since most unhandled names without typical female endings are male.
        // For the sake of catching obvious errors, returning UNDEFINED is safer if we don't know, 
        // but for a heuristic, guessing MALE for names not ending in female markers catches more "Female marked as Male".
        // Let's stick to UNDEFINED if no rule matches to avoid false positives.
        return Member.Gender.UNDEFINED;
    }
}
