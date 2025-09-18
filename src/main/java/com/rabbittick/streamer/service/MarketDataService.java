package com.rabbittick.streamer.service;

import com.rabbittick.streamer.global.dto.MarketDataMessage;

/**
 * 시장 데이터 처리를 담당하는 서비스 인터페이스.
 *
 * <p>주요 책임:
 * <ul>
 *   <li>표준화된 시장 데이터 메시지 처리</li>
 *   <li>외부 API와 독립적인 비즈니스 로직 수행</li>
 *   <li>메시지 검증 및 다음 단계로의 전달</li>
 *   <li>확장 가능한 서비스 계층 추상화 제공</li>
 * </ul>
 *
 * <p>이 인터페이스는 외부 거래소 API에 대한 지식 없이
 * 표준 DTO만을 처리하여 확장성과 유지보수성을 확보한다.
 */
public interface MarketDataService {

	/**
	 * 표준화된 시장 데이터 메시지를 처리한다.
	 *
	 * <p>이 메서드는 어떤 거래소에서 온 데이터인지 알 필요가 없으며,
	 * 표준 DTO 형태로 전달받은 데이터를 처리하고 다음 단계로 전달한다.
	 *
	 * @param message 처리할 시장 데이터 메시지
	 * @throws IllegalArgumentException 메시지가 null이거나 필수 필드가 누락된 경우
	 */
	void processMarketData(MarketDataMessage message);
}