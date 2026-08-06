package com.handovercard.card;

import com.handovercard.member.Member;
import com.handovercard.team.Team;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface HandoverCardRepository extends JpaRepository<HandoverCard, Long> {

    /**
     * 내가 만들었거나, 나에게 온 카드, 또는 내 팀에서 만들어진 카드.
     * team이 null인 카드(팀 없이 만든 카드)가 팀 없는 회원 전체에게 보이지 않도록 :team의 null 여부를 함께 본다.
     */
    @Query("""
            SELECT c FROM HandoverCard c
            WHERE c.owner = :member
               OR c.receiver = :member
               OR (:team IS NOT NULL AND c.team = :team)
            """)
    Page<HandoverCard> findAllAccessibleTo(@Param("member") Member member, @Param("team") Team team, Pageable pageable);

    List<HandoverCard> findAllByOwner(Member owner);

    List<HandoverCard> findAllByReceiver(Member receiver);

    List<HandoverCard> findAllByTeam(Team team);
}
