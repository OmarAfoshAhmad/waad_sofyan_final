package com.waad.tba.modules.providercontract.repository;

import com.waad.tba.TbaWaadApplication;
import com.waad.tba.modules.provider.entity.Provider;
import com.waad.tba.modules.provider.entity.Provider.ProviderType;
import com.waad.tba.modules.provider.repository.ProviderRepository;
import com.waad.tba.modules.providercontract.entity.ProviderContract;
import com.waad.tba.modules.providercontract.entity.ProviderContract.ContractStatus;
import com.waad.tba.modules.providercontract.entity.ProviderContractPricingItem;
import com.waad.tba.support.PostgresIntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(classes = TbaWaadApplication.class)
@ActiveProfiles("test")
class ProviderContractPriceVersioningIntegrationTest extends PostgresIntegrationTestBase {

    @Autowired private ProviderRepository providerRepository;
    @Autowired private ProviderContractRepository contractRepository;
    @Autowired private ProviderContractPricingItemRepository pricingRepository;

    @Test
    void adjacentVersionsSelectOldBeforeBoundaryAndNewOnBoundary() {
        ProviderContract contract = createContract();
        LocalDate boundary = LocalDate.of(2026, 7, 1);

        savePrice(contract, "SRV-100", "كشف طبي", "100.00",
                LocalDate.of(2026, 1, 1), boundary);
        savePrice(contract, "srv-100", "كشف طبي", "120.00", boundary, null);

        assertThat(pricingRepository.findEffectiveInContractByCode(
                contract.getId(), "SRV-100", boundary.minusDays(1)))
                .get().extracting(ProviderContractPricingItem::getContractPrice)
                .isEqualTo(new BigDecimal("100.00"));
        assertThat(pricingRepository.findEffectiveInContractByCode(
                contract.getId(), "SRV-100", boundary))
                .get().extracting(ProviderContractPricingItem::getContractPrice)
                .isEqualTo(new BigDecimal("120.00"));
    }

    @Test
    void databaseRejectsOverlappingActiveVersionsForSameServiceIdentity() {
        ProviderContract contract = createContract();
        savePrice(contract, "SRV-200", "تحاليل", "80.00",
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 7, 1));

        assertThatThrownBy(() -> savePrice(contract, "srv-200", "تحاليل محدثة", "90.00",
                LocalDate.of(2026, 6, 30), null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void databaseRejectsZeroLengthPricePeriods() {
        ProviderContract contract = createContract();
        LocalDate date = LocalDate.of(2026, 4, 1);

        assertThatThrownBy(() -> savePrice(contract, "SRV-300", "أشعة", "75.00", date, date))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private ProviderContract createContract() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        Provider provider = providerRepository.save(Provider.builder()
                .name("Versioned Price Provider " + suffix)
                .licenseNumber("VP-" + suffix)
                .providerType(ProviderType.HOSPITAL)
                .active(true)
                .build());
        return contractRepository.save(ProviderContract.builder()
                .contractCode("VPC-" + suffix)
                .provider(provider)
                .startDate(LocalDate.of(2026, 1, 1))
                .endDate(LocalDate.of(2026, 12, 31))
                .status(ContractStatus.ACTIVE)
                .active(true)
                .build());
    }

    private ProviderContractPricingItem savePrice(ProviderContract contract,
                                                   String code,
                                                   String name,
                                                   String price,
                                                   LocalDate from,
                                                   LocalDate to) {
        return pricingRepository.saveAndFlush(ProviderContractPricingItem.builder()
                .contract(contract)
                .serviceCode(code)
                .serviceName(name)
                .basePrice(new BigDecimal(price))
                .contractPrice(new BigDecimal(price))
                .effectiveFrom(from)
                .effectiveTo(to)
                .active(true)
                .build());
    }
}
