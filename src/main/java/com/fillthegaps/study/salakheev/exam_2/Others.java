package com.fillthegaps.study.salakheev.exam_2;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

public class Others {

    public static class LoadingCache<K, V> {
        Map<K,V> map = new ConcurrentHashMap<>();

        public void add(K key, V value) {
            map.put(key, value);
        }

        public void invalidate(String address) {
            map.remove(address);
        }

        public void cleanUp() { map.clear(); }

    }

    public static class RouterClient {

    }

    public static class RouterState {
        private static AtomicInteger counter = new AtomicInteger(0);
        private String adminAddress;

        public RouterState(String address) {
            this.adminAddress = address + counter.incrementAndGet();
        }

        public String getAdminAddress() {
            return adminAddress;
        }
    }

    public static class RouterStore {
        List<RouterState> states = List.of(
                new RouterState("localhost:8095"),
                new RouterState("123.67.234.67"),
                new RouterState("localhost:8096"),
                new RouterState("123.67.234.68")
        );

        public List<RouterState> getCachedRecords() {
            return states;
        }
    }

    public static class MountTableManager {
        public MountTableManager(String address) {}

        public boolean refresh() {
            return ThreadLocalRandom.current().nextBoolean();
        }
    }
}
