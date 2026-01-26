package com.rabbittick.streamer.converter;

import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.rabbittick.streamer.connector.dto.upbit.UpbitTickerDto;
import com.rabbittick.streamer.connector.dto.upbit.UpbitTradeDto;
import com.rabbittick.streamer.global.dto.MarketDataMessage;
import com.rabbittick.streamer.global.dto.Metadata;
import com.rabbittick.streamer.global.dto.TickerPayload;
import com.rabbittick.streamer.global.dto.TradePayload;

/**
 * Upbit 외부 API DTO를 시스템 표준 DTO로 변환하는 컨버터.
 *
 * <p>각 데이터 타입별로 명시적인 변환 메서드를 제공하여
 * 타입 안전성과 코드 가독성을 확보한다.
 *
 * <p>지원하는 데이터 타입:
 * <ul>
 *   <li>Ticker - 현재가 정보</li>
 *   <li>Trade - 거래 체결 정보</li>
 *   <li>OrderBook - 호가 정보 (향후 추가 예정)</li>
 * </ul>
 */
@Component
public class UpbitDataConverter {

	private static final String EXCHANGE_NAME = "UPBIT";
	private static final String VERSION = "1.0";

	/**
	 * UpbitTickerDto를 표준 MarketDataMessage로 변환한다.
	 *
	 * @param upbitDto Upbit WebSocket에서 수신한 ticker DTO
	 * @return 표준화된 MarketDataMessage
	 * @throws IllegalArgumentException 필수 필드가 누락된 경우
	 */
	public MarketDataMessage convertTickerData(UpbitTickerDto upbitDto) {
		validateTickerInput(upbitDto);

		TickerPayload payload = convertToTickerPayload(upbitDto);
		Metadata metadata = createMetadata(DataType.TICKER);

		return createMessage(metadata, payload);
	}

    /**
     * UpbitTradeDto를 표준 MarketDataMessage로 변환한다.
     *
     * @param upbitDto Upbit WebSocket에서 수신한 trade DTO
     * @return 표준화된 MarketDataMessage
     * @throws IllegalArgumentException 필수 필드가 누락된 경우
     */
    public MarketDataMessage convertTradeData(UpbitTradeDto upbitDto) {
        validateTradeInput(upbitDto);

        TradePayload payload = convertToTradePayload(upbitDto);
        Metadata metadata = createMetadata(DataType.TRADE);

        return createMessage(metadata, payload);
    }

	/**
	 * UpbitTickerDto를 TickerPayload로 변환한다.
	 *
	 * @param dto Upbit ticker DTO
	 * @return 변환된 TickerPayload
	 */
	private TickerPayload convertToTickerPayload(UpbitTickerDto dto) {
		return TickerPayload.builder()
			.marketCode(dto.getMarketCode())
			.tradePrice(dto.getTradePrice())
			.tradeVolume(dto.getTradeVolume())
			.openingPrice(dto.getOpeningPrice())
			.highPrice(dto.getHighPrice())
			.lowPrice(dto.getLowPrice())
			.prevClosingPrice(dto.getPrevClosingPrice())
			.accTradePrice24h(dto.getAccTradePrice24h())
			.accTradeVolume24h(dto.getAccTradeVolume24h())
			.timestamp(dto.getTimestamp())
			.build();
	}

    /**
     * UpbitTradeDto를 TradePayload로 변환한다.
     *
     * @param dto Upbit trade DTO
     * @return 변환된 TradePayload
     */
    private TradePayload convertToTradePayload(UpbitTradeDto dto) {
        return TradePayload.builder()
                .marketCode(dto.getMarketCode())
                .timestamp(dto.getTimestamp())
                .tradeDate(dto.getTradeDate())
                .tradeTime(dto.getTradeTime())
                .tradeTimestamp(dto.getTradeTimestamp())
                .tradePrice(dto.getTradePrice())
                .tradeVolume(dto.getTradeVolume())
                .askBid(dto.getAskBid())
                .prevClosingPrice(dto.getPrevClosingPrice())
                .change(dto.getChange())
                .changePrice(dto.getChangePrice())
                .sequentialId(dto.getSequentialId())
                .bestAskPrice(dto.getBestAskPrice())
                .bestAskSize(dto.getBestAskSize())
                .bestBidPrice(dto.getBestBidPrice())
                .bestBidSize(dto.getBestBidSize())
                .streamType(dto.getStreamType())
                .build();
    }

