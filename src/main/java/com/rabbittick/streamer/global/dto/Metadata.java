package com.rabbittick.streamer.global.dto;

import lombok.Builder;
import lombok.Data;

/**
 * 시장 데이터 메시지의 메타정보를 담는 DTO 클래스.
 *
 * <p>주요 책임:
 * <ul>
 *   <li>메시지 식별 및 추적 정보 제공</li>
 *   <li>데이터 출처 및 타입 정보 관리</li>
 *   <li>메시지 생성 시간 기록</li>
 *   <li>스키마 버전 관리를 통한 호환성 보장</li>
 * </ul>
 *
 * <p>이 클래스는 모든 시장 데이터 메시지에 공통으로 포함되는
 * 메타정보를 표준화한다. 로깅, 디버깅, 메시지 라우팅 시
 * 필요한 모든 정보를 제공한다.
 */
@Data
@Builder
public class Metadata {

	/**
	 * 메시지 고유 식별자 (UUID 형태).
	 *
	 * <p>로깅, 추적, 멱등성 처리에 사용된다.
	 * (예: a1b2c3d4-e5f6-7890-1234-567890abcdef)
	 */
	private String messageId;

	/**
	 * 거래소 이름 (UPBIT, BITHUMB, KORBIT 등).
	 *
	 * <p>데이터의 출처를 명시하여 소비자가 출처별로
	 * 다른 처리 로직을 적용할 수 있도록 한다.
	 */
	private String exchange;

	/**
	 * 데이터 타입 (TICKER, TRADE, ORDERBOOK 등).
	 *
	 * <p>소비자가 페이로드를 올바른 타입으로 파싱하고
	 * 적절한 비즈니스 로직을 적용할 수 있도록 한다.
	 */
	private String dataType;

	/**
	 * 데이터 수집 시각 (ISO 8601 형식).
	 *
	 * <p>시스템에 데이터가 들어온 시점을 기록한다.
	 * 데이터의 실제 발생 시각과 구분하여 지연 시간을 측정할 수 있다.
	 * (예: 2025-08-28T16:49:00.123Z)
	 */
	private String collectedAt;

	/**
	 * 메시지 스키마 버전 (예: 1.0, 2.1).
	 *
	 * <p>향후 DTO 구조 변경 시 구버전과 신버전을
	 * 구분하여 호환성을 유지할 수 있다.
	 */
	private String version;
}