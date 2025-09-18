package com.rabbittick.streamer.config;

import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

/**
 * RabbitMQ 관련 설정을 관리하는 Configuration 클래스.
 *
 * <p>주요 책임:
 * <ul>
 *   <li>Topic Exchange 설정</li>
 *   <li>JSON 메시지 변환기 설정</li>
 *   <li>RabbitMQ 관리 도구 설정</li>
 *   <li>메시지 발행 템플릿 설정</li>
 * </ul>
 */
@Slf4j
@Configuration
public class RabbitMQConfig {

	@Value("${rabbitmq.exchange.market-data}")
	private String exchangeName;

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
	 * 메시지 발행을 위한 RabbitTemplate을 생성한다.
	 *
	 * <p>발행 확인과 에러 처리를 위한 콜백을 설정하여
	 * 메시지 발행의 신뢰성을 높인다.
	 *
	 * @param connectionFactory RabbitMQ 연결 팩토리
	 * @param messageConverter 메시지 변환기
	 * @return 설정된 RabbitTemplate
	 */
	@Bean
	public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory,
		MessageConverter messageConverter) {
		RabbitTemplate template = new RabbitTemplate(connectionFactory);
		template.setMessageConverter(messageConverter);

		// 메시지가 라우팅되지 않으면 예외 발생
		template.setMandatory(true);

		// 발행 확인 콜백 설정
		template.setConfirmCallback((correlationData, ack, cause) -> {
			if (!ack) {
				log.error("메시지 발행 실패: {}", cause);
			}
		});

		// 반환된 메시지 처리 콜백 설정 (라우팅 실패 시)
		template.setReturnsCallback(returned -> {
			log.error("메시지 라우팅 실패 - Exchange: {}, RoutingKey: {}, ReplyText: {}",
				returned.getExchange(), returned.getRoutingKey(), returned.getReplyText());
		});

		return template;
	}
}