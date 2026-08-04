package com.handovercard.member;

import com.handovercard.member.dto.ChangePasswordRequest;
import com.handovercard.member.dto.MemberProfileResponse;
import com.handovercard.member.dto.UpdateProfileRequest;
import com.handovercard.security.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "내 프로필", description = "로그인한 본인의 프로필 조회/수정, 비밀번호 변경, 계정 삭제")
@RestController
@RequestMapping("/api/members/me")
public class MemberController {

    private final MemberService memberService;

    public MemberController(MemberService memberService) {
        this.memberService = memberService;
    }

    @Operation(summary = "내 프로필 조회")
    @GetMapping
    public ResponseEntity<MemberProfileResponse> getProfile(@AuthenticationPrincipal CustomUserDetails principal) {
        return ResponseEntity.ok(memberService.getProfile(principal.getMember().getId()));
    }

    @Operation(summary = "내 프로필 수정", description = "표시 이름을 변경합니다.")
    @PatchMapping
    public ResponseEntity<MemberProfileResponse> updateProfile(@AuthenticationPrincipal CustomUserDetails principal,
                                                                 @Valid @RequestBody UpdateProfileRequest request) {
        return ResponseEntity.ok(memberService.updateProfile(principal.getMember().getId(), request.name()));
    }

    @Operation(summary = "비밀번호 변경", description = "현재 비밀번호를 확인한 뒤 새 비밀번호로 변경합니다. 현재 비밀번호가 틀리면 401을 반환합니다.")
    @PutMapping("/password")
    public ResponseEntity<Void> changePassword(@AuthenticationPrincipal CustomUserDetails principal,
                                                @Valid @RequestBody ChangePasswordRequest request) {
        memberService.changePassword(principal.getMember().getId(), request.currentPassword(), request.newPassword());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "계정 삭제", description = "내 계정을 삭제합니다. 보유한 리프레시 토큰과 소유한 인수인계 카드(오디오 파일 포함)가 "
            + "함께 삭제되며, 내가 수신자로 연결된 다른 사람의 카드는 연결만 해제됩니다.")
    @DeleteMapping
    public ResponseEntity<Void> deleteAccount(@AuthenticationPrincipal CustomUserDetails principal) {
        memberService.deleteAccount(principal.getMember().getId());
        return ResponseEntity.noContent().build();
    }
}
