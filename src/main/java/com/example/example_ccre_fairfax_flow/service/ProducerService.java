package com.example.example_ccre_fairfax_flow.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ProducerService {

    @Value("${jms.ccreQueue}")
    private String destination;

    private final JmsTemplate jmsTemplate;

    public void sendXmlToQueue(String xml) throws JsonProcessingException {
        jmsTemplate.convertAndSend(destination, xml);
    }
}