	/**
	 * 공통 MarketDataMessage 생성 로직.
	 *
	 * @param metadata 메타데이터
	 * @param payload 페이로드
	 * @return 생성된 MarketDataMessage
	 */
	private MarketDataMessage createMessage(Metadata metadata, Object payload) {
		return MarketDataMessage.builder()
			.metadata(metadata)
			.payload(payload)
			.build();
	}

	/**
	 * 데이터 타입에 따른 메타데이터를 생성한다.
	 *
	 * @param dataType 데이터 타입
	 * @return 생성된 메타데이터
	 */
	private Metadata createMetadata(DataType dataType) {
		return Metadata.builder()
			.messageId(UUID.randomUUID().toString())
			.exchange(EXCHANGE_NAME)
			.dataType(dataType.name())
			.collectedAt(Instant.now().toString())
			.version(VERSION)
			.build();
	}

	/**
	 * Ticker DTO의 필수 필드를 검증한다.
	 *
	 * @param dto 검증할 DTO
	 * @throws IllegalArgumentException 필수 필드 누락 시
	 */
	private void validateTickerInput(UpbitTickerDto dto) {
		if (dto == null) {
			throw new IllegalArgumentException("UpbitTickerDto는 null일 수 없다");
		}
		if (dto.getMarketCode() == null || dto.getMarketCode().trim().isEmpty()) {
			throw new IllegalArgumentException("MarketCode는 필수 필드이다");
		}
		if (dto.getTradePrice() == null) {
			throw new IllegalArgumentException("TradePrice는 필수 필드이다");
		}
		if (dto.getTimestamp() <= 0) {
			throw new IllegalArgumentException("Timestamp는 양수여야 한다");
		}
	}

    /**
     * Trade DTO의 필수 필드를 검증한다.
     *
     * @param dto 검증할 DTO
     * @throws IllegalArgumentException 필수 필드 누락 시
     */
    private void validateTradeInput(UpbitTradeDto dto) {
        if (dto == null) {
            throw new IllegalArgumentException("UpbitTradeDto는 null일 수 없다");
        }
        if (dto.getMarketCode() == null || dto.getMarketCode().trim().isEmpty()) {
            throw new IllegalArgumentException("MarketCode는 필수 필드다");
        }
        if (dto.getTradePrice() == null) {
            throw new IllegalArgumentException("TradePrice는 필수 필드다");
        }
        if (dto.getTradeVolume() == null) {
            throw new IllegalArgumentException("TradeVolume는 필수 필드다");
        }
        if (dto.getAskBid() == null || dto.getAskBid().trim().isEmpty()) {
            throw new IllegalArgumentException("AskBid는 필수 필드다");
        }
        if (dto.getSequentialId() <= 0) {
            throw new IllegalArgumentException("SequentialId는 양수여야 한다");
        }
        if (dto.getTimestamp() <= 0) {
            throw new IllegalArgumentException("Timestamp는 양수여야 한다");
        }
        if (dto.getTradeTimestamp() <= 0) {
            throw new IllegalArgumentException("TradeTimestamp는 양수여야 한다");
        }
    }

	/**
	 * 지원하는 데이터 타입을 정의하는 열거형.
	 * 새로운 데이터 타입 추가 시 이 enum에 추가하고
	 * 해당하는 convert 메서드를 구현한다.
	 */
	public enum DataType {
		/** 현재가 정보 */
		TICKER,

        /**
         * 거래 체결 정보
         */
        TRADE,

        /**
         * 호가 정보
         */
        ORDERBOOK
    }
}