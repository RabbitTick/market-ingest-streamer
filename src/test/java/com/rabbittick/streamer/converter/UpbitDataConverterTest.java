package com.rabbittick.streamer.converter;

import static org.assertj.core.api.Assertions.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbittick.streamer.connector.dto.common.CommonOrderbookDto;
import com.rabbittick.streamer.connector.dto.common.CommonTickerDto;
import com.rabbittick.streamer.global.dto.MarketDataMessage;
import com.rabbittick.streamer.global.dto.OrderbookPayload;
import com.rabbittick.streamer.global.dto.OrderbookUnitPayload;
import com.rabbittick.streamer.global.dto.TickerPayload;

class UpbitDataConverterTest {

	private UpbitDataConverter converter;

	@BeforeEach
	void setUp() {
		converter = new UpbitDataConverter(new ObjectMapper());
	}

	@Test
	@DisplayName("정상적인 CommonTickerDto를 표준 메시지로 변환한다")
	void convertTickerData_WithValidDto_ShouldReturnStandardMessage() {
		// given
		CommonTickerDto upbitDto = createValidCommonTickerDto();

		// when
		MarketDataMessage<TickerPayload> result = converter.convertTickerData(upbitDto);

		// then
		assertThat(result).isNotNull();
		assertThat(result.getMetadata()).isNotNull();
		assertThat(result.getPayload()).isNotNull();

		// 메타데이터 검증
		var metadata = result.getMetadata();
		assertThat(metadata.getMessageId()).matches("^[0-9a-f-]{36}$");
		assertThat(metadata.getExchange()).isEqualTo("UPBIT");
		assertThat(metadata.getDataType()).isEqualTo("TICKER");
		assertThat(metadata.getVersion()).isEqualTo("1.0");
		assertThat(metadata.getCollectedAt()).isNotNull();

		// 페이로드 검증
		TickerPayload payload = (TickerPayload)result.getPayload();
		assertThat(payload.getMarketCode()).isEqualTo("KRW-BTC");
		assertThat(payload.getTradePrice()).isEqualByComparingTo(new BigDecimal("70000000.00"));
		assertThat(payload.getTradeVolume()).isEqualByComparingTo(new BigDecimal("0.1234"));
		assertThat(payload.getTimestamp()).isEqualTo(1672531200000L);
	}

	@Test
	@DisplayName("null DTO 입력 시 IllegalArgumentException을 발생시킨다")
	void convertTickerData_WithNullDto_ShouldThrowException() {
		// when & then
		assertThatThrownBy(() -> converter.convertTickerData(null))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("CommonTickerDto는 null일 수 없다");
	}

	@Test
	@DisplayName("필수 필드가 null인 경우 IllegalArgumentException을 발생시킨다")
	void convertTickerData_WithNullMarketCode_ShouldThrowException() {
		// given
		CommonTickerDto upbitDto = createValidCommonTickerDto();
		upbitDto.setMarketCode(null);

		// when & then
		assertThatThrownBy(() -> converter.convertTickerData(upbitDto))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("MarketCode는 필수 필드이다");
	}

	@Test
	@DisplayName("현재가가 null인 경우 IllegalArgumentException을 발생시킨다")
	void convertTickerData_WithNullTradePrice_ShouldThrowException() {
		// given
		CommonTickerDto upbitDto = createValidCommonTickerDto();
		upbitDto.setTradePrice(null);

		// when & then
		assertThatThrownBy(() -> converter.convertTickerData(upbitDto))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("TradePrice는 필수 필드이다");
	}

	@Test
	@DisplayName("메타데이터의 collectedAt은 ISO 8601 형식이어야 한다")
	void convertTickerData_ShouldGenerateValidCollectedAt() {
		// given
		CommonTickerDto upbitDto = createValidCommonTickerDto();

		// when
		MarketDataMessage<TickerPayload> result = converter.convertTickerData(upbitDto);

		// then
		String collectedAt = result.getMetadata().getCollectedAt();
		assertThat(collectedAt).matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{1,9}Z");
		assertThat(Instant.parse(collectedAt)).isNotNull();
	}

	@Test
	@DisplayName("메시지 ID는 매번 다른 UUID를 생성한다")
	void convertTickerData_ShouldGenerateUniqueMessageId() {
		// given
		CommonTickerDto upbitDto = createValidCommonTickerDto();

		// when
		MarketDataMessage<TickerPayload> result1 = converter.convertTickerData(upbitDto);
		MarketDataMessage<TickerPayload> result2 = converter.convertTickerData(upbitDto);

		// then
		String messageId1 = result1.getMetadata().getMessageId();
		String messageId2 = result2.getMetadata().getMessageId();
		assertThat(messageId1).isNotEqualTo(messageId2);
		assertThat(UUID.fromString(messageId1)).isNotNull();
		assertThat(UUID.fromString(messageId2)).isNotNull();
	}

	@Test
	@DisplayName("BigDecimal 정밀도가 유지되어야 한다")
	void convertTickerData_ShouldPreserveBigDecimalPrecision() {
		// given
		CommonTickerDto upbitDto = createValidCommonTickerDto();
		upbitDto.setTradePrice(new BigDecimal("70000000.12345678"));

		// when
		MarketDataMessage<TickerPayload> result = converter.convertTickerData(upbitDto);

		// then
		TickerPayload payload = (TickerPayload)result.getPayload();
		assertThat(payload.getTradePrice().toString()).isEqualTo("70000000.12345678");
	}

