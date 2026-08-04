package com.handovercard.transcription.mock;

import com.handovercard.transcription.TranscriptionException;
import com.handovercard.transcription.TranscriptionRequest;
import com.handovercard.transcription.TranscriptionResult;
import com.handovercard.transcription.TranscriptionService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.nio.file.Files;

@Service
@ConditionalOnProperty(prefix = "transcription", name = "provider", havingValue = "mock", matchIfMissing = true)
public class MockTranscriptionService implements TranscriptionService {

    private final MockTranscriptionProperties props;

    public MockTranscriptionService(MockTranscriptionProperties props) {
        this.props = props;
    }

    @Override
    public TranscriptionResult transcribeAndTranslate(TranscriptionRequest request) {
        if (!Files.exists(request.audioFilePath())) {
            throw new TranscriptionException("Audio file not found: " + request.audioFilePath());
        }

        simulateLatency();

        String fileName = request.audioFilePath().getFileName().toString();
        String transcript = "[mock transcript, source=%s] Handover recording %s for card #%d."
                .formatted(request.sourceLanguage(), fileName, request.cardId());
        String translatedText = "[mock translation, target=%s] Handover recording %s for card #%d."
                .formatted(request.targetLanguage(), fileName, request.cardId());

        return new TranscriptionResult(transcript, translatedText);
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
