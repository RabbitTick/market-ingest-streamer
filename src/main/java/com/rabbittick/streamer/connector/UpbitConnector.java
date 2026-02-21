package com.rabbittick.streamer.connector;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import org.springframework.web.reactive.socket.client.WebSocketClient;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbittick.streamer.connector.dto.upbit.UpbitOrderBookDto;
import com.rabbittick.streamer.connector.dto.upbit.UpbitTickerDto;
import com.rabbittick.streamer.connector.dto.upbit.UpbitTradeDto;
import com.rabbittick.streamer.converter.UpbitDataConverter;
import com.rabbittick.streamer.global.dto.MarketDataMessage;
import com.rabbittick.streamer.service.MarketDataService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.retry.Retry;

/**
 * Upbit WebSocket API 연결 및 실시간 데이터 수집을 담당하는 커넥터.
 *
 * <p>주요 책임:
 * <ul>
 *   <li>Upbit WebSocket 연결 생명주기 관리</li>
 *   <li>실시간 ticker 및 trade 데이터 수신 및 파싱</li>
 *   <li>연결 실패 시 자동 재연결</li>
 *   <li>60초 주기 Ping/Pong 메커니즘으로 연결 유지</li>
 * </ul>
 *
 * <p>애플리케이션 시작 완료 후 자동으로 WebSocket 연결을 시작하며,
 * 애플리케이션 종료 시 연결을 정리한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UpbitConnector implements DisposableBean {

	private final WebSocketClient webSocketClient;
	private final MarketDataService marketDataService;
	private final UpbitDataConverter upbitDataConverter;
	private final ObjectMapper objectMapper;

	private static final URI UPBIT_WEBSOCKET_URI = URI.create("wss://api.upbit.com/websocket/v1");
	private static final Duration PING_INTERVAL = Duration.ofSeconds(60);
    
	/** 블로킹 처리 시 동시에 구독할 내부 Mono 개수 상한 (boundedElastic 스레드 풀 사용). */
	private static final int PROCESSING_CONCURRENCY = 32;

	/**
	 * 사용할 마켓코드 환경 설정 (development, production, full)
	 */
	@Value("${upbit.websocket.environment:development}")
	private String marketEnvironment;

    /**
     * Ticker 데이터 수집 활성화 여부
     */
    @Value("${upbit.websocket.data-types.ticker.enabled:true}")
    private boolean tickerEnabled;

    /**
     * Trade 데이터 수집 활성화 여부
     */
    @Value("${upbit.websocket.data-types.trade.enabled:false}")
    private boolean tradeEnabled;

    /**
     * OrderBook 데이터 수집 활성화 여부
     */
    @Value("${upbit.websocket.data-types.orderbook.enabled:false}")
    private boolean orderbookEnabled;

	@Autowired
	private Environment env;

	/**
	 * WebSocket 연결 상태를 관리하는 Disposable 객체.
	 * 애플리케이션 종료 시 연결을 정리하는 데 사용된다.
	 */
	private Disposable connectionDisposable;

    /**
     * Spring 애플리케이션이 완전히 준비된 후 WebSocket 연결을 시작한다.
     *
     * <p>ApplicationReadyEvent를 사용하여 모든 빈의 초기화가 완료되고
     * 애플리케이션이 요청을 받을 준비가 된 시점에 연결을 시작한다.
     *
     * @param event Spring 애플리케이션 준비 완료 이벤트
     */
    @EventListener(ApplicationReadyEvent.class)
    public void startWebSocketConnection(ApplicationReadyEvent event) {
        log.debug("설정 로드 확인 - tickerEnabled: {}, tradeEnabled: {}, orderbookEnabled: {}",
            tickerEnabled, tradeEnabled, orderbookEnabled);
        List<String> marketCodes = loadMarketCodes();
        log.info("애플리케이션 준비 완료, {} 환경으로 {} 개 마켓코드 WebSocket 연결을 시작합니다.",
                marketEnvironment, marketCodes.size());
        log.info("활성화된 데이터 타입 - Ticker: {}, Trade: {}, OrderBook: {}",
            tickerEnabled, tradeEnabled, orderbookEnabled);
        log.debug("구독할 마켓코드: {}", marketCodes);
        connectToUpbit();
    }

	/**
	 * Spring이 YAML List를 indexed property로 변환하므로
	 * Environment를 통해 동적으로 마켓코드를 로드한다.
	 *
	 * @return 구독할 마켓코드 목록
	 */
	private List<String> loadMarketCodes() {
		List<String> marketCodes = new ArrayList<>();
		List<String> enabledTiers = getEnabledTiers();

		for (String tier : enabledTiers) {
			List<String> tierMarkets = loadTierMarkets(tier);
			if (!tierMarkets.isEmpty()) {
				marketCodes.addAll(tierMarkets);
				log.debug("티어 '{}' 마켓코드 {} 개 추가: {}", tier, tierMarkets.size(), tierMarkets);
			}
		}

		if (marketCodes.isEmpty()) {
			marketCodes = getDefaultMarketCodes();
			log.warn("설정에서 마켓코드를 로드할 수 없어 기본값 사용: {}", marketCodes);
		}

		return marketCodes;
	}

	/**
	 * Environment를 통해 특정 티어의 마켓코드를 동적으로 로드한다.
	 *
	 * @param tier 로드할 티어 (tier1, tier2, tier3)
	 * @return 해당 티어의 마켓코드 목록
	 */
	private List<String> loadTierMarkets(String tier) {
		List<String> markets = new ArrayList<>();
		int index = 0;

		while (true) {
			String propertyKey = String.format("markets.krw.%s[%d]", tier, index);
			String market = env.getProperty(propertyKey);

			if (market == null) {
				break;
			}

			markets.add(market);
			index++;
		}

		log.debug("티어 '{}' 로드 완료: {} 개 마켓코드", tier, markets.size());
		return markets;
	}

	/**
	 * Environment를 통해 환경별 티어 목록을 동적으로 로드한다.
	 *
	 * @return 활성화된 티어 목록
	 */
	private List<String> getEnabledTiers() {
		String environment = marketEnvironment.toLowerCase();
		List<String> tiers = new ArrayList<>();
		int index = 0;

		while (true) {
			String propertyKey = String.format("env.%s[%d]", environment, index);
			String tier = env.getProperty(propertyKey);

			if (tier == null) {
				break;
			}

			tiers.add(tier);
			index++;
		}

		if (tiers.isEmpty()) {
			tiers = List.of("tier1");
			log.debug("환경 '{}' 설정이 없어 기본값 사용: {}", environment, tiers);
		}

		log.debug("환경 '{}' 활성 티어: {}", environment, tiers);
		return tiers;
	}

	/**
	 * Upbit WebSocket 서버에 연결하고 데이터 스트리밍을 시작한다.
	 *
	 * <p>연결 실패 시 지수 백오프 방식으로 자동 재연결을 시도한다.
	 * 최대 백오프 시간은 1분으로 제한된다.
	 */
	private void connectToUpbit() {
		this.connectionDisposable = webSocketClient
			.execute(UPBIT_WEBSOCKET_URI, this::handleWebSocketSession)
			.retryWhen(Retry.backoff(Long.MAX_VALUE, Duration.ofSeconds(5))
				.maxBackoff(Duration.ofMinutes(1))
				.doBeforeRetry(retrySignal ->
					log.warn("Upbit WebSocket 연결 재시도 중... (시도 횟수: {})",
						retrySignal.totalRetries() + 1)))
			.subscribe(
				null,
				error -> log.error("Upbit WebSocket 연결에 복구 불가능한 오류 발생", error),
				() -> log.info("Upbit WebSocket 연결이 정상적으로 종료되었습니다.")
			);
	}

	/**
	 * WebSocket 세션을 처리한다.
	 * 구독 메시지 전송, Ping/Pong 메커니즘, 데이터 수신을 담당한다.
	 *
	 * @param session WebSocket 세션
	 * @return 세션 처리 완료를 나타내는 Mono
	 */
	private Mono<Void> handleWebSocketSession(WebSocketSession session) {
		log.info("Upbit WebSocket 연결 성공, 데이터 구독을 시작합니다.");

		// 구독 메시지 전송
		Mono<Void> sendSubscription = sendSubscriptionMessage(session);

		// 60초 주기 Ping 메시지 전송
		Mono<Void> keepAlive = sendPeriodicPing(session);

		// 메시지 수신 및 처리
		Mono<Void> receiveMessages = receiveAndProcessMessages(session);

		// 모든 작업을 병렬로 실행
		return Mono.when(sendSubscription, keepAlive, receiveMessages);
	}

	/**
	 * Upbit WebSocket에 데이터 구독 메시지를 전송한다.
	 *
	 * @param session WebSocket 세션
	 * @return 메시지 전송 완료를 나타내는 Mono
	 */
	private Mono<Void> sendSubscriptionMessage(WebSocketSession session) {
		try {
			String subscriptionMessage = createSubscriptionMessage();
			log.debug("구독 메시지 전송: {}", subscriptionMessage);

			return session.send(Mono.just(session.textMessage(subscriptionMessage)));
		} catch (Exception e) {
			return Mono.error(new RuntimeException("구독 메시지 생성 실패", e));
		}
	}

	/**
	 * 60초 주기로 Ping 메시지를 전송하여 연결을 유지한다.
	 *
	 * <p>Upbit API 권장사항에 따라 60초 주기로 Ping을 전송한다.
	 *
	 * @param session WebSocket 세션
	 * @return Ping 전송 완료를 나타내는 Mono
	 */
	private Mono<Void> sendPeriodicPing(WebSocketSession session) {
		return Flux.interval(PING_INTERVAL)
			.doOnNext(tick -> log.trace("Ping 메시지 전송 (tick: {})", tick))
			.flatMap(tick -> {
				WebSocketMessage pingMessage = session.pingMessage(factory ->
					factory.wrap("ping".getBytes()));
				return session.send(Mono.just(pingMessage));
			})
			.onErrorContinue((error, obj) ->
				log.warn("Ping 전송 실패, 연결 상태를 확인하세요: {}", error.getMessage()))
			.then();
	}

	/**
	 * WebSocket으로부터 메시지를 수신하고 처리한다.
     * 블로킹 작업(파싱·변환·RabbitMQ 발행)은 boundedElastic 스케줄러에서 실행되어
     * 수신 스레드가 다음 메시지를 계속 받을 수 있도록 한다.
     *
     * @param session WebSocket 세션
     * @return 메시지 수신 완료를 나타내는 Mono
     */
    private Mono<Void> receiveAndProcessMessages(WebSocketSession session) {
        return session.receive()
                .map(WebSocketMessage::getPayloadAsText)
                .flatMap(jsonMessage ->
                                parseMessage(jsonMessage).subscribeOn(Schedulers.boundedElastic()),
                        PROCESSING_CONCURRENCY)
                .doOnError(error -> log.error("메시지 처리 중 오류 발생", error))
                .onErrorContinue((error, obj) ->
                        log.warn("메시지 처리 실패, 다음 메시지 계속 처리: {}", error.getMessage()))
                .then();
    }

    /**
     * WebSocket으로부터 수신된 메시지를 타입에 따라 파싱한다.
     *
     * @param jsonMessage WebSocket으로부터 수신된 JSON 문자열
     * @return 파싱된 메시지를 담는 Mono (타입별 분기 처리)
     */
    private Mono<Void> parseMessage(String jsonMessage) {
        log.debug("WebSocket 메시지 수신: {}", jsonMessage);

        try {
            // 먼저 메시지에서 type 필드를 확인
            JsonNode messageNode = objectMapper.readTree(jsonMessage);
            JsonNode typeNode = messageNode.get("ty");

            if (typeNode == null) {
                log.trace("type 필드가 없는 메시지 무시");
                return Mono.empty();
            }

            String messageType = typeNode.asText();
            log.debug("메시지 타입 확인: {}", messageType);

            switch (messageType) {
                case "ticker":
                    if (tickerEnabled) {
                        return parseTickerData(jsonMessage)
                                .flatMap(this::processTickerData);
                    }
                    break;

                case "trade":
                    if (tradeEnabled) {
                        return parseTradeData(jsonMessage)
                                .flatMap(this::processTradeData);
                    }
                    break;

                case "orderbook":
                    if (orderbookEnabled) {
                        return parseOrderBookData(jsonMessage)
                                .flatMap(this::processOrderBookData);
                    }
                    break;

                default:
                    log.trace("알 수 없는 메시지 타입 무시: {}", messageType);
                    break;
            }

            return Mono.empty();

        } catch (JsonProcessingException e) {
            log.trace("메시지 파싱 실패 (무시): {}",
                    jsonMessage.substring(0, Math.min(100, jsonMessage.length())));
            return Mono.empty();
        }
    }

    /**
     * 수신된 JSON 메시지를 UpbitTickerDto 객체로 파싱한다.
     *
     * <p>파싱에 실패한 메시지(연결 성공 메시지 등)는 무시하고
     * 빈 Mono를 반환한다.
     *
     * @param jsonMessage WebSocket으로부터 수신된 JSON 문자열
     * @return 파싱된 TickerDto 또는 빈 Mono
     */
    private Mono<UpbitTickerDto> parseTickerData(String jsonMessage) {
        try {
            UpbitTickerDto tickerDto = objectMapper.readValue(jsonMessage, UpbitTickerDto.class);
            return Mono.just(tickerDto);
        } catch (JsonProcessingException e) {
            log.trace("Ticker 데이터가 아닌 메시지 수신 (무시): {}",
                    jsonMessage.substring(0, Math.min(100, jsonMessage.length())));
            return Mono.empty();
        }
    }

    /**
     * 수신된 JSON 메시지를 UpbitTradeDto 객체로 파싱한다.
     *
     * @param jsonMessage WebSocket으로부터 수신된 JSON 문자열
     * @return 파싱된 TradeDto 또는 빈 Mono
     */
    private Mono<UpbitTradeDto> parseTradeData(String jsonMessage) {
        try {
            UpbitTradeDto tradeDto = objectMapper.readValue(jsonMessage, UpbitTradeDto.class);
            return Mono.just(tradeDto);
        } catch (JsonProcessingException e) {
            log.trace("Trade 데이터가 아닌 메시지 수신 (무시): {}",
                    jsonMessage.substring(0, Math.min(100, jsonMessage.length())));
            return Mono.empty();
        }
    }

    /**
     * 수신된 JSON 메시지를 UpbitOrderBookDto 객체로 파싱한다.
     *
     * @param jsonMessage WebSocket으로부터 수신된 JSON 문자열
     * @return 파싱된 OrderBookDto 또는 빈 Mono
     */
    private Mono<UpbitOrderBookDto> parseOrderBookData(String jsonMessage) {
        try {
            UpbitOrderBookDto orderBookDto = objectMapper.readValue(jsonMessage, UpbitOrderBookDto.class);
            return Mono.just(orderBookDto);
        } catch (JsonProcessingException e) {
            log.trace("OrderBook 데이터가 아닌 메시지 수신 (무시): {}",
                    jsonMessage.substring(0, Math.min(100, jsonMessage.length())));
            return Mono.empty();
        }
    }

	/**
	 * 수신된 Ticker 데이터를 처리한다.
	 *
	 * <p>Upbit DTO를 표준 DTO로 변환하고 서비스 계층으로 전달한다.
	 *
	 * @param upbitDto 수신된 Upbit ticker DTO
	 * @return 발행 Mono
	 */
	private Mono<Void> processTickerData(UpbitTickerDto upbitDto) {
		log.trace("Ticker 데이터 수신: {} - {}",
			upbitDto.getMarketCode(), upbitDto.getTradePrice());

		try {
			MarketDataMessage<?> message = upbitDataConverter.convertTickerData(upbitDto);
			return marketDataService.processMarketData(message);
		} catch (IllegalArgumentException e) {
			log.warn("Ticker 데이터 검증 실패: {} - {}",
				upbitDto.getMarketCode(), e.getMessage());
			return Mono.empty();
		} catch (Exception e) {
			log.error("Ticker 데이터 처리 실패: {}", upbitDto.getMarketCode(), e);
			return Mono.empty();
		}
	}

    /**
     * 수신된 Trade 데이터를 처리한다.
     *
     * @param upbitDto 수신된 Upbit trade DTO
     * @return 발행 Mono
     */
    private Mono<Void> processTradeData(UpbitTradeDto upbitDto) {
        log.trace("Trade 데이터 수신: {} - {} ({})",
                upbitDto.getMarketCode(), upbitDto.getTradePrice(), upbitDto.getAskBid());

        try {
            MarketDataMessage<?> message = upbitDataConverter.convertTradeData(upbitDto);
            return marketDataService.processMarketData(message);
        } catch (IllegalArgumentException e) {
            log.warn("Trade 데이터 검증 실패: {} - {}",
                    upbitDto.getMarketCode(), e.getMessage());
            return Mono.empty();
        } catch (Exception e) {
            log.error("Trade 데이터 처리 실패: {}", upbitDto.getMarketCode(), e);
            return Mono.empty();
        }
    }

    /**
     * 수신된 OrderBook 데이터를 처리한다.
     *
     * @param upbitDto 수신된 Upbit orderbook DTO
     * @return 발행 Mono
     */
    private Mono<Void> processOrderBookData(UpbitOrderBookDto upbitDto) {
        log.trace("OrderBook 데이터 수신: {} - units: {}",
            upbitDto.getMarketCode(),
            upbitDto.getOrderbookUnits() == null ? 0 : upbitDto.getOrderbookUnits().size());

        try {
            MarketDataMessage<?> message = upbitDataConverter.convertOrderBookData(upbitDto);
            return marketDataService.processMarketData(message);
        } catch (IllegalArgumentException e) {
            log.warn("OrderBook 데이터 검증 실패: {} - {}",
                upbitDto.getMarketCode(), e.getMessage());
            return Mono.empty();
        } catch (Exception e) {
            log.error("OrderBook 데이터 처리 실패: {}", upbitDto.getMarketCode(), e);
            return Mono.empty();
        }
    }

    /**
     * Upbit WebSocket 구독 요청 메시지를 JSON 형식으로 생성한다.
     *
     * <p>설정에 따라 활성화된 데이터 타입만 구독한다:
     * <ul>
     *   <li>ticker: 현재가 정보</li>
     *   <li>trade: 거래 체결 정보</li>
     *   <li>orderbook: 호가 정보</li>
     * </ul>
     *
     * @return JSON 형식의 구독 메시지
     * @throws JsonProcessingException JSON 직렬화 실패 시
     * @throws IllegalStateException 활성화된 데이터 타입이 없는 경우
     */
    private String createSubscriptionMessage() throws JsonProcessingException {
        List<String> marketCodes = loadMarketCodes();
        List<Object> request = new ArrayList<>();

        // Ticket 정보
        request.add(new Ticket(UUID.randomUUID().toString()));

        // 활성화된 데이터 타입별로 구독 요청 추가
        if (tickerEnabled) {
            request.add(new Type("ticker", marketCodes));
            log.info("Ticker 구독 활성화: {} 개 마켓코드", marketCodes.size());
        }

        if (tradeEnabled) {
            request.add(new Type("trade", marketCodes));
            log.info("Trade 구독 활성화: {} 개 마켓코드", marketCodes.size());
        }

        if (orderbookEnabled) {
            request.add(new Type("orderbook", marketCodes));
            log.info("OrderBook 구독 활성화: {} 개 마켓코드", marketCodes.size());
        }

        // 구독할 데이터 타입이 없는 경우 예외 발생
        if (!tickerEnabled && !tradeEnabled && !orderbookEnabled) {
            throw new IllegalStateException("최소 하나의 데이터 타입은 활성화되어야 합니다");
        }

        // Format 정보
        request.add(new Format("SIMPLE"));

		return objectMapper.writeValueAsString(request);
	}

	/**
	 * 설정 로드 실패 시 사용할 기본 마켓코드.
	 *
	 * @return 기본 마켓코드 목록
	 */
	private List<String> getDefaultMarketCodes() {
		return List.of("KRW-BTC", "KRW-ETH", "KRW-XRP");
	}

	/**
	 * 애플리케이션 종료 시 WebSocket 연결을 정리한다.
	 *
	 * <p>DisposableBean 인터페이스 구현을 통해 Spring 컨테이너 종료 시
	 * 자동으로 호출되어 리소스를 정리한다.
	 */
	@Override
	public void destroy() {
		if (connectionDisposable != null && !connectionDisposable.isDisposed()) {
			log.info("Upbit WebSocket 연결을 종료합니다.");
			connectionDisposable.dispose();
		}
	}

	// 구독 메시지 포맷을 위한 내부 레코드 클래스들
	private record Ticket(String ticket) {
	}

	private record Type(String type, List<String> codes) {
	}

	private record Format(String format) {
	}
}