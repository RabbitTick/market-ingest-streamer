package com.rabbittick.streamer.converter;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbittick.streamer.connector.dto.upbit.UpbitOrderBookDto;
import com.rabbittick.streamer.connector.dto.upbit.UpbitTickerDto;
import com.rabbittick.streamer.connector.dto.upbit.UpbitTradeDto;
import com.rabbittick.streamer.global.dto.MarketDataMessage;
import com.rabbittick.streamer.global.dto.Metadata;
import com.rabbittick.streamer.global.dto.OrderBookPayload;
import com.rabbittick.streamer.global.dto.OrderBookUnitPayload;
import com.rabbittick.streamer.global.dto.TickerPayload;
import com.rabbittick.streamer.global.dto.TradePayload;

import lombok.RequiredArgsConstructor;

/**
 * Upbit WebSocket 수신 데이터를 시스템 표준 DTO로 변환하는 컨버터.
 *
 * <p>{@link ExchangeDataConverter}를 구현하여 원시 JSON 문자열을 직접 수신하고,
 * Upbit 전용 DTO로 파싱한 후 거래소 독립적인 {@link MarketDataMessage}로 변환한다.
 *
 * <p>지원하는 데이터 타입:
 * <ul>
 *   <li>Ticker - 현재가 정보</li>
 *   <li>Trade - 거래 체결 정보</li>
 *   <li>OrderBook - 호가 정보</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class UpbitDataConverter implements ExchangeDataConverter {

    private final ObjectMapper objectMapper;

	private static final String EXCHANGE_NAME = "UPBIT";
	private static final String VERSION = "1.0";

	@Override
	public String getExchangeName() {
		return EXCHANGE_NAME;
	}

	/**
	 * 원시 JSON 문자열을 {@link UpbitTickerDto}로 파싱하여 표준 ticker 메시지로 변환한다.
	 *
	 * @param rawJson Upbit WebSocket으로부터 수신된 원시 JSON 문자열
	 * @return 표준화된 ticker MarketDataMessage
	 * @throws IllegalArgumentException JSON 파싱 실패 시
	 */
	@Override
	public MarketDataMessage<TickerPayload> convertTicker(String rawJson) {
		try {
			UpbitTickerDto dto = objectMapper.readValue(rawJson, UpbitTickerDto.class);
			return convertTickerData(dto);
		} catch (JsonProcessingException e) {
			throw new IllegalArgumentException("Ticker JSON 파싱 실패: " + e.getMessage(), e);
		}
	}

	/**
	 * 원시 JSON 문자열을 {@link UpbitTradeDto}로 파싱하여 표준 trade 메시지로 변환한다.
	 *
	 * @param rawJson Upbit WebSocket으로부터 수신된 원시 JSON 문자열
	 * @return 표준화된 trade MarketDataMessage
	 * @throws IllegalArgumentException JSON 파싱 실패 시
	 */
	@Override
	public MarketDataMessage<TradePayload> convertTrade(String rawJson) {
		try {
			UpbitTradeDto dto = objectMapper.readValue(rawJson, UpbitTradeDto.class);
			return convertTradeData(dto);
		} catch (JsonProcessingException e) {
			throw new IllegalArgumentException("Trade JSON 파싱 실패: " + e.getMessage(), e);
		}
	}

	/**
	 * 원시 JSON 문자열을 {@link UpbitOrderBookDto}로 파싱하여 표준 orderbook 메시지로 변환한다.
	 *
	 * @param rawJson Upbit WebSocket으로부터 수신된 원시 JSON 문자열
	 * @return 표준화된 orderbook MarketDataMessage
	 * @throws IllegalArgumentException JSON 파싱 실패 시
	 */
	@Override
	public MarketDataMessage<OrderBookPayload> convertOrderBook(String rawJson) {
		try {
			UpbitOrderBookDto dto = objectMapper.readValue(rawJson, UpbitOrderBookDto.class);
			return convertOrderBookData(dto);
		} catch (JsonProcessingException e) {
			throw new IllegalArgumentException("OrderBook JSON 파싱 실패: " + e.getMessage(), e);
		}
	}

	/**
	 * UpbitTickerDto를 표준 MarketDataMessage로 변환한다.
	 *
	 * @param upbitDto Upbit WebSocket에서 수신한 ticker DTO
	 * @return 표준화된 MarketDataMessage
	 * @throws IllegalArgumentException 필수 필드가 누락된 경우
	 */
	public MarketDataMessage<TickerPayload> convertTickerData(UpbitTickerDto upbitDto) {
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
    public MarketDataMessage<TradePayload> convertTradeData(UpbitTradeDto upbitDto) {
        validateTradeInput(upbitDto);

        TradePayload payload = convertToTradePayload(upbitDto);
        Metadata metadata = createMetadata(DataType.TRADE);

        return createMessage(metadata, payload);
    }

	/**
	 * UpbitOrderBookDto를 표준 MarketDataMessage로 변환한다.
	 *
	 * @param upbitDto Upbit WebSocket에서 수신한 orderbook DTO
	 * @return 표준화된 MarketDataMessage
	 * @throws IllegalArgumentException 필수 필드가 누락된 경우
	 */
	public MarketDataMessage<OrderBookPayload> convertOrderBookData(UpbitOrderBookDto upbitDto) {
		validateOrderBookInput(upbitDto);

		OrderBookPayload payload = convertToOrderBookPayload(upbitDto);
		Metadata metadata = createMetadata(DataType.ORDERBOOK);

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
	 * UpbitOrderBookDto를 OrderBookPayload로 변환한다.
	 *
	 * @param dto Upbit orderbook DTO
	 * @return 변환된 OrderBookPayload
	 */
	private OrderBookPayload convertToOrderBookPayload(UpbitOrderBookDto dto) {
		List<OrderBookUnitPayload> units = dto.getOrderbookUnits().stream()
			.map(unit -> OrderBookUnitPayload.builder()
				.askPrice(unit.getAskPrice())
				.askSize(unit.getAskSize())
				.bidPrice(unit.getBidPrice())
				.bidSize(unit.getBidSize())
				.build())
			.toList();

		return OrderBookPayload.builder()
			.marketCode(dto.getMarketCode())
			.timestamp(dto.getTimestamp())
			.totalAskSize(dto.getTotalAskSize())
			.totalBidSize(dto.getTotalBidSize())
			.orderbookUnits(units)
			.build();
	}

	/**
	 * 공통 MarketDataMessage 생성 로직.
	 *
	 * @param metadata 메타데이터
	 * @param payload 페이로드
	 * @return 생성된 MarketDataMessage
	 */
	private <T> MarketDataMessage<T> createMessage(Metadata metadata, T payload) {
		return MarketDataMessage.<T>builder()
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
	 * OrderBook DTO의 필수 필드를 검증한다.
	 *
	 * @param dto 검증할 DTO
	 * @throws IllegalArgumentException 필수 필드 누락 시
	 */
	private void validateOrderBookInput(UpbitOrderBookDto dto) {
		if (dto == null) {
			throw new IllegalArgumentException("UpbitOrderBookDto는 null일 수 없다");
		}
		if (dto.getMarketCode() == null || dto.getMarketCode().trim().isEmpty()) {
			throw new IllegalArgumentException("MarketCode는 필수 필드다");
		}
		if (dto.getTimestamp() <= 0) {
			throw new IllegalArgumentException("Timestamp는 양수여야 한다");
		}
		if (dto.getOrderbookUnits() == null || dto.getOrderbookUnits().isEmpty()) {
			throw new IllegalArgumentException("OrderBookUnits는 필수 필드다");
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