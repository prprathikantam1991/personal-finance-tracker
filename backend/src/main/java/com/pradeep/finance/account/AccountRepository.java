package com.pradeep.finance.account;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountRepository extends JpaRepository<Account, String> {

    Optional<Account> findByOwnerUserIdAndIdentityKey(String ownerUserId, String identityKey);

    List<Account> findAllByOwnerUserIdOrderByInstitutionAscNameAsc(String ownerUserId);
}
