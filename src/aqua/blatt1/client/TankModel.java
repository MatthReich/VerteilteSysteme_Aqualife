package aqua.blatt1.client;

import aqua.blatt1.common.Direction;
import aqua.blatt1.common.FishModel;
import aqua.blatt1.common.msgtypes.SnapshotCollector;

import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

enum RecordingModeValues {IDLE, LEFT, RIGHT, BOTH}

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
    private Map<String, InetSocketAddress> homeAgent;

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
        this.homeAgent = new HashMap<>();
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
            homeAgent.put(fish.getId(), null);
        }
    }

    synchronized void receiveFish(FishModel fish) {
        fish.setToStart();
        fishies.add(fish);
        if (homeAgent.containsKey(fish.getId())) {
            homeAgent.put(fish.getId(), null);
        } else {
            forwarder.sendNameResolutionRequest(fish.getTankId(), fish.getId());
        }

        if ((recordingMode == RecordingModeValues.BOTH) ||
                (fish.getDirection() == Direction.LEFT && recordingMode == RecordingModeValues.RIGHT) ||
                (fish.getDirection() == Direction.RIGHT && recordingMode == RecordingModeValues.LEFT)) {
            recievingFishies++;
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
                    if ((recordingMode == RecordingModeValues.BOTH) ||
                            (fish.getDirection() == Direction.LEFT && recordingMode == RecordingModeValues.LEFT) ||
                            (fish.getDirection() == Direction.RIGHT && recordingMode == RecordingModeValues.RIGHT)) {
                        recievingFishies--;
                    }
                    forwarder.handOff(fish, this);
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

    public synchronized void receiveToken() {
        hasToken = Boolean.TRUE;
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                hasToken = Boolean.FALSE;
                forwarder.sendToken(leftNeighbor);
            }
        }, 2000);
    }

    public synchronized boolean hasToken() {
        return hasToken;
    }

    public synchronized void finish() {
        forwarder.deregister(id);
    }

    public synchronized void receiveMarker(InetSocketAddress socketAddress) {
        initiateSnapshot(socketAddress);
    }

    public synchronized void initiateSnapshot(InetSocketAddress socketAddress) {
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

    private  synchronized void localSnapshot() {
        this.forEach(fish -> {
            if (!fish.leavingTank())
                sumFishies++;
        });
    }

    public synchronized boolean isCapture() {
        return capture;
    }

    public synchronized void setCapture() {
        this.capture = Boolean.FALSE;
    }

    public synchronized int getSnapshot() {
        return snapshot;
    }

    public synchronized void receiveCollector(SnapshotCollector collector) {
        collector.totalFishies += snapshot;
        if (initiator) {
            snapshot = collector.totalFishies;
            capture = Boolean.TRUE;
            initiator = Boolean.FALSE;
        } else {
            forwarder.sendSnapshotCollector(leftNeighbor, collector);
        }
    }

    public synchronized void locateFishGlobally(String fishId) {
        if (homeAgent.containsKey(fishId)) {
            if (homeAgent.get(fishId) == null) {
                locateFishLocally(fishId);
            } else {
                forwarder.sendLocationRequest(homeAgent.get(fishId), fishId);
            }
        }
    }

    public synchronized void receiveNameResolutionResponse(InetSocketAddress tankAddress, String requestId) {
        forwarder.sendLocationUpdate(tankAddress, requestId);
    }

    public synchronized void locateFishLocally(String fishId) {
        fishies.forEach(fishm->{
            if(fishId.equals(fishm.getId()))
                fishm.toggle();
        });
    }

    public synchronized void receiveLocationUpdate(String fishId, InetSocketAddress location) {
        homeAgent.put(fishId, location);
    }
}
