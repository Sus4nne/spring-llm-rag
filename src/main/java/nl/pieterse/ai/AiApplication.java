package nl.pieterse.ai;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingClient;
import org.springframework.ai.ollama.OllamaChatClient;
import org.springframework.ai.prompt.Prompt;
import org.springframework.ai.prompt.SystemPromptTemplate;
import org.springframework.ai.prompt.messages.UserMessage;
import org.springframework.ai.reader.ExtractedTextFormatter;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.reader.pdf.config.PdfDocumentReaderConfig;
import org.springframework.ai.retriever.VectorStoreRetriever;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.PgVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@SpringBootApplication
public class AiApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiApplication.class, args);
    }

    @Bean
    VectorStore vectorStore(EmbeddingClient ec,
                            JdbcTemplate t) {
        return new PgVectorStore(t, ec);
    }

    @Bean
    VectorStoreRetriever vectorStoreRetriever(VectorStore vs) {
        return new VectorStoreRetriever(vs, 4, 0.75);
    }

    @Bean
    TokenTextSplitter tokenTextSplitter() {
        return new TokenTextSplitter();
    }

    static void init(VectorStore vectorStore, JdbcTemplate template, Resource pdfResource)
            throws Exception {

        System.out.println("Deleting all vectors from the vector store...");
        template.update("delete from vector_store");

        var config = PdfDocumentReaderConfig.builder()
                .withPageExtractedTextFormatter(new ExtractedTextFormatter.Builder().withNumberOfBottomTextLinesToDelete(3)
                        .withNumberOfTopPagesToSkipBeforeDelete(1)
                        .build())
                .withPagesPerDocument(1)
                .build();

        var pdfReader = new PagePdfDocumentReader(pdfResource, config);
        var textSplitter = new TokenTextSplitter();
        System.out.println("Storing vectors in the vector store...");
        vectorStore.accept(textSplitter.apply(pdfReader.get()));

    }

    @Bean
    ApplicationRunner applicationRunner(
            ChatBot chatBot,
            VectorStore vectorStore,
            JdbcTemplate template,
            @Value("file:///Users/susannepieterse/Downloads/Handreiking-2023+Bescherm+domeinnamen+tegen+phishing+2.0.pdf") Resource pdfResource) {
        return args -> {
            System.out.println("Beginning the chat with Ollama...");

            init(vectorStore, template, pdfResource);

            var response = chatBot.chat("Wat adviseert het NCSC om te doen tegen phishing?");

            System.out.println(Map.of("response", response));

            System.out.println("Chat with Ollama complete.");
        };
    }

}

@Component
class ChatBot {

    private final String templatePrompt = """
            Je assisteert met vragen over de bescherming tegen phishing.
                    
            Gebruik de informatie uit de sectie DOCUMENTS hieronder om te accurate antwoorden te geven.
            Als je het niet zeker weet, geef dan aan dat je het niet weet.
            Beantwoord de vragen in het Nederlands.
                    
            DOCUMENTS:
            {documents}
            
            """;

    private  final OllamaChatClient chatClient;

    private final VectorStoreRetriever vsr;

    ChatBot(OllamaChatClient chatClient, VectorStoreRetriever vsr) {
        this.chatClient = chatClient;
        this.vsr = vsr;
    }

    public String chat (String message) {
        var listOfSimilarDocuments = vsr.retrieve(message);
        var documents = listOfSimilarDocuments
                .stream()
                .map(Document::getContent)
                .collect(Collectors.joining(System.lineSeparator()));
        var systemMessage = new SystemPromptTemplate(this.templatePrompt)
                .createMessage(Map.of("documents", documents));
        var userMessage = new UserMessage(message);
        var prompt = new Prompt(List.of(systemMessage, userMessage));
        var aiResponse = chatClient.generate(prompt);
        return aiResponse.getGeneration().getContent();
    }
}
