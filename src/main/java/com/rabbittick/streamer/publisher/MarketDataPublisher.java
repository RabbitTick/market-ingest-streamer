package com.rabbittick.streamer.publisher;

import com.rabbittick.streamer.metrics.PublishFailureMetrics;
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
import reactor.rabbitmq.OutboundMessage;
import reactor.rabbitmq.Sender;

/**
 * 시장 데이터를 RabbitMQ에 발행하는 퍼블리셔.
 *
 * <p>reactor-rabbitmq의 Sender를 사용하여 논블로킹 I/O로 메시지를 발행한다.
 * 스레드가 응답을 기다리지 않고 즉시 반환된다.
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

	private final ObjectMapper objectMapper;
	private final Sender sender;
    private final PublishFailureMetrics publishFailureMetrics;

    @Value("${rabbitmq.exchange.market-data}")
	private String exchangeName;

	/**
	 * MarketDataMessage를 RabbitMQ에 발행한다.
	 *
	 * <p>발행 작업은 reactor-rabbitmq의 boundedElastic 스케줄러에서 수행된다.
	 * 반환된 Mono를 구독하여 완료·실패를 처리할 수 있다.
	 * 라우팅 키 형식: {exchange}.{dataType}.{marketCode}
	 *
	 * @param message 발행할 시장 데이터 메시지
	 * @return 발행 완료 시 onComplete, 실패 시 onError를 전파하는 Mono
	 */
    public Mono<Void> publishAsync(MarketDataMessage<?> message) {
        return Mono.fromCallable(() -> createOutboundMessage(message))
                .flatMap(outbound -> sender.send(Mono.just(outbound)))
                .doOnSuccess(v -> log.debug("메시지 발행 성공 - Routing Key: {}, Message ID: {}, thread: {}",
                        buildRoutingKey(message), message.getMetadata().getMessageId(), Thread.currentThread().getName()))
                .doOnError(e -> {
                    String dataType = message.getMetadata().getDataType();
                    publishFailureMetrics.record(dataType);
                    log.error("메시지 발행 실패 - Message ID: {}, DataType: {}",
                            message.getMetadata().getMessageId(), dataType, e);
                });
    }

	/**
	 * MarketDataMessage를 Exchange·라우팅 키·JSON 바이트로 변환하여 OutboundMessage를 만든다.
	 *
	 * @param message 변환할 시장 데이터 메시지
	 * @return RabbitMQ로 보낼 OutboundMessage
	 * @throws JsonProcessingException JSON 직렬화 실패 시
	 */
	private OutboundMessage createOutboundMessage(MarketDataMessage<?> message) 
            throws JsonProcessingException {
        String routingKey = buildRoutingKey(message);
        byte[] body = objectMapper.writeValueAsBytes(message);

        return new OutboundMessage(exchangeName, routingKey, body);
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

		/**
		 * 메시지와 원인을 지정하여 예외를 생성한다.
		 *
		 * @param message 예외 메시지
		 * @param cause 원인 예외
		 */
		public MessagePublishException(String message, Throwable cause) {
			super(message, cause);
		}
	}
}