package comp90015.idxsrv.peer;

import comp90015.idxsrv.textgui.PeerGUI;

import java.net.Socket;
import java.util.HashMap;
import java.util.concurrent.LinkedBlockingDeque;

/**
 * Manage all requests
 *
 */
public class ShareMgrThread implements Runnable{

    private PeerGUI tgui;
    private final LinkedBlockingDeque<Socket> incomingConnections;

    private HashMap<String,ShareRecord> shareRecords;


    public ShareMgrThread(LinkedBlockingDeque<Socket> incomingConnections, PeerGUI tgui, HashMap<String,ShareRecord> shareRecords){
        this.tgui = tgui;
        this.incomingConnections = incomingConnections;
        this.shareRecords = shareRecords;
    }

    @Override
    public void run() {
       while(!Thread.interrupted()){
           //poll may return null if queue is empty
            Socket socket = incomingConnections.poll();
            if(socket != null){
                Thread sharingThread= new Thread(new SharingThread(socket, tgui, shareRecords));

                tgui.logDebug("ShareMgr start a sharing thread");
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
