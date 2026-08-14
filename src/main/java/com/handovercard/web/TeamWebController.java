package com.handovercard.web;

import com.handovercard.common.ResourceNotFoundException;
import com.handovercard.security.CustomUserDetails;
import com.handovercard.team.TeamOperationException;
import com.handovercard.team.TeamService;
import com.handovercard.team.dto.TeamResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** 팀 생성 / 가입 신청 / 팀장 승인 화면. */
@Controller
@RequestMapping("/web/teams")
public class TeamWebController {

    private final TeamService teamService;

    public TeamWebController(TeamService teamService) {
        this.teamService = teamService;
    }

    @GetMapping
    public String teams(@AuthenticationPrincipal CustomUserDetails principal, Model model) {
        model.addAttribute("memberName", principal.getMember().getName());
        model.addAttribute("page", teamService.loadTeamPage(principal.getMember().getId()));
        return "teams";
    }

    @PostMapping
    public String create(@RequestParam String name, @AuthenticationPrincipal CustomUserDetails principal,
                          RedirectAttributes redirectAttributes) {
        TeamResponse team = teamService.createTeam(name, principal.getMember().getId());
        redirectAttributes.addFlashAttribute("message", "'" + team.name() + "' 팀을 만들었습니다. 팀장으로 등록되었습니다.");
        return "redirect:/web/teams";
    }

    @PostMapping("/{teamId}/join")
    public String join(@PathVariable Long teamId, @AuthenticationPrincipal CustomUserDetails principal,
                        RedirectAttributes redirectAttributes) {
        teamService.apply(teamId, principal.getMember().getId());
        redirectAttributes.addFlashAttribute("message", "가입을 신청했습니다. 팀장이 승인하면 팀원이 됩니다.");
        return "redirect:/web/teams";
    }

    @PostMapping("/requests/{requestId}/cancel")
    public String cancel(@PathVariable Long requestId, @AuthenticationPrincipal CustomUserDetails principal,
                          RedirectAttributes redirectAttributes) {
        teamService.cancelMyRequest(requestId, principal.getMember().getId());
        redirectAttributes.addFlashAttribute("message", "가입 신청을 취소했습니다. 다른 팀에 다시 신청할 수 있습니다.");
        return "redirect:/web/teams";
    }

    @PostMapping("/requests/{requestId}/approve")
    public String approve(@PathVariable Long requestId, @AuthenticationPrincipal CustomUserDetails principal,
                           RedirectAttributes redirectAttributes) {
        teamService.approve(requestId, principal.getMember().getId());
        redirectAttributes.addFlashAttribute("message", "가입 신청을 승인했습니다.");
        return "redirect:/web/teams";
    }

    @PostMapping("/requests/{requestId}/reject")
    public String reject(@PathVariable Long requestId, @AuthenticationPrincipal CustomUserDetails principal,
                          RedirectAttributes redirectAttributes) {
        teamService.reject(requestId, principal.getMember().getId());
        redirectAttributes.addFlashAttribute("message", "가입 신청을 거절했습니다.");
        return "redirect:/web/teams";
    }

    @PostMapping("/members/{memberId}/promote")
    public String promote(@PathVariable Long memberId, @AuthenticationPrincipal CustomUserDetails principal,
                           RedirectAttributes redirectAttributes) {
        teamService.transferLeadership(memberId, principal.getMember().getId());
        redirectAttributes.addFlashAttribute("message", "팀장을 위임했습니다.");
        return "redirect:/web/teams";
    }

    @PostMapping("/members/{memberId}/kick")
    public String kick(@PathVariable Long memberId, @AuthenticationPrincipal CustomUserDetails principal,
                        RedirectAttributes redirectAttributes) {
        teamService.kickMember(memberId, principal.getMember().getId());
        redirectAttributes.addFlashAttribute("message", "팀원을 내보냈습니다.");
        return "redirect:/web/teams";
    }

    @PostMapping("/delete")
    public String deleteTeam(@AuthenticationPrincipal CustomUserDetails principal,
                              RedirectAttributes redirectAttributes) {
        teamService.deleteTeam(principal.getMember().getId());
        redirectAttributes.addFlashAttribute("message", "팀을 삭제했습니다.");
        return "redirect:/web/teams";
    }

    @PostMapping("/leave")
    public String leave(@AuthenticationPrincipal CustomUserDetails principal, RedirectAttributes redirectAttributes) {
        teamService.leave(principal.getMember().getId());
        redirectAttributes.addFlashAttribute("message", "팀에서 나왔습니다.");
        return "redirect:/web/teams";
    }

    @ExceptionHandler({TeamOperationException.class, ResourceNotFoundException.class})
    public String handleTeamError(Exception e, RedirectAttributes redirectAttributes) {
        redirectAttributes.addFlashAttribute("error", e.getMessage());
        return "redirect:/web/teams";
    }
}
