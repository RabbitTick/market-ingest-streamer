package com.rabbittick.streamer.metrics;

import java.util.concurrent.atomic.AtomicLong;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * RabbitMQ 메시지 발행 실패 건수를 집계하고 주기적으로 로그에 출력한다.
 *
 * <p>매 초마다 직전 1초 동안 발행 실패한 메시지 수를 로그로 남기며,
 * 마지막 구간의 수치와 누적 값을 API로 조회할 수 있도록 보관한다.
 */
@Slf4j
@Component
public class PublishFailureMetrics {

    private final AtomicLong currentTicker = new AtomicLong(0);
    private final AtomicLong currentTrade = new AtomicLong(0);
    private final AtomicLong currentOrderbook = new AtomicLong(0);

    private final AtomicLong cumulativeTicker = new AtomicLong(0);
    private final AtomicLong cumulativeTrade = new AtomicLong(0);
    private final AtomicLong cumulativeOrderbook = new AtomicLong(0);

    private volatile long lastSecondTotal;
    private volatile long lastSecondTicker;
    private volatile long lastSecondTrade;
    private volatile long lastSecondOrderbook;

    /**
     * 발행 실패 1건을 데이터 타입별로 기록한다.
     *
     * @param dataType 메타데이터의 dataType (TICKER, TRADE, ORDERBOOK 등)
     */
    public void record(String dataType) {
        if (dataType == null) {
            return;
        }
        String normalized = dataType.toUpperCase();
        switch (normalized) {
            case "TICKER" -> {
                currentTicker.incrementAndGet();
                cumulativeTicker.incrementAndGet();
            }
            case "TRADE" -> {
                currentTrade.incrementAndGet();
                cumulativeTrade.incrementAndGet();
            }
            case "ORDERBOOK" -> {
                currentOrderbook.incrementAndGet();
                cumulativeOrderbook.incrementAndGet();
            }
            default -> log.warn("알 수 없는 데이터 타입 발행 실패: {}", dataType);
        }
    }

    /**
     * 매 초 실행: 직전 1초 구간의 실패 건수를 저장하고, 실패가 있으면 로그에 남긴다.
     */
    @Scheduled(fixedRate = 1000)
    public void flushAndLog() {
        long ticker = currentTicker.getAndSet(0);
        long trade = currentTrade.getAndSet(0);
        long orderbook = currentOrderbook.getAndSet(0);
        long total = ticker + trade + orderbook;

        lastSecondTotal = total;
        lastSecondTicker = ticker;
        lastSecondTrade = trade;
        lastSecondOrderbook = orderbook;

        if (total > 0) {
            log.warn("Publish failures: {} msg/s (ticker: {}, trade: {}, orderbook: {}), cumulative: {}",
                    total, ticker, trade, orderbook, getCumulativeTotal());
        }
    }

    public long getCumulativeTotal() {
        return cumulativeTicker.get() + cumulativeTrade.get() + cumulativeOrderbook.get();
    }

    public long getCumulativeTicker() {
        return cumulativeTicker.get();
    }

    public long getCumulativeTrade() {
        return cumulativeTrade.get();
    }

    public long getCumulativeOrderbook() {
        return cumulativeOrderbook.get();
    }

    public long getLastSecondTotal() {
        return lastSecondTotal;
    }

    public long getLastSecondTicker() {
        return lastSecondTicker;
    }

    public long getLastSecondTrade() {
        return lastSecondTrade;
    }

    public long getLastSecondOrderbook() {
        return lastSecondOrderbook;
    }
}