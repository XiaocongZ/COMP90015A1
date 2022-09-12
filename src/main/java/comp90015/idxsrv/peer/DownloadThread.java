package comp90015.idxsrv.peer;

import comp90015.idxsrv.filemgr.FileMgr;
import comp90015.idxsrv.server.IndexElement;

/**
 * One thread per file downloading.
 * One thread connects to all seeding peers, as disk io is considerably faster than net io.
 */
public class DownloadThread implements Runnable{
    private FileMgr fileMgr;

    private IndexElement[] indexElementArray;

    public DownloadThread(FileMgr fileMgr, IndexElement[] indexElementArray){
        this.fileMgr = fileMgr;
        this.indexElementArray = indexElementArray;
    }




    @Override
    public void run() {

    }
}
