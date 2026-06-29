package com.waad.tba.modules.settlement.entity;

public enum PaymentMethod {
    CASH("نقدي"),
    BANK_TRANSFER("تحويل مصرفي"),
    CHECK("صك"),
    OTHER("غير ذلك");

    private final String arabicLabel;

    PaymentMethod(String arabicLabel) {
        this.arabicLabel = arabicLabel;
    }

    public String getArabicLabel() {
        return arabicLabel;
    }
}
