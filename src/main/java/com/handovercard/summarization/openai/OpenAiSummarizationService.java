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
            You summarize an asynchronous shift/task handover note for a software engineering team. \
            The team is international, so the translated text is authoritative. \
            Keep each entry short and concrete. Use an empty array if a category has nothing to report.""";

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
