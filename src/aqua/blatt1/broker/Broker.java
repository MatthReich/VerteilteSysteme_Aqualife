package aqua.blatt1.broker;

import aqua.blatt1.client.TankModel;
import aqua.blatt1.common.Direction;
import aqua.blatt1.common.Properties;
import aqua.blatt1.common.msgtypes.*;
import aqua.blatt2.broker.PoisonPill;
import messaging.Endpoint;
import messaging.Message;

import javax.swing.*;
import java.net.InetSocketAddress;
import java.sql.Timestamp;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class Broker {
    private static final int PORT = Properties.PORT;
    private final Endpoint endpoint;
    private final ClientCollection<InetSocketAddress> clientList;
    boolean isRunning;
    private final java.util.Timer timer = new Timer();
    private final int DEFAULT_LEASE = 10000;


    public Broker() {
        endpoint = new Endpoint(PORT);
        clientList = new ClientCollection<>();
    }

    public static void main(String[] args) {
        Broker broker = new Broker();
        broker.broker();
    }

    public void broker() {
        ExecutorService executorService = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
        isRunning = true;
        /*
            executorService.execute(() -> {
                JOptionPane.showMessageDialog(null, "Press ->OK<- when you want to stop");
                isRunning = false;
            });
        */
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                for (int i = 0; i < clientList.size(); i++) {
                    Timestamp registered = clientList.getTimeStamp(i);
                    InetSocketAddress client = clientList.getClient(i);
                    if (System.currentTimeMillis() - DEFAULT_LEASE > registered.getTime()) {
                        deregister(client);
                    }
                }
            }
        }, 0, 5000);
        while (isRunning) {
            Message message = endpoint.blockingReceive();
            if (message.getPayload() instanceof PoisonPill) {
                isRunning = false;
            } else {
                executorService.execute(new Runnable() {
                    final ReadWriteLock lock = new ReentrantReadWriteLock();

                    @Override
                    public void run() {
                        if (message.getPayload() instanceof RegisterRequest) {
                            lock.writeLock().lock();
                            register(message.getSender());
                            lock.writeLock().unlock();
                        } else if (message.getPayload() instanceof DeregisterRequest) {
                            lock.writeLock().lock();
                            deregister(message.getSender());
                            lock.writeLock().unlock();
                        } else if (message.getPayload() instanceof HandoffRequest) {
                            lock.readLock().lock();
                            handoffFish(((HandoffRequest) message.getPayload()), message.getSender());
                            lock.readLock().unlock();
                        } else if (message.getPayload() instanceof NameResolutionRequest) {
                            lock.writeLock().lock();
                            resolutionRequest((NameResolutionRequest) message.getPayload(), message.getSender());
                            lock.writeLock().unlock();
                        }
                    }
                });
            }
        }
        executorService.shutdown();
    }

    private void register(InetSocketAddress clientAddress) {
        if (clientList.indexOf(clientAddress) < 0) {
            registerNew(clientAddress);
        } else {
            registerUpdate(clientAddress);
        }
    }

    private void registerUpdate(InetSocketAddress clientAddress) {
        int tankIndex = clientList.indexOf(clientAddress);

        clientList.update(tankIndex, new Timestamp(System.currentTimeMillis()));
        endpoint.send(clientAddress, new RegisterResponse(clientList.getID(tankIndex)));
    }

    private void registerNew(InetSocketAddress clientAddress) {
        final String id = "tank" + clientList.size();
        clientList.add(id, clientAddress, new Timestamp(System.currentTimeMillis()));

        InetSocketAddress neighborL = clientList.getLeftNeighorOf(clientList.indexOf(clientAddress));
        InetSocketAddress neighborR = clientList.getRightNeighorOf(clientList.indexOf(clientAddress));

        if (clientList.size() == 1) {
            endpoint.send(clientAddress, new NeighborUpdate(clientAddress, clientAddress));
            endpoint.send(clientAddress, new Token());
        } else {
            endpoint.send(clientAddress, new NeighborUpdate(neighborL, neighborR));
            endpoint.send(neighborL, new NeighborUpdate(clientList.getLeftNeighorOf(clientList.indexOf(neighborL)), clientAddress));
            endpoint.send(neighborR, new NeighborUpdate(clientAddress, clientList.getRightNeighorOf(clientList.indexOf(neighborR))));
        }

        endpoint.send(clientAddress, new RegisterResponse(id));
    }

    private void deregister(InetSocketAddress clientAddress) {
        InetSocketAddress neighborL = clientList.getLeftNeighorOf(clientList.indexOf(clientAddress));
        InetSocketAddress neighborR = clientList.getRightNeighorOf(clientList.indexOf(clientAddress));

        endpoint.send(neighborL, new NeighborUpdate(clientList.getLeftNeighorOf(clientList.indexOf(neighborL)), neighborR));
        endpoint.send(neighborR, new NeighborUpdate(neighborL, clientList.getRightNeighorOf(clientList.indexOf(neighborR))));

        clientList.remove(clientList.indexOf(clientAddress));
    }

    private void handoffFish(HandoffRequest handoffRequest, InetSocketAddress clientAddress) {
        InetSocketAddress neighbor;
        if (handoffRequest.getFish().getDirection() == Direction.LEFT) {
            neighbor = clientList.getLeftNeighorOf(clientList.indexOf(clientAddress));
        } else {
            neighbor = clientList.getRightNeighorOf(clientList.indexOf(clientAddress));
        }
        endpoint.send(neighbor, handoffRequest);
    }

    private void resolutionRequest(NameResolutionRequest nameResolutionRequest, InetSocketAddress sender) {
        int tankIndex = clientList.indexOf(nameResolutionRequest.requestedTank);
        if (isPresent(tankIndex)) {
            InetSocketAddress tankAddress = clientList.getClient(tankIndex);
            endpoint.send(sender, new NameResolutionResponse(tankAddress, nameResolutionRequest.requestId));
        }
    }

    private boolean isPresent(int tankIndex) {
        return tankIndex > 0;
    }

}