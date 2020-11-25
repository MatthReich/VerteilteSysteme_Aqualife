package aqua.blatt1.client;

import aqua.blatt1.common.Direction;
import aqua.blatt1.common.FishModel;
import aqua.blatt1.common.msgtypes.SnapshotCollector;

import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

enum RecordingModeValues {IDLE, LEFT, RIGHT, BOTH}

enum ReferenceStates {HERE, LEFT, RIGHT}

public class TankModel extends Observable implements Iterable<FishModel> {

    public static final int WIDTH = 600;
    public static final int HEIGHT = 350;
    protected static final int MAX_FISHIES = 5;
    protected static final Random rand = new Random();
    protected volatile String id;
    protected final Set<FishModel> fishies;
    protected int fishCounter = 0;
    protected final ClientCommunicator.ClientForwarder forwarder;
    private InetSocketAddress leftNeighbor;
    private InetSocketAddress rightNeighbor;
    private boolean hasToken;
    private final Timer timer = new Timer();
    private RecordingModeValues recordingMode = RecordingModeValues.IDLE;
    private boolean initiator = Boolean.FALSE;
    private int sumFishies = 0;
    private int recievingFishies = 0;
    private Map<FishModel, ReferenceStates> referenceFishies;

    private int snapshot = 0;

    private boolean capture = Boolean.FALSE;

    public InetSocketAddress getLeftNeighbor() {
        return leftNeighbor;
    }

    public void setLeftNeighbor(InetSocketAddress leftNeighbor) {
        this.leftNeighbor = leftNeighbor;
    }

    public InetSocketAddress getRightNeighbor() {
        return rightNeighbor;
    }

    public void setRightNeighbor(InetSocketAddress rightNeighbor) {
        this.rightNeighbor = rightNeighbor;
    }


    public TankModel(ClientCommunicator.ClientForwarder forwarder) {
        this.fishies = Collections.newSetFromMap(new ConcurrentHashMap<FishModel, Boolean>());
        this.forwarder = forwarder;
        this.referenceFishies = new HashMap<>();
    }

    synchronized void onRegistration(String id) {
        this.id = id;
        newFish(WIDTH - FishModel.getXSize(), rand.nextInt(HEIGHT - FishModel.getYSize()));
    }

    public synchronized void newFish(int x, int y) {
        if (fishies.size() < MAX_FISHIES) {
            x = Math.min(x, WIDTH - FishModel.getXSize() - 1);
            y = Math.min(y, HEIGHT - FishModel.getYSize());

            FishModel fish = new FishModel("fish" + (++fishCounter) + "@" + getId(), x, y,
                    rand.nextBoolean() ? Direction.LEFT : Direction.RIGHT);

            fishies.add(fish);
            referenceFishies.put(fish, ReferenceStates.HERE);
        }
    }

    synchronized void receiveFish(FishModel fish) {
        fish.setToStart();
        fishies.add(fish);
        referenceFishies.put(fish, ReferenceStates.HERE);

        switch (recordingMode) {
            case IDLE:
                break;
            case BOTH:
                recievingFishies++;
                break;
            case LEFT:
                if (fish.getDirection() == Direction.RIGHT) {
                    recievingFishies++;
                }
                break;
            case RIGHT:
                if (fish.getDirection() == Direction.LEFT) {
                    recievingFishies++;
                }
                break;
        }
    }

    public String getId() {
        return id;
    }

    public synchronized int getFishCounter() {
        return fishCounter;
    }

    public synchronized Iterator<FishModel> iterator() {
        return fishies.iterator();
    }

    private synchronized void updateFishies() {
        for (Iterator<FishModel> it = iterator(); it.hasNext(); ) {
            FishModel fish = it.next();

            fish.update();

            if (fish.hitsEdge()) {
                if (!hasToken()) {
                    fish.reverse();
                } else {
                    switch (recordingMode) {
                        case IDLE:
                            break;
                        case BOTH:
                            recievingFishies--;
                            break;
                        case LEFT:
                            if (fish.getDirection() == Direction.LEFT) {
                                recievingFishies--;
                            }
                            break;
                        case RIGHT:
                            if (fish.getDirection() == Direction.RIGHT) {
                                recievingFishies--;
                            }
                            break;
                    }
                    forwarder.handOff(fish, this);
                    referenceFishies.put(fish, fish.getDirection() == Direction.LEFT ? ReferenceStates.LEFT : ReferenceStates.RIGHT);
                }
            }

            if (fish.disappears())
                it.remove();
        }
    }

