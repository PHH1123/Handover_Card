package com.handovercard.summarization.openai;

import com.handovercard.summarization.SummarizationException;
import com.handovercard.summarization.SummarizationRequest;
import com.handovercard.summarization.SummarizationService;
import com.handovercard.summarization.SummaryResult;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(prefix = "summarization", name = "provider", havingValue = "openai")
public class OpenAiSummarizationService implements SummarizationService {

    private static final String SYSTEM_PROMPT = """
            You turn a spoken shift/task handover note into a structured, actionable summary for a \
            software engineering team. The speaker is handing work over to a teammate who was not present.

            First group the note by topic: one incident, task or decision = ONE entry. Only then write \
            the entries. Assign each topic to exactly one category and never repeat it in another.

            - keyPoints: what happened and where things stand now — diagnoses, decisions taken, changes \
              shipped. Merge aggressively: at most 4 entries, and fewer is better.
            - actionItems: what the RECEIVER must do next. Include every request the speaker makes, every \
              decision the receiver has to make, review requests, and deadlines. Keep stated deadlines and \
              owners. A handover almost always contains requests — re-read the note before leaving this empty.
            - blockers: unresolved problems, risks, and anything the speaker could not finish or decide \
              alone. State why it is blocked. A temporary workaround that still needs a real fix belongs here.

            Write summaries, not transcript fragments. BOTH `source` and `target` must be your own \
            condensed wording — never a sentence lifted from the note. Drop filler and hedging, but KEEP \
            concrete details (numbers, names, deadlines) and keep the reason whenever the speaker gave one.

            One clause per entry. Every entry must stand on its own and be understandable in isolation — \
            never emit a dangling fragment that only makes sense beside another entry.

            Write every entry twice with identical meaning: `source` in the note's source language and \
            `target` in the target language. Never leave either blank; never mix languages within a field.
            Use an empty array only when the note genuinely has nothing for that category.

            ---
            WORKED EXAMPLE (source language ko, target language en)

            Note: "어제 저녁에 로그인 API가 간헐적으로 500 떨어졌는데요, 처음엔 DB 커넥션 풀 문제인 줄 \
            알았는데 확인해보니까 세션 캐시 서버가 메모리 부족으로 몇 번 죽었더라고요. 일단 캐시 서버 \
            메모리를 4기가로 올려놨고요. 근데 이건 임시고 세션 TTL을 줄이는 게 맞을 것 같아요. 모니터링 \
            알람은 아직 안 걸었어요. 어떤 임계값으로 잡을지 몰라서요. 내일 오전에 대시보드 한번 봐주시고 \
            알람 임계값 정해주세요. 그리고 배포 스크립트 PR 리뷰도 부탁드려요."

            Expected output:
            {
              "keyPoints": [
                {"source": "로그인 API 간헐적 500 오류의 원인은 세션 캐시 서버 메모리 부족",
                 "target": "Intermittent 500s on the login API were caused by the session cache server running out of memory"},
                {"source": "임시 조치로 캐시 서버 메모리를 4GB로 상향",
                 "target": "Raised the cache server memory to 4GB as a stopgap"}
              ],
              "actionItems": [
                {"source": "내일 오전 대시보드 확인 후 모니터링 알람 임계값 결정",
                 "target": "Review the dashboard tomorrow morning and set the monitoring alert threshold"},
                {"source": "배포 스크립트 PR 리뷰",
                 "target": "Review the deployment script PR"}
              ],
              "blockers": [
                {"source": "메모리 상향은 임시 조치이며 세션 TTL 단축이 근본 해결책으로 남아 있음",
                 "target": "The memory bump is only a stopgap; shortening the session TTL is still the real fix"},
                {"source": "적정 임계값을 몰라 모니터링 알람을 아직 걸지 못함",
                 "target": "Monitoring alerts are not set up yet because the right threshold is unknown"}
              ]
            }

            Note how three sentences about one incident collapse into a single keyPoint, nothing is copied \
            word-for-word, and each blocker keeps the reason it is blocked.""";

    private final ChatClient chatClient;

    public OpenAiSummarizationService(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    @Override
    public SummaryResult summarize(SummarizationRequest request) {
        String userPrompt = """
                Source language: %s
                Target language: %s

                Original transcript:
                %s

                Translated text:
                %s
                """.formatted(request.sourceLanguage(), request.targetLanguage(),
                request.transcript(), request.translatedText());

        try {
            return chatClient.prompt()
                    .system(SYSTEM_PROMPT)
                    .user(userPrompt)
                    .call()
                    .entity(SummaryResult.class);
        } catch (Exception e) {
            throw new SummarizationException("OpenAI summarization request failed", e);
        }
    }
}
