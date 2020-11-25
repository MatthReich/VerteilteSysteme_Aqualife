package aqua.blatt1.client;

import java.net.InetSocketAddress;

import aqua.blatt1.common.Direction;
import aqua.blatt1.common.msgtypes.*;
import messaging.Endpoint;
import messaging.Message;
import aqua.blatt1.common.FishModel;
import aqua.blatt1.common.Properties;

public class ClientCommunicator {
    private final Endpoint endpoint;

    public ClientCommunicator() {
        endpoint = new Endpoint();
    }

    public class ClientForwarder {
        private final InetSocketAddress broker;

        private ClientForwarder() {
            this.broker = new InetSocketAddress(Properties.HOST, Properties.PORT);
        }

        public void register() {
            endpoint.send(broker, new RegisterRequest());
        }

        public void deregister(String id) {
            endpoint.send(broker, new DeregisterRequest(id));
        }

        public void handOff(FishModel fish, TankModel tankModel) {
            if (fish.getDirection() == Direction.RIGHT) {
                endpoint.send(tankModel.getRightNeighbor(), new HandoffRequest(fish));

            } else if (fish.getDirection() == Direction.LEFT) {
                endpoint.send(tankModel.getLeftNeighbor(), new HandoffRequest(fish));
            }
        }

        public void sendToken(InetSocketAddress neighbor) {
            endpoint.send(neighbor, new Token());
        }

        public void sendSnapshotMarker(InetSocketAddress neighbor) {
            endpoint.send(neighbor, new SnapshotMarker());
        }

        public void sendSnapshotCollector(InetSocketAddress neighbor, SnapshotCollector collector) {
            endpoint.send(neighbor, collector);
        }

        public void sendLocationRequest(InetSocketAddress neighbor, String fishId) {
            endpoint.send(neighbor, new LocationRequest(fishId));
        }

    }

    public class ClientReceiver extends Thread {
        private final TankModel tankModel;

        private ClientReceiver(TankModel tankModel) {
            this.tankModel = tankModel;
        }

        @Override
        public void run() {
            while (!isInterrupted()) {
                Message msg = endpoint.blockingReceive();

                if (msg.getPayload() instanceof Token) {
                    tankModel.receiveToken();
                }

                if (msg.getPayload() instanceof RegisterResponse) {
                    tankModel.onRegistration(((RegisterResponse) msg.getPayload()).getId());
                }

                if (msg.getPayload() instanceof HandoffRequest) {
                    tankModel.receiveFish(((HandoffRequest) msg.getPayload()).getFish());
                }

                if (msg.getPayload() instanceof NeighborUpdate) {
                    tankModel.setLeftNeighbor(((NeighborUpdate) msg.getPayload()).getFishL());
                    tankModel.setRightNeighbor(((NeighborUpdate) msg.getPayload()).getFishR());
                }

                if (msg.getPayload() instanceof SnapshotMarker) {
                    tankModel.receiveMarker(msg.getSender());
                }

                if (msg.getPayload() instanceof SnapshotCollector) {
                    tankModel.receiveCollector((SnapshotCollector) msg.getPayload());
                }

                if (msg.getPayload() instanceof  LocationRequest) {
                    tankModel.locateFishGlobally((((LocationRequest) msg.getPayload()).fishId));
                }

            }
            System.out.println("Receiver stopped.");
        }
    }

    public ClientForwarder newClientForwarder() {
        return new ClientForwarder();
    }

    public ClientReceiver newClientReceiver(TankModel tankModel) {
        return new ClientReceiver(tankModel);
    }

}
