package comp90015.idxsrv.peer;


import java.io.*;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.LinkedBlockingDeque;

import comp90015.idxsrv.filemgr.FileMgr;
import comp90015.idxsrv.message.*;
import comp90015.idxsrv.server.IOThread;
import comp90015.idxsrv.textgui.ISharerGUI;

import comp90015.idxsrv.filemgr.FileDescr;
/**
 * Skeleton Peer class to be completed for Project 1.
 * @author aaron
 *
 */
public class Peer implements IPeer {

	private IOThread ioThread;

	private Thread shareMgrThread;

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
		incomingConnections = new LinkedBlockingDeque<>();
		ioThread = new IOThread(port,incomingConnections,socketTimeout,tgui);
		ioThread.start();

		shareMgrThread = new Thread(new ShareMgrThread(incomingConnections, tgui));
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
			tgui.logDebug(file.getCanonicalPath());
			tgui.logDebug(file.getPath());
			FileMgr fileMgr = new FileMgr(file.getPath());

			FileDescr fileDesc = fileMgr.getFileDescr();
			ShareRequest sReq = new ShareRequest(fileDesc, file.getName(), shareSecret, port);

			Socket socket = new Socket(idxAddress, idxPort);
			InputStream inputStream = socket.getInputStream();
			OutputStream outputStream = socket.getOutputStream();
			BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
			BufferedWriter bufferedWriter = new BufferedWriter(new OutputStreamWriter(outputStream, StandardCharsets.UTF_8));

			WelcomeMsg welcomeMsg = (WelcomeMsg) readMsg(bufferedReader);
			tgui.logInfo("Server welcome: " + welcomeMsg.toString());

			AuthenticateRequest aReq = new AuthenticateRequest(idxSecret);
			writeMsg(bufferedWriter, aReq);
			AuthenticateReply aRep = (AuthenticateReply) readMsg(bufferedReader);
			if( !aRep.success){
				throw new IOException("Authentication Failure");
			}
			else{
				writeMsg(bufferedWriter, sReq);
				ShareReply sRep = (ShareReply) readMsg(bufferedReader);

				ShareRecord sRec = new ShareRecord( fileMgr, sRep.numSharers, "Seeding", idxAddress, idxPort, idxSecret, shareSecret);

				tgui.logInfo("Share succeed; number of sharers: " + sRep.numSharers);
				//TODO make it relative path.
				tgui.addShareRecord(file.getPath(), sRec);
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

		//set up sharing threads
		//get connections from incomingConnections


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
	 * @return boolean indicating if drop is successful
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


	/*
	 * Util methods for writing and reading messages.
	 * copied from server
	 */

	private void writeMsg(BufferedWriter bufferedWriter, Message msg) throws IOException {
		tgui.logDebug("sending: "+msg.toString());
		bufferedWriter.write(msg.toString());
		bufferedWriter.newLine();
		bufferedWriter.flush();
	}

	private Message readMsg(BufferedReader bufferedReader) throws IOException, JsonSerializationException {
		String jsonStr = bufferedReader.readLine();
		if(jsonStr!=null) {
			Message msg = (Message) MessageFactory.deserialize(jsonStr);
			tgui.logDebug("received: "+msg.toString());
			return msg;
		} else {
			throw new IOException();
		}
	}

}
