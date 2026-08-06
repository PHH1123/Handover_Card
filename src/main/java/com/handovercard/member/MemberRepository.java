package com.handovercard.member;

import com.handovercard.team.Team;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MemberRepository extends JpaRepository<Member, Long> {

    Optional<Member> findByEmail(String email);

    boolean existsByEmail(String email);

    // 이름이 정확히 일치하는 같은 팀 회원만 반환한다. 부분 일치를 허용하면 한 글자만 넣어도
    // 회원 이메일을 쓸어담을 수 있어 수신자 자동완성 용도로는 완전 일치 + 같은 팀으로 제한한다.
    List<Member> findByNameIgnoreCaseAndTeam(String name, Team team);

    List<Member> findAllByTeamOrderByNameAsc(Team team);
}
