package com.waad.tba.modules.member.service;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Configuration class for Member Import column mappings and template field codes.
 * Extracted from MemberExcelImportService to reduce complexity.
 */
public class MemberImportFieldConfig {

    public static final Set<String> TEMPLATE_FIELD_CODES = Set.of(
            "full_name", "name", "employer", "birth_date", "gender", "civil_id",
            "phone", "email", "policy_number", "nationality", "employee_number");

    public static final Set<String> TEMPLATE_ARABIC_FIELD_LABELS = Set.of(
            "الاسم الكامل", "جهة العمل", "تاريخ الميلاد", "الجنس", "الرقم الوطني",
            "رقم الهاتف", "البريد الإلكتروني", "رقم الوثيقة", "الجنسية", "الرقم الوظيفي");

    public static final Set<String> TEMPLATE_ENGLISH_FIELD_LABELS = Set.of(
            "full name", "birth date", "gender", "national id / civil id", "phone number",
            "email address", "policy number", "nationality", "employee number");

    /**
     * Mandatory columns (at least one variant required)
     */
    public static final List<String[]> MANDATORY_COLUMNS = java.util.Collections.singletonList(
            // Full Name - الاسم الكامل (MANDATORY)
            new String[] {
                    "full_name", "name", "full_name_arabic", "fullname", "member_name",
                    "full-name", "full name", "member name", "beneficiary name", "beneficiary_name",
                    "subscriber name", "subscriber_name", "patient name", "patient_name",
                    "الاسم الكامل", "الاسم", "اسم الموظف", "الاسم بالعربية", "اسم العضو",
                    "الاسم الثلاثي", "الاسم الرباعي", "اسم المؤمن عليه", "اسم المستفيد",
                    "اسم المشترك", "اسم المريض", "الاسم بالكامل", "الاسم العربي", "المستفيد",
                    "اسم المؤمن"
            });

    /**
     * Optional core field mappings
     */
    public static final Map<String, String[]> OPTIONAL_FIELD_MAPPINGS = Map.ofEntries(
            // Employer - جهة العمل (OPTIONAL)
            Map.entry("employer", new String[] {
                    "employer", "company", "company_id", "company_name", "employer_name",
                    "work_company", "organization", "employer_code", "employer name", "company name",
                    "جهة العمل", "الشركة", "اسم الشركة", "المؤسسة", "جهة الانتساب",
                    "صاحب العمل", "الجهة", "مكان العمل", "كود الجهة", "اسم جهة العمل",
                    "الشركه"
            }),
            // Civil ID - الرقم الوطني
            Map.entry("nationalNumber", new String[] {
                    "national_id", "identification_id", "civil_id", "civilid", "national_number",
                    "id_number", "identity_number", "national id", "civil id", "id number",
                    "identity number", "national number", "identity",
                    "الرقم الوطني", "رقم الهوية", "الرقم المدني", "رقم البطاقة الشخصية",
                    "رقم الهوية الوطنية", "رقم بطاقة الاحوال", "رقم الهويه", "الرقم الوطني للمستفيد",
                    "الرقم الوطني للعضو", "رقم بطاقة الهوية"
            }),
            // Card Number / Barcode - IGNORED by logic but defined for mapping
            Map.entry("cardNumber", new String[] {
                    "card_number", "cardnumber", "card number", "member_no", "member_number",
                    "insurance_no", "insurance_number", "membership_no", "membership_number",
                    "barcode", "badge_id", "employee_id", "card no", "member no", "member number",
                    "insurance number", "card_no", "card-number", "card-no", "principal_card_number",
                    "principal card number", "principal-card-number",
                    "رقم البطاقة", "رقم العضوية", "رقم التأمين", "رقم العضو", "رقم بطاقة التأمين",
                    "الباركود", "رقم الشارة", "رقم بطاقة المستفيد", "رقم بطاقة المؤمن عليه", "رقم الكارت",
                    "رقم بطاقة الرئيسي"
            }),
            // Birth Date - تاريخ الميلاد
            Map.entry("birthDate", new String[] {
                    "birth_date", "birthday", "dob", "date_of_birth", "birthdate", "birth date", "date of birth",
                    "تاريخ الميلاد", "تاريخ الولادة", "الميلاد", "تاريخ ميلاد"
            }),
            // Gender - الجنس
            Map.entry("gender", new String[] {
                    "gender", "sex",
                    "الجنس", "النوع"
            }),
            // Phone - الهاتف
            Map.entry("phone", new String[] {
                    "phone", "mobile", "mobile_phone", "work_phone", "phone_number",
                    "telephone", "tel", "cell", "cellphone", "phone number", "mobile number",
                    "الهاتف", "الجوال", "رقم الهاتف", "رقم الجوال", "هاتف العمل",
                    "الموبايل", "رقم التواصل", "رقم موبايل", "رقم جوال", "التواصل"
            }),
            // Email - البريد الإلكتروني
            Map.entry("email", new String[] {
                    "email", "work_email", "email_address", "e_mail", "email address",
                    "البريد الإلكتروني", "الإيميل", "البريد", "البريد الالكتروني", "ايميل"
            }),
            // Nationality - الجنسية
            Map.entry("nationality", new String[] {
                    "nationality", "country", "country_id",
                    "الجنسية", "البلد"
            }),
            // Employee Number - رقم الموظف
            Map.entry("employeeNumber", new String[] {
                    "employee_number", "employee_id", "badge_id", "barcode", "emp_no",
                    "employee_code", "staff_id",
                    "رقم الموظف", "الرقم الوظيفي", "رقم العمل", "كود الموظف"
            }),
            // Policy Number - رقم الوثيقة
            Map.entry("policyNumber", new String[] {
                    "policy_number", "policy", "benefit_policy", "insurance_policy",
                    "رقم الوثيقة", "رقم البوليصة", "الوثيقة", "البوليصة", "رقم العقد", "العقد"
            }),
            // Start Date - تاريخ البداية
            Map.entry("startDate", new String[] {
                    "start_date", "join_date", "hire_date", "employment_date",
                    "تاريخ البداية", "تاريخ الالتحاق", "تاريخ التعيين", "تاريخ بدء التغطية", "تاريخ التفعيل", "تاريخ البدء"
            }),
            // Address - العنوان
            Map.entry("address", new String[] {
                    "address", "home_address", "street", "location",
                    "العنوان", "عنوان السكن", "الموقع"
            }),
            // Marital Status - الحالة الاجتماعية
            Map.entry("maritalStatus", new String[] {
                    "marital_status", "marital", "status_marital",
                    "الحالة الاجتماعية", "الحالة الزوجية"
            }),
            // Relationship - الصلة
            Map.entry("relationship", new String[] {
                    "relationship", "relation", "relative_relation", "rel",
                    "الصلة", "صلة القرابة", "صلة", "الصلة بالموظف", "القرابة"
            }));

