package com.rabbittick.streamer.connector;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketSession;
import org.springframework.web.reactive.socket.client.WebSocketClient;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbittick.streamer.converter.ExchangeDataConverter;
import com.rabbittick.streamer.global.dto.MarketDataMessage;
import com.rabbittick.streamer.service.MarketDataService;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

/**
 * Upbit WebSocket API 연결 및 실시간 데이터 수집을 담당하는 커넥터.
 *
 * <p>주요 책임:
 * <ul>
 *   <li>Upbit 전용 WebSocket 구독 메시지 생성 및 전송</li>
 *   <li>{@code "ty"} 필드 기반 메시지 타입 식별 및 {@link ExchangeDataConverter} 위임</li>
 *   <li>Upbit 마켓코드 tier별 로드</li>
 * </ul>
 *
 * <p>연결 생명주기(청크 분할, 재연결, Ping, 수신 루프)는
 * {@link AbstractExchangeConnector}가 처리한다.
 */
@Slf4j
@Component
public class UpbitConnector extends AbstractExchangeConnector {

    private final ExchangeDataConverter converter;
    private final ObjectMapper objectMapper;

    private static final URI UPBIT_WEBSOCKET_URI = URI.create("wss://api.upbit.com/websocket/v1");
    private static final Duration PING_INTERVAL = Duration.ofSeconds(60);

    /** Upbit WebSocket 연결당 최대 구독 가능 마켓코드 수 */
    private static final int MAX_MARKETS_PER_CONNECTION = 100;

    /** 블로킹 처리 시 동시에 구독할 내부 Mono 개수 상한 (boundedElastic 스레드 풀 사용) */
    private static final int PROCESSING_CONCURRENCY = 32;

    /** 구독할 마켓 prefix 목록 (KRW, BTC, USDT 등) */
    @Value("${upbit.websocket.market-prefixes:KRW}")
    private List<String> marketPrefixes;

    /**
     * 사용할 마켓코드 환경 설정 (development, production, full, fetched)
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
     * 생성자 주입.
     *
     * <p>{@code webSocketClient}와 {@code marketDataService}는 부모 클래스로 전달한다.
     *
     * @param webSocketClient WebSocket 연결 클라이언트
     * @param marketDataService 시장 데이터 처리 서비스
     * @param converter Upbit DTO → 표준 DTO 변환 컨버터
     * @param objectMapper JSON 파싱용 ObjectMapper
     */
    public UpbitConnector(WebSocketClient webSocketClient,
                          MarketDataService marketDataService,
                          ExchangeDataConverter converter,
                          ObjectMapper objectMapper) {
        super(webSocketClient, marketDataService);
        this.converter = converter;
        this.objectMapper = objectMapper;
    }

    /**
     * Spring 애플리케이션이 완전히 준비된 후 WebSocket 연결을 시작한다.
     *
     * <p>Commit 4에서 {@code ConnectorManager}로 이관 예정.
     *
     * @param event Spring 애플리케이션 준비 완료 이벤트
     */
    @EventListener(ApplicationReadyEvent.class)
    public void startWebSocketConnection(ApplicationReadyEvent event) {
        log.debug("설정 로드 확인 - tickerEnabled: {}, tradeEnabled: {}, orderbookEnabled: {}",
            tickerEnabled, tradeEnabled, orderbookEnabled);
        log.info("활성화된 데이터 타입 - Ticker: {}, Trade: {}, OrderBook: {}",
            tickerEnabled, tradeEnabled, orderbookEnabled);
        start();
    }

    @Override
    public String getExchangeName() {
        return "UPBIT";
    }

    @Override
    public URI getWebSocketUri() {
        return UPBIT_WEBSOCKET_URI;
    }

