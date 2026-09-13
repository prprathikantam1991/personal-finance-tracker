package com.pradeep.finance.account;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "accounts")
public class Account {

    @Id
    @Column(nullable = false, updatable = false)
    private String id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String institution;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_type", nullable = false)
    private AccountType accountType;

    @Column(name = "last_four")
    private String lastFour;

    @Column(name = "identity_key", unique = true)
    private String identityKey;

    @Column(nullable = false)
    private String currency;

    @Column(name = "current_apr")
    private BigDecimal currentApr;

    @Column(name = "promotional_apr")
    private BigDecimal promotionalApr;

    @Column(name = "promotional_apr_expires_on")
    private String promotionalAprExpiresOn;

    @Column(name = "credit_limit")
    private BigDecimal creditLimit;

    @Column(name = "penalty_apr")
    private BigDecimal penaltyApr;

    @Column(name = "created_at", nullable = false)
    private String createdAt;

    protected Account() {
    }

    Account(String name, String institution, AccountType accountType, String lastFour, String identityKey, String currency) {
        this.name = name;
        this.institution = institution;
        this.accountType = accountType;
        this.lastFour = lastFour;
        this.identityKey = identityKey;
        this.currency = currency;
    }

    @PrePersist
    void initializePersistenceFields() {
        if (id == null) {
            id = UUID.randomUUID().toString();
        }
        if (createdAt == null) {
            createdAt = Instant.now().toString();
        }
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getInstitution() { return institution; }
    public AccountType getAccountType() { return accountType; }
    public String getLastFour() { return lastFour; }
    public String getIdentityKey() { return identityKey; }
    public String getCurrency() { return currency; }
    public BigDecimal getCurrentApr() { return currentApr; }
    public BigDecimal getPromotionalApr() { return promotionalApr; }
    public String getPromotionalAprExpiresOn() { return promotionalAprExpiresOn; }
    public BigDecimal getCreditLimit() { return creditLimit; }
    public BigDecimal getPenaltyApr() { return penaltyApr; }
    public Instant getCreatedAt() { return Instant.parse(createdAt); }

    void captureCardTerms(ExtractedCardTerms terms) {
        if (terms.currentApr() != null) currentApr = terms.currentApr();
        if (terms.promotionalApr() != null) promotionalApr = terms.promotionalApr();
        if (terms.promotionalAprExpiresOn() != null) promotionalAprExpiresOn = terms.promotionalAprExpiresOn().toString();
        if (terms.creditLimit() != null) creditLimit = terms.creditLimit();
        if (terms.penaltyApr() != null) penaltyApr = terms.penaltyApr();
    }

}
