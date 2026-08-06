package com.handovercard.transcription.mock;

import com.handovercard.transcription.TranscriptionException;
import com.handovercard.transcription.TranscriptionRequest;
import com.handovercard.transcription.TranscriptionResult;
import com.handovercard.transcription.TranscriptionService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(prefix = "transcription", name = "provider", havingValue = "mock", matchIfMissing = true)
public class MockTranscriptionService implements TranscriptionService {

    private final MockTranscriptionProperties props;

    public MockTranscriptionService(MockTranscriptionProperties props) {
        this.props = props;
    }

    @Override
    public TranscriptionResult transcribeAndTranslate(TranscriptionRequest request) {
        if (request.audio() == null || !request.audio().exists()) {
            throw new TranscriptionException("Audio file not found: " + describe(request.audio()));
        }

        simulateLatency();

        String fileName = request.audio().getFilename();
        String transcript = "[mock transcript, source=%s] Handover recording %s for card #%d."
                .formatted(request.sourceLanguage(), fileName, request.cardId());
        String translatedText = "[mock translation, target=%s] Handover recording %s for card #%d."
                .formatted(request.targetLanguage(), fileName, request.cardId());

        return new TranscriptionResult(transcript, translatedText);
    }

    private String describe(Resource audio) {
        return audio == null ? "(none)" : audio.getDescription();
    }

    private void simulateLatency() {
        try {
            Thread.sleep(props.simulatedDelayMs());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new TranscriptionException("Transcription interrupted", e);
        }
    }
}
