package comp90015.idxsrv.peer;


import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.util.concurrent.LinkedBlockingDeque;

import comp90015.idxsrv.server.IOThread;
import comp90015.idxsrv.textgui.ISharerGUI;

/**
 * Skeleton Peer class to be completed for Project 1.
 * @author aaron
 *
 */
public class Peer implements IPeer {

	private IOThread ioThread;
	
	private LinkedBlockingDeque<Socket> incomingConnections;
	
	private ISharerGUI tgui;
	
	private String basedir;
	
	private int timeout;
	
	private int port;
	
	public Peer(int port, String basedir, int socketTimeout, ISharerGUI tgui) throws IOException {
		this.tgui=tgui;
		this.port=port;
		this.timeout=socketTimeout;
		this.basedir=new File(basedir).getCanonicalPath();
		ioThread = new IOThread(port,incomingConnections,socketTimeout,tgui);
		ioThread.start();
	}
	
	public void shutdown() throws InterruptedException, IOException {
		ioThread.shutdown();
		ioThread.interrupt();
		ioThread.join();
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
		tgui.logError("shareFileWithIdxServer unimplemented");
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
		tgui.logError("searchIdxServer unimplemented");
	}

	/**
	 * Send a {@link comp90015.idxsrv.message.DropShareRequest} to index server.
	 * Parse the {@link comp90015.idxsrv.message.DropShareReply}.
	 * Stop listening to incoming {@link comp90015.idxsrv.message.BlockRequest}.
	 * @param relativePathname the filename relative to the `basedir`
	 * @param shareRecord describes the shared file to drop
	 * @return
	 */
	@Override
	public boolean dropShareWithIdxServer(String relativePathname, ShareRecord shareRecord) {
		tgui.logError("dropShareWithIdxServer unimplemented");
		return false;
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
		tgui.logError("downloadFromPeers unimplemented");
	}
	
}
