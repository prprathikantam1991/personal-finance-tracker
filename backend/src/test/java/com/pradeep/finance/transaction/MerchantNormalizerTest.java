package com.pradeep.finance.transaction;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MerchantNormalizerTest {
    private final MerchantNormalizer normalizer = new MerchantNormalizer();

    @Test
    void prioritizesFinancialMeaningOverUnrelatedBrandMentions() {
        assertThat(normalizer.normalize("Mobile Banking payment to CRD 7098 Help prevent check fraud Mobile Banking requires that you download the Mobile Banking app"))
                .isEqualTo("Card Payment");
        assertThat(normalizer.normalize("Zelle payment to Madhurisri NEW: BankAmeriDeals Mobile Banking requires that you download the Mobile Banking app"))
                .isEqualTo("Zelle Transfer");
        assertThat(normalizer.normalize("PUBLIC SERVICE DES:PSEG ID:007732909305 PPD Mobile Banking requires that you download the Mobile Banking app"))
                .isEqualTo("PSEG");
    }
}
