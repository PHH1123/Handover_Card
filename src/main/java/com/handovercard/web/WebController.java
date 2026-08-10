package com.handovercard.web;

import com.handovercard.auth.AuthService;
import com.handovercard.auth.DuplicateEmailException;
import com.handovercard.auth.InvalidCredentialsException;
import com.handovercard.auth.dto.LoginRequest;
import com.handovercard.auth.dto.RefreshRequest;
import com.handovercard.auth.dto.SignupRequest;
import com.handovercard.auth.dto.TokenResponse;
import com.handovercard.auth.oauth2.SocialLoginProviders;
import com.handovercard.auth.oauth2.SocialMemberOAuth2UserService;
import com.handovercard.card.HandoverCard;
import com.handovercard.card.HandoverCardMapper;
import com.handovercard.card.HandoverCardService;
import com.handovercard.card.InvalidCardStateException;
import com.handovercard.card.dto.HandoverCardResponse;
import com.handovercard.card.dto.HandoverCardUploadRequest;
import com.handovercard.common.ResourceNotFoundException;
import com.handovercard.pipeline.HandoverProcessingPipeline;
import com.handovercard.security.CustomUserDetails;
import com.handovercard.storage.StorageException;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

/**
 * 기능 확인용 서버 사이드 렌더링 화면. REST API와 같은 서비스 계층을 그대로 호출하며,
 * 인증 토큰만 브라우저가 다룰 수 있도록 헤더 대신 쿠키로 주고받는다.
 */
@Controller
@RequestMapping("/web")
public class WebController {

    private static final int PAGE_SIZE = 20;

    private final AuthService authService;
    private final HandoverCardService handoverCardService;
    private final HandoverCardMapper handoverCardMapper;
    private final HandoverProcessingPipeline processingPipeline;
    private final AuthTokenCookies authTokenCookies;
    private final SocialLoginProviders socialLoginProviders;

    public WebController(AuthService authService, HandoverCardService handoverCardService,
                          HandoverCardMapper handoverCardMapper, HandoverProcessingPipeline processingPipeline,
                          AuthTokenCookies authTokenCookies, SocialLoginProviders socialLoginProviders) {
        this.authService = authService;
        this.handoverCardService = handoverCardService;
        this.handoverCardMapper = handoverCardMapper;
        this.processingPipeline = processingPipeline;
        this.authTokenCookies = authTokenCookies;
        this.socialLoginProviders = socialLoginProviders;
    }

    @GetMapping
    public String index() {
        return "redirect:/web/cards";
    }

    @ModelAttribute("languages")
    public SupportedLanguage[] languages() {
        return SupportedLanguage.values();
    }

    // ---------- 인증 ----------

    @GetMapping("/login")
    public String loginForm(@RequestParam(required = false) String registered,
                             @RequestParam(required = false) String error, Model model) {
        if (registered != null) {
            model.addAttribute("message", "가입이 완료되었습니다. 로그인해 주세요.");
        }
        if (error != null) {
            model.addAttribute("error", socialLoginErrorMessage(error));
        }
        model.addAttribute("socialProviders", socialLoginProviders.configured());
        return "login";
    }

    @PostMapping("/login")
    public String login(@RequestParam String email, @RequestParam String password,
                         HttpServletResponse response, Model model) {
        try {
            TokenResponse tokens = authService.login(new LoginRequest(email, password));
            authTokenCookies.write(response, tokens);
        } catch (InvalidCredentialsException e) {
            model.addAttribute("error", "이메일 또는 비밀번호가 올바르지 않습니다.");
            model.addAttribute("email", email);
            model.addAttribute("socialProviders", socialLoginProviders.configured());
            return "login";
        }
        return "redirect:/web/cards";
    }

    /** 소셜 로그인 실패 사유는 코드로만 넘어온다. 화면에 띄우는 문구는 서버가 정한 것만 쓴다. */
    private String socialLoginErrorMessage(String errorCode) {
        if (SocialMemberOAuth2UserService.UNVERIFIED_EMAIL.equals(errorCode)) {
            return "소셜 계정의 이메일을 확인할 수 없습니다. 공급자에서 이메일 인증을 마친 뒤 다시 시도해 주세요.";
        }
        return "소셜 로그인에 실패했습니다. 잠시 후 다시 시도해 주세요.";
    }

    @GetMapping("/signup")
    public String signupForm(@ModelAttribute("form") SignupRequest form) {
        return "signup";
    }

    @PostMapping("/signup")
    public String signup(@Valid @ModelAttribute("form") SignupRequest form, BindingResult bindingResult, Model model) {
        if (bindingResult.hasErrors()) {
            return "signup";
        }
        try {
            authService.signup(form);
        } catch (DuplicateEmailException e) {
            model.addAttribute("error", "이미 가입된 이메일입니다.");
            return "signup";
        }
        return "redirect:/web/login?registered";
    }

