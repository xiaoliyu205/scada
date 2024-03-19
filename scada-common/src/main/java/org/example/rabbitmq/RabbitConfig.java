package org.example.rabbitmq;

import org.example.constant.DptConstant;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {

    @Bean
    public Queue subQueue() {
        return new Queue(DptConstant.DPT_PREDIX + "*");
    }

    @Bean
    public FanoutExchange subExchange() {
        return new FanoutExchange(DptConstant.EXCHANGE);
    }

    @Bean
    public Binding Binding() {
        return BindingBuilder.bind(subQueue()).to(subExchange());
    }

}
