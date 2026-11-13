package com.Monarca.Backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.mail.javamail.JavaMailSender;

@Configuration(proxyBeanMethods = false)
public class ResendMailTransportConfig {
    /** MAIL_PASSWORD ya almacena una API Key de Resend. No duplicarla en Railway. */
    @Bean
    @Primary
    @ConditionalOnProperty(name = "monarca.mail.transport", havingValue = "resend-http")
    public JavaMailSender resendHttpsMailSender(@Value("${spring.mail.password:}") String apiKey) {
        return new ResendHttpsMailSender(apiKey);
    }
}
