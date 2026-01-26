package com.rabbittick.streamer.test;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * RabbitMQ 메시지 수신을 테스트하기 위한 임시 Consumer.
 *
 * <p>주요 책임:
 * <ul>
 *   <li>메시지 발행 기능의 동작 확인</li>
 *   <li>RabbitMQ 토폴로지 설정 검증</li>
 *   <li>실시간 메시지 수신 상태 모니터링</li>
 *   <li>개발 및 디버깅 시 메시지 내용 확인</li>
 * </ul>
 *
 * <p>이 클래스는 개발 단계에서만 사용되며,
 * application.yml에서 test.consumer.enabled=true로 설정하면 활성화된다.
 * 실제 데이터 처리 로직은 별도의 Persister에서 구현한다.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "test.consumer.enabled", havingValue = "true")
public class TestConsumer {

	/**
	 * RabbitMQ에서 메시지를 수신하여 로그로 출력한다.
	 *
	 * <p>수신된 메시지의 JSON 내용을 그대로 출력하여
	 * 메시지 발행이 정상적으로 이루어지는지 확인할 수 있다.
	 *
	 * @param message 수신된 JSON 메시지
	 */
	@RabbitListener(queues = "test.market.data.queue")
	public void consumeMarketData(String message) {
		log.info("=== 수신된 메시지 ===");
		log.info("Message: {}", message);
		log.info("==================");
	}

	/**
	 * 테스트용 큐와 바인딩 설정을 담당하는 Configuration 클래스.
	 *
	 * <p>주요 책임:
	 * <ul>
	 *   <li>테스트용 큐 생성 및 관리</li>
	 *   <li>Topic Exchange와의 바인딩 설정</li>
	 *   <li>모든 ticker 메시지 수신을 위한 라우팅 키 패턴 적용</li>
	 * </ul>
	 *
	 * <p>이 설정은 테스트 Consumer가 활성화될 때만 동작하며,
	 * 운영 환경에서는 비활성화되어야 한다.
	 */
	@Configuration
	@ConditionalOnProperty(name = "test.consumer.enabled", havingValue = "true")
	public static class TestQueueConfig {

		/**
		 * 테스트용 시장 데이터 큐를 생성한다.
		 *
		 * <p>durable 설정을 통해 서버 재시작 시에도
		 * 큐와 메시지가 유지되도록 한다.
		 *
		 * @return 생성된 테스트 큐
		 */
		@Bean
		public Queue testMarketDataQueue() {
			return QueueBuilder.durable("test.market.data.queue").build();
		}

        /**
         * 테스트 큐와 마켓 데이터 Exchange 간의 바인딩을 설정한다.
         *
         * <p>라우팅 키 패턴:
         * <ul>
         *   <li>"*.ticker.*" - 모든 거래소의 ticker 메시지</li>
         *   <li>"*.trade.*" - 모든 거래소의 trade 메시지</li>
         * </ul>
         *
         * @param testMarketDataQueue 테스트용 큐
         * @param marketDataExchange 마켓 데이터 Exchange
         * @return 설정된 바인딩 목록
         */
        @Bean
        public List<Binding> testMarketDataBindings(Queue testMarketDataQueue, TopicExchange marketDataExchange) {
            return List.of(
                    // ticker 바인딩
                    BindingBuilder.bind(testMarketDataQueue)
                            .to(marketDataExchange)
                            .with("*.ticker.*"),

                    // trade 바인딩
                    BindingBuilder.bind(testMarketDataQueue)
                            .to(marketDataExchange)
                            .with("*.trade.*")
            );
        }
	}
}