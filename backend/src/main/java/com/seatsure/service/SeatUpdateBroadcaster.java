package com.seatsure.service;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.seatsure.dto.SeatUpdateMessage;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class SeatUpdateBroadcaster {

    private final SimpMessagingTemplate messagingTemplate;

    // AFTER_COMMIT: if the booking rolls back, nobody ever sees the seat turn grey
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onSeatsChanged(SeatsChangedEvent event) {
        String topic = "/topic/shows/" + event.showId() + "/seats";
        try {
            messagingTemplate.convertAndSend(topic, new SeatUpdateMessage(event.showId(), event.seats()));
        } catch (RuntimeException e) {
            // live updates are a nice to have, the seat map is reloaded on every hold anyway
            log.warn("Could not broadcast seat update for show {}", event.showId(), e);
        }
    }
}
