package com.handovercard.member;

import com.handovercard.member.dto.ChangePasswordRequest;
import com.handovercard.member.dto.MemberProfileResponse;
import com.handovercard.member.dto.UpdateProfileRequest;
import com.handovercard.security.CustomUserDetails;
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

@RestController
@RequestMapping("/api/members/me")
public class MemberController {

    private final MemberService memberService;

    public MemberController(MemberService memberService) {
        this.memberService = memberService;
    }

    @GetMapping
    public ResponseEntity<MemberProfileResponse> getProfile(@AuthenticationPrincipal CustomUserDetails principal) {
        return ResponseEntity.ok(memberService.getProfile(principal.getMember().getId()));
    }

    @PatchMapping
    public ResponseEntity<MemberProfileResponse> updateProfile(@AuthenticationPrincipal CustomUserDetails principal,
                                                                 @Valid @RequestBody UpdateProfileRequest request) {
        return ResponseEntity.ok(memberService.updateProfile(principal.getMember().getId(), request.name()));
    }

    @PutMapping("/password")
    public ResponseEntity<Void> changePassword(@AuthenticationPrincipal CustomUserDetails principal,
                                                @Valid @RequestBody ChangePasswordRequest request) {
        memberService.changePassword(principal.getMember().getId(), request.currentPassword(), request.newPassword());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping
    public ResponseEntity<Void> deleteAccount(@AuthenticationPrincipal CustomUserDetails principal) {
        memberService.deleteAccount(principal.getMember().getId());
        return ResponseEntity.noContent().build();
    }
}
