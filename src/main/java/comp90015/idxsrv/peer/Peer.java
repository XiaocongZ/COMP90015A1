package comp90015.idxsrv.peer;


import java.io.*;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.NoSuchAlgorithmException;

import java.util.concurrent.LinkedBlockingDeque;

import comp90015.idxsrv.filemgr.FileMgr;
import comp90015.idxsrv.message.*;
import comp90015.idxsrv.server.IOThread;
import comp90015.idxsrv.server.IndexElement;
import comp90015.idxsrv.textgui.ISharerGUI;

import comp90015.idxsrv.filemgr.FileDescr;
import comp90015.idxsrv.textgui.PeerGUI;



/**
 * Skeleton Peer class to be completed for Project 1.
 * TODO close file
 * @author aaron
 *
 */
public class Peer implements IPeer {

	public enum SHARE_STATUS{
		SEEDING, STOPPED
	}

	private IOThread ioThread;

	private Thread shareMgrThread;

	private LinkedBlockingDeque<Socket> incomingConnections;

	private PeerGUI tgui;

	private String basedir;

	private int timeout;

	private int port;

	public Peer(int port, String basedir, int socketTimeout, ISharerGUI tgui) throws IOException {
		this.tgui= (PeerGUI) tgui;
		this.port=port;
		this.timeout=socketTimeout;
		this.basedir=new File(basedir).getCanonicalPath();
		incomingConnections = new LinkedBlockingDeque<>();
		ioThread = new IOThread(port,incomingConnections,socketTimeout,tgui);
		ioThread.start();

		shareMgrThread = new Thread(new ShareMgrThread(incomingConnections, (PeerGUI) tgui));
		shareMgrThread.start();

	}

	public void shutdown() throws InterruptedException, IOException {
		ioThread.shutdown();
		ioThread.interrupt();
		ioThread.join();

		shareMgrThread.interrupt();
		shareMgrThread.join();
	}

	/*
	 * Students are to implement the interface below.
	 */

	/**
	 * Send a {@link comp90015.idxsrv.message.ShareRequest} to index server.
	 * Set up {@link comp90015.idxsrv.server.IOThread} to listen to incoming {@link comp90015.idxsrv.message.BlockRequest}.
	 * Need to create a new thread for seeding.
	 * @param file The file to share index to server
	 * @param idxAddress Address of index server
	 * @param idxPort Listening port of index server
	 * @param idxSecret password for index server
	 * @param shareSecret password for the file
	 */
	@Override
	public void shareFileWithIdxServer(File file, InetAddress idxAddress, int idxPort, String idxSecret,
			String shareSecret) {
		try {
			String relativePath = getRelativePath(file.getCanonicalPath());
			if(!checkPath(relativePath)){
				tgui.logError("Path not in basedir");
				return;
			}
			FileMgr fileMgr = new FileMgr(relativePath);

			FileDescr fileDesc = fileMgr.getFileDescr();
			ShareRequest sReq = new ShareRequest(fileDesc, relativePath, shareSecret, port);

			Message rep = reqServer(sReq, idxAddress, idxPort, idxSecret);
			if(rep.getClass() == ErrorMsg.class){
				tgui.logError("share failed: " + rep.toString());
				return;
			} else if (rep.getClass() == ShareReply.class) {
				ShareReply sRep = (ShareReply) rep;

				ShareRecord sRec = new ShareRecord( fileMgr, sRep.numSharers, SHARE_STATUS.SEEDING.name(), idxAddress, idxPort, idxSecret, shareSecret);

				tgui.logInfo("Share succeed; number of sharers: " + sRep.numSharers);

				tgui.addShareRecord(relativePath, sRec);
			}

		}
		catch (IOException e){
			tgui.logError("shareFileWithIdxServer: " + e.getMessage());
		}
		catch (NoSuchAlgorithmException e){
			tgui.logError("shareFileWithIdxServer: " + e.getMessage());
		}
	}

	/**
	 * Send a {@link comp90015.idxsrv.message.SearchRequest} to index server.
	 * Parse the {@link comp90015.idxsrv.message.SearchReply}.
	 * @param keywords
	 * @param maxhits
	 * @param idxAddress
	 * @param idxPort
	 * @param idxSecret
	 */
	@Override
	public void searchIdxServer(String[] keywords,
			int maxhits,
			InetAddress idxAddress,
			int idxPort,
			String idxSecret) {

		try {
			Socket socket = new Socket(idxAddress, idxPort);
			SocketMgr sMgr = new SocketMgr(socket);

			SearchRequest sReq = new SearchRequest(maxhits, keywords);

			WelcomeMsg welcomeMsg = (WelcomeMsg) sMgr.readMsg();
			tgui.logInfo("Server welcome: " + welcomeMsg.toString());

			AuthenticateRequest aReq = new AuthenticateRequest(idxSecret);
			sMgr.writeMsg(aReq);
			AuthenticateReply aRep = (AuthenticateReply) sMgr.readMsg();
			if( !aRep.success){
				throw new IOException("Authentication Failure");
			}
			sMgr.writeMsg(sReq);
			SearchReply sRep = (SearchReply) sMgr.readMsg();
			IndexElement[] hits = sRep.hits;
			if (hits.length == 0){
				tgui.logError("No such file found.");
				return;
			} else {
				tgui.logInfo("Search success; numbers of seeders: " + sRep.seedCounts.toString());
				//update log
				//one record or multiple? multiple
				for(int i = 0; i < hits.length; i++){
					SearchRecord sRec = new SearchRecord(hits[i].fileDescr, sRep.seedCounts[i], idxAddress, idxPort, idxSecret, hits[i].secret);
					tgui.addSearchHit(hits[i].filename, sRec);
				}

			}

		}
		catch (IOException e){
			tgui.logError("searchIdxServer: " + e.getMessage());
		}
		catch (JsonSerializationException e) {
			tgui.logError("searchIdxServer: " + e.getMessage());
		}

	}

