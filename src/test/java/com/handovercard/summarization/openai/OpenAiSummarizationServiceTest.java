package com.handovercard.summarization.openai;

import com.handovercard.summarization.SummarizationException;
import com.handovercard.summarization.SummarizationRequest;
import com.handovercard.summarization.SummaryEntry;
import com.handovercard.summarization.SummaryResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OpenAiSummarizationServiceTest {

    private ChatModel chatModel;

    @BeforeEach
    void setUp() {
        chatModel = mock(ChatModel.class);
        when(chatModel.getOptions()).thenReturn(ChatOptions.builder().build());
    }

    private OpenAiSummarizationService service() {
        return new OpenAiSummarizationService(ChatClient.builder(chatModel));
    }

    private void stubChatReply(String content) {
        when(chatModel.call(any(Prompt.class)))
                .thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage(content)))));
    }

    @Test
    void parsesBilingualSummaryFromChatResponse() {
        stubChatReply("""
                {"keyPoints":[{"source":"Deployment is stable","target":"배포가 안정적입니다"}],\
                "actionItems":[{"source":"Monitor queue","target":"큐를 모니터링하세요"}],\
                "blockers":[]}""");

        SummarizationRequest request = new SummarizationRequest("transcript", "translated", "en", "ko");
        SummaryResult result = service().summarize(request);

        assertThat(result.keyPoints())
                .containsExactly(new SummaryEntry("Deployment is stable", "배포가 안정적입니다"));
        assertThat(result.actionItems())
                .containsExactly(new SummaryEntry("Monitor queue", "큐를 모니터링하세요"));
        assertThat(result.blockers()).isEmpty();
    }

    @Test
    void wrapsChatModelErrorAsSummarizationException() {
        when(chatModel.call(any(Prompt.class))).thenThrow(new RuntimeException("boom"));

        SummarizationRequest request = new SummarizationRequest("transcript", "translated", "en", "ko");

        assertThatThrownBy(() -> service().summarize(request))
                .isInstanceOf(SummarizationException.class);
    }

    @Test
    void wrapsMalformedResponseAsSummarizationException() {
        stubChatReply("not json");

        SummarizationRequest request = new SummarizationRequest("transcript", "translated", "en", "ko");

        assertThatThrownBy(() -> service().summarize(request))
                .isInstanceOf(SummarizationException.class);
    }
}
