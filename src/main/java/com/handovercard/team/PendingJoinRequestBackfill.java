package com.handovercard.team;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 대기 신청의 {@code pending_member_id}가 비어 있는 옛 데이터를 시작할 때 한 번 보정한다.
 *
 * <p>"대기 중인 신청은 회원당 하나"는 이 열의 유니크 제약으로 지켜지는데, 제약이 생기기 전에 저장된
 * 행에는 값이 없어 제약이 걸리지 않는다. 값을 채워야 비로소 보호를 받는다.
 *
 * <p>이미 대기 신청이 있는 회원의 중복 행은 채울 수 없으므로(제약에 걸린다) 가장 오래된 것만 남기고
 * 나머지는 거절 처리한다. 여러 번 실행해도 결과가 같고, 보정할 게 없으면 아무 일도 하지 않는다.
 *
 * <p>모든 환경의 데이터가 보정된 뒤에는 지워도 되는 코드다.
 */
@Component
class PendingJoinRequestBackfill implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PendingJoinRequestBackfill.class);

    private final TeamJoinRequestRepository joinRequestRepository;

    PendingJoinRequestBackfill(TeamJoinRequestRepository joinRequestRepository) {
        this.joinRequestRepository = joinRequestRepository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        List<TeamJoinRequest> unmarked = joinRequestRepository
                .findAllByStatusAndPendingMemberIdIsNullOrderByIdAsc(TeamJoinRequestStatus.PENDING);
        if (unmarked.isEmpty()) {
            return;
        }

        // 이미 표시가 붙은 대기 신청이 있는 회원은 그쪽이 유일한 대기 신청으로 남아야 한다
        Map<Long, TeamJoinRequest> keepByMember = new LinkedHashMap<>();
        for (TeamJoinRequest request : unmarked) {
            Long memberId = request.getMember().getId();
            boolean alreadyMarked = !joinRequestRepository
                    .findAllByMemberAndStatus(request.getMember(), TeamJoinRequestStatus.PENDING).stream()
                    .filter(other -> other.getPendingMemberId() != null)
                    .toList()
                    .isEmpty();
            if (alreadyMarked) {
                continue;
            }
            keepByMember.putIfAbsent(memberId, request);
        }

        int restored = 0;
        int rejected = 0;
        for (TeamJoinRequest request : unmarked) {
            if (keepByMember.get(request.getMember().getId()) == request) {
                request.restorePendingMarker();
                restored++;
            } else {
                // 같은 회원의 중복 대기 신청. 하나만 유효할 수 있으므로 나머지는 거절로 정리한다.
                request.reject();
                rejected++;
            }
        }
        log.info("Backfilled {} pending join request(s); rejected {} duplicate(s)", restored, rejected);
    }
}
