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
			FileMgr fileMgr = new FileMgr(relativePath);

			FileDescr fileDesc = fileMgr.getFileDescr();
			ShareRequest sReq = new ShareRequest(fileDesc, file.getName(), shareSecret, port);

			Socket socket = new Socket(idxAddress, idxPort);
			SocketMgr sMgr = new SocketMgr(socket);


			WelcomeMsg welcomeMsg = (WelcomeMsg) sMgr.readMsg();
			tgui.logInfo("Server welcome: " + welcomeMsg.toString());

			AuthenticateRequest aReq = new AuthenticateRequest(idxSecret);
			sMgr.writeMsg(aReq);
			AuthenticateReply aRep = (AuthenticateReply) sMgr.readMsg();
			if( !aRep.success){
				socket.close();
				throw new IOException("Authentication Failure");

			}
			else{
				sMgr.writeMsg(sReq);
				ShareReply sRep = (ShareReply) sMgr.readMsg();
				socket.close();

				ShareRecord sRec = new ShareRecord( fileMgr, sRep.numSharers, "Seeding", idxAddress, idxPort, idxSecret, shareSecret);

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
		catch (JsonSerializationException e) {
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
				tgui.logInfo("Search success; numbers of seeders: " + sRep.seedCounts);
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
		try {
			Socket socket = new Socket(shareRecord.idxSrvAddress, shareRecord.idxSrvPort);
			SocketMgr sMgr = new SocketMgr(socket);

			DropShareRequest dSReq = new DropShareRequest(relativePathname, shareRecord.fileMgr.getFileDescr().getFileMd5(), shareRecord.sharerSecret, shareRecord.idxSrvPort);

			WelcomeMsg welcomeMsg = (WelcomeMsg) sMgr.readMsg();
			tgui.logInfo("Server welcome: " + welcomeMsg.toString());

			AuthenticateRequest aReq = new AuthenticateRequest(shareRecord.idxSrvSecret);
			sMgr.writeMsg(aReq);
			AuthenticateReply aRep = (AuthenticateReply) sMgr.readMsg();
			if( !aRep.success){
				throw new IOException("Authentication Failure");
			}
			sMgr.writeMsg(dSReq);
			DropShareReply dSRep = (DropShareReply) sMgr.readMsg();
			tgui.logInfo("Share dropped successfully");
		}
		catch (IOException e){
			tgui.logError("dropShareWithIdxServer: " + e.getMessage());
			return false;
		}
		catch (JsonSerializationException e) {
			tgui.logError("dropShareWithIdxServer: " + e.getMessage());
			return false;
		}

		//drop ShareRecord
		tgui.getShareRecords().remove(relativePathname);

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
		//tgui.logError("downloadFromPeers unimplemented");
		IndexElement[] indexElementArray = null;

		FileDescr fileDesc = null;
		try {
			fileDesc = searchRecord.fileDescr;
			LookupRequest lReq = new LookupRequest(relativePathname, fileDesc.getFileMd5());
			Socket socket = new Socket(searchRecord.idxSrvAddress, searchRecord.idxSrvPort);
			SocketMgr sMgr = new SocketMgr(socket);

			sMgr.writeMsg(lReq);
			LookupReply lRep = (LookupReply) sMgr.readMsg();

			indexElementArray = lRep.hits;

		} catch (IOException e) {
			tgui.logError("downloadFromPeers Lookup Failure: " + e.getMessage());
		} catch (JsonSerializationException e) {
			tgui.logError("downloadFromPeers Lookup Failure: " + e.getMessage());
		}

		if(indexElementArray == null || indexElementArray.length == 0){
			//TODO handle error
			return;
			//throw new IOException("Lookup Reply return 0 result");
		}

		//create FileMgr for file
		String filename = indexElementArray[0].filename;
		FileMgr fileMgr;
		try{
			fileMgr = new FileMgr(filename, fileDesc);
		} catch (IOException e) {
			throw new RuntimeException(e);
		} catch (NoSuchAlgorithmException e) {
			throw new RuntimeException(e);
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

}
