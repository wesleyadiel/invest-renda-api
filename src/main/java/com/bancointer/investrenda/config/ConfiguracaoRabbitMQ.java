package com.bancointer.investrenda.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ConfiguracaoRabbitMQ {

    @Value("${investrenda.rabbitmq.exchange}")
    private String nomeExchange;

    @Value("${investrenda.rabbitmq.fila}")
    private String nomeFila;

    @Value("${investrenda.rabbitmq.chave-roteamento}")
    private String chaveRoteamento;

    @Value("${investrenda.rabbitmq.fila-morta}")
    private String nomeFilaMorta;

    @Bean
    public DirectExchange exchangeAplicacoes() {
        return new DirectExchange(nomeExchange);
    }

    @Bean
    public Queue filaAplicacoes() {
        return QueueBuilder.durable(nomeFila)
                .withArgument("x-dead-letter-exchange", "")
                .withArgument("x-dead-letter-routing-key", nomeFilaMorta)
                .build();
    }

    @Bean
    public Queue filaMortaAplicacoes() {
        return QueueBuilder.durable(nomeFilaMorta).build();
    }

    @Bean
    public Binding bindingAplicacoes(Queue filaAplicacoes, DirectExchange exchangeAplicacoes) {
        return BindingBuilder.bind(filaAplicacoes).to(exchangeAplicacoes).with(chaveRoteamento);
    }

    @Bean
    public Jackson2JsonMessageConverter conversorMensagem() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory,
                                          Jackson2JsonMessageConverter conversorMensagem) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(conversorMensagem);
        return template;
    }
}
