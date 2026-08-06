package com.handovercard.member;

import com.handovercard.member.dto.MemberLookupResult;
import com.handovercard.security.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "회원 조회", description = "수신자 지정을 돕는 같은 팀 회원 조회")
@RestController
@RequestMapping("/api/members")
public class MemberLookupController {

    private final MemberService memberService;

    public MemberLookupController(MemberService memberService) {
        this.memberService = memberService;
    }

    @Operation(summary = "같은 팀 회원 이메일 조회",
            description = "이름이 정확히 일치하는 같은 팀 회원의 이메일을 반환합니다. "
                    + "이메일이 노출되는 조회라 부분 일치는 지원하지 않고, 두 글자 미만이면 빈 목록을 반환합니다. "
                    + "소속된 팀이 없으면 hasTeam=false와 함께 빈 목록을 반환하며, 이 경우 수신자 이메일을 직접 입력해야 합니다.")
    @GetMapping("/lookup")
    public ResponseEntity<MemberLookupResult> lookup(
            @Parameter(description = "찾을 회원의 이름 (완전 일치, 대소문자 무시)", example = "김민지")
            @RequestParam(required = false) String name,
            @AuthenticationPrincipal CustomUserDetails principal) {
        return ResponseEntity.ok(memberService.lookupTeammatesByName(name, principal.getMember().getId()));
    }
}
