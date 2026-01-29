package com.rabbittick.streamer.publisher;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbittick.streamer.global.dto.MarketDataMessage;
import com.rabbittick.streamer.global.dto.Metadata;
import com.rabbittick.streamer.global.dto.OrderBookPayload;
import com.rabbittick.streamer.global.dto.TickerPayload;

@ExtendWith(MockitoExtension.class)
class MarketDataPublisherTest {

	@Mock
	private RabbitTemplate rabbitTemplate;

	@Mock
	private ObjectMapper objectMapper;

	@InjectMocks
	private MarketDataPublisher publisher;

	private MarketDataMessage<TickerPayload> tickerMessage;

	private final String exchangeName = "market-data.exchange";

	@BeforeEach
	void setUp() {
		ReflectionTestUtils.setField(publisher, "exchangeName", exchangeName);
		tickerMessage = createTickerMessage();
	}

	@Test
	@DisplayName("정상적인 메시지를 올바른 라우팅 키로 발행한다")
	void publish_WithValidMessage_ShouldPublishWithCorrectRoutingKey() throws JsonProcessingException {
		// given
		String expectedJson = "{\"metadata\":{...},\"payload\":{...}}";
		when(objectMapper.writeValueAsString(tickerMessage)).thenReturn(expectedJson);

		// when
		publisher.publish(tickerMessage);

		// then
		ArgumentCaptor<String> routingKeyCaptor = ArgumentCaptor.forClass(String.class);
		ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);

		verify(rabbitTemplate).convertAndSend(
			eq(exchangeName),
			routingKeyCaptor.capture(),
			messageCaptor.capture()
		);

		String routingKey = routingKeyCaptor.getValue();
		String jsonMessage = messageCaptor.getValue();

