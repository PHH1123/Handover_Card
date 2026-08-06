package com.handovercard.transcription.openai;

import com.handovercard.transcription.TranscriptionException;
import com.handovercard.transcription.TranscriptionRequest;
import com.handovercard.transcription.TranscriptionResult;
import com.handovercard.transcription.TranscriptionService;
import org.springframework.ai.audio.transcription.AudioTranscriptionPrompt;
import org.springframework.ai.audio.transcription.AudioTranscriptionResponse;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiAudioTranscriptionModel;
import org.springframework.ai.openai.OpenAiAudioTranscriptionOptions;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@ConditionalOnProperty(prefix = "transcription", name = "provider", havingValue = "openai")
public class OpenAiTranscriptionService implements TranscriptionService {

    private static final String TRANSLATION_SYSTEM_PROMPT = """
            You are a professional translator for workplace shift-handover notes. \
            Translate the user's text faithfully, preserving names, numbers, and technical terms. \
            Respond with ONLY the translated text — no quotes, no explanations, no source text.""";

    private final OpenAiAudioTranscriptionModel transcriptionModel;
    private final ChatClient chatClient;

    public OpenAiTranscriptionService(OpenAiAudioTranscriptionModel transcriptionModel, ChatClient.Builder chatClientBuilder) {
        this.transcriptionModel = transcriptionModel;
        this.chatClient = chatClientBuilder.build();
    }

    @Override
    public TranscriptionResult transcribeAndTranslate(TranscriptionRequest request) {
        String transcript = transcribe(request);

        if (StringUtils.hasText(request.sourceLanguage())
                && request.sourceLanguage().equalsIgnoreCase(request.targetLanguage())) {
            return new TranscriptionResult(transcript, transcript);
        }

        String translatedText = translate(transcript, request.sourceLanguage(), request.targetLanguage());
        return new TranscriptionResult(transcript, translatedText);
    }

    private String transcribe(TranscriptionRequest request) {
        OpenAiAudioTranscriptionOptions.Builder optionsBuilder = OpenAiAudioTranscriptionOptions.builder()
                .from(transcriptionModel.getOptions());
        if (StringUtils.hasText(request.sourceLanguage()) && !"auto".equalsIgnoreCase(request.sourceLanguage())) {
            optionsBuilder.language(request.sourceLanguage());
        }

        AudioTranscriptionPrompt prompt = new AudioTranscriptionPrompt(request.audio(), optionsBuilder.build());

        AudioTranscriptionResponse response;
        try {
            response = transcriptionModel.call(prompt);
        } catch (Exception e) {
            throw new TranscriptionException("OpenAI transcription request failed", e);
        }

        String transcript = response.getResult() != null ? response.getResult().getOutput() : null;
        if (!StringUtils.hasText(transcript)) {
            throw new TranscriptionException("Unexpected OpenAI transcription response shape");
        }
        return transcript;
    }

    private String translate(String transcript, String sourceLanguage, String targetLanguage) {
        String sourceDescription = StringUtils.hasText(sourceLanguage) ? sourceLanguage : "the detected source language";
        String userPrompt = """
                Source language: %s
                Target language: %s

                Text to translate:
                %s
                """.formatted(sourceDescription, targetLanguage, transcript);

        String translated;
        try {
            translated = chatClient.prompt()
                    .system(TRANSLATION_SYSTEM_PROMPT)
                    .user(userPrompt)
                    .call()
                    .content();
        } catch (Exception e) {
            throw new TranscriptionException("OpenAI translation request failed", e);
        }

        if (!StringUtils.hasText(translated)) {
            throw new TranscriptionException("Unexpected OpenAI translation response shape");
        }
        return translated.trim();
    }
}
