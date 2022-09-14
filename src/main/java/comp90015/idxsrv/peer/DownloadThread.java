package comp90015.idxsrv.peer;

import comp90015.idxsrv.filemgr.FileDescr;
import comp90015.idxsrv.filemgr.FileMgr;
import comp90015.idxsrv.message.*;
import comp90015.idxsrv.server.IndexElement;
import comp90015.idxsrv.textgui.PeerGUI;

import java.io.IOException;
import java.net.Socket;
import java.util.*;

/**
 * One thread per file downloading.
 * One thread connects to all seeding peers, as disk io is considerably faster than net io.
 */
public class DownloadThread implements Runnable{
    private FileMgr fileMgr;

    private List<IndexElement> indexElementList;

    private ArrayList<SocketMgr> socketMgrList;

    private ArrayList<SocketMgr> toRead;

    private int nextBlock;

    private PeerGUI tgui;

    public DownloadThread(FileMgr fileMgr, IndexElement[] indexElementArray, PeerGUI tgui){
        this.fileMgr = fileMgr;
        this.socketMgrList = new ArrayList<>();
        this.indexElementList = Arrays.asList(indexElementArray);
        this.toRead = new ArrayList<SocketMgr>();
        this.nextBlock = 0;
        this.tgui = tgui;
    }




    @Override
    public void run() {

        //set up socket mgr for all peer connections
        for(IndexElement element: indexElementList){
            try{
                Socket socket = new Socket(element.ip, element.port);
                //tgui.logDebug("DownloadThread connect " + element.ip + element.port);
                socketMgrList.add(new SocketMgr(socket));
            }
            catch (IOException e) {
                tgui.logDebug(e.getMessage());
            }
        }
        if(socketMgrList.isEmpty()){
            tgui.logError("All socket connections failed");
            return;
        }


        //tgui.logDebug("fileMgr.getFileDescr before");
        FileDescr fileDescr = fileMgr.getFileDescr();
        //tgui.logDebug("fileMgr.getFileDescr after");
        while(!fileMgr.isComplete()){
            //tgui.logDebug("fileMgr.isComplete() not true");
            ListIterator<SocketMgr> socketMgrListIterator = socketMgrList.listIterator();
            //send message
            while(socketMgrListIterator.hasNext()){
                SocketMgr sMgr = socketMgrListIterator.next();
                int unavailableBlock = getUnavailable();
                if(unavailableBlock == -1){
                    //seems no block to request, but need to confirm
                    break;
                }
                //filenames must be the same, guaranteed by lookup reply

                BlockRequest bReq = new BlockRequest(indexElementList.get(0).filename , fileDescr.getFileMd5(), unavailableBlock);
                tgui.logDebug(indexElementList.get(0).filename + unavailableBlock);
                try {
                    sMgr.writeMsg(bReq);
                    tgui.logDebug("sent BlockRequest" + bReq.toString());
                    toRead.add(sMgr);
                } catch (IOException e) {
                    //remove socket if write fails
                    tgui.logDebug("write block req failed");
                    socketMgrListIterator.remove();

                    if(socketMgrList.isEmpty()){
                        tgui.logError("File:" + indexElementList.get(0).filename + "No available peer connection");
                        return;
                    }
                }
            }
            tgui.logDebug("read message");
            ListIterator<SocketMgr> toReadListIterator = toRead.listIterator();
            while(toReadListIterator.hasNext()){
                SocketMgr sMgr = socketMgrListIterator.next();
                Message msg;
                try {

                    msg = sMgr.readMsg();

                } catch (IOException e) {
                    tgui.logError(e.getMessage());
                    continue;
                } catch (JsonSerializationException e) {
                    tgui.logError(e.getMessage());
                    continue;
                }

                if(msg.getClass() == BlockReply.class){
                    tgui.logDebug("read Block" + msg.toString());
                    BlockReply bRep = (BlockReply) msg;
                    try {
                        fileMgr.writeBlock(bRep.blockIdx, bRep.getBytes());
                    } catch (IOException e) {
                        //end thread if cannot access file
                        tgui.logError(e.getMessage());
                        return;
                    }
                } else if (msg.getClass() == ErrorMsg.class) {
                    //do nothing here by design
                    tgui.logDebug(msg.toString());
                }

            }
            //clear toRead after read
            toRead = new ArrayList<SocketMgr>();

        }
        //TODO send goodbye to all connection
        Goodbye gdbye = new Goodbye();
        for(SocketMgr sMgr: socketMgrList){
            try {
                sMgr.writeMsg(gdbye);
                sMgr.close();
            } catch (IOException e) {
                tgui.logDebug("write goodbye/close socket failed, no big deal");
            }
        }

        try {
            fileMgr.closeFile();
        } catch (IOException e) {
            tgui.logError(e.getMessage());
            return;
        }

        tgui.logInfo("DownloadThread return");
    }


    private int getUnavailable(){
        if(fileMgr.isComplete()){
            return -1;
        }
        while(fileMgr.isBlockAvailable(nextBlock)){
            nextBlock = (nextBlock + 1) % fileMgr.getFileDescr().getNumBlocks();
        }
        return nextBlock;

    }
}