    /**
     * Columns that go to attributes
     */
    public static final Map<String, String[]> ATTRIBUTE_MAPPINGS = Map.ofEntries(
            // Job Title - المسمى الوظيفي
            Map.entry("job_title", new String[] {
                    "job_title", "job_id", "job", "position", "title", "job_position",
                    "الوظيفة", "المسمى الوظيفي", "المنصب", "الدرجة الوظيفية"
            }),
            // Department - القسم
            Map.entry("department", new String[] {
                    "department", "department_id", "dept", "division", "section",
                    "القسم", "الإدارة", "الوحدة", "الفرع"
            }),
            // Work Location - موقع العمل
            Map.entry("work_location", new String[] {
                    "work_location", "work_location_id", "location", "office", "branch",
                    "موقع العمل", "مكان العمل", "الفرع", "المكتب"
            }),
            // Grade - الدرجة
            Map.entry("grade", new String[] {
                    "grade", "x_grade", "level", "rank", "class",
                    "الدرجة", "المستوى", "الرتبة", "الفئة"
            }),
            // Manager - المدير
            Map.entry("manager", new String[] {
                    "manager", "parent_id", "manager_name", "supervisor", "direct_manager",
                    "المدير", "المسؤول", "المدير المباشر"
            }),
            // Cost Center - مركز التكلفة
            Map.entry("cost_center", new String[] {
                    "cost_center", "x_cost_center", "cost_code",
                    "مركز التكلفة", "رمز التكلفة"
            }),
            // Start Date - تاريخ البداية
            Map.entry("start_date", new String[] {
                    "start_date", "join_date", "hire_date", "employment_date",
                    "تاريخ البداية", "تاريخ الالتحاق", "تاريخ التعيين"
            }),
            // End Date - تاريخ النهاية
            Map.entry("end_date", new String[] {
                    "end_date", "termination_date", "leave_date",
                    "تاريخ النهاية", "تاريخ الانتهاء"
            }),
            // Benefit Class - فئة المنافع
            Map.entry("benefit_class", new String[] {
                    "benefit_class", "class", "coverage_class", "plan_class",
                    "فئة المنافع", "فئة التغطية", "الفئة"
            }),
            // Notes - ملاحظات
            Map.entry("notes", new String[] {
                    "notes", "remarks", "comment", "comments",
                    "ملاحظات", "تعليقات"
            }));
}
