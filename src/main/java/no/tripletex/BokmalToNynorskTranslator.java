package no.tripletex;

import com.theokanning.openai.completion.chat.ChatCompletionChoice;
import com.theokanning.openai.completion.chat.ChatCompletionRequest;
import com.theokanning.openai.completion.chat.ChatMessage;
import com.theokanning.openai.service.OpenAiService;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

public class BokmalToNynorskTranslator {

    private static final String MODEL = "gpt-4o";
    private static final String INPUT_FILE = "ApplicationResources.properties";
    private static final String OUTPUT_FILE = "ApplicationResources_nn_NN.properties";

    public static void main(String[] args) {
        try {
            LinkedHashMap<String, String> propertiesMap = new LinkedHashMap<>();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(INPUT_FILE), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (!line.trim().isEmpty() && line.contains("=")) {
                        int index = line.indexOf("=");
                        String key = line.substring(0, index);
                        String value = line.substring(index + 1);
                        propertiesMap.put(key, value);
                    }
                }
            }

            String apiKey =  "API-KEY";
            if (apiKey == null || apiKey.isEmpty()) {
                throw new IllegalStateException("Missing OpenAI API key. Set OPENAI_API_KEY environment variable.");
            }

            OpenAiService service = new OpenAiService(apiKey, Duration.ofSeconds(60));

            LinkedHashMap<String, String> translatedMap = new LinkedHashMap<>();
            int totalEntries = propertiesMap.size();
            int completed = 0;

            for (Map.Entry<String, String> entry : propertiesMap.entrySet()) {
                String translatedValue = translateText(service, entry.getValue());
                translatedMap.put(entry.getKey(), translatedValue);
                completed++;

                int progress = (int) (((double) completed / totalEntries) * 100);
                System.out.println("Progress: " + completed + "/" + totalEntries + " (" + progress + "% completed)");
                System.out.println(entry.getKey() + "=" + translatedValue);
            }

            try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(OUTPUT_FILE), StandardCharsets.UTF_8))) {
                for (Map.Entry<String, String> entry : translatedMap.entrySet()) {
                    writer.write(entry.getKey() + "=" + entry.getValue());
                    writer.newLine();
                }
            }

            System.out.println("Translation completed! Output saved to " + OUTPUT_FILE);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static String translateText(OpenAiService service, String text) {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new ChatMessage("system",
                "You are a translation assistant. You are translating the accounting system Tripletex from Norwegian Bokmål to Nynorsk. " +
                        "The input text may contain special Norwegian characters (e.g., æ, ø, å) and HTML characters, links and sometimes javascript. " +
                        "You must **preserve them exactly as they are** in the translation. " +
                        "Do not change any formatting, HTML entities, or escape sequences if they are in the original text. " +
                        "Convert Norwegian Bokmål text to Norwegian Nynorsk while keeping the meaning identical." +
                        " If word contains ø æ å, then use ø æ å in the translation. If the word contains Unicode escape sequences then use Unicode escape sequences." +
                        "Translate contemporary conservative nynorsk, use a-endings (adressa instead of adressen, fila instead of filen).  " +
                        "Do not add any extra explanations. Only return the translated text and nothing else." +
                        "Verify that the resulting text is spelled correctly in nynorsk language. Do not replace Unicode escape sequence with HTML entity. "+
                        "Do not replace ø æ å with HTML entity. Keep existing Unicode escape sequence. Preserve existing HTML syntax. " +
                        "Verifiser at teksten er skrevet på korrekt nynorsk for bruk i et økonomisystem. Bruk HTML entities i stedet for æ ø å i tekst hvor det allerede er i bruk. "
        ));
        messages.add(new ChatMessage("user", "Bokmål: " + text + "\nNynorsk:"));

        ChatCompletionRequest request = ChatCompletionRequest.builder()
                .messages(messages)
                .model(MODEL)
                .temperature(0.2)
                .maxTokens(16384)
                .build();

        int maxRetries = 10;
        int attempt = 0;
        while (attempt <= maxRetries) {
            try {
                List<ChatCompletionChoice> choices = service.createChatCompletion(request).getChoices();
                return choices.isEmpty() ? text : choices.get(0).getMessage().getContent().trim();
            } catch (Exception e) {
                System.err.println("Error during translation (attempt " + (attempt + 1) + "): " + e.getMessage());
                if (attempt == maxRetries) {
                    System.err.println("Translation failed after " + (maxRetries + 1) + " attempts. Returning original text.");
                    return text;
                }
                attempt++;
            }
        }
        return text;
    }
}
