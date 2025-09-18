package com.rabbittick.streamer.service;

import org.springframework.stereotype.Service;

import com.rabbittick.streamer.global.dto.MarketDataMessage;
import com.rabbittick.streamer.publisher.MarketDataPublisher;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 시장 데이터 처리 서비스의 구현체.
 *
 * <p>주요 책임:
 * <ul>
 *   <li>표준 DTO의 기본적인 유효성 검증</li>
 *   <li>메시지 발행을 위한 Publisher 계층과의 조합</li>
 *   <li>외부 API와 독립적인 비즈니스 로직 처리</li>
 *   <li>로깅 및 추적을 위한 메시지 정보 기록</li>
 * </ul>
 *
 * <p>이 클래스는 외부 거래소에 대한 지식이 전혀 없으며,
 * 단일 책임 원칙에 따라 메시지 검증과 발행 조합만을 담당한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MarketDataServiceImpl implements MarketDataService {

	private final MarketDataPublisher marketDataPublisher;

	/**
	 * 시장 데이터 메시지를 검증하고 발행한다.
	 *
	 * <p>이 메서드는 어떤 거래소의 데이터인지 알지 못하며,
	 * 표준 DTO의 유효성만 검증하고 발행을 위임한다.
	 *
	 * @param message 처리할 시장 데이터 메시지
	 * @throws IllegalArgumentException 메시지 검증 실패 시
	 */
	@Override
	public void processMarketData(MarketDataMessage message) {
		log.trace("시장 데이터 처리 시작 - Message ID: {}, Exchange: {}, DataType: {}",
			message.getMetadata().getMessageId(),
			message.getMetadata().getExchange(),
			message.getMetadata().getDataType());

		// 메시지 유효성 검증
		validateMessage(message);

		// RabbitMQ 발행
		marketDataPublisher.publish(message);

		log.trace("시장 데이터 처리 완료 - Message ID: {}",
			message.getMetadata().getMessageId());
	}

	/**
	 * 메시지의 필수 필드를 검증한다.
	 *
	 * @param message 검증할 메시지
	 * @throws IllegalArgumentException 필수 필드 누락 시
	 */
	private void validateMessage(MarketDataMessage message) {
		if (message == null) {
			throw new IllegalArgumentException("메시지는 null일 수 없다");
		}

		if (message.getMetadata() == null) {
			throw new IllegalArgumentException("메타데이터는 필수이다");
		}

		if (message.getPayload() == null) {
			throw new IllegalArgumentException("페이로드는 필수이다");
		}

		// 메타데이터 필수 필드 검증
		var metadata = message.getMetadata();
		if (isBlank(metadata.getMessageId())) {
			throw new IllegalArgumentException("Message ID는 필수이다");
		}
		if (isBlank(metadata.getExchange())) {
			throw new IllegalArgumentException("Exchange는 필수이다");
		}
		if (isBlank(metadata.getDataType())) {
			throw new IllegalArgumentException("DataType은 필수이다");
		}
	}

	/**
	 * 문자열이 null이거나 공백인지 확인한다.
	 *
	 * @param str 검증할 문자열
	 * @return null이거나 공백이면 true
	 */
	private boolean isBlank(String str) {
		return str == null || str.trim().isEmpty();
	}
}