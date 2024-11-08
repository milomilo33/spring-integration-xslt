package com.example.example_ccre_fairfax_flow.integration;

import lombok.RequiredArgsConstructor;
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.file.FileWritingMessageHandler;
import org.springframework.integration.file.support.FileExistsMode;
import org.springframework.integration.handler.LoggingHandler;
import org.springframework.integration.handler.advice.RequestHandlerRetryAdvice;
import org.springframework.integration.jms.dsl.Jms;
import org.springframework.integration.xml.transformer.XsltPayloadTransformer;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandler;

import java.io.File;

@RequiredArgsConstructor
@Configuration
public class CCREFlow {

    @Value("${file.output.directory}")
    private String outputDirectory;

    @Value("${jms.ccreQueue}")
    private String destination;

    private final RequestHandlerRetryAdvice retryAdvice;

    @Bean
    public MessageChannel transformChannel() {
        return new DirectChannel();
    }

    // XSLT Transformer using the provided CCRE.xslt file
    @Bean
    public XsltPayloadTransformer xsltTransformer() {
        return new XsltPayloadTransformer(new ClassPathResource("xslt/CCRE.xslt"));
    }

//    @Bean
//    public XsltPayloadTransformer xsltTransformer() throws SaxonApiException, IOException {
//        // Use Saxon as the transformer
//        Processor processor = new Processor(false);
//        XsltCompiler compiler = processor.newXsltCompiler();
//        XsltExecutable executable = compiler.compile(new StreamSource(new ClassPathResource("/xslt/CCRE.xslt").getInputStream()));
//        new XsltPayloadTransformer(TransformerFactory.newInstance().newTemplates(executable.load()))
//        return executable.load();
//    }

    // Flow for transforming XML from JMS queue
    @Bean
    public IntegrationFlow transformFlow(ActiveMQConnectionFactory connectionFactory) {
        return IntegrationFlow
                .from(Jms.messageDrivenChannelAdapter(connectionFactory)
                    .destination("FCE.TO.DISPOSITION.CCRE")
                    .errorChannel("errorChannel"))
                .log(LoggingHandler.Level.INFO, "Disposition CCRE Incoming Payload: ", Message::getPayload)
                .transform(xsltTransformer())
                .log(LoggingHandler.Level.INFO, "Disposition CCRE Transformed Payload: ", Message::getPayload)
                .channel(transformChannel())
                .get();
    }

    // File writing handler to save transformed data with logging
    @Bean
    public MessageHandler fileWritingMessageHandler() {
        FileWritingMessageHandler handler = new FileWritingMessageHandler(new File(outputDirectory));
        handler.setFileExistsMode(FileExistsMode.REPLACE);
        handler.setExpectReply(false);
        handler.setFileNameGenerator(message -> "CCRE_Disposition_" + System.currentTimeMillis() + ".txt");
        return handler;
    }

    // Flow for handling file output with logging
    @Bean
    public IntegrationFlow outputFlow() {
        return IntegrationFlow.from(transformChannel())
                .log(LoggingHandler.Level.INFO, "Disposition CCRE Flat File Payload: ", Message::getPayload)
                .handle(fileWritingMessageHandler(), e -> e.advice(retryAdvice))
                .get();
    }
}
