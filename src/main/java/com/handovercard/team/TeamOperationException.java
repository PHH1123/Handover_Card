package com.handovercard.team;

/** 팀 생성/신청/승인 규칙을 어긴 요청 (이미 팀 소속, 중복 신청, 이미 처리된 신청 등). */
public class TeamOperationException extends RuntimeException {

    public TeamOperationException(String message) {
        super(message);
    }
}
