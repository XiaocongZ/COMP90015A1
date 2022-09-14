package comp90015.idxsrv.peer;

import comp90015.idxsrv.textgui.PeerGUI;

import java.net.Socket;
import java.util.concurrent.LinkedBlockingDeque;

/**
 * Manage all requests
 *
 */
public class ShareMgrThread implements Runnable{

    private PeerGUI tgui;
    private final LinkedBlockingDeque<Socket> incomingConnections;


    public ShareMgrThread(LinkedBlockingDeque<Socket> incomingConnections, PeerGUI tgui){
        this.tgui = tgui;
        this.incomingConnections = incomingConnections;

    }

    @Override
    public void run() {
       while(!Thread.interrupted()){
           //poll may return null if queue is empty
            Socket socket = incomingConnections.poll();
            if(socket != null){
                Thread sharingThread= new Thread(new SharingThread(socket, tgui));
                sharingThread.start();
            }
       }
       shutdown();
    }

    /**
     * no need for shutdown if we make this thread daemon.
     */
    public void shutdown(){

    }
}
