package com.handovercard.summarization.mock;

import com.handovercard.summarization.SummarizationException;
import com.handovercard.summarization.SummarizationRequest;
import com.handovercard.summarization.SummarizationService;
import com.handovercard.summarization.SummaryResult;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@ConditionalOnProperty(prefix = "summarization", name = "provider", havingValue = "mock", matchIfMissing = true)
public class MockSummarizationService implements SummarizationService {

    private final MockSummarizationProperties props;

    public MockSummarizationService(MockSummarizationProperties props) {
        this.props = props;
    }

    @Override
    public SummaryResult summarize(SummarizationRequest request) {
        simulateLatency();

        return new SummaryResult(
                List.of("[mock summary, target=%s] %s".formatted(request.targetLanguage(), request.translatedText())),
                List.of("[mock] Review the handover note"),
                List.of()
        );
    }

    private void simulateLatency() {
        try {
            Thread.sleep(props.simulatedDelayMs());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SummarizationException("Summarization interrupted", e);
        }
    }
}