    @Override
    public Duration getPingInterval() {
        return PING_INTERVAL;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    protected int getMaxMarketsPerConnection() {
        return MAX_MARKETS_PER_CONNECTION;
    }

    @Override
    protected int getProcessingConcurrency() {
        return PROCESSING_CONCURRENCY;
    }

    /**
     * Upbit WebSocket에 데이터 구독 메시지를 전송한다.
     *
     * <p>활성화된 데이터 타입만 구독 요청에 포함하며,
     * {@code [{"ticket":…}, {"type":"ticker","codes":[…]}, {"format":"SIMPLE"}]} 형식으로 전송한다.
     *
     * @param session WebSocket 세션
     * @param marketCodes 구독할 마켓코드 목록
     * @return 구독 메시지 전송 완료를 나타내는 Mono
     */
    @Override
    public Mono<Void> sendSubscription(WebSocketSession session, List<String> marketCodes) {
        try {
            String subscriptionMessage = createSubscriptionMessage(marketCodes);
            log.debug("구독 메시지 전송 (마켓코드 {}개)", marketCodes.size());
            return session.send(Mono.just(session.textMessage(subscriptionMessage)));
        } catch (Exception e) {
            return Mono.error(new RuntimeException("구독 메시지 생성 실패", e));
        }
    }

    /**
     * 수신된 원시 JSON 메시지를 {@code "ty"} 필드로 타입을 식별하고
     * {@link ExchangeDataConverter}에 변환을 위임한 후 서비스 계층으로 전달한다.
     *
     * <p>비활성화된 타입의 메시지와 파싱 불가 메시지(연결 수락 응답 등)는 무시한다.
     *
     * @param rawJson WebSocket으로부터 수신된 원시 JSON 문자열
     * @return 처리 완료를 나타내는 Mono. 무시된 메시지의 경우 빈 Mono
     */
    @Override
    public Mono<Void> handleMessage(String rawJson) {
        log.debug("[UPBIT] WebSocket 메시지 수신: {}", rawJson);

        try {
            JsonNode messageNode = objectMapper.readTree(rawJson);
            JsonNode typeNode = messageNode.get("ty");

            if (typeNode == null) {
                log.trace("[UPBIT] type 필드가 없는 메시지 무시");
                return Mono.empty();
            }

            String messageType = typeNode.asText();
            log.debug("[UPBIT] 메시지 타입 확인: {}", messageType);

            return switch (messageType) {
                case "ticker" -> tickerEnabled ? processMessage(converter.convertTicker(rawJson)) : Mono.empty();
                case "trade"  -> tradeEnabled  ? processMessage(converter.convertTrade(rawJson))  : Mono.empty();
                case "orderbook" -> orderbookEnabled ? processMessage(converter.convertOrderBook(rawJson)) : Mono.empty();
                default -> {
                    log.trace("[UPBIT] 알 수 없는 메시지 타입 무시: {}", messageType);
                    yield Mono.empty();
                }
            };

        } catch (JsonProcessingException e) {
            log.trace("[UPBIT] 메시지 파싱 실패 (무시): {}",
                rawJson.substring(0, Math.min(100, rawJson.length())));
            return Mono.empty();
        }
    }

    /**
     * 변환된 시장 데이터 메시지를 서비스 계층에 전달한다.
     *
     * @param message 표준화된 시장 데이터 메시지
     * @return 처리 완료를 나타내는 Mono
     */
    private Mono<Void> processMessage(MarketDataMessage<?> message) {
        try {
            return marketDataService.processMarketData(message);
        } catch (IllegalArgumentException e) {
            log.warn("[UPBIT] 데이터 검증 실패: {}", e.getMessage());
            return Mono.empty();
        } catch (Exception e) {
            log.error("[UPBIT] 데이터 처리 실패", e);
            return Mono.empty();
        }
    }

    /**
     * Spring이 YAML List를 indexed property로 변환하므로
     * {@link Environment}를 통해 동적으로 마켓코드를 로드한다.
     *
     * @return 구독할 마켓코드 목록
     */
    @Override
    public List<String> loadMarketCodes() {
        List<String> marketCodes = new ArrayList<>();

        for (String prefix : marketPrefixes) {
            List<String> enabledTiers = getEnabledTiers();
            for (String tier : enabledTiers) {
                List<String> tierMarkets = loadTierMarkets(prefix.toLowerCase(), tier);
                if (!tierMarkets.isEmpty()) {
                    marketCodes.addAll(tierMarkets);
                    log.debug("마켓 '{}' 티어 '{}' 마켓코드 {}개 추가", prefix, tier, tierMarkets.size());
                }
            }
        }

        if (marketCodes.isEmpty()) {
            marketCodes = getDefaultMarketCodes();
            log.warn("설정에서 마켓코드를 로드할 수 없어 기본값 사용: {}", marketCodes);
        }

        log.info("전체 마켓코드 {}개 로드 완료 (prefix: {})", marketCodes.size(), marketPrefixes);
        return marketCodes;
    }

    /**
     * Environment를 통해 특정 티어의 마켓코드를 동적으로 로드한다.
     *
     * @param marketPrefix 마켓 prefix (krw, btc, usdt)
     * @param tier 로드할 티어 (tier1, tier2, tier3)
     * @return 해당 티어의 마켓코드 목록
     */
    private List<String> loadTierMarkets(String marketPrefix, String tier) {
        List<String> markets = new ArrayList<>();
        int index = 0;

        while (true) {
            String propertyKey = String.format("markets.%s.%s[%d]", marketPrefix, tier, index);
            String market = env.getProperty(propertyKey);

            if (market == null) {
                break;
            }

            markets.add(market);
            index++;
        }

        log.debug("마켓 '{}' 티어 '{}' 로드 완료: {}개 마켓코드", marketPrefix, tier, markets.size());
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
     * Upbit WebSocket 구독 요청 메시지를 JSON 형식으로 생성한다.
     *
     * <p>설정에 따라 활성화된 데이터 타입만 구독한다:
     * <ul>
     *   <li>ticker: 현재가 정보</li>
     *   <li>trade: 거래 체결 정보</li>
     *   <li>orderbook: 호가 정보</li>
     * </ul>
     *
     * @param marketCodes 구독할 마켓코드 목록
     * @return JSON 형식의 구독 메시지
     * @throws JsonProcessingException JSON 직렬화 실패 시
     * @throws IllegalStateException 활성화된 데이터 타입이 없는 경우
     */
    private String createSubscriptionMessage(List<String> marketCodes) throws JsonProcessingException {
        List<Object> request = new ArrayList<>();
        request.add(new Ticket(UUID.randomUUID().toString()));

        if (tickerEnabled) {
            request.add(new Type("ticker", marketCodes));
            log.info("Ticker 구독 활성화: {}개 마켓코드", marketCodes.size());
        }
        if (tradeEnabled) {
            request.add(new Type("trade", marketCodes));
            log.info("Trade 구독 활성화: {}개 마켓코드", marketCodes.size());
        }
        if (orderbookEnabled) {
            request.add(new Type("orderbook", marketCodes));
            log.info("OrderBook 구독 활성화: {}개 마켓코드", marketCodes.size());
        }

        if (!tickerEnabled && !tradeEnabled && !orderbookEnabled) {
            throw new IllegalStateException("최소 하나의 데이터 타입은 활성화되어야 합니다");
        }

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

    // 구독 메시지 포맷을 위한 내부 레코드 클래스들
    private record Ticket(String ticket) {}
    private record Type(String type, List<String> codes) {}
    private record Format(String format) {}
}
