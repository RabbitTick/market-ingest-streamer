package com.rabbittick.streamer.publisher;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbittick.streamer.global.dto.MarketDataMessage;
import com.rabbittick.streamer.global.dto.OrderBookPayload;
import com.rabbittick.streamer.global.dto.TickerPayload;
import com.rabbittick.streamer.global.dto.TradePayload;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * 시장 데이터를 RabbitMQ에 발행하는 역할을 담당하는 퍼블리셔.
 *
 * <p>publishAsync는 RabbitTemplate 발행을 boundedElastic 스레드에서 수행하여
 * 호출 스레드를 블로킹하지 않고 Mono로 완료를 전달한다.
 *
 * <p>주요 책임:
 * <ul>
 *   <li>MarketDataMessage를 JSON으로 직렬화</li>
 *   <li>동적 라우팅 키 생성</li>
 *   <li>RabbitMQ Exchange에 메시지 발행</li>
 *   <li>발행 실패 시 로깅 및 Mono 실패 전파</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MarketDataPublisher {

	private final RabbitTemplate rabbitTemplate;
	private final ObjectMapper objectMapper;

	@Value("${rabbitmq.exchange.market-data}")
	private String exchangeName;

	/**
	 * MarketDataMessage를 RabbitMQ에 발행한다.
	 * 발행 작업은 boundedElastic 스레드에서 수행되며, 반환된 Mono를 구독하여 완료·실패를 처리할 수 있다.
	 *
	 * <p>라우팅 키: {exchange}.{dataType}.{marketCode} 형식
	 *
	 * @param message 발행할 시장 데이터 메시지
	 * @return 발행 완료 시 onComplete, 실패 시 onError
	 */
	public Mono<Void> publishAsync(MarketDataMessage<?> message) {
		try {
			String routingKey = buildRoutingKey(message);
			String jsonMessage = objectMapper.writeValueAsString(message);

			return Mono.<Void>fromRunnable(() -> {
				try {
					rabbitTemplate.convertAndSend(exchangeName, routingKey, jsonMessage);
					log.debug("메시지 발행 성공 - Routing Key: {}, Message ID: {}, 스레드: {}",
						routingKey, message.getMetadata().getMessageId(), Thread.currentThread().getName());
				} catch (Exception e) {
					log.error("메시지 발행 실패 - Message ID: {}", message.getMetadata().getMessageId(), e);
					throw new MessagePublishException("메시지 발행 실패", e);
				}
			})
				.subscribeOn(Schedulers.boundedElastic())
				.then();
		} catch (JsonProcessingException e) {
			log.error("JSON 직렬화 실패 - Message ID: {}", message.getMetadata().getMessageId(), e);
			return Mono.error(new MessagePublishException("메시지 직렬화 실패", e));
		}
	}

	/**
	 * 메시지 메타데이터를 기반으로 라우팅 키를 생성한다.
	 *
	 * <p>형식: {exchange}.{dataType}.{marketCode}
	 * 예시: upbit.ticker.KRW-BTC
	 *
	 * @param message 라우팅 키를 생성할 메시지
	 * @return 생성된 라우팅 키
	 */
	private String buildRoutingKey(MarketDataMessage<?> message) {
		String exchange = message.getMetadata().getExchange().toLowerCase();
		String dataType = message.getMetadata().getDataType().toLowerCase();
		String marketCode = extractMarketCode(message);

		return String.format("%s.%s.%s", exchange, dataType, marketCode);
	}

    /**
     * 메시지 페이로드에서 마켓 코드를 추출한다.
     *
     * @param message 마켓 코드를 추출할 메시지
     * @return 추출된 마켓 코드
     * @throws IllegalArgumentException 지원하지 않는 payload 타입인 경우
     */
    private String extractMarketCode(MarketDataMessage<?> message) {
        Object payload = message.getPayload();

        if (payload instanceof TickerPayload) {
            return ((TickerPayload) payload).getMarketCode();
        }

        if (payload instanceof TradePayload) {
            return ((TradePayload) payload).getMarketCode();
        }
        if (payload instanceof OrderBookPayload) {
            return ((OrderBookPayload) payload).getMarketCode();
        }
        throw new IllegalArgumentException("지원하지 않는 payload 타입: " + message.getPayload().getClass().getSimpleName());
    }

	/**
	 * 메시지 발행 관련 예외를 나타내는 커스텀 예외.
	 */
	public static class MessagePublishException extends RuntimeException {

		public MessagePublishException(String message, Throwable cause) {
			super(message, cause);
		}
	}
}