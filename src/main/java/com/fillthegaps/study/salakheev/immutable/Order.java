package com.fillthegaps.study.salakheev.immutable;

import lombok.Getter;
import lombok.Value;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

import static com.fillthegaps.study.salakheev.immutable.Status.CREATED;
import static com.fillthegaps.study.salakheev.immutable.Status.DELIVERED;
import static java.util.Collections.unmodifiableList;

@Getter
public class Order {

    private static final Status CHECKABLE_STATUS = DELIVERED;
    private static final LongAdder nextId = new LongAdder();

    private final Long id;
    private final List<Item> items;
    private PaymentInfo paymentInfo;
    private boolean isPacked;
    private Status status;

    public Order(List<Item> items) {
        this.items = items;
        this.status = CREATED;
        nextId.increment();
        this.id = nextId.longValue();
    }

    public Order(Long id, List<Item> items, PaymentInfo paymentInfo, boolean isPacked, Status status) {
        this.id = id;
        this.items = items;
        this.paymentInfo = paymentInfo;
        this.isPacked = isPacked;
        this.status = status;
    }

    public Order withStatus(Status status) {
        return new Order(this.id, this.items, this.paymentInfo, this.isPacked, status);
    }

    public Order withPaymentInfo(PaymentInfo paymentInfo) {
        return new Order(this.id, this.items, paymentInfo, this.isPacked, this.status);
    }

    public Order doPack() {
        return new Order(this.id, this.items, this.paymentInfo, true, this.status);
    }

    public boolean checkStatus() {
        if (status != CHECKABLE_STATUS && items != null && !items.isEmpty() && paymentInfo != null && isPacked) {
            status = DELIVERED;
            return true;
        }
        return false;
    }

    public List<Item> getItems() {
        return unmodifiableList(items);
    }
}

@Value
class Item {
}

@Value
class PaymentInfo {
}

enum Status {
    CREATED,
    DELIVERED // etc
}