	@Test
	@DisplayName("0 값들도 정상적으로 처리되어야 한다")
	void convertTickerData_WithZeroValues_ShouldHandleCorrectly() {
		// given
		CommonTickerDto upbitDto = createValidCommonTickerDto();
		upbitDto.setTradeVolume(BigDecimal.ZERO);
		upbitDto.setAccTradeVolume24h(BigDecimal.ZERO);

		// when
		MarketDataMessage<TickerPayload> result = converter.convertTickerData(upbitDto);

		// then
		TickerPayload payload = (TickerPayload)result.getPayload();
		assertThat(payload.getTradeVolume()).isEqualByComparingTo(BigDecimal.ZERO);
		assertThat(payload.getAccTradeVolume24h()).isEqualByComparingTo(BigDecimal.ZERO);
	}

	@Test
	@DisplayName("정상적인 CommonOrderbookDto를 표준 메시지로 변환한다")
	void convertOrderBookData_WithValidDto_ShouldReturnStandardMessage() {
		// given
		CommonOrderbookDto upbitDto = createValidCommonOrderbookDto();

		// when
		MarketDataMessage<OrderbookPayload> result = converter.convertOrderBookData(upbitDto);

		// then
		assertThat(result).isNotNull();
		assertThat(result.getMetadata()).isNotNull();
		assertThat(result.getPayload()).isNotNull();

		var metadata = result.getMetadata();
		assertThat(metadata.getDataType()).isEqualTo("ORDERBOOK");
		assertThat(metadata.getExchange()).isEqualTo("UPBIT");

		OrderbookPayload payload = (OrderbookPayload)result.getPayload();
		assertThat(payload.getMarketCode()).isEqualTo("KRW-BTC");
		assertThat(payload.getTotalAskSize()).isEqualByComparingTo(new BigDecimal("12.34"));
		assertThat(payload.getOrderbookUnits()).hasSize(1);

		OrderbookUnitPayload unit = payload.getOrderbookUnits().get(0);
		assertThat(unit.getAskPrice()).isEqualByComparingTo(new BigDecimal("70000000"));
		assertThat(unit.getBidPrice()).isEqualByComparingTo(new BigDecimal("69900000"));
	}

	@Test
	@DisplayName("OrderBook DTO가 null이면 IllegalArgumentException을 발생시킨다")
	void convertOrderBookData_WithNullDto_ShouldThrowException() {
		// when & then
		assertThatThrownBy(() -> converter.convertOrderBookData(null))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("CommonOrderbookDto는 null일 수 없다");
	}

	@Test
	@DisplayName("OrderBook 필수 필드가 누락되면 IllegalArgumentException을 발생시킨다")
	void convertOrderBookData_WithMissingFields_ShouldThrowException() {
		// given
		CommonOrderbookDto upbitDto = createValidCommonOrderbookDto();
		upbitDto.setOrderbookUnits(List.of());

		// when & then
		assertThatThrownBy(() -> converter.convertOrderBookData(upbitDto))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("OrderBookUnits는 필수 필드다");
	}

	private CommonTickerDto createValidCommonTickerDto() {
		CommonTickerDto dto = new CommonTickerDto();
		dto.setMarketCode("KRW-BTC");
		dto.setTradePrice(new BigDecimal("70000000.00"));
		dto.setTradeVolume(new BigDecimal("0.1234"));
		dto.setOpeningPrice(new BigDecimal("69000000.00"));
		dto.setHighPrice(new BigDecimal("71000000.00"));
		dto.setLowPrice(new BigDecimal("68000000.00"));
		dto.setPrevClosingPrice(new BigDecimal("69500000.00"));
		dto.setAccTradePrice24h(new BigDecimal("1234567890.123"));
		dto.setAccTradeVolume24h(new BigDecimal("123.456"));
		dto.setTimestamp(1672531200000L);
		return dto;
	}

	private CommonOrderbookDto createValidCommonOrderbookDto() {
		CommonOrderbookDto dto = new CommonOrderbookDto();
		dto.setMarketCode("KRW-BTC");
		dto.setTimestamp(1672531200000L);
		dto.setTotalAskSize(new BigDecimal("12.34"));
		dto.setTotalBidSize(new BigDecimal("10.56"));
		dto.setOrderbookUnits(List.of(createOrderBookUnit(new BigDecimal("70000000"), new BigDecimal("1.2"),
			new BigDecimal("69900000"), new BigDecimal("0.8"))));
		return dto;
	}

	private CommonOrderbookDto.OrderbookUnit createOrderBookUnit(BigDecimal askPrice, BigDecimal askSize,
		BigDecimal bidPrice, BigDecimal bidSize) {
		CommonOrderbookDto.OrderbookUnit unit = new CommonOrderbookDto.OrderbookUnit();
		unit.setAskPrice(askPrice);
		unit.setAskSize(askSize);
		unit.setBidPrice(bidPrice);
		unit.setBidSize(bidSize);
		return unit;
	}
}