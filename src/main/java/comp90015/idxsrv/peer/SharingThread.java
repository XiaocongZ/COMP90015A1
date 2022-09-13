package comp90015.idxsrv.peer;

import comp90015.idxsrv.filemgr.BlockUnavailableException;
import comp90015.idxsrv.message.*;
import comp90015.idxsrv.textgui.PeerGUI;

import java.io.*;
import java.net.Socket;

import static java.lang.Thread.interrupted;

public class SharingThread implements Runnable{
    private PeerGUI tgui;
    private Socket socket;

    private SocketMgr socketMgr;

    public SharingThread(Socket socket, PeerGUI tgui){
        this.tgui = tgui;
        this.socket = socket;

    }

    @Override
    public void run() {
        try {
            this.socketMgr = new SocketMgr(socket);
        } catch (IOException e) {
            tgui.logDebug(e.getMessage());
        }
        try {

            while (!interrupted()) {
                Message msg = socketMgr.readMsg();
                if(msg.getClass() == BlockRequest.class){
                    BlockRequest bReq = (BlockRequest) msg;
                    // reply
                    ShareRecord sRec = tgui.getShareRecords().get(bReq.fileName);
                    if(sRec == null){
                        ErrorMsg eRep = new ErrorMsg("No share record");
                        socketMgr.writeMsg(eRep);
                        continue;
                    }
                    if(!sRec.status.equals("Seeding")){
                        ErrorMsg eRep = new ErrorMsg("Share Record Status not Seeding");
                        socketMgr.writeMsg(eRep);
                        continue;
                    }
                    byte[] bytes = null;
                    try{
                        bytes = sRec.fileMgr.readBlock(bReq.blockIdx);
                    }catch(BlockUnavailableException e){
                        socketMgr.writeMsg(new ErrorMsg(e.getMessage()));
                    }
                    BlockReply bRep = new BlockReply(bReq.fileName, bReq.fileMd5, bReq.blockIdx, bytes);
                    socketMgr.writeMsg(bRep);
                } else if (msg.getClass() == Goodbye.class) {
                    break;
                }else {
                    throw new IOException("Message unidentified");
                }
            }
        }
        catch(JsonSerializationException e){
            Thread.currentThread().interrupt();
        } catch(IOException e){
            Thread.currentThread().interrupt();
        }
        try {
            socketMgr.close();
        } catch (IOException e) {
            tgui.logDebug("close socket failed, no big deal");
        }
    }

}
