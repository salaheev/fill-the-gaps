package com.fillthegaps.study.salakheev.exam_2;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static com.fillthegaps.study.salakheev.exam_2.Others.*;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.stream.Collectors.toUnmodifiableList;

public class MountTableRefresherService {

    private RouterStore routerStore = new RouterStore();
    private long cacheUpdateTimeout;

    /**
     * All router admin clients cached. So no need to create the client again and
     * again. Router admin address(host:port) is used as key to cache RouterClient
     * objects.
     */
    private Others.LoadingCache<String, RouterClient> routerClientsCache;

    /**
     * Removes expired RouterClient from routerClientsCache.
     */
    private ScheduledExecutorService clientCacheCleanerScheduler;

    public void serviceInit() {
        long routerClientMaxLiveTime = 1L;
        this.cacheUpdateTimeout = 10L;
        routerClientsCache = new Others.LoadingCache<>();
        routerStore.getCachedRecords().stream().map(RouterState::getAdminAddress)
                .forEach(addr -> routerClientsCache.add(addr, new RouterClient()));

        initClientCacheCleaner(routerClientMaxLiveTime);
    }

    public void serviceStop() {
        clientCacheCleanerScheduler.shutdown();
        // remove and close all admin clients
        routerClientsCache.cleanUp();
    }

    private void initClientCacheCleaner(long routerClientMaxLiveTime) {
        ThreadFactory tf = new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread();
                t.setName("MountTableRefresh_ClientsCacheCleaner");
                t.setDaemon(true);
                return t;
            }
        };

        clientCacheCleanerScheduler =
                Executors.newSingleThreadScheduledExecutor(tf);
        /*
         * When cleanUp() method is called, expired RouterClient will be removed and
         * closed.
         */
        clientCacheCleanerScheduler.scheduleWithFixedDelay(
                () -> routerClientsCache.cleanUp(), routerClientMaxLiveTime,
                routerClientMaxLiveTime, MILLISECONDS);
    }

    /**
     * Refresh mount table cache of this router as well as all other routers.
     */
    public void refresh() {

        List<RouterState> cachedRecords = routerStore.getCachedRecords();
        List<MountTableRefresherThread> refreshThreads = new ArrayList<>();
        for (RouterState routerState : cachedRecords) {
            String adminAddress = routerState.getAdminAddress();
            if (adminAddress == null || adminAddress.length() == 0) {
                // this router has not enabled router admin.
                continue;
            }
            if (isLocalAdmin(adminAddress)) {
                /*
                 * Local router's cache update does not require RPC call, so no need for
                 * RouterClient
                 */
                refreshThreads.add(getLocalRefresher(adminAddress));
            } else {
                refreshThreads.add(new MountTableRefresherThread(
                        new MountTableManager(adminAddress), adminAddress));
            }
        }
        if (!refreshThreads.isEmpty()) {
            invokeRefresh(refreshThreads);
        }
    }

    protected MountTableRefresherThread getLocalRefresher(String adminAddress) {
        return new MountTableRefresherThread(new MountTableManager("local"), adminAddress);
    }

    private void removeFromCache(String adminAddress) {
        routerClientsCache.invalidate(adminAddress);
    }

    private void invokeRefresh(List<MountTableRefresherThread> refreshThreads) {
        List<CompletableFuture<MountTableRefresherThread>> futures = refreshThreads
                .stream()
                .map(thread -> CompletableFuture.supplyAsync(() -> {
                    thread.refresh();
                    return thread;
                })).collect(toUnmodifiableList());
        CompletableFuture
                .allOf(futures.toArray(CompletableFuture<?>[]::new))
                .orTimeout(cacheUpdateTimeout, MILLISECONDS)
                .thenApply(res -> futures.stream()
                        .map(CompletableFuture::join)
                        .filter(t -> !t.isSuccess())
                        .collect(toUnmodifiableList())
                ).whenComplete((res, thr) -> {
                    if (thr != null) {
                        Throwable cause = thr.getCause();
                        if (cause instanceof InterruptedException) {
                            System.out.println("Mount table cache refresher was interrupted.");
                        }
                        if (cause instanceof TimeoutException) {
                            System.out.println("Not all router admins updated their cache");
                        }
                    }
                }).thenAccept(res -> logResult(res, futures.size()))
                .join();
    }

    private boolean isLocalAdmin(String adminAddress) {
        return adminAddress.contains("local");
    }

    private void logResult(List<MountTableRefresherThread> failedList, int initSize) {
        failedList
                .forEach(f -> // remove RouterClient from cache so that new client is created
                        removeFromCache(f.getAdminAddress()));
        System.out.printf(
                "Mount table entries cache refresh successCount=%d,failureCount=%d%n",
                initSize - failedList.size(), failedList.size());
    }

    public static void main(String[] args) throws InterruptedException {
        MountTableRefresherService service = new MountTableRefresherService();
        service.serviceInit();

        service.refresh();

        Thread.sleep(2000);
        System.out.println("done");
        service.serviceStop();
    }
}
