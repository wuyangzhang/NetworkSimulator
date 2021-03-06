/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.rutgers.winlab.simulator.gaming.videostream;

import edu.rutgers.winlab.simulator.core.EventQueue;
import edu.rutgers.winlab.simulator.core.ISerializable;
import edu.rutgers.winlab.simulator.core.Serial;
import edu.rutgers.winlab.simulator.core.SimulatorQueue;
import edu.rutgers.winlab.simulator.gaming.common.GameClient;
import edu.rutgers.winlab.simulator.gaming.common.Frame;
import edu.rutgers.winlab.simulator.gaming.common.Packet;
import edu.rutgers.winlab.simulator.gaming.common.UserEvent;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;

/**
 *
 * @author ubuntu
 */
public class GameServer extends edu.rutgers.winlab.simulator.gaming.common.GameServer {

    // here, the processing time should be larger than that of traditional gaming
    // the game event contains the client game logic (user event -> game event)
    // and the server game logic (game event -> update)
    // I'm adding them up nows
    private static long getGameEventProcessingTime() {
        return 50 * EventQueue.MICRO_SECOND;
    }

    private static int getFrameSize(Iterable<UserEvent> pendingEvents) {
        // 100Mb/s / 60f/s
        return 100 * ISerializable.M_BIT / 60;
    }

    private class GameInfo {

        public HashSet<String> gameClients = new HashSet<>();
        public HashMap<Integer, UserEvent> pendingUEs = new HashMap<>();
    }

    private final HashMap<String, GameInfo> _gameClients = new HashMap<>();

    public GameServer(String name, SimulatorQueue<ISerializable> innerIncomingQueue) {
        super(name, innerIncomingQueue);
    }

    @Override
    public void addGameClient(String game, String client) {
        GameInfo gi = _gameClients.get(game);
        if (gi == null) {
            _gameClients.put(game, gi = new GameInfo());
            //start game thread
            new Serial<String>(this::_beforeServerGameLogic, game);
        }
        gi.gameClients.add(client);
    }

    public void removePendingUE(String game, int id) {
        GameInfo gi = _gameClients.get(game);
        gi.pendingUEs.remove(id);
    }
    
    public int getPendingUESize(String name) {
        return _gameClients.get(name).pendingUEs.size();
    }

    @Override
    protected long _processPacket(Serial<ISerializable> s, ISerializable param) {
//        System.out.printf("[%d] SR %s received %s%n", EventQueue.now(), getName(), param);
        Packet pkt = (Packet) param;
        UserEvent ue = (UserEvent) pkt.getPayload();
        String dst = pkt.getDst();

        // it should be there, so gi should not be null
        GameInfo gi = _gameClients.get(dst);
        gi.pendingUEs.put(ue.getId(), ue);
        return getGameEventProcessingTime();
    }

    @Override
    protected void _handleFailedPacket(ISerializable packet) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    //wait a frame before real logic starts
    private long _beforeServerGameLogic(Serial<String> s, String gameName) {
        s.addEvent(this::_serverGameLogic, gameName);
        return GameClient.FRAME_INTERVAL;
    }

    private long _serverGameLogic(Serial<String> s, String gameName) {
        if (!isRunning()) {
            return 0;
        }
        GameInfo gi = _gameClients.get(gameName);
//        System.out.printf("[%d] SH %s[%s] UEs=%s%n", EventQueue.now(), getName(), gameName, gi.pendingUEs);
        Frame f = new Frame(getFrameSize(gi.pendingUEs.values()), gi.pendingUEs.values());

        gi.gameClients.forEach(c -> {
            Packet pkt = new Packet(gameName, c, f);
            EventQueue.addEvent(EventQueue.now() + getGameEventProcessingTime(),
                    this::_sendFrame, pkt);
        });

        s.addEvent(this::_serverGameLogic, gameName);
        return GameClient.FRAME_INTERVAL;
    }

    private void _sendFrame(Object... parameters) {
//        System.out.printf("[%d] SS %s send %s%n", EventQueue.now(), getName(), parameters[0]);
        sendPacket((Packet) parameters[0], false);
    }
}