    @PostMapping("/logout")
    public String logout(@CookieValue(value = AuthTokenCookies.REFRESH_TOKEN_COOKIE, required = false) String refreshToken,
                          HttpServletResponse response) {
        if (refreshToken != null) {
            authService.logout(new RefreshRequest(refreshToken));
        }
        authTokenCookies.clear(response);
        return "redirect:/web/login";
    }

    // ---------- 인수인계 카드 ----------

    @GetMapping("/cards")
    public String list(@AuthenticationPrincipal CustomUserDetails principal,
                        @RequestParam(defaultValue = "0") int page, Model model) {
        Page<HandoverCardResponse> cards = handoverCardService
                .listAccessible(principal.getMember(), PageRequest.of(page, PAGE_SIZE, Sort.by(Sort.Direction.DESC, "createdAt")))
                .map(handoverCardMapper::toResponse);

        model.addAttribute("cards", cards);
        model.addAttribute("memberName", principal.getMember().getName());
        model.addAttribute("uploadForm", newUploadForm(principal));
        return "cards";
    }

    @PostMapping("/cards")
    public String upload(@Valid @ModelAttribute("uploadForm") HandoverCardUploadRequest uploadForm,
                          BindingResult bindingResult, @AuthenticationPrincipal CustomUserDetails principal,
                          @RequestParam(defaultValue = "0") int page, Model model) {
        if (bindingResult.hasErrors() || uploadForm.getAudio() == null || uploadForm.getAudio().isEmpty()) {
            if (uploadForm.getAudio() == null || uploadForm.getAudio().isEmpty()) {
                bindingResult.rejectValue("audio", "required", "음성 파일을 선택해 주세요.");
            }
            populateList(model, principal, page);
            return "cards";
        }

        // 발신자는 로그인한 회원으로 고정한다 — 폼 값은 표시용이라 그대로 신뢰하지 않는다
        uploadForm.setSenderName(principal.getMember().getName());

        HandoverCard card = handoverCardService.createAndPersist(uploadForm, principal.getMember());
        processingPipeline.processAsync(card.getId());
        return "redirect:/web/cards/" + card.getId();
    }

    @GetMapping("/cards/{id}")
    public String detail(@PathVariable Long id, @AuthenticationPrincipal CustomUserDetails principal, Model model) {
        HandoverCard card = handoverCardService.getAccessible(id, principal.getMember());
        HandoverCardResponse response = handoverCardMapper.toResponse(card);

        model.addAttribute("card", response);
        model.addAttribute("owned", card.getOwner().getId().equals(principal.getMember().getId()));
        // 파이프라인이 비동기라 진행 중일 때는 화면을 자동 새로고침해서 상태 변화를 보여준다
        model.addAttribute("inProgress", switch (response.status()) {
            case COMPLETED, FAILED -> false;
            default -> true;
        });
        return "card-detail";
    }

    @PostMapping("/cards/{id}/delete")
    public String delete(@PathVariable Long id, @AuthenticationPrincipal CustomUserDetails principal,
                          RedirectAttributes redirectAttributes) {
        handoverCardService.delete(id, principal.getMember());
        redirectAttributes.addFlashAttribute("message", "카드를 삭제했습니다.");
        return "redirect:/web/cards";
    }

    @PostMapping("/cards/{id}/reprocess")
    public String reprocess(@PathVariable Long id, @AuthenticationPrincipal CustomUserDetails principal) {
        HandoverCard card = handoverCardService.reprocess(id, principal.getMember());
        processingPipeline.processAsync(card.getId());
        return "redirect:/web/cards/" + id;
    }

    @ExceptionHandler({ResourceNotFoundException.class, InvalidCardStateException.class, StorageException.class})
    public String handleCardError(Exception e, RedirectAttributes redirectAttributes) {
        redirectAttributes.addFlashAttribute("error", e.getMessage());
        return "redirect:/web/cards";
    }

    private HandoverCardUploadRequest newUploadForm(CustomUserDetails principal) {
        HandoverCardUploadRequest form = new HandoverCardUploadRequest();
        form.setSenderName(principal.getMember().getName());
        form.setSourceLanguage(SupportedLanguage.KO.getCode());
        form.setTargetLanguage(SupportedLanguage.EN.getCode());
        return form;
    }

    private void populateList(Model model, CustomUserDetails principal, int page) {
        Page<HandoverCardResponse> cards = handoverCardService
                .listAccessible(principal.getMember(), PageRequest.of(page, PAGE_SIZE, Sort.by(Sort.Direction.DESC, "createdAt")))
                .map(handoverCardMapper::toResponse);
        model.addAttribute("cards", cards);
        model.addAttribute("memberName", principal.getMember().getName());
    }

}