    private synchronized void update() {
        updateFishies();
        setChanged();
        notifyObservers();
    }

    protected void run() {
        forwarder.register();

        try {
            while (!Thread.currentThread().isInterrupted()) {
                update();
                TimeUnit.MILLISECONDS.sleep(10);
            }
        } catch (InterruptedException consumed) {
            Thread.currentThread().interrupt();
        }
    }

    public void receiveToken() {
        hasToken = Boolean.TRUE;
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                hasToken = Boolean.FALSE;
                forwarder.sendToken(leftNeighbor);
            }
        }, 2000);
    }

    public boolean hasToken() {
        return hasToken;
    }

    public synchronized void finish() {
        forwarder.deregister(id);
    }

    public void receiveMarker(InetSocketAddress socketAddress) {
        initiateSnapshot(socketAddress);
    }

    public void initiateSnapshot(InetSocketAddress socketAddress) {
        if (recordingMode == RecordingModeValues.IDLE) {
            if (socketAddress == null) {
                initiator = Boolean.TRUE;
                recordingMode = RecordingModeValues.BOTH;
            } else if (socketAddress.equals(leftNeighbor)) {
                recordingMode = RecordingModeValues.RIGHT;
            } else if (socketAddress.equals(rightNeighbor)) {
                recordingMode = RecordingModeValues.LEFT;
            }
            sumFishies = 0;
            recievingFishies = 0;
            localSnapshot();
            forwarder.sendSnapshotMarker(leftNeighbor);
            forwarder.sendSnapshotMarker(rightNeighbor);
        } else {
            if (recordingMode == RecordingModeValues.BOTH) {
                if (socketAddress.equals(leftNeighbor)) {
                    recordingMode = RecordingModeValues.RIGHT;
                } else if (socketAddress.equals(rightNeighbor)) {
                    recordingMode = RecordingModeValues.LEFT;
                }
            } else if ((recordingMode == RecordingModeValues.LEFT && socketAddress.equals(leftNeighbor)) ||
                    (recordingMode == RecordingModeValues.RIGHT && socketAddress.equals(rightNeighbor))) {
                recordingMode = RecordingModeValues.IDLE;
                snapshot = sumFishies + recievingFishies;
                if (initiator) {
                    forwarder.sendSnapshotCollector(leftNeighbor, new SnapshotCollector());
                }
            }
        }
    }

    private void localSnapshot() {
        this.forEach(fish -> {
            if (!fish.leavingTank())
                sumFishies++;
        });
    }

    public boolean isCapture() {
        return capture;
    }

    public void setCapture() {
        this.capture = Boolean.FALSE;
    }

    public int getSnapshot() {
        return snapshot;
    }

    public void receiveCollector(SnapshotCollector collector) {
        collector.totalFishies += snapshot;
        if (initiator) {
            snapshot = collector.totalFishies;
            capture = Boolean.TRUE;
            initiator = Boolean.FALSE;
        } else {
            forwarder.sendSnapshotCollector(leftNeighbor, collector);
        }
    }

    public void locateFishGlobally(String fishId) {
        for (var entryPair : referenceFishies.entrySet()) {
            FishModel fish = entryPair.getKey();
            ReferenceStates reference = entryPair.getValue();
            if (fish.getId().equals(fishId)) {
                if (fishies.contains(fish) && reference == ReferenceStates.HERE) {
                    fish.toggle();
                } else {
                    forwarder.sendLocationRequest(reference == ReferenceStates.RIGHT ? rightNeighbor : leftNeighbor, fishId);
                }
                break;
            }
        }
    }

    public void receiveNameRequest(InetSocketAddress requestedTank, String requestId) {

    }

}
