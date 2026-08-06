package com.handovercard.transcription.openai;

import com.handovercard.transcription.TranscriptionException;
import com.handovercard.transcription.TranscriptionRequest;
import com.handovercard.transcription.TranscriptionResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.audio.transcription.AudioTranscription;
import org.springframework.ai.audio.transcription.AudioTranscriptionPrompt;
import org.springframework.ai.audio.transcription.AudioTranscriptionResponse;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.ai.openai.OpenAiAudioTranscriptionModel;
import org.springframework.ai.openai.OpenAiAudioTranscriptionOptions;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpenAiTranscriptionServiceTest {

    @TempDir
    Path tempDir;

    private OpenAiAudioTranscriptionModel transcriptionModel;
    private ChatModel chatModel;
    private Resource audioFile;

    @BeforeEach
    void setUp() throws IOException {
        transcriptionModel = mock(OpenAiAudioTranscriptionModel.class);
        when(transcriptionModel.getOptions())
                .thenReturn(OpenAiAudioTranscriptionOptions.builder().model("gpt-4o-mini-transcribe").build());

        chatModel = mock(ChatModel.class);
        when(chatModel.getOptions()).thenReturn(ChatOptions.builder().build());

        Path file = tempDir.resolve("sample.wav");
        Files.write(file, "fake-audio-bytes".getBytes(StandardCharsets.UTF_8));
        audioFile = new FileSystemResource(file);
    }

    private OpenAiTranscriptionService service() {
        return new OpenAiTranscriptionService(transcriptionModel, ChatClient.builder(chatModel));
    }

    private void stubTranscription(String text) {
        when(transcriptionModel.call(any(AudioTranscriptionPrompt.class)))
                .thenReturn(new AudioTranscriptionResponse(new AudioTranscription(text)));
    }

    private void stubChatReply(String content) {
        when(chatModel.call(any(Prompt.class)))
                .thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage(content)))));
    }

    @Test
    void transcribesAndTranslatesWhenLanguagesDiffer() {
        stubTranscription("Hello there");
        stubChatReply("안녕하세요");

        TranscriptionRequest request = new TranscriptionRequest(1L, audioFile, "en", "ko");
        TranscriptionResult result = service().transcribeAndTranslate(request);

        assertThat(result.transcript()).isEqualTo("Hello there");
        assertThat(result.translatedText()).isEqualTo("안녕하세요");
    }

    @Test
    void skipsTranslationCallWhenSourceAndTargetLanguageMatchIgnoringCase() {
        stubTranscription("Hello there");

        TranscriptionRequest request = new TranscriptionRequest(1L, audioFile, "en", "EN");
        TranscriptionResult result = service().transcribeAndTranslate(request);

        assertThat(result.transcript()).isEqualTo("Hello there");
        assertThat(result.translatedText()).isEqualTo("Hello there");
        verify(chatModel, never()).call(any(Prompt.class));
    }

    @Test
    void setsLanguageHintWhenSourceLanguageProvided() {
        stubTranscription("Hello there");
        stubChatReply("안녕");

        service().transcribeAndTranslate(new TranscriptionRequest(1L, audioFile, "en", "ko"));

        ArgumentCaptor<AudioTranscriptionPrompt> captor = ArgumentCaptor.forClass(AudioTranscriptionPrompt.class);
        verify(transcriptionModel).call(captor.capture());
        OpenAiAudioTranscriptionOptions options = (OpenAiAudioTranscriptionOptions) captor.getValue().getOptions();
        assertThat(options.getLanguage()).isEqualTo("en");
    }

    @Test
    void omitsLanguageHintWhenSourceLanguageIsAuto() {
        stubTranscription("hi");
        stubChatReply("안녕");

        service().transcribeAndTranslate(new TranscriptionRequest(1L, audioFile, "auto", "ko"));

        ArgumentCaptor<AudioTranscriptionPrompt> captor = ArgumentCaptor.forClass(AudioTranscriptionPrompt.class);
        verify(transcriptionModel).call(captor.capture());
        OpenAiAudioTranscriptionOptions options = (OpenAiAudioTranscriptionOptions) captor.getValue().getOptions();
        assertThat(options.getLanguage()).isNull();
    }

    @Test
    void wrapsTranscriptionErrorAsTranscriptionException() {
        when(transcriptionModel.call(any(AudioTranscriptionPrompt.class)))
                .thenThrow(new RuntimeException("boom"));

        TranscriptionRequest request = new TranscriptionRequest(1L, audioFile, "en", "ko");

        assertThatThrownBy(() -> service().transcribeAndTranslate(request))
                .isInstanceOf(TranscriptionException.class);
    }

    @Test
    void throwsWhenTranscriptionResponseHasBlankOutput() {
        when(transcriptionModel.call(any(AudioTranscriptionPrompt.class)))
                .thenReturn(new AudioTranscriptionResponse(new AudioTranscription("")));

        TranscriptionRequest request = new TranscriptionRequest(1L, audioFile, "en", "ko");

        assertThatThrownBy(() -> service().transcribeAndTranslate(request))
                .isInstanceOf(TranscriptionException.class);
    }

    @Test
    void wrapsTranslationErrorAsTranscriptionException() {
        stubTranscription("Hello there");
        when(chatModel.call(any(Prompt.class))).thenThrow(new RuntimeException("boom"));

        TranscriptionRequest request = new TranscriptionRequest(1L, audioFile, "en", "ko");

        assertThatThrownBy(() -> service().transcribeAndTranslate(request))
                .isInstanceOf(TranscriptionException.class);
    }

    @Test
    void throwsWhenTranslationResponseIsBlank() {
        stubTranscription("Hello there");
        stubChatReply("   ");

        TranscriptionRequest request = new TranscriptionRequest(1L, audioFile, "en", "ko");

        assertThatThrownBy(() -> service().transcribeAndTranslate(request))
                .isInstanceOf(TranscriptionException.class);
    }

    @Test
    void throwsWhenAudioFileDoesNotExist() {
        Resource missing = new FileSystemResource(tempDir.resolve("missing.wav"));
        when(transcriptionModel.call(any(AudioTranscriptionPrompt.class)))
                .thenThrow(new RuntimeException("file not found"));

        TranscriptionRequest request = new TranscriptionRequest(1L, missing, "en", "ko");

        assertThatThrownBy(() -> service().transcribeAndTranslate(request))
                .isInstanceOf(TranscriptionException.class);
    }
}
