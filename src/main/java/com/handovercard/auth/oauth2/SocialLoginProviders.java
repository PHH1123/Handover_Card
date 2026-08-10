package com.handovercard.auth.oauth2;

import com.handovercard.member.AuthProvider;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

/**
 * 실제로 쓸 수 있게 설정된 소셜 로그인 공급자를 알려준다.
 *
 * <p>클라이언트 ID/시크릿은 사람마다 다르게 발급받는 값이라 저장소에 넣을 수 없다. 그렇다고 값이 없을 때
 * 애플리케이션이 뜨지 않으면 곤란해서, 설정하지 않은 공급자는 {@link #UNCONFIGURED_CLIENT_ID}라는
 * 자리표시자를 달고 등록만 되어 있다. 여기서 그 자리표시자를 걸러내 로그인 화면에는 실제로 동작하는
 * 버튼만 보이게 하고, API는 설정되지 않은 공급자를 명확한 오류로 거절한다.
 */
@Component
public class SocialLoginProviders {

    /** `application.yml`에서 환경 변수가 없을 때 채워 넣는 값. */
    public static final String UNCONFIGURED_CLIENT_ID = "unset";

    private final ClientRegistrationRepository clientRegistrationRepository;

    public SocialLoginProviders(ClientRegistrationRepository clientRegistrationRepository) {
        this.clientRegistrationRepository = clientRegistrationRepository;
    }

    /** 로그인 화면에 버튼을 띄울 공급자 목록. */
    public List<AuthProvider> configured() {
        return Arrays.stream(AuthProvider.values())
                .filter(provider -> provider != AuthProvider.LOCAL)
                .filter(this::isConfigured)
                .toList();
    }

    public boolean isConfigured(AuthProvider provider) {
        return find(provider) != null;
    }

    /** 설정된 공급자의 등록 정보. 설정되지 않았으면 {@link SocialLoginException}. */
    public ClientRegistration require(AuthProvider provider) {
        ClientRegistration registration = find(provider);
        if (registration == null) {
            throw new SocialLoginException(provider.getDisplayName() + " 로그인이 설정되어 있지 않습니다.");
        }
        return registration;
    }

    private ClientRegistration find(AuthProvider provider) {
        ClientRegistration registration =
                clientRegistrationRepository.findByRegistrationId(provider.getRegistrationId());
        if (registration == null || UNCONFIGURED_CLIENT_ID.equals(registration.getClientId())) {
            return null;
        }
        return registration;
    }
}
