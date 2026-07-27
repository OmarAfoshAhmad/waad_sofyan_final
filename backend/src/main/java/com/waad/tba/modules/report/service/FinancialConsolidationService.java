package com.waad.tba.modules.report.service;

import com.waad.tba.modules.report.dto.FinancialConsolidationDto;
import com.waad.tba.modules.report.dto.FinancialConsolidationDto.MonthlyFinancials;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class FinancialConsolidationService {

    @PersistenceContext
    private EntityManager entityManager;

    @Transactional(readOnly = true)
    public List<FinancialConsolidationDto> getMonthlyFinancialConsolidation(int year) {
        // NOTE: COALESCE fallbacks use the integer literal 0, not 0.0 — a
        // double literal here would force Hibernate to promote the whole
        // SUM to a floating-point result type even though every source
        // column is BigDecimal, reintroducing binary floating-point drift
        // into money totals that this report is supposed to be authoritative on.
        String jpql = "SELECT c.member.employer.name, MONTH(c.serviceDate), " +
                      "SUM(COALESCE(c.requestedAmount, 0)), " +
                      "SUM(COALESCE(c.netProviderAmount, COALESCE(c.approvedAmount, 0))), " +
                      "SUM(COALESCE(c.refusedAmount, 0)), " +
                      "SUM(COALESCE(c.paidAmount, 0)), " +
                      "SUM(COALESCE(c.companyDiscountAmount, 0)) " +
                      "FROM Claim c " +
                      "WHERE YEAR(c.serviceDate) = :year " +
                      "AND c.active = true " +
                      "AND c.status IN ('APPROVED', 'BATCHED', 'SETTLED') " +
                      "GROUP BY c.member.employer.name, MONTH(c.serviceDate)";

        Query query = entityManager.createQuery(jpql);
        query.setParameter("year", year);

        @SuppressWarnings("unchecked")
        List<Object[]> results = query.getResultList();

        Map<String, FinancialConsolidationDto> map = new HashMap<>();

        for (Object[] row : results) {
            String employerName = (String) row[0];
            int month = (Integer) row[1];
            BigDecimal requested = decimal(row[2]);
            BigDecimal approved = decimal(row[3]);
            BigDecimal rejected = decimal(row[4]);
            BigDecimal paid = decimal(row[5]);
            BigDecimal companyDiscount = decimal(row[6]);
            BigDecimal remaining = approved.subtract(paid);

            if (employerName == null) employerName = "غير معروف";

            FinancialConsolidationDto dto = map.computeIfAbsent(employerName, 
                name -> FinancialConsolidationDto.builder().employerName(name)
                        .month1(new MonthlyFinancials()).month2(new MonthlyFinancials()).month3(new MonthlyFinancials())
                        .month4(new MonthlyFinancials()).month5(new MonthlyFinancials()).month6(new MonthlyFinancials())
                        .month7(new MonthlyFinancials()).month8(new MonthlyFinancials()).month9(new MonthlyFinancials())
                        .month10(new MonthlyFinancials()).month11(new MonthlyFinancials()).month12(new MonthlyFinancials())
                        .totalAmount(new MonthlyFinancials()).build());

            MonthlyFinancials monthly = null;
            switch (month) {
                case 1: monthly = dto.getMonth1(); break;
                case 2: monthly = dto.getMonth2(); break;
                case 3: monthly = dto.getMonth3(); break;
                case 4: monthly = dto.getMonth4(); break;
                case 5: monthly = dto.getMonth5(); break;
                case 6: monthly = dto.getMonth6(); break;
                case 7: monthly = dto.getMonth7(); break;
                case 8: monthly = dto.getMonth8(); break;
                case 9: monthly = dto.getMonth9(); break;
                case 10: monthly = dto.getMonth10(); break;
                case 11: monthly = dto.getMonth11(); break;
                case 12: monthly = dto.getMonth12(); break;
            }

            if (monthly != null) {
                monthly.setRequestedAmount(requested);
                monthly.setApprovedAmount(approved);
                monthly.setRejectedAmount(rejected);
                monthly.setPaidAmount(paid);
                monthly.setRemainingAmount(remaining);
                monthly.setCompanyDiscountAmount(companyDiscount);
            }

            MonthlyFinancials total = dto.getTotalAmount();
            total.setRequestedAmount(total.getRequestedAmount().add(requested));
            total.setApprovedAmount(total.getApprovedAmount().add(approved));
            total.setRejectedAmount(total.getRejectedAmount().add(rejected));
            total.setPaidAmount(total.getPaidAmount().add(paid));
            total.setRemainingAmount(total.getRemainingAmount().add(remaining));
            total.setCompanyDiscountAmount(total.getCompanyDiscountAmount().add(companyDiscount));
        }

        return new ArrayList<>(map.values());
    }

    private BigDecimal decimal(Object value) {
        if (value == null) return BigDecimal.ZERO;
        if (value instanceof BigDecimal decimal) return decimal;
        return new BigDecimal(value.toString());
    }
}
