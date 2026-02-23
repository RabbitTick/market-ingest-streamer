package com.rabbittick.streamer.config;

import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import reactor.rabbitmq.RabbitFlux;
import reactor.rabbitmq.Sender;
import reactor.rabbitmq.SenderOptions;

/**
 * RabbitMQ 관련 설정을 관리하는 Configuration 클래스.
 *
 * <p>주요 책임:
 * <ul>
 *   <li>Topic Exchange 설정</li>
 *   <li>JSON 메시지 변환기 설정</li>
 *   <li>RabbitMQ 관리 도구 설정</li>
 *   <li>논블로킹 메시지 발행을 위한 reactor-rabbitmq Sender 설정</li>
 * </ul>
 */
@Slf4j
@Configuration
public class RabbitMQConfig {

	@Value("${rabbitmq.exchange.market-data}")
	private String exchangeName;

	@Value("${spring.rabbitmq.host}")
    private String host;

    @Value("${spring.rabbitmq.port}")
    private int port;

    @Value("${spring.rabbitmq.username}")
    private String username;

    @Value("${spring.rabbitmq.password}")
    private String password;

	/** reactor-rabbitmq Sender 인스턴스. Bean 반환 및 종료 시 정리에 사용한다. */
	private Sender sender;

	/**
	 * 설정값이 올바르게 주입되었는지 검증한다.
	 *
	 * @throws IllegalArgumentException Exchange 이름이 비어있는 경우
	 */
	@PostConstruct
	private void validateConfig() {
		if (!StringUtils.hasText(exchangeName)) {
			throw new IllegalArgumentException("Exchange name must not be blank");
		}
		log.info("RabbitMQ Exchange 설정 완료: {}", exchangeName);
	}

	/**
	 * 시장 데이터용 Topic Exchange를 생성한다.
	 *
	 * <p>Topic Exchange는 라우팅 키 패턴 매칭을 통해
	 * 유연한 메시지 라우팅을 제공한다.
	 *
	 * @return 설정된 TopicExchange
	 */
	@Bean
	public TopicExchange marketDataExchange() {
		return new TopicExchange(
			exchangeName,
			true,    // durable: 서버 재시작 시에도 유지
			false    // autoDelete: 사용하지 않을 때 자동 삭제하지 않음
		);
	}

	/**
	 * RabbitMQ 인프라를 자동으로 관리하는 AmqpAdmin을 생성한다.
	 *
	 * <p>Exchange, Queue, Binding 등의 RabbitMQ 구성 요소를
	 * 프로그래밍 방식으로 생성하고 관리할 수 있다.
	 *
	 * @param connectionFactory RabbitMQ 연결 팩토리
	 * @return AmqpAdmin 인스턴스
	 */
	@Bean
	public AmqpAdmin amqpAdmin(ConnectionFactory connectionFactory) {
		return new RabbitAdmin(connectionFactory);
	}

	/**
	 * JSON 메시지 직렬화/역직렬화를 위한 MessageConverter를 생성한다.
	 *
	 * <p>Jackson ObjectMapper를 사용하여 Java 객체와 JSON 간
	 * 변환을 수행한다.
	 *
	 * @param objectMapper JSON 변환을 위한 ObjectMapper
	 * @return Jackson2JsonMessageConverter 인스턴스
	 */
	@Bean
	public MessageConverter jsonMessageConverter(ObjectMapper objectMapper) {
		return new Jackson2JsonMessageConverter(objectMapper);
	}

	/**
	 * 논블로킹 메시지 발행을 위한 Sender를 생성한다.
	 *
	 * <p>SenderOptions.connectionFactory()를 사용하면 Connection 관리를
	 * reactor-rabbitmq가 내부적으로 처리한다. NIO를 사용하여 블로킹 없이 발행한다.
	 *
	 * @return reactor-rabbitmq Sender 인스턴스
	 */
	@Bean
	public Sender reactorRabbitSender() {
        com.rabbitmq.client.ConnectionFactory connectionFactory = 
            new com.rabbitmq.client.ConnectionFactory();
        connectionFactory.setHost(host);
        connectionFactory.setPort(port);
        connectionFactory.setUsername(username);
        connectionFactory.setPassword(password);
        connectionFactory.useNio();  // 논블로킹 I/O 활성화

        SenderOptions senderOptions = new SenderOptions()
            .connectionFactory(connectionFactory)
            .resourceManagementScheduler(reactor.core.scheduler.Schedulers.boundedElastic());

        this.sender = RabbitFlux.createSender(senderOptions);
        log.info("Reactor RabbitMQ Sender 초기화 완료 (host: {}:{}, NIO)", host, port);
        
        return this.sender;
    }

	/**
	 * 애플리케이션 종료 시 Sender 리소스를 정리한다.
	 *
	 * <p>Sender가 null이 아닐 때만 close()를 호출하여 연결을 닫는다.
	 */
	@PreDestroy
	public void cleanup() {
        if (sender != null) {
            sender.close();
            log.info("Reactor RabbitMQ Sender 종료");
        }
    }
}