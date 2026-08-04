package com.handovercard.card;

import com.handovercard.member.Member;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface HandoverCardRepository extends JpaRepository<HandoverCard, Long> {

    @Query("SELECT c FROM HandoverCard c WHERE c.owner = :member OR c.receiver = :member ORDER BY c.createdAt DESC")
    List<HandoverCard> findAllAccessibleTo(@Param("member") Member member);
}
