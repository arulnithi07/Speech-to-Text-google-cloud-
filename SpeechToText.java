
import com.google.cloud.speech.v1.*;
import com.google.protobuf.ByteString;
import javax.sound.sampled.*;
import java.io.IOException;

public class SpeechToText {
    private static final int SAMPLE_RATE = 16000; // Hz
    private static final int CHUNK_SIZE = 1024;   // Audio chunk size
    private static final String LANGUAGE_CODE = "en-US";

    public static void main(String[] args) {  
        try {  
            new Thread(SpeechToText::transcribeAudio).start();  
        } catch (Exception e) {  
            System.err.println("Error: " + e.getMessage());  
        }  
    }  

    private static void transcribeAudio() {  
        try {  
            AudioFormat format = new AudioFormat(SAMPLE_RATE, 16, 1, true, false);  
            DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);  
            TargetDataLine microphone = (TargetDataLine) AudioSystem.getLine(info);  
            microphone.open(format);  
            microphone.start();  

            try (SpeechClient speechClient = SpeechClient.create()) {  
                RecognitionConfig config = RecognitionConfig.newBuilder()  
                        .setEncoding(RecognitionConfig.AudioEncoding.LINEAR16)  
                        .setSampleRateHertz(SAMPLE_RATE)  
                        .setLanguageCode(LANGUAGE_CODE)  
                        .build();  

                StreamingRecognitionConfig streamingConfig = StreamingRecognitionConfig.newBuilder()  
                        .setConfig(config)  
                        .setInterimResults(true)  
                        .build();  

                StreamController streamController = new StreamController(speechClient, streamingConfig);  
                new Thread(() -> streamAudio(microphone, streamController)).start();  
                streamController.processResponses();  
            }  
        } catch (LineUnavailableException | IOException e) {  
            System.err.println("Error during transcription: " + e.getMessage());  
        }  
    }  

    private static void streamAudio(TargetDataLine microphone, StreamController streamController) {  
        byte[] buffer = new byte[CHUNK_SIZE];  
        while (microphone.isOpen()) {  
            int bytesRead = microphone.read(buffer, 0, buffer.length);  
            if (bytesRead > 0) {  
                ByteString audioBytes = ByteString.copyFrom(buffer, 0, bytesRead);  
                StreamingRecognizeRequest request = StreamingRecognizeRequest.newBuilder()  
                        .setAudioContent(audioBytes)  
                        .build();  
                streamController.send(request);  
            }  
        }  
    }  

    static class StreamController {  
        private final SpeechClient speechClient;  
        private final StreamingRecognitionConfig config;  
        private StreamingRecognizeResponse response;  

        StreamController(SpeechClient speechClient, StreamingRecognitionConfig config) {  
            this.speechClient = speechClient;  
            this.config = config;  
        }  

        void send(StreamingRecognizeRequest request) {  
            speechClient.streamingRecognizeCallable().splitCall((responseObserver) -> {  
                responseObserver.onNext(StreamingRecognizeRequest.newBuilder()  
                        .setStreamingConfig(config)  
                        .build());  
                responseObserver.onNext(request);  
            });  
        }  

        void processResponses() {  
            speechClient.streamingRecognizeCallable().splitCall((responseObserver) -> {  
                responseObserver.onNext(StreamingRecognizeRequest.newBuilder()  
                        .setStreamingConfig(config)  
                        .build());  
                responseObserver.onStart((controller) -> {  
                    while (true) {  
                        try {  
                            StreamingRecognizeResponse response = controller.get();  
                            if (response != null) {  
                                for (StreamingRecognitionResult result : response.getResultsList()) {  
                                    if (result.getAlternativesCount() > 0) {  
                                        String transcript = result.getAlternatives(0).getTranscript();  
                                        System.out.println("Transcript: " + transcript);  
                                    }  
                                }  
                            }  
                        } catch (Exception e) {  
                            System.err.println("Error processing response: " + e.getMessage());  
                        }  
                    }  
                });  
            });  
        }  
    }
}
