package com.waad.tba.modules.report.service;

import com.waad.tba.modules.report.dto.FinancialConsolidationDto;
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
        String jpql = "SELECT c.member.employer.name, MONTH(c.serviceDate), " +
                      "SUM((c.netProviderAmount * COALESCE(c.appliedDiscountPercent, 0.0)) / 100.0) " +
                      "FROM Claim c " +
                      "WHERE YEAR(c.serviceDate) = :year " +
                      "AND c.active = true " +
                      "AND c.status IN ('APPROVED', 'SETTLED') " +
                      "GROUP BY c.member.employer.name, MONTH(c.serviceDate)";

        Query query = entityManager.createQuery(jpql);
        query.setParameter("year", year);

        @SuppressWarnings("unchecked")
        List<Object[]> results = query.getResultList();

        Map<String, FinancialConsolidationDto> map = new HashMap<>();

        for (Object[] row : results) {
            String employerName = (String) row[0];
            int month = (Integer) row[1];
            BigDecimal amount = row[2] != null ? BigDecimal.valueOf(((Number) row[2]).doubleValue()) : BigDecimal.ZERO;

            if (employerName == null) employerName = "غير معروف";

            FinancialConsolidationDto dto = map.computeIfAbsent(employerName, 
                name -> FinancialConsolidationDto.builder().employerName(name)
                        .month1(BigDecimal.ZERO).month2(BigDecimal.ZERO).month3(BigDecimal.ZERO)
                        .month4(BigDecimal.ZERO).month5(BigDecimal.ZERO).month6(BigDecimal.ZERO)
                        .month7(BigDecimal.ZERO).month8(BigDecimal.ZERO).month9(BigDecimal.ZERO)
                        .month10(BigDecimal.ZERO).month11(BigDecimal.ZERO).month12(BigDecimal.ZERO)
                        .totalAmount(BigDecimal.ZERO).build());

            switch (month) {
                case 1: dto.setMonth1(amount); break;
                case 2: dto.setMonth2(amount); break;
                case 3: dto.setMonth3(amount); break;
                case 4: dto.setMonth4(amount); break;
                case 5: dto.setMonth5(amount); break;
                case 6: dto.setMonth6(amount); break;
                case 7: dto.setMonth7(amount); break;
                case 8: dto.setMonth8(amount); break;
                case 9: dto.setMonth9(amount); break;
                case 10: dto.setMonth10(amount); break;
                case 11: dto.setMonth11(amount); break;
                case 12: dto.setMonth12(amount); break;
            }

            BigDecimal currentTotal = dto.getTotalAmount() != null ? dto.getTotalAmount() : BigDecimal.ZERO;
            dto.setTotalAmount(currentTotal.add(amount));
        }

        return new ArrayList<>(map.values());
    }
}
