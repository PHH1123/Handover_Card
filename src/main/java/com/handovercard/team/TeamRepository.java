package com.handovercard.team;

import com.handovercard.member.Member;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TeamRepository extends JpaRepository<Team, Long> {

    boolean existsByNameIgnoreCase(String name);

    boolean existsByLeader(Member leader);

    List<Team> findAllByOrderByNameAsc();
}
