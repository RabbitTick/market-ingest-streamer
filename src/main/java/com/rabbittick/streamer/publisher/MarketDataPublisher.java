package com.rabbittick.streamer.publisher;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbittick.streamer.global.dto.MarketDataMessage;
import com.rabbittick.streamer.global.dto.TickerPayload;
import com.rabbittick.streamer.global.dto.TradePayload;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 시장 데이터를 RabbitMQ에 발행하는 역할을 담당하는 퍼블리셔.
 *
 * <p>주요 책임:
 * <ul>
 *   <li>MarketDataMessage를 JSON으로 직렬화</li>
 *   <li>동적 라우팅 키 생성</li>
 *   <li>RabbitMQ Exchange에 메시지 발행</li>
 *   <li>발행 실패 시 예외 처리</li>
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
	 *
	 * <p>라우팅 키는 토폴로지 설계에 따라 동적으로 생성된다:
	 * {exchange}.{dataType}.{marketCode} 형식
	 *
	 * @param message 발행할 시장 데이터 메시지
	 * @throws MessagePublishException 메시지 발행 실패 시
	 */
	public void publish(MarketDataMessage message) {
		try {
			String routingKey = buildRoutingKey(message);
			String jsonMessage = objectMapper.writeValueAsString(message);

			rabbitTemplate.convertAndSend(exchangeName, routingKey, jsonMessage);

            log.debug("메시지 발행 성공 - Routing Key: {}, Message ID: {}", routingKey, message.getMetadata().getMessageId());

        } catch (JsonProcessingException e) {
            log.error("JSON 직렬화 실패 - Message ID: {}", message.getMetadata().getMessageId(), e);
            throw new MessagePublishException("메시지 직렬화 실패", e);
        } catch (Exception e) {
            log.error("메시지 발행 실패 - Message ID: {}", message.getMetadata().getMessageId(), e);
            throw new MessagePublishException("메시지 발행 실패", e);
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
	private String buildRoutingKey(MarketDataMessage message) {
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
    private String extractMarketCode(MarketDataMessage message) {
        Object payload = message.getPayload();

        if (payload instanceof TickerPayload) {
            return ((TickerPayload) payload).getMarketCode();
        }

        if (payload instanceof TradePayload) {
            return ((TradePayload) payload).getMarketCode();
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