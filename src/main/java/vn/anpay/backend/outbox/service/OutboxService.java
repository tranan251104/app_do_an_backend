package vn.anpay.backend.outbox.service;

import vn.anpay.backend.outbox.entity.OutboxEvent;
import vn.anpay.backend.outbox.repository.OutboxRepository;
import org.springframework.stereotype.Service;
import java.util.UUID;

@Service
public class OutboxService {
    private final OutboxRepository repo;
    public OutboxService(OutboxRepository r) {
        repo=r;

    }
    public void add(String agg,UUID id,String type,String payload) {
        repo.save(new OutboxEvent(agg,id,type,payload));

    }

}
