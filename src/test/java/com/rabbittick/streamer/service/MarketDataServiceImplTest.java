package com.rabbittick.streamer.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.rabbittick.streamer.global.dto.MarketDataMessage;
import com.rabbittick.streamer.global.dto.Metadata;
import com.rabbittick.streamer.global.dto.TickerPayload;
import com.rabbittick.streamer.publisher.MarketDataPublisher;

@ExtendWith(MockitoExtension.class)
class MarketDataServiceImplTest {

	@Mock
	private MarketDataPublisher marketDataPublisher;

	@InjectMocks
	private MarketDataServiceImpl marketDataService;

	private MarketDataMessage validMessage;

	@BeforeEach
	void setUp() {
		validMessage = createValidMarketDataMessage();
	}

	@Test
	@DisplayName("정상적인 메시지를 처리하고 Publisher를 호출한다")
	void processMarketData_WithValidMessage_ShouldCallPublisher() {
		// when
		marketDataService.processMarketData(validMessage);

		// then
		verify(marketDataPublisher).publish(validMessage);
	}

	@Test
	@DisplayName("null 메시지 입력 시 NullPointerException을 발생시킨다")
	void processMarketData_WithNullMessage_ShouldThrowNullPointerException() {
		// when & then
		assertThatThrownBy(() -> marketDataService.processMarketData(null))
			.isInstanceOf(NullPointerException.class)
			.hasMessageContaining(
				"Cannot invoke \"com.rabbittick.streamer.global.dto.MarketDataMessage.getMetadata()\" because \"message\" is null");

		verify(marketDataPublisher, never()).publish(any());
	}

	@Test
	@DisplayName("메타데이터가 null인 경우 NullPointerException을 발생시킨다")
	void processMarketData_WithNullMetadata_ShouldThrowNullPointerException() {
		// given
		validMessage.setMetadata(null);

		// when & then
		assertThatThrownBy(() -> marketDataService.processMarketData(validMessage))
			.isInstanceOf(NullPointerException.class)
			.hasMessageContaining(
				"Cannot invoke \"com.rabbittick.streamer.global.dto.Metadata.getMessageId()\" because the return value of \"com.rabbittick.streamer.global.dto.MarketDataMessage.getMetadata()\" is null");

		verify(marketDataPublisher, never()).publish(any());
	}

	@Test
	@DisplayName("페이로드가 null인 경우 IllegalArgumentException을 발생시킨다")
	void processMarketData_WithNullPayload_ShouldThrowException() {
		// given
		validMessage.setPayload(null);

		// when & then
		assertThatThrownBy(() -> marketDataService.processMarketData(validMessage))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("페이로드는 필수이다");

		verify(marketDataPublisher, never()).publish(any());
	}

	@Test
	@DisplayName("Message ID가 null인 경우 IllegalArgumentException을 발생시킨다")
	void processMarketData_WithNullMessageId_ShouldThrowException() {
		// given
		validMessage.getMetadata().setMessageId(null);

		// when & then
		assertThatThrownBy(() -> marketDataService.processMarketData(validMessage))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("Message ID는 필수이다");

		verify(marketDataPublisher, never()).publish(any());
	}

	@Test
	@DisplayName("Message ID가 빈 문자열인 경우 IllegalArgumentException을 발생시킨다")
	void processMarketData_WithBlankMessageId_ShouldThrowException() {
		// given
		validMessage.getMetadata().setMessageId("   ");

		// when & then
		assertThatThrownBy(() -> marketDataService.processMarketData(validMessage))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("Message ID는 필수이다");

		verify(marketDataPublisher, never()).publish(any());
	}

	@Test
	@DisplayName("Exchange가 null인 경우 IllegalArgumentException을 발생시킨다")
	void processMarketData_WithNullExchange_ShouldThrowException() {
		// given
		validMessage.getMetadata().setExchange(null);

		// when & then
		assertThatThrownBy(() -> marketDataService.processMarketData(validMessage))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("Exchange는 필수이다");

		verify(marketDataPublisher, never()).publish(any());
	}

	@Test
	@DisplayName("DataType이 null인 경우 IllegalArgumentException을 발생시킨다")
	void processMarketData_WithNullDataType_ShouldThrowException() {
		// given
		validMessage.getMetadata().setDataType(null);

		// when & then
		assertThatThrownBy(() -> marketDataService.processMarketData(validMessage))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("DataType은 필수이다");

		verify(marketDataPublisher, never()).publish(any());
	}

	@Test
	@DisplayName("Publisher에서 예외 발생 시 그대로 전파한다")
	void processMarketData_WhenPublisherThrowsException_ShouldPropagateException() {
		// given
		RuntimeException publishException = new RuntimeException("발행 실패");
		doThrow(publishException).when(marketDataPublisher).publish(any());

		// when & then
		assertThatThrownBy(() -> marketDataService.processMarketData(validMessage))
			.isInstanceOf(RuntimeException.class)
			.hasMessage("발행 실패");
	}

	@Test
	@DisplayName("모든 필수 필드가 유효한 경우 정상 처리된다")
	void processMarketData_WithAllValidFields_ShouldProcessSuccessfully() {
		// given
		MarketDataMessage message = MarketDataMessage.builder()
			.metadata(Metadata.builder()
				.messageId("valid-uuid")
				.exchange("UPBIT")
				.dataType("TICKER")
				.collectedAt("2023-01-01T00:00:00.000Z")
				.version("1.0")
				.build())
			.payload(TickerPayload.builder()
				.marketCode("KRW-BTC")
				.build())
			.build();

		// when
		marketDataService.processMarketData(message);

		// then
		verify(marketDataPublisher).publish(message);
	}

	@Test
	@DisplayName("빈 문자열 필드들도 유효성 검증에서 걸러진다")
	void processMarketData_WithEmptyStringFields_ShouldThrowException() {
		// given
		validMessage.getMetadata().setExchange("");

		// when & then
		assertThatThrownBy(() -> marketDataService.processMarketData(validMessage))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("Exchange는 필수이다");

		verify(marketDataPublisher, never()).publish(any());
	}

	private MarketDataMessage createValidMarketDataMessage() {
		Metadata metadata = Metadata.builder()
			.messageId("test-message-id")
			.exchange("UPBIT")
			.dataType("TICKER")
			.collectedAt("2023-01-01T00:00:00.000Z")
			.version("1.0")
			.build();

		TickerPayload payload = TickerPayload.builder()
			.marketCode("KRW-BTC")
			.build();

		return MarketDataMessage.builder()
			.metadata(metadata)
			.payload(payload)
			.build();
	}
}