		assertThat(routingKey).isEqualTo("upbit.ticker.KRW-BTC");
		assertThat(jsonMessage).isEqualTo(expectedJson);
	}

	@Test
	@DisplayName("다른 거래소와 마켓코드로 올바른 라우팅 키를 생성한다")
	void publish_WithDifferentExchangeAndMarket_ShouldGenerateCorrectRoutingKey() throws JsonProcessingException {
		// given
		MarketDataMessage<TickerPayload> binanceMessage = createMessageWithExchangeAndMarket("BINANCE", "BTCUSDT");
		when(objectMapper.writeValueAsString(binanceMessage)).thenReturn("{}");

		// when
		publisher.publish(binanceMessage);

		// then
		ArgumentCaptor<String> routingKeyCaptor = ArgumentCaptor.forClass(String.class);
		verify(rabbitTemplate).convertAndSend(
			eq(exchangeName),
			routingKeyCaptor.capture(),
			any(String.class)
		);

		assertThat(routingKeyCaptor.getValue()).isEqualTo("binance.ticker.BTCUSDT");
	}

	@Test
	@DisplayName("TRADE 데이터 타입으로 올바른 라우팅 키를 생성한다")
	void publish_WithTradeDataType_ShouldGenerateCorrectRoutingKey() throws JsonProcessingException {
		// given
		Metadata tradeMetadata = Metadata.builder()
			.messageId("test-id")
			.exchange("UPBIT")
			.dataType("TRADE")
			.collectedAt("2023-01-01T00:00:00.000Z")
			.version("1.0")
			.build();

		TickerPayload payload = TickerPayload.builder()
			.marketCode("KRW-ETH")
			.tradePrice(BigDecimal.valueOf(2000000))
			.build();

		MarketDataMessage<TickerPayload> tradeMessage = MarketDataMessage.<TickerPayload>builder()
			.metadata(tradeMetadata)
			.payload(payload)
			.build();

		when(objectMapper.writeValueAsString(tradeMessage)).thenReturn("{}");

		// when
		publisher.publish(tradeMessage);

		// then
		ArgumentCaptor<String> routingKeyCaptor = ArgumentCaptor.forClass(String.class);
		verify(rabbitTemplate).convertAndSend(
			eq(exchangeName),
			routingKeyCaptor.capture(),
			any(String.class)
		);

		assertThat(routingKeyCaptor.getValue()).isEqualTo("upbit.trade.KRW-ETH");
	}

	@Test
	@DisplayName("ORDERBOOK 데이터 타입으로 올바른 라우팅 키를 생성한다")
	void publish_WithOrderBookDataType_ShouldGenerateCorrectRoutingKey() throws JsonProcessingException {
		// given
		Metadata metadata = Metadata.builder()
			.messageId("test-id")
			.exchange("UPBIT")
			.dataType("ORDERBOOK")
			.collectedAt("2023-01-01T00:00:00.000Z")
			.version("1.0")
			.build();

		OrderBookPayload payload = OrderBookPayload.builder()
			.marketCode("KRW-BTC")
			.build();

		MarketDataMessage<OrderBookPayload> orderBookMessage = MarketDataMessage.<OrderBookPayload>builder()
			.metadata(metadata)
			.payload(payload)
			.build();

		when(objectMapper.writeValueAsString(orderBookMessage)).thenReturn("{}");

		// when
		publisher.publish(orderBookMessage);

		// then
		ArgumentCaptor<String> routingKeyCaptor = ArgumentCaptor.forClass(String.class);
		verify(rabbitTemplate).convertAndSend(
			eq(exchangeName),
			routingKeyCaptor.capture(),
			any(String.class)
		);

		assertThat(routingKeyCaptor.getValue()).isEqualTo("upbit.orderbook.KRW-BTC");
	}

	@Test
	@DisplayName("JSON 직렬화 실패 시 MessagePublishException을 발생시킨다")
	void publish_WhenJsonSerializationFails_ShouldThrowMessagePublishException() throws JsonProcessingException {
		// given
		JsonProcessingException jsonException = new JsonProcessingException("JSON 변환 실패") {
		};
		when(objectMapper.writeValueAsString(tickerMessage)).thenThrow(jsonException);

		// when & then
		assertThatThrownBy(() -> publisher.publish(tickerMessage))
			.isInstanceOf(MarketDataPublisher.MessagePublishException.class)
			.hasMessageContaining("메시지 직렬화 실패")
			.hasCause(jsonException);
	}

	@Test
	@DisplayName("RabbitTemplate에서 예외 발생 시 MessagePublishException을 발생시킨다")
	void publish_WhenRabbitTemplateThrowsException_ShouldThrowMessagePublishException() throws JsonProcessingException {
		// given
		when(objectMapper.writeValueAsString(tickerMessage)).thenReturn("{}");
		RuntimeException rabbitException = new RuntimeException("RabbitMQ 연결 실패");
		doThrow(rabbitException).when(rabbitTemplate)
			.convertAndSend(any(String.class), any(String.class), any(String.class));

		// when & then
		assertThatThrownBy(() -> publisher.publish(tickerMessage))
			.isInstanceOf(MarketDataPublisher.MessagePublishException.class)
			.hasMessageContaining("메시지 발행 실패")
			.hasCause(rabbitException);
	}

	@Test
	@DisplayName("마켓코드에 특수문자가 있어도 라우팅 키를 올바르게 생성한다")
	void publish_WithSpecialCharactersInMarketCode_ShouldHandleCorrectly() throws JsonProcessingException {
		// given
		MarketDataMessage<TickerPayload> message = createMessageWithExchangeAndMarket("UPBIT", "KRW-BTC");
		when(objectMapper.writeValueAsString(message)).thenReturn("{}");

		// when
		publisher.publish(message);

		// then
		ArgumentCaptor<String> routingKeyCaptor = ArgumentCaptor.forClass(String.class);
		verify(rabbitTemplate).convertAndSend(
			eq(exchangeName),
			routingKeyCaptor.capture(),
			any(String.class)
		);

		assertThat(routingKeyCaptor.getValue()).isEqualTo("upbit.ticker.KRW-BTC");
	}

	@Test
	@DisplayName("null 메시지 입력 시 NullPointerException을 발생시킨다")
	void publish_WithNullMessage_ShouldThrowNullPointerException() {
		// when & then
		assertThatThrownBy(() -> publisher.publish(null))
			.isInstanceOf(NullPointerException.class);
	}

	@Test
	@DisplayName("대소문자 변환이 올바르게 적용된다")
	void publish_ShouldConvertToLowerCase() throws JsonProcessingException {
		// given
		MarketDataMessage<TickerPayload> message = createMessageWithExchangeAndMarket("UPBIT", "KRW-BTC");
		when(objectMapper.writeValueAsString(message)).thenReturn("{}");

		// when
		publisher.publish(message);

		// then
		ArgumentCaptor<String> routingKeyCaptor = ArgumentCaptor.forClass(String.class);
		verify(rabbitTemplate).convertAndSend(
			eq(exchangeName),
			routingKeyCaptor.capture(),
			any(String.class)
		);

		String routingKey = routingKeyCaptor.getValue();

		assertThat(routingKey).matches("^[a-z]+\\.[a-z]+\\..+$");
		assertThat(routingKey).isEqualTo("upbit.ticker.KRW-BTC");
	}

	private MarketDataMessage<TickerPayload> createTickerMessage() {
		return createMessageWithExchangeAndMarket("UPBIT", "KRW-BTC");
	}

	private MarketDataMessage<TickerPayload> createMessageWithExchangeAndMarket(String exchange, String marketCode) {
		Metadata metadata = Metadata.builder()
			.messageId("test-message-id")
			.exchange(exchange)
			.dataType("TICKER")
			.collectedAt("2023-01-01T00:00:00.000Z")
			.version("1.0")
			.build();

		TickerPayload payload = TickerPayload.builder()
			.marketCode(marketCode)
			.tradePrice(BigDecimal.valueOf(70000000))
			.tradeVolume(BigDecimal.valueOf(0.1234))
			.timestamp(1672531200000L)
			.build();

		return MarketDataMessage.<TickerPayload>builder()
			.metadata(metadata)
			.payload(payload)
			.build();
	}
}