	/**
	 * Send a {@link comp90015.idxsrv.message.DropShareRequest} to index server.
	 * Parse the {@link comp90015.idxsrv.message.DropShareReply}.
	 * Stop listening to incoming {@link comp90015.idxsrv.message.BlockRequest}.
	 * @param relativePathname the filename relative to the `basedir`
	 * @param shareRecord describes the shared file to drop
	 * @return boolean indicating if drop is successful
	 */
	@Override
	public boolean dropShareWithIdxServer(String relativePathname, ShareRecord shareRecord) {

		DropShareRequest dReq = new DropShareRequest(relativePathname, shareRecord.fileMgr.getFileDescr().getFileMd5(), shareRecord.sharerSecret, port);

		Message msg = reqServer(dReq, shareRecord.idxSrvAddress, shareRecord.idxSrvPort, shareRecord.idxSrvSecret);
		if(msg.getClass() ==ErrorMsg.class){
			tgui.logInfo("Share dropped failed: " + msg.toString());
			return false;
		}
		tgui.logInfo("Share dropped successfully");


		//update ShareRecord
		//note: textgui will remove it anyway, so useless for now
		ShareRecord droppedRecord = new ShareRecord(shareRecord.fileMgr, shareRecord.numSharers, SHARE_STATUS.STOPPED.name(), shareRecord.idxSrvAddress, shareRecord.idxSrvPort, shareRecord.idxSrvSecret, shareRecord.sharerSecret);
		tgui.addShareRecord(relativePathname, droppedRecord);

		return true;
	}

	/**
	 * Start a session.
	 * Send {@link comp90015.idxsrv.message.BlockRequest}.
	 * parse {@link comp90015.idxsrv.message.BlockReply}.
	 * Verify MD5 Hash value, store file block.
	 * End session.
	 * Manage concurrency, keep record of share and get.
	 * @param relativePathname
	 * @param searchRecord
	 */
	@Override
	public void downloadFromPeers(String relativePathname, SearchRecord searchRecord) {

		IndexElement[] indexElementArray = null;

		FileDescr fileDesc = null;

		fileDesc = searchRecord.fileDescr;
		LookupRequest lReq = new LookupRequest(relativePathname, fileDesc.getFileMd5());

		Message rep = reqServer(lReq, searchRecord.idxSrvAddress, searchRecord.idxSrvPort, searchRecord.idxSrvSecret);
		if(rep.getClass() == ErrorMsg.class){
			tgui.logError("download failed: " + rep.toString());
			return;
		} else if (rep.getClass() == LookupReply.class) {
			LookupReply lRep = (LookupReply) rep;
			indexElementArray = lRep.hits;

		}



		if(indexElementArray == null || indexElementArray.length == 0){
			tgui.logInfo("no lookup result");
			return;

		}

		//create FileMgr for file
		String filename = indexElementArray[0].filename;
		FileMgr fileMgr;
		try{
			fileMgr = new FileMgr(filename + "_test_download", fileDesc);
		} catch (IOException e) {
			tgui.logDebug("downloadFromPeers fileMgr creation" + e.getMessage());
			return;
		} catch (NoSuchAlgorithmException e) {
			tgui.logDebug("downloadFromPeers fileMgr creation" + e.getMessage());
			return;
		}

		//start threads to download from peers
		Thread downloadThread = new Thread(new DownloadThread(fileMgr, indexElementArray, (PeerGUI) tgui));
		//maybe no need to join.
		downloadThread.setDaemon(true);
		downloadThread.start();
	}


	private String getRelativePath(String canonicalPath){
		Path pathAbsolute = Paths.get(canonicalPath);
		Path pathBase = Paths.get(basedir);
		Path pathRelative = pathBase.relativize(pathAbsolute);
		return pathRelative.toString();
	}

	private boolean checkPath(String relativePath){
		if(relativePath.length() >=2 && relativePath.substring(0, 2).equals("..")){
			return false;
		}
		return true;
	}

	//return message, error or reply
	private Message reqServer(Message reqMsg, InetAddress idxAddress, int idxPort, String idxSrvSecret){
		Socket socket = null;
		SocketMgr sMgr = null;
		try {
			socket = new Socket(idxAddress, idxPort);
			sMgr = new SocketMgr(socket);
		} catch (IOException e) {
			return new ErrorMsg(e.getMessage());
		}


		try {
			WelcomeMsg welcomeMsg = (WelcomeMsg) sMgr.readMsg();
			tgui.logInfo("Server welcome: " + welcomeMsg.toString());
			AuthenticateRequest aReq = new AuthenticateRequest(idxSrvSecret);
			sMgr.writeMsg(aReq);
			AuthenticateReply aRep = (AuthenticateReply) sMgr.readMsg();
			if (!aRep.success) {
				sMgr.close();
				return new ErrorMsg("Authentication Failure");
			}
			sMgr.writeMsg(reqMsg);
			return sMgr.readMsg();

		}catch (Exception e) {
			try {
				sMgr.close();
			} catch (IOException ex) {
				tgui.logDebug("cannot close sMgr when return error");
			}
			tgui.logError(e.getMessage());
			return new ErrorMsg(e.getMessage());
		}
	}

}
