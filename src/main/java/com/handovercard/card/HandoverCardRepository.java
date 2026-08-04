package com.handovercard.card;

import com.handovercard.member.Member;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface HandoverCardRepository extends JpaRepository<HandoverCard, Long> {

    @Query("SELECT c FROM HandoverCard c WHERE c.owner = :member OR c.receiver = :member")
    Page<HandoverCard> findAllAccessibleTo(@Param("member") Member member, Pageable pageable);
}
